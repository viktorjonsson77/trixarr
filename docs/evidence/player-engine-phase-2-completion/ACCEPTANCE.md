# Phase 2 completion acceptance

Status: **ARRSTREAMER_PLAYER_ENGINE_PHASE_2_COMPLETION_PARTIAL**

## Implementation

Implemented central declaration/evidence capability policy, deterministic Media3
selection, bounded initialization fallback, shared core session identity/generation,
controller lifecycle and resume integration, ordered/deduplicated Jellyfin reporting,
DV base-layer safety restriction and session/policy/reporting diagnostics. Existing
TV controls and default engine retained. No Kodi integration or production services
used. See [architecture](../../architecture/PLAYER_ENGINE_PHASE_2_COMPLETION.md).

Acceptance is PARTIAL because automated fixtures do not yet cover the complete
PlaybackInfo negotiation, actual Activity recreation, nonseekable transcode stream
restart, or experimental core PlaySessionService/queue lifecycle. That service also
needs pre-PLAYING ownership for initialization errors. Late cancelled live stream
negotiation needs explicit remote-resource close coverage. These are software gaps,
separate from required physical hardware testing. No failed executed tests remain.

## Tests and build

2026-11-06, JDK 21, Android SDK 36, Gradle wrapper 9.1.0:

```sh
./gradlew -I docs/evidence/player-engine-phase-2-completion/focused-tests.gradle \
  :app:testDebugUnitTest :playback:core:testDebugUnitTest :app:assembleDebug
ANDROID_SERIAL=10.51.0.31:5555 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.jellyfin.androidtv.playback.PlayerEngineInstrumentedTest
```

- JVM: 51 app playback tests + 8 core tests PASS, 0 failures/errors.
  Includes 17 production-controller chain fixtures and codec selector restriction.
  [Per-suite counts](jvm-results.txt). Only playback tests selected; the Kotest spec
  filter avoids accidentally discovering the entire application suite.
- Android: 4 tests PASS on virtual redroid Android 15/API35 x86_64.
  Real Media3 lifecycle/seek/track disable, session diagnostic identity/cleanup,
  pinned Media3 DV5/7 HEVC exclusion, packaged FFmpeg JNI availability.
- One final instrumentation invocation aborted before any test with `Process crashed`;
  rerunning the unchanged APK completed all 4 tests successfully. Cause was not
  established; both logs are retained. No claim of perfect test-run stability.
- Gradle debug build PASS. Existing Java 8/deprecated API and Gradle deprecation
  warnings remain; no signing configuration was changed.
- No physical Nvidia Shield installation or test. No real Jellyfin account.

## APK verification

Path: `/opt/jellyfintv-artifacts/player-engine-phase-2-completion/arrstreamer-player-engine-phase-2-completion-debug.apk`

SHA-256: `1e7eeb00ecc832f39b4542fd81251f59e91e03967bce612ff657e7ae91922526`

Signer certificate SHA-256:
`f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`

- Package: `se.viktorjonsson.jellyfintv.debug`
- Version: `0.0.0-dev.1`, versionCode `1`, minSdk21, targetSdk36.
- Universal APK includes arm64-v8a (also armv7/x86/x86_64).
- Both `libffmpegJNI.so` and `libandroidx.graphics.path.so` present for ARM64;
  readelf confirms ELF64/AArch64. DT_NEEDED resolves only Android platform libraries:
  libc, libm, libdl, libandroid, liblog. No additional missing packaged JNI dependency
  identified. ARM64 runtime execution remains unverified.
- `apksigner verify --verbose --print-certs`: verifies, one existing Android Debug
  signer, v1/v2 true. Expected META-INF/JAR entry warnings; APK v2 verification passes.
- Manifest: Leanback startup activity present; existing INTERNET/network/Wi-Fi,
  foreground service/media playback, microphone, wake lock, TV EPG and boot permissions.
  Touchscreen not required. No manifest/permissions/signing changes in these commits.
- Artifact directory contains build, instrumentation, badging and signing logs.
- Previous Phase 2 APK preserved, SHA-256 remains
  `a95f4b43d86c1a8f12ab25e31efa17a2058c701d6e176aaebdd99c2572f13a88`.

## Remaining physical tests

Shield TV Pro ARM64: DV5/7/8 (including FEL/MEL), HDR10/HLG/HDR10+, TV mode/color,
4K Remux high bitrate, refresh-rate transitions, HDMI TrueHD/DTS-HD/Atmos/DTS:X,
pause/seek/track switching, background/recreation and D-pad diagnostics. Neither
Android codec declarations nor virtual tests verify actual HDMI output. Dolby Vision
is **not reported fixed**.

## Git and reproducibility

Branch: `feat/player-engine-phase-2-completion`, based on `feat/player-engine-phase-2`.
Local implementation commit `e10b7cdbb`; focused regression commit `2dbc338bf`.
This evidence and architecture document are committed in the subsequent docs commit.
No push, tag or deployment. Unrelated pre-existing dirty branding/catalog/UI work
is preserved and is present in the built workspace APK; it is not part of these
commits. The original feasibility and Phase 2 documents were not modified.

Original dirty-work inventory: all 190 pre-existing porcelain status entries remain
unchanged after focused commits (including 146 tracked modifications/deletions).
