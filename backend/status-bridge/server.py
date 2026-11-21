#!/usr/bin/env python3
"""TLS-only private listener. No access logging and no mutation endpoints."""
import argparse
import ipaddress
import json
import os
import ssl
import threading
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from adapters import Transport, UpstreamError, credential, credential_path
from bridge import Bridge, Denied


def private_bind(value):
    address = ipaddress.ip_address(value)
    if address.is_unspecified or not (address.is_private or address in ipaddress.ip_network("100.64.0.0/10")):
        raise ValueError("Bind to an explicit private or loopback IP")
    return value


def handler(bridge):
    slots = threading.BoundedSemaphore(16)
    class Handler(BaseHTTPRequestHandler):
        server_version = "TrixarrStatusBridge/1"
        sys_version = ""
        def log_message(self, *args):
            pass  # Neither auth headers nor request URLs can reach an access log.
        def answer(self, status, body):
            data = json.dumps(body, allow_nan=False, separators=(",", ":")).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        def do_GET(self):
            if not slots.acquire(False):
                self.answer(503, {"error": "BUSY"}); return
            try:
                if len(self.path) > 4096:
                    raise Denied(400)
                user, cookie = bridge.authenticate(self.headers.get("Authorization", ""))
                target = urllib.parse.urlsplit(self.path)
                query = urllib.parse.parse_qs(target.query, strict_parsing=True) if target.query else {}
                if target.path == "/api/v1/health" and not query:
                    self.answer(200, {"status": "ok", "version": 1, "authenticated": True})
                    return
                parts = target.path.strip("/").split("/")
                episode_only = False
                if target.path == "/api/v1/requests/status":
                    if set(query) - {"ids"}:
                        raise Denied(400)
                    ids = [int(i) for i in query["ids"][0].split(",")] if "ids" in query else None
                elif len(parts) == 5 and parts[:3] == ["api", "v1", "requests"] and parts[4] in {"status", "episodes"} and not query:
                    ids = [int(parts[3])]
                    episode_only = parts[4] == "episodes"
                else:
                    raise Denied(404)
                result = bridge.statuses(user, cookie, self.headers.get("X-Jellyfin-Token"), ids)
                if episode_only:
                    request = result["requests"][0]
                    result = {"requestId": request["requestId"], "episodes": request["episodes"], "seasons": request["seasons"], "observedAt": request["observedAt"]}
                self.answer(200, result)
            except Denied as error:
                self.answer(error.status, {"error": {401: "AUTH_REQUIRED", 403: "FORBIDDEN", 404: "NOT_FOUND", 400: "BAD_REQUEST"}.get(error.status, "UPSTREAM_UNAVAILABLE")})
            except (ValueError, KeyError, IndexError, TypeError):
                self.answer(400, {"error": "BAD_REQUEST"})
            except UpstreamError:
                self.answer(503, {"error": "UPSTREAM_UNAVAILABLE"})
            except Exception:
                self.answer(503, {"error": "STATUS_UNAVAILABLE"})
            finally:
                slots.release()
    return Handler


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bind", default="127.0.0.1", type=private_bind)
    parser.add_argument("--port", default=8443, type=int)
    args = parser.parse_args()
    urls = {name: os.environ.get(name.upper() + "_URL") for name in ["seerr", "radarr", "sonarr", "qbit", "jellyfin"]}
    if not urls["seerr"]:
        raise SystemExit("SEERR_URL is required")
    for base in filter(None, urls.values()):
        url = urllib.parse.urlsplit(base)
        if url.scheme not in {"http", "https"} or not url.hostname or url.username or url.password or url.query or url.fragment:
            raise SystemExit("Invalid upstream URL")
    secrets = {name: credential(name) for name in ["radarr_key", "sonarr_key", "qbit_username", "qbit_password"]}
    certificate = credential_path("tls_cert", required=True)
    private_key = credential_path("tls_key", required=True)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(certificate, private_key)
    server = ThreadingHTTPServer((args.bind, args.port), handler(Bridge(Transport(urls, secrets))))
    server.socket = context.wrap_socket(server.socket, server_side=True)
    server.serve_forever(poll_interval=0.5)

if __name__ == "__main__":
    main()
