# TRIXARR_BRANDING_INTEGRATION_V2

Date: 2026-11-19. **TRIXARR_BRANDING_V2_PASS**, using the user's explicitly authorized text-only reuse of the existing triangle. Branding/resources/build and bounded Shield startup/library/playback checks pass. This is not a new general Dolby Vision/HDMI certification; runtime warnings and external observations are separated below.

## Baseline and asset provenance

Initial branch `feat/trixarr-brand-assets-recovery-v1`, HEAD `897f1eee37252e65fd2cad8c594e4cb0c38cf42d`, **143 dirty status entries**. No applicable AGENTS.md was present in the repository/ancestors. Current build/architecture/branding instructions were read. Created local `feat/trixarr-branding-v2`. No reset, clean, stash or branch merge.

The supplied `assets/trixarr-triangle-brand-20261118` name resolves to a configured origin **branch**, not a directory. `git fetch origin --no-tags` obtained `origin/assets/trixarr-triangle-brand-20261118`, full commit in [build-inputs.json](build-inputs.json), short **62349998b**, parent5794fdc52. Actual files are under `docs/design/trixarr/branding/`. The eight source PNGs are preserved byte-for-byte as committed on that branch. No previous APK/source original is overwritten.

[GitHub asset inventory](github-asset-inventory.json) records every filename, PNG dimensions, SHA-256, declared/actual transparency, integrity result, Git blob and intended usage. Five older PNGs decode correctly; all three latest `triangle-v1` PNGs are truncated inside IDAT and lack IEND. They cannot be used safely as complete images. They are preserved as source evidence and **not packaged as Android inputs**. The earlier cursive design is also not substituted.

The user then explicitly instructed: **“Du kan använda den gamla triangeldesignen och bara byta texten.”** Accordingly the active design is the existing blue/purple folded triangle, with only the wordmark changed to **Trixarr**. This supersedes dependence on the invalid triangle-v1 files; no new symbol or color scheme is invented.

[Design provenance](design-provenance.json) and the [selected source README](../../design/trixarr/branding/approved-reuse/README.md) document:

* Preserved original `original-triangle-symbol.svg`, copied unchanged from `docs/design/arrstreamer/ArrStreamer_symbol.svg`, SHA-256 **9c98d59fc4d71fe1b64d0d37c397a04564497aef437b315ae37e46e5da552bc2**.
* Original gradient definitions, Bézier paths, opacity and symbol layer bytes are embedded unchanged. Host verification confirms exact inclusion. Original design/reference files remain untouched.
* The white wordmark uses the same **Lato Black**, original font scale/baseline and native glyph proportions. Font SHA-256 **4ed66fa3928ae769205635c916b4f9e6a63f6a77f5d9693ac31a83d0d96ae1ef**; SIL OFL notice preserved. Only the text changes; the shorter composition is centered on the old canvas, without stretching the glyphs or triangle.

## Integrated resources

[Resource verification](resource-verification.json) inventories generated PNG dimensions, alpha ranges and SHA-256. [Exporter and source SVGs](../../design/trixarr/branding/approved-reuse/) make the adaptation reviewable/reproducible.

| Surface | Integration |
| --- | --- |
| Android TV icon | Existing user-approved symbol-only icons retained byte-identical at all five main/debug densities; adaptive PNG, background and monochrome shapes remain the same |
| Android TV banner | New Trixarr wordmark, **320×180** mdpi and480×270/640×360/960×540/1280×720 density variants; original background and existing DEBUG badge retained |
| Layer-list banner | Main/debug transparent1280×720 foregrounds updated, so drawable consumers cannot select the old wordmark |
| Splash / screensaver logo | New transparent **1825×530** `drawable-nodpi/app_logo.png`; existing consumers use the same resource ID |
| Legacy qualifier fallbacks | Already-deleted old `drawable/app_logo.xml`, `drawable-v24/app_logo.xml`, banner/icon foreground XML removals are incorporated so an old wordmark/duplicate resource cannot override the PNG |
| Other UI | Existing Trixarr app label, About/settings/server/diagnostic text retained; no permanent home-header logo or navigation/control changes |

