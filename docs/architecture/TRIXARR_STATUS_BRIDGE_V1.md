# Trixarr Status Bridge V1

## Baseline

Dedicated branch/worktree `feat/trixarr-seerr-download-status-v1`,
`/opt/jellyfintv-seerr-download-status-v1`, based on Seerr Discover
`b87c100252aaaf6392a13912e17f7add1eb9b24f`. Baseline is version0.1.1-beta.2/code10102,
including integrated Beta1.1, Branding V3, Image Quality V1 and accepted playback.
Actual original worktree:117 dirty entries. No applicable parent/repository/subtree
AGENTS.md found. Existing worktrees and unrelated work remain untouched.

No existing Trixarr backend/deployment was found. Implementation adds
`backend/status-bridge`: Python standard-library transport, pure status rules,
authorized aggregation, TLS server, systemd credential unit and synthetic tests.
No database or production credentials are added. No playback engine rewrite.

## Trust and deployment

Android → **verified HTTPS** → private Status Bridge → configured LAN upstreams.
The bridge requires a Seerr session on every request, encoded in
`Authorization: Seerr <base64 session cookie>`. Base64 is transport encoding, not
encryption; HTTPS is mandatory. It validates the session against `GET auth/me`.
Request ownership is checked against upstream `requestedBy.id` for every client
request ID before any download information is fetched. This API deliberately limits
even administrators to their own requests; Seerr's broader administrative view is
not exposed. IDs alone grant no authorization. Seerr/Jellyfin responses are not
cached across users. No access logger records headers, cookies, keys or raw bodies.

For Jellyfin verification, Android supplies its existing user token over TLS. The
bridge checks `/Users/Me` and requires its user ID to equal the Seerr-linked
Jellyfin user ID. Missing/mismatched links cannot establish availability. The
Jellyfin origin is server-configured, preventing client-controlled upstream targets.
Tokens are used in memory only, not stored in the bridge or database.

Arr keys, qBittorrent account and TLS key/certificate are server-only systemd
credentials. Android contains none of these administrator credentials. Arr keys
lack endpoint-specific read-only scopes; the adapter restricts business requests
to GET. The only POST is qBittorrent authentication, never a torrent mutation.
Environment HTTP proxies are ignored for private upstream credentials.
Redirects are denied upstream and client-side to prevent credential forwarding.
TLS certificate verification is never disabled. Only explicit private/loopback
bind addresses are accepted, including private tailnet addresses. No public listener
or upstream exposure was created. Deployment instructions and out-of-band credential
names: [backend README](../../backend/status-bridge/README.md).

## Read integrations

| Source | Supported reads / correlation |
| --- | --- |
| Seerr | `GET /api/v1/auth/me`, `/request/{id}`, scoped `/request?requestedBy=...`, `/tv/{tmdbId}` if TVDB lookup needed. Request ID, owner, approval/media status, TMDB/TVDB and requested seasons retained. Multiple requests remain independent. |
| Radarr | `GET /api/v3/movie?tmdbId=...`, `/queue?movieIds=...&pageSize=250&includeMovie=true`, `/history/movie?movieId=...`, `/command`. Exact unique TMDB identity, own movie ID rechecked in queue. |
| Sonarr | `GET /api/v3/series?tvdbId=...`, `/episode?seriesId=...`, `/episodefile?seriesId=...`, `/queue?seriesIds=...&includeEpisode=true&pageSize=250`, `/history/series?seriesId=...`, `/command`. Exact unique TVDB identity; per-episode ID and requested-season scoping. |
| qBittorrent5.3 | `/api/v2/auth/login` for session only; `GET /api/v2/torrents/info?hashes=...` limited to Arr download IDs. Exact case-insensitive canonical/v1/v2 hash match, never name matching. |
| Jellyfin | Existing server/user identity, provider-filtered user items and series episodes with media-source verification. Provider IDs are compared case-insensitively, virtual/missing-source media excluded. |
| Prowlarr | Optional and not used in V1. No per-request searching inferred from indexer health. |

