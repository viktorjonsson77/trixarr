# Download Status V1 — acceptance, 2026-11-21

**TRIXARR_SEERR_DOWNLOAD_STATUS_PARTIAL**.
Implemented server and Android code, synthetic secure integration tests, built APK
and safe Shield installation delivered. **FEATURE_PASS is not established against
the real infrastructure. PHYSICAL_PASS is NOT VERIFIED.** The bridge is not deployed
live: private HTTPS connectivity, LAN routing and server credentials are missing.

## Baseline and preservation

Branch/worktree `feat/trixarr-seerr-download-status-v1`,
`/opt/jellyfintv-seerr-download-status-v1`, based on `b87c10025` /0.1.1-beta.2/code10102.
Baseline includes Seerr Discover, Branding V3, Image Quality V1, Media3, DV, audio
passthrough/local decode and refresh/session handling. No applicable AGENTS.md found.
Original worktree remains at117 dirty entries; other original worktrees untouched.
No reset, clean, blanket stash, force push, main merge, tag or public release.

## IMPLEMENTED

[Architecture and API](../../architecture/TRIXARR_STATUS_BRIDGE_V1.md).

- Python Status Bridge: authenticated TLS-only private listener, fixed upstreams,
  systemd credential files, no database, no torrent engine and no download controls.
- Per-user Seerr session verification and explicit request ownership checks before
  data access; supplied IDs never authorize access. Administrators are also scoped
  to their own requests through this limited API.
- Radarr TMDB and Sonarr TVDB identity matching, scoped queue/history/command reads,
  episode/file import evidence and per-request season/episode projections.
- qBittorrent filtered identifier reads, real progress/speed/remaining bytes, paused,
  stalled, error, queued and completion states. No title-based correlation or raw
  hashes, paths, torrent names, trackers or credentials in the API.
- Jellyfin linked-user/provider/media-source verification; qBittorrent100% and Arr
  imported files alone cannot mark media AVAILABLE.
- Independent and shared-pack transfers are never averaged into misleading request
  or episode percentages. Partial seasons retain explicit available/total counts.
- Separate absolute download/availability estimates, confidence/source/freshness,
  bounded valid qBittorrent ETA only; playback ETA remains unknown without evidence.
- Begär compact status and detail/episode dialogs, Swedish formatting, HTTPS-only
  profile connection setting,12-second dialog/45-second list polling, lifecycle and
  cancellation handling, keyed focus/scroll preservation and existing playback.
- Prowlarr is optional and not used; no request searching inferred from its health.

Implementation commits: `3fde52f03` (bridge), `81b52a15e` (Android),
`19274561f` (private upstream proxy isolation). Documentation/evidence follows.

## TESTED

**26 Python tests PASS**, including real HTTP adapters against synthetic Seerr,
Radarr, Sonarr, qBittorrent and Jellyfin servers, and authenticated HTTPS bridge
requests with an explicitly trusted ephemeral test certificate. Default trust rejects
that untrusted certificate. No production TLS verification is disabled.

Covers ownership/unauthorized IDs, method/input bounds, credential-file permissions,
environment-proxy isolation, stable IDs, private projection, exact download matching,
multiple downloads, pack attribution, paused/stalled/error states, invalid/stale ETA,
import/Jellyfin transitions, partial seasons, future episodes and upstream failures.

**199 JVM tests PASS**:170 app +29 playback/core, zero failures/errors/skips. Includes
nine new download-status/secure-URL/ETA/timezone/poll-cancellation tests and the existing
Seerr, catalog, image, startup and playback regression suite.

**23 Shield instrumentation tests PASS**: three new download status tests plus the
20 existing discovery/artwork/branding/Media3/local-audio tests. New tests verify
6/8 season rendering, progress updates while episode focus stays unchanged, D-pad
selection/Back, insecure URL rejection, and Play gating until Jellyfin verification.
Device progress tests are synthetic; they are not live download observations.

Commands:
- `python3 -m unittest discover -s backend/status-bridge/tests -v`
- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk ./gradlew -I docs/evidence/trixarr-seerr-download-status-v1/focused-tests.gradle :app:testDebugUnitTest :playback:core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
- Explicit Shield instrumentation class list: DownloadStatusUiTests,
  SeerrDiscoverUiTests, ArtworkRenderingTest, BrandingInstrumentedTest,
  LocalAudioDecodeInstrumentedTest, PlayerEngineInstrumentedTest.

See [bridge results](backend-tests.txt), [JVM counts](jvm-tests.json),
[final device tests](android-tests-final.txt), [build](build-final.txt).
No failing tests were skipped or suppressed. An initial bridge test exposed a lost
Jellyfin identity-service failure flag; it is now reported safely. A test helper
requiring JVM11 could not inline into the existing JVM8 test target; the assertion
was expressed using the repo-compatible runCatching without changing targets. The
first device run had one ambiguous assertion because its fixture assigned an
aggregate series speed; the fixture now matches the real no-aggregate API contract,
and the same unique speed/focus assertion passes. Initial failure evidence retained.

