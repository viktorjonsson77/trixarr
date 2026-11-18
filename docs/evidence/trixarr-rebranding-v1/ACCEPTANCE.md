# TRIXARR_REBRANDING_V1

Date: 2026-11-18. **TRIXARR_REBRANDING_PARTIAL — FEATURE_PARTIAL / PHYSICAL_PARTIAL.** Text branding, build/update compatibility and the bounded player regression pass. Approved artwork is missing; existing ArrStreamer wordmarks remain in the launcher TV banner and animated splash. This is not full product-identity acceptance.

## Baseline and scope

Starting branch `feat/trixarr-dv-local-audio-decode-v1`, HEAD `dc6319664f52a46673bf4ce1f8c7c95204dbd878`, **195 dirty status entries**. Created `feat/trixarr-rebranding-v1`. No applicable AGENTS.md was found in the repository or ancestor paths. The safe-negotiation and stability branch tips are ancestors of this baseline; local-audio decoding is committed in it. The older detached controlled-build worktree was inspected and left untouched. No merge, reset, clean or stash was used, and no work was discarded.

The existing dirty branding strings already changed upstream Jellyfin app-facing copy to ArrStreamer. Those related name substitutions are incorporated as Trixarr changes. Unrelated catalog/startup/refresh/icon work remains in place. The About file was staged selectively: product text is committed, while its pre-existing icon selection remains unstaged. A content-hash audit covers unrelated dirty files, including the untouched player work. Related branding entries becoming committed explains why the remaining dirty count differs from195; it does not indicate discarded work.

## Changed product labels and documentation

* Release and debug Android application names both resolve to **Trixarr**, including Android TV launcher labels, notification app identity and string-resource consumers. Existing localized app-facing references retain their surrounding translations. Search, welcome/login-help, permission/error, license, beta-notification and screensaver text now use Trixarr.
* About shows **Trixarr app version / Trixarr 0.0.0-dev.1 debug**. Server selection version text uses Trixarr. The diagnostics dialog title uses **Trixarr Player Engine**. The engine-initialization error dialog uses the same product display name.
* Human-readable support-report client/repository values use Trixarr and the actual repository; report field names/schema are unchanged. Stable HTTP user-agent strings, technical engine ID `Jellyfin Media3`, log tag `ArrStreamerLaunch`, resource identifiers, package names and historical asset paths remain stable.
* README uses Trixarr, **Your media. Your way.**, the actual clone URL and explicit upstream attribution. The old logo image is removed from the current README. New [build guide](../../BUILDING.md) and [current architecture overview](../../architecture/TRIXARR_OVERVIEW.md) explain the current product, backend and build dependencies.
* Jellyfin server/API/authentication/compatibility references remain. LICENSE, upstream notices, historical design/evidence reports, commits and APK artifacts are not rewritten or renamed. No permanent home-header logo or navigation/control redesign was added.

## Graphics — BRAND_ASSETS_MISSING

No approved Trixarr logo assets were found. The user was asked for an alternate local path/branch; no location was supplied at recording time. [Asset inventory](asset-inventory.json) records the retained assets and hashes. Existing provenance is [historical ArrStreamer design material](../../design/arrstreamer/README.md), not Trixarr approval.

No substitute logo, generated artwork, crop, stretch or new animation framework was introduced. Existing symbol/icon/adaptive resources and TV banners are untouched. They resolve correctly, but that does not make them approved Trixarr artwork. [Launcher capture](launcher-legacy-banner.png) shows the remaining ArrStreamer raster banner even though [launcher accessibility metadata](launcher-label.json) says Trixarr.

The existing startup animation is preserved. A fresh cold-start recording at `/opt/jellyfintv-artifacts/trixarr-rebranding-v1/startup.mp4` confirms the old ArrStreamer splash wordmark; [frame at4.1s](startup-legacy-wordmark.png). No new delay, startup network call or player initialization was introduced. **Correct Trixarr logo/banner/splash acceptance remains blocked.** The screensaver can also reuse the same legacy wordmark resource.

## Preserved technical and player identities

`applicationId`, namespace, production Java/Kotlin packages, manifest component identities, ContentProvider authorities, deep links, signing configuration and preference keys are unchanged. Debug package remains **se.viktorjonsson.jellyfintv.debug**. Manifest/build configuration changes already dirty before this task remain unmodified and unstaged.

PlayerEngine/Manager, Media3 **1.8.0**, DV safe negotiation, local EAC3 decode policy, PCM sink, DD+ passthrough, video MIME/codec selection, refresh switching, ownership, reporting and resume code are untouched. [Player input hashes](preserved-player-inputs.json) match the prior verified local-audio candidate. The only VideoManager/PlaybackDiagnostics edits are literal human-readable error/dialog labels. ARM64 native-library bytes match the installed baseline exactly. No Kodi or server change.

## Build and focused tests — PASS

Controlled directory `/opt/jellyfintv-rebranding-build` copies the verified `/opt/jellyfintv-local-audio-build` inputs and overlays only this task's files. The same five unfinished prior startup/refresh inputs are excluded, with exact task hashes and baseline provenance in [build-inputs.json](build-inputs.json). This is not an implicit acceptance of all dirty workspace work.

JDK21 / Android SDK36, command:

