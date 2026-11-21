"""Authenticated status aggregation, independent of HTTP hosting."""
from datetime import datetime, timezone
from concurrent.futures import ThreadPoolExecutor
import base64
import re
import urllib.parse
from adapters import UpstreamError
from model import classify, date, identity_match, series_summary, stamp, unknown_eta, matching_torrent, transfer


class Denied(Exception):
    def __init__(self, status=401):
        self.status = status


class Bridge:
    def __init__(self, transport, clock=lambda: datetime.now(timezone.utc)):
        self.transport, self.clock = transport, clock

    def authenticate(self, authorization):
        try:
            scheme, encoded = authorization.split(" ", 1)
            if scheme != "Seerr" or len(encoded) > 8192:
                raise ValueError()
            cookie = base64.b64decode(encoded, validate=True).decode("ascii")
            if not re.fullmatch(r"connect\.sid=[^\s;\r\n]+", cookie):
                raise ValueError()
        except (ValueError, UnicodeError, AttributeError):
            raise Denied() from None
        try:
            user = self.transport.get("seerr", "api/v1/auth/me", {"Cookie": cookie}, cache=False)
        except UpstreamError as error:
            raise Denied(401 if error.status in {401, 403} else 503) from None
        if not isinstance(user, dict) or type(user.get("id")) is not int or user["id"] <= 0:
            raise Denied()
        return user, cookie

    def authorized_requests(self, user, cookie, ids=None):
        headers = {"Cookie": cookie}
        if ids is not None:
            if not ids or len(ids) > 25 or any(not isinstance(i, int) or i <= 0 for i in ids):
                raise Denied(400)
            def owned_request(request_id):
                try:
                    request = self.transport.get("seerr", f"api/v1/request/{request_id}", headers, cache=False)
                except UpstreamError as error:
                    raise Denied(404 if error.status in {403, 404} else 503) from None
                # Even a Seerr administrator sees only their own requests through this API.
                if request.get("id") != request_id or request.get("requestedBy", {}).get("id") != user["id"]:
                    raise Denied(404)
                return request
            with ThreadPoolExecutor(max_workers=4) as pool:
                return list(pool.map(owned_request, sorted(set(ids))))

        response = self.transport.get("seerr", "api/v1/request?" + urllib.parse.urlencode({"requestedBy": user["id"], "take": 25, "skip": 0, "sort": "added"}), headers, cache=False)
        return [r for r in response.get("results", []) if r.get("requestedBy", {}).get("id") == user["id"]][:25]

    def statuses(self, user, cookie, jellyfin_token=None, ids=None):
        requests = self.authorized_requests(user, cookie, ids)
        linked = False
        identity_issue = None
        if jellyfin_token and user.get("jellyfinUserId"):
            try:
                jf = self.transport.get("jellyfin", "Users/Me", {"X-Emby-Token": jellyfin_token}, cache=False)
                linked = str(jf.get("Id", "")).replace("-", "").lower() == str(user["jellyfinUserId"]).replace("-", "").lower()
            except UpstreamError as error:
                identity_issue = "JELLYFIN_AUTH_REQUIRED" if error.status in {401, 403} else "JELLYFIN_UNAVAILABLE"
        with ThreadPoolExecutor(max_workers=4) as pool:
            values = list(pool.map(lambda request: self.status(request, user, cookie, jellyfin_token if linked else None), requests))
        if identity_issue:
            for value in values:
                value["issues"].append(identity_issue)
        return {"requests": values, "limit": 25, "observedAt": stamp(self.clock())}

    def status(self, request, user, cookie, jellyfin_token):
        now = self.clock()
        errors = set()
        def read(service, path, fallback):
            try:
                value = self.transport.get(service, path)
                if not isinstance(value, type(fallback)) or isinstance(value, list) and any(not isinstance(v, dict) for v in value):
                    raise UpstreamError(service)
                if isinstance(value, dict) and "records" in value and (not isinstance(value["records"], list) or any(not isinstance(v, dict) for v in value["records"])):
                    raise UpstreamError(service)
                return value
            except UpstreamError:
                errors.add(service.upper() + "_UNAVAILABLE")
                return fallback
        media = request.get("media", {})
        kind = media.get("mediaType")
        tmdb, tvdb = media.get("tmdbId"), media.get("tvdbId")
        public = {"requestId": request["id"], "mediaType": kind, "tmdbId": tmdb, "tvdbId": tvdb,
                  "seerrMediaStatus": media.get("status"), "approvalStatus": request.get("status"),
                  "episodes": [], "seasons": [], "issues": [], "observedAt": stamp(now)}
        if kind not in {"movie", "tv"} or not isinstance(tmdb, int) or tmdb <= 0:
            public["status"] = classify(request, None, [], [], now, now)
            public["issues"] = ["IDENTITY_UNKNOWN"]
            return public
        if kind == "tv" and not tvdb:
            try:
                details = self.transport.get("seerr", f"api/v1/tv/{tmdb}", {"Cookie": cookie}, cache=False)
                tvdb = details.get("externalIds", {}).get("tvdbId") or details.get("tvdbId")
                public["tvdbId"] = tvdb
            except UpstreamError:
                errors.add("SEERR_UNAVAILABLE")
        service = "radarr" if kind == "movie" else "sonarr"
        resource = "movie" if kind == "movie" else "series"
        identity_key, identity = ("tmdbId", tmdb) if kind == "movie" else ("tvdbId", tvdb)
        candidates = read(service, f"api/v3/{resource}?" + urllib.parse.urlencode({identity_key: identity or 0}), [])
        item = identity_match(candidates, identity_key, identity)
        queues, history, commands = [], [], []
        if item:
            query = {"movieIds" if kind == "movie" else "seriesIds": item["id"], "pageSize": 250,
                     "includeMovie" if kind == "movie" else "includeEpisode": "true"}
            queues = read(service, "api/v3/queue?" + urllib.parse.urlencode(query), {}).get("records", [])
            # Enforce identity again, independent of upstream query filtering.
            queues = [q for q in queues if q.get("movieId" if kind == "movie" else "seriesId") == item["id"]]
            history = read(service, f"api/v3/history/{resource}?{resource}Id={item['id']}", [])
            commands = read(service, "api/v3/command", [])
        try:
            torrents = self.transport.torrents([q.get("downloadId") for q in queues])
            if not isinstance(torrents, list) or any(not isinstance(t, dict) for t in torrents):
                raise UpstreamError("qbit")
        except UpstreamError:
            torrents = []
            errors.add("QBIT_UNAVAILABLE")
        observed = self.clock()
        local = self.jellyfin_items(user, jellyfin_token, kind, tmdb, tvdb, errors)
        def active_search(episode_id=None, season_number=None):
            for command in commands:
                body = command.get("body", {})
                if command.get("status") != "started":
                    continue
                name = command.get("name", body.get("name"))
                if kind == "movie" and name == "MoviesSearch" and item and item["id"] in body.get("movieIds", []):
                    return True
                if kind == "tv" and item and (name == "SeriesSearch" or name == "SeasonSearch" and body.get("seasonNumber") == season_number) and body.get("seriesId") == item["id"]:
                    return True
                if episode_id and name == "EpisodeSearch" and episode_id in body.get("episodeIds", []):
                    return True
            return False
        if kind == "movie":
            verified = len(local) == 1
            public["status"] = classify(request, item, queues, torrents, observed, self.clock(), verified,
                imported=bool(item and item.get("hasFile")), release=date(item.get("digitalRelease") or item.get("physicalRelease") or item.get("inCinemas")) if item else None,
                searching=active_search())
        else:
            requested_seasons = {s.get("seasonNumber") for s in request.get("seasons", []) if isinstance(s.get("seasonNumber"), int) and s["seasonNumber"] > 0}
            episodes = read(service, f"api/v3/episode?seriesId={item['id']}", []) if item else []
            files = read(service, f"api/v3/episodefile?seriesId={item['id']}", []) if item else []
            file_ids = {f.get("id") for f in files}
            shared_ids = {}
            for queue in queues:
                queue_ids = set(queue.get("episodeIds", []))
                if queue.get("episodeId") is not None:
                    queue_ids.add(queue["episodeId"])
                shared_ids.setdefault(queue.get("downloadId"), set()).update(queue_ids)
            relevant_ids = set()
            for episode in episodes:
                season, number = episode.get("seasonNumber"), episode.get("episodeNumber")
                if season not in requested_seasons or not isinstance(number, int) or number <= 0:
                    continue
                eq = [q for q in queues if q.get("episodeId") == episode.get("id") or episode.get("id") in q.get("episodeIds", [])]
                verified = (season, number) in local
                status = classify(request, episode, eq, torrents, observed, self.clock(), verified,
                    imported=episode.get("hasFile") is True and episode.get("episodeFileId") in file_ids,
                    release=date(episode.get("airDateUtc")), searching=active_search(episode.get("id"), season))
                relevant_ids.update(q.get("downloadId") for q in eq)
                if any(len(shared_ids.get(q.get("downloadId"), set())) > 1 for q in eq):
                    status.update(progress=None, bytesPerSecond=None, remainingBytes=None, downloadEta=unknown_eta())
                    status.pop("transfers", None)
                public["episodes"].append({"seasonNumber": season, "episodeNumber": number, "status": status})
            public["seasons"] = [series_summary([e for e in public["episodes"] if e["seasonNumber"] == n]) for n in sorted(requested_seasons)]
            available = sum(e["status"]["jellyfinVerified"] for e in public["episodes"])
            public["status"] = classify(request, item, [], [], observed, self.clock())
            if public["seasons"] and all(s["totalEpisodes"] > 0 and s["availableEpisodes"] == s["totalEpisodes"] for s in public["seasons"]):
                public["status"].update(state="AVAILABLE", jellyfinVerified=True)
            elif available:
                public["status"]["state"] = "PARTIALLY_AVAILABLE"
            elif public["episodes"]:
                states = {e["status"]["state"] for e in public["episodes"]}
                public["status"]["state"] = next((s for s in ["FAILED", "DOWNLOADING", "IMPORTING", "WAITING_FOR_JELLYFIN", "PAUSED", "STALLED", "QUEUED", "WAITING_FOR_RELEASE"] if s in states), "UNKNOWN")
            public["status"]["transfers"] = [transfer(torrent, observed, self.clock()) for download_id in sorted(filter(None, relevant_ids))
                if (torrent := matching_torrent(download_id, torrents)) is not None]
            # Pack progress is shown once per transfer; never attributed to an individual episode or averaged.
        latest = max((h for h in history if date(h.get("date"))), key=lambda h: date(h["date"]), default={})
        if latest.get("eventType") == "downloadFailed" and public["status"]["state"] in {"APPROVED", "UNKNOWN"}:
            public["status"]["state"] = "FAILED"
        public["observedAt"] = stamp(observed)
        public["issues"] = sorted(errors)
        return public

    def jellyfin_items(self, user, token, kind, tmdb, tvdb, errors):
        if not token:
            errors.add("JELLYFIN_NOT_VERIFIED")
            return []
        headers = {"X-Emby-Token": token}
        uid = str(user["jellyfinUserId"])
        if not re.fullmatch(r"[0-9a-fA-F-]{32,36}", uid):
            return []
        provider, value = ("tmdb", tmdb) if kind == "movie" else ("tvdb", tvdb)
        try:
            query = urllib.parse.urlencode({"Recursive": "true", "IncludeItemTypes": "Movie" if kind == "movie" else "Series",
                "AnyProviderIdEquals": f"{provider}.{value}", "Fields": "ProviderIds,MediaSources", "Limit": 10})
            response = self.transport.get("jellyfin", f"Users/{uid}/Items?{query}", headers, cache=False)
            matches = [i for i in response.get("Items", []) if str({k.lower(): v for k, v in i.get("ProviderIds", {}).items()}.get(provider, "")) == str(value) and i.get("Type") == ("Movie" if kind == "movie" else "Series") and i.get("LocationType") != "Virtual"]
            if len(matches) != 1:
                return []
            if kind == "movie":
                return matches if matches[0].get("MediaSources") else []
            sid = matches[0].get("Id", "")
            if not re.fullmatch(r"[0-9a-fA-F-]{32,36}", sid):
                return []
            response = self.transport.get("jellyfin", f"Shows/{sid}/Episodes?" + urllib.parse.urlencode({"UserId": uid, "Fields": "MediaSources", "Limit": 2000}), headers, cache=False)
            return {(e.get("ParentIndexNumber"), e.get("IndexNumber")) for e in response.get("Items", []) if e.get("Type") == "Episode" and e.get("LocationType") != "Virtual" and e.get("MediaSources")}
        except UpstreamError:
            errors.add("JELLYFIN_UNAVAILABLE")
            return []
