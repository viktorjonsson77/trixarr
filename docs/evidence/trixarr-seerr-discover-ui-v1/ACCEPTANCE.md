# Seerr Discover UI V1 — acceptance, 2026-11-21

**TRIXARR_SEERR_DISCOVER_UI_PARTIAL** — implemented, built and safely installed.
Fresh user physical acceptance and real request submission remain unverified.

## IMPLEMENTED

Baseline `061cb6543980b9c77d522864b85632b85fb7c08a`, integrated Beta1.1 with Branding
V3, Image Quality V1, AudioSink regressions, PlayerEngine and existing Seerr.
Dedicated branch/worktree `feat/trixarr-seerr-discover-ui-v1`,
`/opt/jellyfintv-seerr-discover-ui-v1`. No merge assumptions or unrelated dirty work
included. Original worktree's117 dirty entries preserved; no applicable AGENTS.md.

Architecture, endpoint mapping and explicit first-page limitations:
[design record](../../design/trixarr/SEERR_DISCOVER_UI_V1.md).
All six Swedish tabs implemented: Nya releaser, Kommande releaser, Begär, Populärt,
Topplistor, Genrer. Cinematic metadata-driven hero, lazy artwork carousels, contextual
actions, remote details/poster, movie confirmation and selectable remaining TV seasons.
Existing Seerr authentication/configuration/client/request history retained. General
or per-kind request permission supported; no administrator actions exposed.

Local Play requires unambiguous provider-ID verification against nonvirtual Jellyfin
media, never Seerr availability alone. Local series retain playback/episode actions
and a separate request-seasons action when remote seasons remain requestable.
Existing playback launcher/engine and accepted branding/image policy/cache retained.
Image transport honors the server's existing cacheImages mode; no TMDB metadata API
or separate image loader added, and no server configuration changed.

Implementation commits:
- `685e4d75a`: category mapping, request/season safety and focused JVM tests.
- `f459a901b`: active catalog UI, detail/dialog adaptation, remote tests and version.
- `889ce4ded`: deployed Seerr genre validation and configured image-delivery handling.

## TESTED

JDK21 / Android SDK36:
`./gradlew -I docs/evidence/trixarr-seerr-discover-ui-v1/focused-tests.gradle :app:testDebugUnitTest :playback:core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`

- **190 JVM tests PASS**:161 app plus29 playback/core; zero failures/errors/skips.
- **20 Shield instrumentation tests PASS**: four new discovery/request UI tests,
  four existing artwork tests, four branding tests and eight Media3/local-audio tests.
- Discovery tests cover endpoint/category/date/rating mappings, real response shapes,
  cache reuse, cancellation, status/permission handling, movie and TV request payloads,
  consent, invalid/duplicate seasons, repeated submissions, local-match safety,
  empty/error states, horizontal scrolling and focus restoration after remount.
- Dialog tests use synthetic callbacks; API mutations use MockWebServer only.
- Existing relevant catalog/startup, image sizing/cache identity, DV negotiation,
  audio/session/refresh and player regressions pass. No physical codec/output claim
  is inferred from synthetic tests. Unrelated full suites are not claimed as run.
- Approved branding master SHA256
  `22421f41e2c3f8a00d8a6b135a6aca8c4c04fc567e5ae94f3afc559bb7967e83`
  and all30 derivative hashes/dimensions pass. Image Quality V1 implementation unchanged.
- ARM64 ELF64/AArch64 libraries verified; both native libraries match the predecessor
  byte-for-byte ([native comparison](native-baseline-match.json)).

See [JVM counts](jvm-tests.json), [final Android results](android-tests-r5.txt),
[build](build-final.txt), [branding](branding-assets.txt).

### Investigated failures and corrections

No failing test was skipped or suppressed. The first test invocation failed before
execution because its init script did not guard buildSrc's absent app project; the
null guard corrected the harness. Initial dialog Back tests failed with Compose key
injection and with global hardware injection. A generic BackHandler alone did not
resolve the harness failure. The final dialog handles Back key-up explicitly in
its Compose content as well as the Android dispatcher. The original dismissal
assertion now passes; a live ADB Back check independently dismisses the real dialog.
An attempted Espresso helper did not compile because Espresso is not a dependency;
no dependency was added. Initial failure reports remain for audit.

Live genre calls returned400: `/query/page`, “Unknown query parameter 'page'”. A
read-only paired probe got200 without that parameter. Genre-slider calls now omit
page; a regression asserts exact paths. Numeric genre media pages retain pagination.

