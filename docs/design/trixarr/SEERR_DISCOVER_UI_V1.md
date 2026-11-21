# Seerr Discover UI V1

## Baseline and scope

Dedicated branch/worktree `feat/trixarr-seerr-discover-ui-v1`,
`/opt/jellyfintv-seerr-discover-ui-v1`, starts at verified integrated Beta1.1
`061cb6543980b9c77d522864b85632b85fb7c08a`. That commit descends from
Image Quality V1 `0b7e5016e`, AudioSink regressions `c443283f5`, Branding V3
`9ed83d86c` and Beta1 `553465c3a`. No merges/cherry-picks required. Original
worktree has117 unrelated dirty entries, preserved. No applicable AGENTS.md was
found in parent paths or tracked/app/docs subtrees. Existing README/build/project
conventions were inspected. Existing working directories remain in place.

The active navigation destination is `CatalogFragment(section=Discover)`, not the
legacy `DiscoverFragment`. Implementation extends that active catalog, its stack,
focus handling, existing detail view, request confirmation and playback launcher.
No separate TMDB client, new image loader, playback engine or branding is introduced.
This is an original dark Trixarr layout using existing cyan focus accents; no Telia
branding, prices, rental/purchase controls or technical capability badges.

## Architecture and endpoint mapping

`SeerrClientProvider` retains profile configuration, shared OkHttp transport and
encrypted cookie storage. `SeerrSessionManager` restores the current user. The
existing API adapter gains cancellable asynchronous calls and normalizes genre
array responses into `results`; numeric genre paths are allowlisted. There is no
server configuration change. `SeerrDiscovery` maps/caches successful media pages
for180 seconds and fetches only the selected category. The catalog repository maps
results into existing `CatalogTitle` values and resolves verified local matches.

| Tab | Seerr data / mapping |
| --- | --- |
| Nya releaser | `GET discover/movies`, `discover/tv`; dates in the last90 days, newest first |
| Kommande releaser | `GET discover/movies/upcoming`, `discover/tv/upcoming`; valid future dates, earliest first |
| Begär | `GET request?requestedBy=<current user>&skip=0&take=50&sort=added`, existing detail enrichment; permitted missing popular movies/TV alongside own history |
| Populärt | `GET discover/movies`, `discover/tv`, preserving returned order |
| Topplistor | Those loaded popular pages ranked by known positive `voteAverage`; explicitly labeled “Högst betyg bland populära …” |
| Genrer | `GET discover/genreslider/movie`, `discover/genreslider/tv`; actual genre names/IDs, then only selected `discover/movies/genre/<id>` or `discover/tv/genre/<id>` |

All paths are relative to the existing `api/v1/` base. V1 uses the first discovery
page per selected section (normally20 entries); this is bounded discovery, not a
complete release calendar or global all-time ranking. Genre selectors cover movie
and TV genres without fetching every genre's media. Missing/malformed dates or
unknown ratings are excluded from date/rating-specific mappings rather than invented.
Genre-slider calls omit `page` (the deployed server rejects that parameter).
Genre names retain the server's localization; new app-owned labels are Android
string resources with Swedish values.

Primary API references checked against Seerr source:
[discovery routes](https://github.com/seerr-team/seerr/blob/develop/server/routes/discover.ts),
[request permission rules](https://github.com/seerr-team/seerr/blob/develop/server/entity/MediaRequest.ts),
[own-request scope](https://github.com/seerr-team/seerr/blob/develop/server/routes/request.ts).

## Hero, details and artwork

Focused title selects the cinematic background and title. The existing debounced
`LatestFanart` loader retrieves detail metadata; canceled selections cannot publish
stale responses. The hero shows year, returned genres, short overview, current
availability, future release date, contextual primary action and More information.
No movie examples are hardcoded. Empty/error/loading surfaces keep the category
navigation available. Remote details include backdrop, bounded poster, title,
overview, release/genre metadata, status, and the existing information/related UI.

`Fanart`, `TitleCard`, `CatalogImage`, the shared Coil loader/cache and unchanged
Image Quality V1 interceptor retain measured target sizes, source aspect, focus
margins, URL/cache identity and bounded decoding. Existing fallback backgrounds
cover missing artwork. Discovery scopes fanart state to the selected title so a
failed image cannot display an unrelated previous title's background.

The deployed Seerr server has image caching disabled: its image proxy returns404
with “Image caching is disabled.” The existing API adapter now reads public
`cacheImages` settings. It uses the Seerr proxy when enabled, or the normal TMDB
image CDN for Seerr-provided artwork paths when disabled. This is image delivery,
not another metadata/API integration: no TMDB API client, key or discovery call
is added. The existing Image Quality V1 policy already supports both URL forms.
No server setting is changed. Branding V3 source and all30 assets remain byte-identical.

## Availability and request safety

A Seerr Available status is not playback proof. Local resolution requires the same
media kind and an unambiguous matching TMDB/TVDB/IMDb ID, rejecting conflicting IDs.
Title/year similarity cannot grant playback. Existing nonvirtual Jellyfin media
provides the playback ID; series use existing episode selection. Unmatched external
availability shows information/status, never a fabricated Play action.

Requests use the existing `RequestRepository` and `POST request`. General REQUEST
or the relevant movie/TV grant authorizes requests (ADMIN remains supported), matching
Seerr's OR rule. UI exposes no admin/server/quality override actions. Submission
refreshes details first, then validates current media/season state and permissions.
Movies require missing/requestable status. Partially available, requested or processing
series can request remaining positive-numbered seasons; specials, available/requested
seasons, empty selections and repeated submitted seasons are rejected. A mutex
prevents concurrent submissions. Other unrequested seasons can be submitted later.
Local series retain a separate “Begär säsonger” action for remaining remote
seasons while preserving their existing playback/episode actions.
Server conflict remains authoritative, with a localized message to refresh status.

The dialog starts on Cancel. Selecting seasons never submits. Only explicit final
confirmation invokes submission; in-flight cancellation/repeated activation is guarded.
Success updates visible status and shows feedback; authentication, permissions,
conflict, timeout, network and server errors show Swedish messages without secrets.
Authenticated users can view their own scoped history without admin permissions.

## Remote navigation and performance

Fixed category navigation plus a lazy vertical content list and horizontal lazy rows.
D-pad Left/Right navigates tabs/cards; Select activates; Up/Down moves among hero,
rows and categories. Left edge opens the existing navigation rail. Back uses the
existing catalog stack/menu policy and dismisses request dialogs before leaving.
Page list state, row scroll states, selected title and focus token survive details
and dialog returns. Categories switch only on activation, not incidental focus.

No automatic hero rotation/new decorative animation. Existing brief focus/fanart
transitions remain. Shared HTTP calls cancel when their coroutine is canceled;
connect/read/write timeouts remain bounded. Cache only successful pages; network
failures are not silently converted to empty content. Detail hydration and request
history enrichment reuse existing flows. First-page and short-lived in-memory cache
limits are explicit; no full-catalog paging or long memory soak is claimed.

## Delivery

Debug candidate version `0.1.1-beta.2`, code10102, above integrated Beta code10101.
Base applicationId, `.debug` suffix, namespaces and debug signer unchanged.
See [acceptance evidence](../../evidence/trixarr-seerr-discover-ui-v1/ACCEPTANCE.md)
for exact APK identity, tests, device checks and remaining gates. No real Seerr
request is authorized during automated testing; mocks/synthetic callbacks only.
