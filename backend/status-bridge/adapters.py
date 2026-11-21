"""Bounded, read-only upstream adapters. Secrets are read from server credential files."""
import http.cookiejar
import json
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


class UpstreamError(Exception):
    def __init__(self, service, status=None):
        self.service, self.status = service, status
        super().__init__(service + " unavailable")  # Never log URL, body, headers, or cookies.


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def credential_path(name, required=False):
    directory = os.environ.get("CREDENTIALS_DIRECTORY", "/run/credentials/trixarr-status-bridge.service")
    path = Path(directory) / name
    if not path.is_file():
        if required:
            raise RuntimeError("Missing server credential: " + name)
        return None
    if path.stat().st_mode & 0o077:
        raise RuntimeError("Credential file permissions must restrict access: " + name)
    return str(path)


def credential(name, required=False):
    path = credential_path(name, required)
    return Path(path).read_text().strip() if path else None


class Transport:
    def __init__(self, urls, secrets, timeout=4):
        self.urls, self.secrets, self.timeout = urls, secrets, timeout
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        self.qbit_cookie = None
        self.lock = threading.Lock()
        self.cache = {}
        self.read_locks = {}

    def call(self, service, path, headers=None, form=None):
        base = self.urls.get(service)
        if not base:
            raise UpstreamError(service)
        # All routes are constructed internally; the client cannot choose an upstream host.
        url = base.rstrip("/") + "/" + path.lstrip("/")
        values = dict(headers or {})
        if service in {"radarr", "sonarr", "prowlarr"}:
            key = self.secrets.get(service + "_key")
            if not key:
                raise UpstreamError(service)
            values["X-Api-Key"] = key
        data = urllib.parse.urlencode(form).encode() if form is not None else None
        if data is not None and not (service == "qbit" and path == "api/v2/auth/login"):
            raise ValueError("Only qBittorrent session authentication may use POST")
        try:
            request = urllib.request.Request(url, headers=values, data=data)
            with self.opener.open(request, timeout=self.timeout) as response:
                raw = response.read(8 * 1024 * 1024 + 1)
                if len(raw) > 8 * 1024 * 1024:
                    raise UpstreamError(service)
                if form is not None:
                    if raw.strip() != b"Ok.":
                        raise UpstreamError(service, 401)
                    self.qbit_cookie = response.headers.get("Set-Cookie", "").split(";", 1)[0]
                    return {}
                return json.loads(raw)
        except urllib.error.HTTPError as error:
            raise UpstreamError(service, error.code) from None
        except (OSError, ValueError):
            raise UpstreamError(service) from None

    def get(self, service, path, headers=None, cache=True):
        # Per-user Seerr/Jellyfin responses are NEVER stored in this service-wide cache.
        cache = cache and service in {"radarr", "sonarr", "prowlarr"}
        key = service, path
        if not cache:
            return self.call(service, path, headers)
        with self.lock:
            if len(self.read_locks) > 512:
                # Existing lock objects stay alive for in-flight callers.
                self.read_locks = {k: v for k, v in self.read_locks.items() if v.locked()}
            read_lock = self.read_locks.setdefault(key, threading.Lock())
        with read_lock:
            with self.lock:
                old = self.cache.get(key)
                if old and time.monotonic() - old[0] < 5:
                    return old[1]
            result = self.call(service, path, headers)
            with self.lock:
                if len(self.cache) > 256:
                    self.cache.clear()
                self.cache[key] = time.monotonic(), result
            return result

    def torrents(self, download_ids):
        ids = sorted({v.lower() for v in download_ids if isinstance(v, str) and re.fullmatch(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}", v)})
        if not ids:
            return []
        # Never fetch the global torrent list. Only Arr-correlated identifiers are queried.
        with self.lock:
            if not self.qbit_cookie:
                username, password = self.secrets.get("qbit_username"), self.secrets.get("qbit_password")
                if not username or not password:
                    raise UpstreamError("qbit")
                self.call("qbit", "api/v2/auth/login", {"Referer": self.urls["qbit"] + "/"}, {"username": username, "password": password})
            cookie = self.qbit_cookie
        try:
            return self.get("qbit", "api/v2/torrents/info?" + urllib.parse.urlencode({"hashes": "|".join(ids)}), {"Cookie": cookie}, cache=False)
        except UpstreamError as error:
            if error.status in {401, 403}:
                self.qbit_cookie = None
            raise