Primary references:
[Radarr OpenAPI](https://github.com/Radarr/Radarr/blob/develop/src/Radarr.Api.V3/openapi.json),
[Sonarr OpenAPI](https://github.com/Sonarr/Sonarr/blob/develop/src/Sonarr.Api.V3/openapi.json),
[qBittorrent5 Web API](https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-%28qBittorrent-5.0%29).

Upstream URLs are fixed server configuration. Public projections contain request IDs,
external media IDs, season/episode numbers, bounded numeric metrics and timestamps;
no torrent names/hashes, trackers, paths, credentials or raw service payloads.

## API

All routes require authenticated HTTPS; no unauthenticated health exception.

- `GET /api/v1/requests/status?ids=1,2`: batch, maximum25 positive IDs. Without ids,
  returns at most25 of the current user's recent requests. Android chunks the loaded
  request IDs into batches of25 so multiple requests/media are retained.
- `GET /api/v1/requests/{id}/status`: same authorization and projection.
- `GET /api/v1/requests/{id}/episodes`: authorized episode/season projection.
- `GET /api/v1/health`: authenticated service liveness, without credentials or URLs.

401: authentication required;404: absent/not-owned request;400: malformed/bounded
input;503: required service unavailable. Optional service failure yields limited
status plus safe issue codes instead of breaking discovery. Responses use no-store.
Private listener limits active handlers to16; upstream requests have4-second timeouts,
8MB response bounds and at most four parallel aggregation/authorization workers.
Successful Arr reads share a five-second cache with per-key request coalescing.
No persistent status database or torrent management endpoints exist.

## State and ETA rules

Requested/awaiting approval/approved map from Seerr request status. Waiting for release
requires an actual future release/air date. Not monitored is explicit Arr metadata.
Searching requires an active, identity-scoped MoviesSearch, SeriesSearch, SeasonSearch
or EpisodeSearch command; season searches must match the episode's season. General
RSS/indexer activity is not evidence. Queue/error/import flags come from Arr.

Downloading, queued, paused/stopped, stalled and error states use the matched
qBittorrent record.100% means DOWNLOAD_COMPLETE, not AVAILABLE. Importing requires
Arr import-state evidence; imported files with no verified Jellyfin media become
WAITING_FOR_JELLYFIN. AVAILABLE requires user-scoped Jellyfin provider/file evidence.
Unknown identities, stale data or unavailable sources cannot invent progress.

For TV, only requested positive-numbered seasons and their actual episodes are used.
Arr episode files establish import evidence; Jellyfin series provider ID plus season/
episode numbers establish availability. A season reports6/8 when six of eight
requested episodes are verified. Every requested season must have a known nonzero
episode count and all episodes verified before full availability. Empty/missing
seasons cannot make a request fully available.

Independent downloads are not averaged. A shared pack's percentage is shown once in
the transfer list, never as a made-up individual episode percentage. Episodes with
an exact, exclusive transfer can display their real transfer progress; other episodes
show state with unknown percentage. Raw hashes never leave the bridge.

Two separate estimate objects contain absolute UTC estimatedAt, confidence and source;
status contains observedAt/stale. Download ETA uses qBittorrent only for active
DOWNLOADING, positive finite speed/remaining bytes, valid progress<100%, fresh data
(max30 seconds) and ETA1 second–7 days. Paused, stalled, completed, invalid, sentinel
or stale ETA returns ETA_UNKNOWN. No arbitrary import/indexing duration is added.
Playback availability ETA remains unknown without sufficient evidence; import and
Jellyfin indexing are states, not guaranteed completion times. Android uses the
device timezone and Swedish “Idag/Imorgon cirka HH:mm”, rejecting old/past/invalid
estimates. Old status is explicitly labeled rather than presented as live.

## Android TV UI and lifecycle

Begär retains the cinematic hero, artwork rows, image cache, branding and playback.
Cards show compact progress; multiple requests for one title remain independently
viewable. Selection opens a remote-friendly status dialog with metrics, two ETAs,
confidence/source, season counts and expandable episode states. Shared downloads
are separate entries. Playback remains the existing action and additionally requires
fresh bridge availability plus an existing local playback ID; no automatic playback.

Statusanslutning accepts only a credential-free HTTPS base URL and stores it per
profile. No dummy URL or synthetic progress is configured in production. With no
bridge configured the UI displays setup/unavailable information and discovery still
works. Request status does not create or change requests/downloads.

Polling is12 seconds while the status dialog is visible,45 seconds for the request
list; stops when the activity is not resumed, category is inactive, or menu obscures
content. Coroutines/HTTP calls cancel on stale selection/disposal. Successful updates
change keyed status data, not catalog rows or their scroll/focus identity. Dialog
reading/episode controls support D-pad navigation and Back. Playback pause/resume
lifecycle suspends status polling.

## Verification and deployment gates

See [acceptance](../evidence/trixarr-seerr-download-status-v1/ACCEPTANCE.md).
The bridge is implemented and exercised over synthetic upstream HTTP and authenticated
TLS, not deployed against the user's LAN. Current host cannot reach the specified
LAN services, no relevant service credentials exist in inspected credential stores,
and no approved private HTTPS endpoint/certificate has been configured. No unrelated
certificate, public domain, authentication bypass or cleartext fallback is used.
These block live end-to-end/physical acceptance, not building and delivering the code.