All13 generated PNGs decode correctly. The original splash canvas and current400dp/FIT_CENTER view are unchanged, so startup code, fade timing, animation framework, drawing/handoff and network behavior are untouched. Transparency and proportions are preserved. Adaptive symbol safe radius is below33dp. No current Android wordmark resource contains ArrStreamer; historical filenames, metadata, notices and stable resource/log identifiers may retain it.

## Preserved application/player identities

Package **se.viktorjonsson.jellyfintv.debug**, namespace, component/ContentProvider/deep-link identities, signing and preference keys are unchanged. No production Java/Kotlin player/startup code was edited. PlayerEngine/Manager, Media3 **1.8.0**, DV negotiation/MIME/codec selection, passthrough/local decode, refresh handling, session/reporting and resume logic remain identical to the prior controlled rebranding build. [Production input proof](preserved-production-inputs.json). ARM64 library bytes match the installed baseline exactly.

Related dirty branding resources are intentionally incorporated into the focused commit; unrelated startup/catalog/refresh work is preserved. The same five unfinished prior-task inputs remain excluded from the controlled APK, not silently accepted or discarded. [Build provenance](build-inputs.json) records the exact overlay, prior build directory and exclusions. This does not claim the unfinished workspace unit-test failure is repaired.

## Tests and build

JDK21 / Android SDK36, `/opt/jellyfintv-branding-v2-build`, copied from the prior verified `/opt/jellyfintv-rebranding-build`, with only the branding/resource/test inputs overlaid. Commands in [build-inputs.json](build-inputs.json).

* Host branding/resource verification **PASS**: exact original SVG symbol/gradients, Trixarr glyph label, PNG integrity/alpha/canvas, all banner qualifiers and adaptive safe area. Actual packaged splash/icon/banner hashes match the expected resources.
* Focused JVM **90/90 PASS**, zero failures/errors/skips: core26, Jellyfin5, app59. [Counts](jvm-tests.json).
* Gradle `:app:assembleDebug` and `:app:assembleDebugAndroidTest` **PASS**; ARM64 libraries verified ELF64/AArch64, unchanged bytes. Existing other ABIs remain.
* Final combined Shield instrumentation **10/10 PASS**: four branding tests (including exact Android-selected splash/icon/banner resource hashes), two local-audio capability tests, four existing engine regressions. [Final output](android-tests-final.txt).

The first combined Android run crashed after the branding tests in `DefaultAudioSink.onAudioCapabilitiesChanged`, with **“Current looper (main) is not the playback looper (null)”**. [Original output](android-tests-initial.txt), [sanitized trace](android-crash-sanitized.txt). Isolated branding4/4 and player4/4 passed. Inspection of pinned Media3 1.8.0 showed the capabilities receiver binds to the caller's Looper; the existing local-audio test invoked the probe on the instrumentation worker, which has no Looper. **Only that test harness** was changed to execute the probe/profile assertion via `runOnMainSync`. The combined10-test run then passed. No production audio sink/probe/policy change or unsafe fallback was introduced. The production APK was unchanged by this test-only correction.

## APK and safe Shield update

* APK: **`/opt/jellyfintv-artifacts/trixarr-branding-v2/trixarr-branding-v2-debug.apk`**
* APK and independently read installed base.apk SHA-256: **dc78768a3aaeddf25d14494c79709bb75bbf18fa9d0ce576b70bca6415491f3c**
* Signer SHA-256: **f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c**, matches freshly pulled installed baseline.
* Baseline APK SHA-256 **eecf795d609e57afc1e895444caa5779fdc0934ea6e1f55914f940ac461f5652**, preserved at `installed-baseline.apk` in the new artifact directory. Earlier artifacts and the initial test APK are retained; the corrected test APK has a separate filename.
* Package **se.viktorjonsson.jellyfintv.debug**, version **0.0.0-dev.1**, code1, min21/target36, label **Trixarr**, existing LEANBACK_LAUNCHER activity. Package/version/signer/hash/ARM64 checks passed before `adb install -r`.
* Explicit target **100.91.172.18:5555**: NVIDIA SHIELD Android TV/mdarcy, Android11/API30, arm64-v8a. Install succeeded without uninstall, clear or data deletion. CE data inode remains **652903**, first install remains **2026-09-02 23:52:47**, update **2026-11-19 18:35:48** device local UTC+1.

