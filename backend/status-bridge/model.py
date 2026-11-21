"""Pure correlation/status rules. Public projections never contain upstream payloads."""
from datetime import datetime, timezone, timedelta
import math

STATES = {"REQUESTED", "AWAITING_APPROVAL", "APPROVED", "WAITING_FOR_RELEASE", "SEARCHING",
          "QUEUED", "DOWNLOADING", "DOWNLOAD_COMPLETE", "PAUSED", "STALLED", "IMPORTING",
          "WAITING_FOR_JELLYFIN", "PARTIALLY_AVAILABLE", "AVAILABLE", "FAILED", "UNKNOWN", "NOT_MONITORED"}


def stamp(value):
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def date(value):
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return (parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)).astimezone(timezone.utc)
    except (ValueError, AttributeError, TypeError):
        return None


def number(value, minimum=0):
    return value if isinstance(value, (float, int)) and not isinstance(value, bool) and math.isfinite(value) and value >= minimum else None


def unknown_eta():
    return {"estimatedAt": None, "confidence": "UNKNOWN", "source": "ETA_UNKNOWN"}


def transfer(torrent, observed, now):
    """Only an ID-correlated torrent enters this function; no raw names/hashes escape."""
    state = torrent.get("state", "")
    stale = (now - observed).total_seconds() > 30 or observed > now + timedelta(seconds=5)
    progress = number(torrent.get("progress"))
    if progress is not None and progress > 1:
        progress = None
    speed, remaining = number(torrent.get("dlspeed")), number(torrent.get("amount_left"))
    if state in {"error", "missingFiles"}:
        unified = "FAILED"
    elif state in {"pausedDL", "stoppedDL"}:
        unified = "PAUSED"
    elif state == "stalledDL":
        unified = "STALLED"
    elif state == "queuedDL":
        unified = "QUEUED"
    elif state in {"downloading", "forcedDL"}:
        unified = "DOWNLOADING"
    elif state in {"uploading", "stalledUP", "queuedUP", "pausedUP", "stoppedUP", "forcedUP"} and progress == 1:
        unified = "DOWNLOAD_COMPLETE"
    else:
        unified = "UNKNOWN"
    eta = unknown_eta()
    seconds = number(torrent.get("eta"), 1)
    if (not stale and unified == "DOWNLOADING" and speed is not None and speed > 0 and
            remaining is not None and remaining > 0 and progress is not None and progress < 1 and
            seconds is not None and seconds <= 7 * 86400):
        eta = {"estimatedAt": stamp(observed + timedelta(seconds=seconds)), "confidence": "MEDIUM", "source": "QBITTORRENT"}
    return {"state": "UNKNOWN" if stale else unified, "progress": progress, "bytesPerSecond": speed,
            "remainingBytes": remaining, "downloadEta": eta, "availabilityEta": unknown_eta(),
            "observedAt": stamp(observed), "stale": stale}


def matching_torrent(download_id, torrents):
    if not isinstance(download_id, str) or len(download_id) not in {40, 64}:
        return None
    matches = [t for t in torrents if download_id.lower() in {
        str(t.get("hash", "")).lower(), str(t.get("infohash_v1", "")).lower(), str(t.get("infohash_v2", "")).lower()}]
    return matches[0] if len(matches) == 1 else None


def identity_match(values, key, expected):
    if not isinstance(expected, int) or isinstance(expected, bool) or expected <= 0:
        return None
    matches = [v for v in values if v.get(key) == expected]
    return matches[0] if len(matches) == 1 else None


def initial_state(request):
    return {1: "AWAITING_APPROVAL", 2: "APPROVED", 3: "FAILED", 4: "FAILED"}.get(request.get("status"), "REQUESTED")


def classify(request, item, queues, torrents, observed, now, jellyfin=False, imported=False, release=None, searching=False):
    result = {"state": initial_state(request), "progress": None, "bytesPerSecond": None, "remainingBytes": None,
              "downloadEta": unknown_eta(), "availabilityEta": unknown_eta(), "observedAt": stamp(observed),
              "stale": (now - observed).total_seconds() > 30, "jellyfinVerified": bool(jellyfin)}
    if jellyfin:
        result["state"] = "AVAILABLE"
        return result
    if result["stale"]:
        result["state"] = "UNKNOWN"
        return result
    if imported:
        result["state"] = "WAITING_FOR_JELLYFIN"
        return result
    transfers = []
    for queue in queues:
        if queue.get("trackedDownloadStatus") in {"error", "warning"} and queue.get("status") == "failed":
            result["state"] = "FAILED"
            return result
        if queue.get("trackedDownloadState") in {"importPending", "importing"}:
            result["state"] = "IMPORTING"
            return result
        if queue.get("trackedDownloadState") in {"importFailed", "downloadFailed"}:
            result["state"] = "FAILED"
            return result
        torrent = matching_torrent(queue.get("downloadId"), torrents)
        if torrent is not None:
            transfers.append(transfer(torrent, observed, now))
    # Independent transfers must never be averaged into a fictitious whole-request percentage.
    if len(transfers) == 1:
        result.update(transfers[0])
    elif len(transfers) > 1:
        result["state"] = "DOWNLOADING" if any(t["state"] == "DOWNLOADING" for t in transfers) else "UNKNOWN"
    elif queues:
        result["state"] = "QUEUED"
    elif item is None:
        result["state"] = "UNKNOWN" if request.get("status") == 2 else initial_state(request)
    elif item.get("monitored") is False:
        result["state"] = "NOT_MONITORED"
    elif release and release > now:
        result["state"] = "WAITING_FOR_RELEASE"
    elif searching:
        result["state"] = "SEARCHING"
    result["transfers"] = transfers
    return result


def series_summary(episodes):
    total = len(episodes)
    available = sum(e["status"]["jellyfinVerified"] for e in episodes)
    return {"seasonNumber": episodes[0]["seasonNumber"] if episodes else None,
            "availableEpisodes": available, "totalEpisodes": total,
            "state": "AVAILABLE" if total and available == total else "PARTIALLY_AVAILABLE" if available else "UNKNOWN"}