```sh
./gradlew -I docs/evidence/player-engine-phase-2-completion/focused-tests.gradle \
  :playback:core:testDebugUnitTest :playback:jellyfin:testDebugUnitTest \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Gradle **BUILD SUCCESSFUL**. [JVM](jvm-tests.json): **90 tests /0 failures/errors/skips** (core26, Jellyfin5, app59). [Shield Android](android-tests.txt): **9/9 PASS**, including three new branding tests, two local-audio tests and four existing engine regressions. No unrelated full suite was run.

| Requested check | Result |
| --- | --- |
| A App label | Trixarr in built APK, installed PackageManager and resources |
| B Leanback metadata | Existing startup activity and LEANBACK_LAUNCHER preserved; launcher metadata Trixarr |
| C Icon resources | Legacy icon/banner/splash resources resolve; approved Trixarr graphics BLOCKED |
| D Splash | Startup works, animation unchanged; old wordmark observed, branding acceptance BLOCKED |
| E About / F Settings | Device UI accessible; new version/name and translated license text captured |
| G Package / H Signing | Exact identities retained and update verified |
| I Resource compilation | Debug APK/test APK assembly PASS; localized resources compile |
| J Relevant player regressions | 90 focused JVM, six player Android checks and Resident Evil runtime below |

## Artifact and safe Shield update

* APK: `/opt/jellyfintv-artifacts/trixarr-rebranding-v1/trixarr-rebranding-v1-debug.apk`
* APK and freshly installed base.apk SHA-256: **`eecf795d609e57afc1e895444caa5779fdc0934ea6e1f55914f940ac461f5652`**
* Signer SHA-256: **`f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`**, exact match with freshly pulled installed baseline `4091dc4e72557756cbc579fca53d189896fd6c80a535f951225a9ef7a488bcd9`.
* Package `se.viktorjonsson.jellyfintv.debug`, version **0.0.0-dev.1**, code1, min21/target36, application-label **Trixarr**. Both native ARM64 libraries are ELF64/AArch64 and byte-identical to baseline; existing other ABIs remain packaged.
* Explicit ADB target **100.91.172.18:5555**, NVIDIA SHIELD Android TV/mdarcy, Android11/API30, arm64-v8a. Identity/package/certificate/hash checks passed before installation. `adb install -r` succeeded; no uninstall, clear or data deletion. First install remains2026-09-02 23:52:47, CE data inode remains652903; update2026-11-18 22:24:21 local.

[Installation record](device-installation.json). Previous APKs are preserved. This is a local debug validation update; no production deployment, push/tag or repository rename.

## Device UI and Resident Evil regression

The installed app launched into authenticated MainActivity and loaded real library/catalog titles (including Parasit and Backrooms) without asking for login. [Settings text](settings-visible-text.txt) and [About text](about-visible-text.txt)/[screenshot](about.png) confirm accessibility and the new product/version. Diagnostics are accessible and show the new Trixarr Player Engine title.

Resident Evil source **dae676fc22d2a272784e30b9ff521ab8**, source audio **index1**, DV5 level6 / `dvhe.05.06`, EAC3 5.1/48kHz. Existing bitstream preference was absent/effective ON before and after; it was never changed. No audio/global-display setting was changed. The existing player retains its own automatic refresh behavior.

Regression device-local22:27:28 launch, AudioTrack start22:27:31.841, diagnostic pause at **46679ms**, normal stop22:28:43.804, ExoPlayer Release22:28:43.823. **DirectPlay**, **OMX.Nvidia.DOVI.decode**, actual **video/dolby-vision**, 3832×1600. AudioTrack **E_AC3 / DIRECT /48000Hz/native channelMask63** (six channels); diagnostic Java channelConfig252. Encoded output does not claim a local audio decoder. Final diagnostic dropped frames **0**, fallback NONE, no named playback/decoder/audio-sink error. Native stop/destructor status0, underrun0 and codec completion confirm cleanup.

[Diagnostic text](REGRESSION-diagnostics.txt), [dialog screenshot](REGRESSION-diagnostics.png), [filtered log](REGRESSION-logcat.txt), [codec/AudioTrack metrics](REGRESSION-media-metrics.txt). Logs retain only sanitized technical observations; no authenticated URLs, tokens or credentials. This is a46.7s regression sample, not a long stability test. Local PCM OFF was not physically retested because this task prohibited audio-setting changes; its policy, renderer/sink sources and packaged library remain unchanged and relevant tests pass.

**Physical TCL DV/colors and Yamaha display: NOT VERIFIED for this APK.** Requested during playback; no response at recording time. Prior-task physical observations are not relabeled as fresh verification.

## Acceptance and commits

**FEATURE_PARTIAL / PHYSICAL_PARTIAL.** Text/name/package/signing/test/build/update/library/settings/diagnostics/playback checks pass. `BRAND_ASSETS_MISSING` prevents approved icon/banner/splash replacement and the required absence of all current visible old branding. Fresh TCL/Yamaha observations remain separate. Do not claim complete rebranding or a Trixarr-branded splash.

Implementation/documentation commit **bb87e8d6d**; acceptance evidence is a separate focused local commit. Related prior name substitutions are incorporated, and all unrelated dirty work is preserved and content-hash checked. No historical reports/assets/artifacts or upstream licensing notices were changed.

Final workspace audit: **2,128 unrelated files content-hash unchanged**, zero mismatches. **52** prior status entries containing related branding names are incorporated; **143** original dirty entries remain. [Audit summary](workspace-preservation.json). The original About icon-only dirty hunk remains unstaged. Current text-resource/README scan finds no ArrStreamer/JellyfinTV/Jellyfin TV product strings; visible legacy raster branding is the explicitly recorded exception.