[Installation record](device-installation.json). No audio/global-display settings were changed. Existing bitstream preference was absent/effective ON before and after. No push, tag or production deployment.

## Physical device UI and bounded DV regression

[Launcher capture](launcher.png) shows the approved blue/purple triangle and **Trixarr** wordmark in the TV tile, with no ArrStreamer wordmark; [launcher metadata](launcher-label.json) independently says Trixarr. [Splash capture](splash.png), frame4.1s of `/opt/jellyfintv-artifacts/trixarr-branding-v2/startup.mp4`, shows the same approved triangle and Trixarr text through the existing fade. Both are actual device captures, not mockups.

Cold start reached authenticated MainActivity and a real library/catalog (Synden, Backrooms, Parasit), with no login flow. [Home text](home-visible-text.txt). Existing Jellyfin login and library access are preserved; application startup is successful.

Resident Evil source **dae676fc22d2a272784e30b9ff521ab8**, source audio **index1**. Device-local start18:39:54; initial AudioTrack18:39:57.340; controlled diagnostic pause at **93405ms**; normal stop18:41:55.902, ExoPlayer Release18:41:55.921. Actual results:

* **DirectPlay**, source audio remains **audio/eac3**, six channels/48kHz; no server conversion forced.
* **OMX.Nvidia.DOVI.decode**, actual **video/dolby-vision**, **dvhe.05.06**, Profile5/Level6,3832×1600. Final dropped video frames **0**, reporting **PROGRESS_SENT**, fallback NONE.
* Encoded **E_AC3 / DIRECT**,48kHz, native channelMask63 (Java252), six channels. Local decoder is not claimed for an encoded path. PCM OFF was not physically retested because this task forbids audio-setting changes; its unmodified production code and focused regressions are verified separately.
* A recoverable **AudioSink WriteException** is retained in the panel during startup track recreation: track580 was stopped/destroyed, track588 started successfully and continued to93.4s. This is recorded rather than calling the run error-free. No terminal playback/video/audio-decoder error appears in the captured panel/targeted logs. Final AudioTrack flush/stop/destructor status0, underrun0 and released DV codec metrics confirm cleanup.

[Diagnostic text](REGRESSION-diagnostics.txt), [dialog screenshot](REGRESSION-diagnostics.png), [sanitized targeted log](REGRESSION-logcat.txt), [native codec/AudioTrack metrics](REGRESSION-media-metrics.txt).

**External TCL DV indicator/colors and Yamaha display: NOT VERIFIED in this run.** The user was asked during playback; no response at recording time. ADB screenshots/Android MIME do not certify physical HDMI colors or AVR format. Earlier user observations are not reused as fresh verification. This limits HDMI certification, not the directly observed launcher/splash/login/library and bounded playback checks.

## Outcome, remaining limits and Git

**Branding integration and device UI acceptance PASS under the user's approved old-triangle/text-only instruction.** No graphic-source blocker remains for this selected design. The three invalid GitHub triangle PNGs remain unusable and excluded; a future switch to those specific files requires complete originals. The final combined tests pass after the test-only Looper correction. The recoverable startup AudioSink warning and missing external TV/AVR confirmation remain explicitly documented limits; no universal audio/DV/long-stability claim is made.

Implementation/assets/resources/docs commit **fa456080c**. Test-harness commit **40ebb861c**. This acceptance is a separate focused local evidence commit. Unrelated dirty work is retained and content-hash audited; related existing branding-resource changes are incorporated. Original source files, old artifacts and historical reports are preserved. No reset/clean/stash/push/tag.
