# Trixarr Status Bridge V1

Dependency-free Python3.11+ service for a **private, authenticated HTTPS endpoint**.
No database, torrent engine, download controls or automatic media requests.

Run the self-contained synthetic suite:

```sh
python3 -m unittest discover -s backend/status-bridge/tests -v
```

Tests create an ephemeral localhost TLS certificate, explicitly trust that test CA,
and start synthetic upstream servers. They do not use LAN services or real secrets.
Production and Android retain normal TLS certificate/hostname verification.

## Deployment prerequisites

Use a host that can reach the configured private Seerr, Radarr, Sonarr, qBittorrent
and Jellyfin origins. Provision a private DNS hostname and valid device-trusted TLS
certificate for that hostname. Do not publish the upstream services or their WebUIs.
The listener rejects unspecified/public bind addresses; allow only the intended
private client network at the host firewall. The Android setting accepts HTTPS only.

Copy this directory to `/opt/trixarr-status-bridge`. Copy `environment.example` to
`/etc/trixarr-status-bridge/environment` and set the explicit private bind address
and the existing Jellyfin origin. This environment file contains **URLs only**.
Install `trixarr-status-bridge.service` using the host's normal systemd workflow.

Provision the unit's named credential files out of band, through the host's secret
management. Never put values in Git, Android resources, shell command arguments or
chat. Unit `LoadCredential` copies credentials into the service's restricted runtime
directory. `LoadCredentialEncrypted` may replace it where the operator already uses
systemd encrypted credentials. The service checks that credential files have no
group/other permissions. Required names:

| Credential | Purpose |
| --- | --- |
| `radarr_key`, `sonarr_key` | Read Arr identity, queues, files, commands and history |
| `qbit_username`, `qbit_password` | Establish qBittorrent session for filtered read-only torrent information |
| `tls_cert`, `tls_key` | Certificate chain and private key, readable only through service credentials |

The unit maps these names from `/etc/credstore/trixarr-*`. Missing Arr/qBittorrent
credentials in the Python component produce unknown/partial status. Provision all
unit credential sources before enabling the unit; missing TLS credentials prevent startup.
No credentials were provisioned by this implementation task. Arr API keys and the
qBittorrent API do not provide endpoint-specific read-only scopes: use dedicated
accounts/network restrictions where supported. The adapter enforces GET-only
business operations, with only qBittorrent session login allowed to use POST.

## Client connection

In Trixarr → Upptäck → Begär → Statusanslutning, enter the private HTTPS base URL.
No service API key or administrator password belongs in this dialog. Android reuses
the active profile's encrypted Seerr session and Jellyfin user token, over verified
TLS, to authenticate and verify availability. Redirects are refused. The bridge
forwards the Seerr session only to the fixed configured Seerr origin and uses a
fixed configured Jellyfin origin, never an origin supplied in the client request.

The server is not automatically installed/enabled until these security prerequisites
exist. Do not configure HTTP, bypass certificate verification, install a test CA on
Shield, or substitute synthetic status for a live connection.

See `docs/architecture/TRIXARR_STATUS_BRIDGE_V1.md` in the repository for the API,
status/ETA rules and authorization model.