Missing artwork was diagnosed with read-only image probes: the Seerr proxy returned
404, “Image caching is disabled.” Existing public cacheImages settings now select
Seerr proxy or normal image CDN delivery, without changing that setting. Both modes
are regression-tested. Latest screenshots show correct backdrops and artwork.
A stale prior-title backdrop on image failure was also reproduced; discovery now
scopes existing fanart state to each title, keeping fallback artwork honest.

Temporary read-only probe source and superseded diagnostic screenshots/APKs remain
under `/opt/jellyfintv-artifacts/trixarr-seerr-discover-ui-v1/`; they add no shipped
API client. Fresh capture scripts require a newly generated hierarchy so stale dump
files are not accepted as evidence. Screenshots are device captures, not user reports.

## APK identity — TESTED

Final artifact:
`/opt/jellyfintv-artifacts/trixarr-seerr-discover-ui-v1/trixarr-0.1.1-beta.2-seerr-discover-ui-v1-r5-debug.apk`

- SHA256: `abcded2604087f973084f20dba45763d3e7c3b0a8184fcdfe8afcefd7bdfd8b9`
- Signer SHA256: `f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`
- Package: `se.viktorjonsson.jellyfintv.debug`
- Version: `0.1.1-beta.2`, versionCode10102 (integrated Beta10101).
- ApplicationId base, namespaces and signing identity preserved. Debug signing only.
- All earlier APK artifacts retained; intermediate candidates have distinct filenames.

See [APK verification](apk-verification.json), [metadata](apk-badging.txt),
[certificate](apk-signer.txt).

## RUNTIME VERIFIED — bounded Shield checks

NVIDIA SHIELD Android TV / mdarcy Pro, API30, ARM64, ADB100.91.172.18:5555.
Signature compared with pulled installed predecessor before `adb install -r`.
Update-compatible code and signing verified; final candidate installed successfully.
No uninstall, pm clear, app-data deletion, server configuration update or real Seerr
request submission performed. UID10048, first-install time2026-09-02 23:52:47 and data inode652903 match the
predecessor. Default preference and Seerr configuration file hashes are identical
before/after; pulled/read-back final installed APK matches the delivered SHA256.
See [preservation](device-after.json) and [package](package-after.txt).
Existing Jellyfin login and Seerr connection remain
usable; catalog and scoped request history load without logging in again.

All six categories populate from live Seerr. Correct hero title/metadata/backdrop,
readable artwork rows and selected cyan tabs captured. Genre names and selected
movie-genre results load. Existing detail view opens and returns to discovery.
Movie request confirmation opens; ADB Back dismisses it without submission and
returns to the catalog. TV season selection/duplicate protection is independently
tested with synthetic data, without creating a real request.

Representative evidence: [popular artwork](r5-popular.png),
[genres](verified-genres.png), [new releases](verified-new.png),
[movie confirmation](verified-movie-dialog.png), [Back return](verified-dialog-back.png),
[upcoming](delivery-upcoming-ui.txt), [top lists](delivery-top-ui.txt),
[own requests](delivery-requests-ui.txt).

Bounded playback smoke: existing Resident Evil flow reaches Direct playback,
recovers a transient AudioSink -6, advances to5.5s, stops and releases ExoPlayer.
Display mode22 (23.976Hz) returns to default31 after teardown. This is a bounded
runtime smoke, not physical picture/audio acceptance or a long stability claim.
See [startup](latest-manual-startup-signal.txt), [cleanup](playback-final-log.txt),
[refresh restoration](refresh-final.txt). No playback/audio implementation changed.
The observed Silo partial-status detail had no remaining request action; no live
season mutation was attempted or claimed. Synthetic remaining-season tests pass.

## USER PHYSICALLY VERIFIED

**NOT VERIFIED for this new UI.** No fresh user report of TV readability, artwork,
remote responsiveness, visible HDR or audible output was received. Earlier Image
Quality V1 acceptance is historical and is not represented as acceptance of this UI.

## NOT VERIFIED / limitations

Real Seerr request creation is intentionally unverified and requires explicit user
approval. Synthetic HTTP/payload/permission/conflict/season tests pass. Broad live
TV-season combinations, server quotas and long history pagination are not exhaustively
verified. Discovery uses first pages and bounded client mappings; Topplistor is a
sampled popular ranking, not a global all-time list. Server/CDN availability can
still cause missing artwork; fallback retains title/status and avoids stale art.

No long scrolling/memory soak or new physical playback format matrix is claimed.
Existing HDMI interruptions and unverified long-term audio stability remain. Release
signing is separate. No main merge, tag, public release or force push performed.