## APK and backend artifacts — VERIFIED

- APK: `/opt/jellyfintv-artifacts/trixarr-seerr-download-status-v1/trixarr-0.1.1-beta.3-download-status-debug.apk`
- APK SHA256: `52bf968e91e3e22f0455bd0f2bc19274739a88c1921923b42adee2b6e2c6660f`
- Signer SHA256: `f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`
- Package: `se.viktorjonsson.jellyfintv.debug`, version0.1.1-beta.3/code10103.
- Existing applicationId base, namespaces and debug signing identity preserved.
- ARM64 ELF libraries verified and byte-identical to the installed predecessor.
- Branding master SHA256 `22421f41e2c3f8a00d8a6b135a6aca8c4c04fc567e5ae94f3afc559bb7967e83`;
  all30 approved derivatives pass. No branding/image policy/playback source changes.
- Backend source bundle: `/opt/jellyfintv-artifacts/trixarr-seerr-download-status-v1/trixarr-status-bridge-v1-source.tar.gz`.
  [Backend checksum](backend-artifact.json), [deployment README](../../../backend/status-bridge/README.md).
- Earlier APKs/source bundles retained without overwriting. No keys/passwords or
  production certificates in the bundles/APK/repository.

## RUNTIME VERIFIED — actual Shield, limited scope

NVIDIA SHIELD Android TV / mdarcy Pro, Android11/API30, ARM64,
ADB100.91.172.18:5555. Installed predecessor code10102 and signer checked before
`adb install -r`. New code10103 safely installed. Final installed APK read back:
exact match to the delivered SHA256. No uninstall, pm clear or user-data deletion.

UID10048, first-install time2026-09-02 23:52:47 and data inode652903 preserved.
Default preferences, Seerr configuration and status-bridge configuration presence/hash
are unchanged. Existing Jellyfin login and Seerr connection load real catalog and
own-request history without reauthentication. No permanent user setting changed.

Upptäck loads, Begär retains its hero/artwork/request rows and honestly displays
“Anslut en säker statusbrygga för nedladdningsstatus”. Statusanslutning opens an
HTTPS-only setup dialog; ADB Back returns to Begär. No dummy endpoint or simulated
production progress was saved. No real Seerr request or download mutation performed.

Evidence: [requests](runtime-requests.png), [secure setup](runtime-secure-setup.png),
[Back](runtime-setup-back-ui.txt), [configuration preservation](device-after.json),
[package](package-after.txt), [installed checksum](installed-after-sha256.txt).

Playback smoke: the first seven-second sample was inconclusive while buffering.
A subsequent harness refused to click when it could not identify fresh movie details;
its old reusable UI dump was replaced by a fresh bounded identity check. A bounded
20-second check then reached Direct playback, recovered an existing transient AudioSink
-6, advanced to18.1s, stopped, released ExoPlayer and restored default display mode31.
Initial samples are retained; no speculative playback/audio change was made.
See [startup](latest-manual-startup-signal.txt), [cleanup](playback-final-log.txt),
[refresh restoration](refresh-final.txt). This is not physical HDR/audio acceptance.

## LIVE INTEGRATION / USER PHYSICAL — NOT VERIFIED

The LAN endpoints did not connect from this host, including direct TCP probes
without HTTP proxy involvement. No relevant service credentials were found in the
inspected credential stores. No private HTTPS bridge URL/certificate/listener exists;
no URL was supplied in response to the optional non-secret endpoint question.
See [direct connectivity](direct-lan-preflight.json), [preflight](live-preflight.json).

The production service was not installed/enabled. No unrelated public certificate or
domain, public listener, unauthorized credential extraction, disabled TLS checking
or unauthenticated HTTP fallback was used. No credentials were requested in chat.

Live authorized Radarr/Sonarr/qBittorrent progress/correlation, Jellyfin linkage via
the bridge, live ETA updates and real episode/import transitions remain unverified.
No user-confirmed physical UI/picture/audio behavior was received. Neither FEATURE_PASS
against the real services nor PHYSICAL_PASS is claimed from synthetic tests.

To finish live acceptance, provision the bridge on an authorized LAN-reachable host,
provide its private device-trusted HTTPS endpoint, and configure its server credential
files/Jellyfin origin out of band. Then enter only the HTTPS URL in Statusanslutning
and run read-only checks. Do not send secrets in chat or change active downloads.

## Remaining limitations

Requests are fetched in bounded batches of25; the default API list is25 and Android
explicitly batches loaded request IDs. Queue reads are bounded at250 entries and
Jellyfin episode verification at2000 entries. Unknown/unmatched/unavailable data stays
unknown rather than inferred from names, indexer health or cached percentages.
No historical import/indexing duration model exists, so playback ETA is usually
ETA_UNKNOWN. No long scrolling/memory or audio stability soak is claimed. Existing
HDMI interruptions may recur. Release signing and production deployment are separate.
