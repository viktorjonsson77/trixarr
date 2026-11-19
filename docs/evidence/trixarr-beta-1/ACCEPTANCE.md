# Trixarr Beta 1 acceptance — 2026-11-19

**TRIXARR_BETA_1_PARTIAL**

**BETA_BUILD_PASS** for the signed debug Beta candidate. **PHYSICAL_BETA_PASS NOT
ACHIEVED**: fresh TCL/Yamaha observations are not available, DV7 source playback is
blocked by the existing safety policy, and actual Activity recreation remains unverified.
No terminal playback failure was observed on the tested DV5/DV8 supported paths.
This is a usable debug Beta candidate, not a production-signed release.

## Baseline and integration — IMPLEMENTED

See [release notes](../../releases/TRIXARR_BETA_1.md) for the dependency chain and fixes.
No AGENTS.md was found at repository or applicable parent paths. Existing architecture,
DV-safe/local-audio evidence and branding acceptance were read before implementation.
The diagnostic skill's reproduction-first workflow was used.

* Original: `/opt/jellyfintv`, `feat/trixarr-branding-v2`, HEAD
  `1d7407710293a829c482b56baf5d1f5099eaba3f`, **117** dirty entries.
* Integration: `/opt/jellyfintv-beta-1`, `release/trixarr-beta-1`.
* The accepted PlayerEngine, Media3 1.8.0, DV-safe negotiation, local E-AC3 decoding,
  DD+ passthrough, refresh matching, naming, triangle icon/banner and splash are included.
* Existing verified APK dependencies in dirty app/catalog/startup/package files were
  committed as `bc9d6533b`. They were already present in the prior branding build;
  this task did not introduce a new catalog design or navigation feature.
* Unfinished duplicate refresh-rate classes were excluded, preserving the existing
  controller/window implementation. Startup observations were completed within the
  existing PlaybackSessionOwner, with no second session owner or retry engine.
* `45db472d6` contains the stability fixes and regression tests;
  `163580d4b` contains Beta version metadata.
* [Branch inventory](branches.txt), [original status](original-worktree-status.txt),
  [overlay hashes](integration-overlay.json), and [preservation verification](original-worktree-preserved.json).
  All 65 original overlay inputs still match their initial hashes/deletion states;
  original status is byte-identical and still has 117 entries. No reset, clean,
  blanket stash, merge of experimental branches, push or tag was used.

## AudioSink diagnosis — IMPLEMENTED, TESTED, RUNTIME VERIFIED

Three separate issues must not be conflated:

1. **Recoverable route interruption:** the recurring WriteException is now captured
   as errorCode **-6 / DEAD_OBJECT**, recoverable **true**, at position 0 during DV5
   startup (BUFFERING), and at 179 ms during DV8 startup (READY). The events coincide
   with HDMI/window refresh-rate transitions. Android media metrics show old E-AC3
   tracks flushed/destructed, replacement E-AC3 DIRECT output and continuing playback.
   No terminal PlaybackException followed in these supported tests. This supports
   classification **B: recoverable audio output error** for these instances. No
   error was hidden or indiscriminately downgraded; Media3's recovery is preserved.
   The earlier user-initiated restart cannot be classified as a crash from this evidence.
2. **Probe callback lifecycle:** a disposable context-bound DefaultAudioSink queried
   on a worker can record a null playback Looper while receiving main-thread route
   callbacks. Capability-only probes now use AudioCapabilities.getCapabilities(context)
   and a context-free snapshot sink. Native/FFmpeg decoder checks and PCM/channel/rate
   checks remain; the production renderer sink still receives route changes normally.
   The worker Android regression passes without creating callback ownership.
3. **Stale diagnostic release:** the old AudioTrack's asynchronous release could
   overwrite replacement output fields with UNKNOWN. A real Android regression failed
   before the fix ([red result](diagnostics-regression-red.txt)). Live initialized-track
   counting now clears fields only after the final release, including equal configs.
   Candidate 2 runtime shows correct E-AC3/PCM output after route recovery.

An initial Android run accidentally used the previous test APK and crashed due to
its real-sink test querying a context-bound sink from a worker without a Looper.
That test now runs on main, as a real playback sink requires. The new capability
probe regression deliberately remains on a worker. This harness failure and the
preliminary playback interruption caused by starting instrumentation are not reported
as application playback crashes. Final candidate: 12 Android tests PASS.

## Startup diagnosis — IMPLEMENTED, TESTED; historical origin NOT VERIFIED

The historical One Battle After Another test showed ~124 seconds buffering with no
initialized decoder. It negotiated Transcode before the DV-safe policy existed.
The evidence does not establish whether the original wait was network, parsing,
server transcoding, track discovery or decoder preparation. No unsupported root
cause or codec workaround has been invented.

A client defect **was** reproduced: a controller session stuck before decoder readiness
could buffer indefinitely. [Red controller result](startup-regression-red.txt).
Generation-gated startup monitoring now bounds negotiation (45 s), source/opening and
preparation (90 s per phase), decoder and first frame (30 s per phase). These are
phase budgets, not a single total timeout. Monotonic time excludes background periods;
bytes cannot postpone the deadline. Events record source/load/track/decoder/frame
progress. Failure uses normal owner cleanup/reporting, closes playback and does not retry.

Current One Battle After Another source: DV7 L6, HEVC Main10/PQ, source range mapped
Unknown by the SDK, TrueHD 7.1/48 kHz default track 1, AC3 5.1 alternative track 2.
Both ON and OFF select **Transcode**, without verified video-copy/DV signaling.
Both are rejected as **UNSAFE_DV_DELIVERY**, in ~208 ms for the ON attempt, before
source loading, decoder or AudioTrack initialization. No 124-second stall occurs
on this current path. Local TrueHD decoding reports available when OFF; therefore
absence of an audio decoder cannot be asserted as the server decision's cause.
The exact server transcode reason and a supported DV7 original-video path remain
unresolved. No alternate track was forced, no unsafe conversion was opened and no
DV7 FEL/MEL claim is made. Failed-attempt cleanup was followed by successful DV8 playback.

## Tests — TESTED

| Suite | Result | Coverage |
| --- | --- | --- |
| Playback app JVM | 62 PASS | Controller, owner/engine chain, reporting, DV/audio policies, pause/seek, replay, cancellation, stale callbacks, simulated recreation/background, failures |
| Core JVM | 29 PASS | Capability/DV/local-audio policies, cleanup/ownership; new phase deadlines, byte progress and stale-event regressions |
| Jellyfin JVM | 5 PASS | Playback services/reporting/session behavior |
| Integrated catalog/startup JVM | 41 PASS | Accepted catalog and launch dependencies |
| Shield Android | 12 PASS | Labels, launcher/approved resources/signature, real PCM sink, FFmpeg, worker capability probe, Media3 lifecycle and delayed-release regression |
| Approved branding verifier | PASS | Original symbol/gradients/canvas and resource hashes/dimensions unchanged |
| APK/resource build | PASS | assembleDebug + assembleDebugAndroidTest |

Total **137 JVM + 12 Android**, no failures or skips in these selected suites.
[Playback counts](playback-jvm-results.json), [catalog/startup counts](catalog-startup-jvm-results.json),
[Android results](android-tests.txt), [branding verification](branding-verification.json).
Commands used JDK21, `/opt/android-sdk`, the existing completion `focused-tests.gradle`,
then `branding-startup-tests.gradle` for catalog/startup. No unrelated full suite was run.
Actual Activity recreation and a full lifecycle soak are NOT VERIFIED; JVM checkpoint
fixtures and real adapter release tests do not constitute actual Activity recreation.
No global-setting-writing launch test was run.

## Shield playback — RUNTIME VERIFIED

NVIDIA SHIELD Android TV, mdarcy / Shield Pro, Android 11 API30, ARM64.
ADB explicitly targeted `100.91.172.18:5555` for every operation.
HDMI topology is the supplied Shield → Yamaha RX-V6A → TCL chain.

| Case | Server / source | Video | Actual audio | Outcome |
| --- | --- | --- | --- | --- |
| Resident Evil ON | DirectPlay; source dae676fc22d2a272784e30b9ff521ab8; audio 1 | Nvidia DOVI, video/dolby-vision, dvhe.05.06 | E-AC3 DIRECT, 6ch/48k; Java encoding6 | Start/pause/resume/seek; route recovery; normal exit |
| Resident Evil replay ON | Same source/audio, new session ID | Same DV5 decoder/MIME | E-AC3 DIRECT, 6ch/48k | 25.255 s final position; normal stop/release, zero track underruns |
| Resident Evil OFF | Same original source/audio; DirectPlay; no server audio conversion | Same DV5 decoder/MIME | ffmpegLavc60.3.100-eac3 → PCM16, 6ch/48k; Java encoding2, native mask63 | 83.502 s final position; 8 dropped video frames; no terminal playback error; normal stop/release |
| One Battle After Another ON | Transcode, default TrueHD track1 | No decoder | No AudioTrack | Safe early rejection |
| One Battle After Another OFF | Transcode despite local decoder capability | No decoder | No AudioTrack | Safe early rejection; original setting restored again |
| Insidan ut 2 ON | DirectPlay; source 20d28146fdd74f675b91681fb935b9bf; audio1 | Nvidia DOVI, video/dolby-vision, dvhe.08.06 | E-AC3, 6ch/48k | Started after failed DV7 attempts; pause/seek/resume; stopped at68.088s |

The ON foreground/relaunch test returned to Home and cleanly stopped the old player.
Automatic in-place playback continuation after launcher re-entry is **NOT VERIFIED**;
this candidate does not claim it. Both replay and supported playback after rejection
were verified without restarting the device or clearing data.

For DV8, an explicit seek advanced position **24.845s →55.687s**; player returned READY
and then played before normal stop. DV8 is runtime-confirmed for this file/profile,
not a blanket compatibility guarantee or proof of full enhancement-layer rendering.

Refresh: DV5 uses mode22 **3840×2160@23.976025**, DV8 mode26 **24.000002**. After normal
exits, active/default mode is31 **59.940060**. Existing window mode selection/restoration
is retained, with no global display settings written. No extra refresh policy was added.

Evidence is the corresponding `ON-*`, `REPLAY-*`, `OFF-*`, `DV7-*`, `DV7OFF-*`, `DV8-*`
UI/log/metrics files. UI was paused for readable diagnostics; wall duration includes
these pauses and is not continuous playback duration. `PRELIMINARY-ON-*` is development
candidate 1 evidence, not final candidate acceptance. Logs are filtered before storage:
no credential/token/URL/path or raw authentication exports are included.

Android's native PCM AudioTrack651 also reports `restoreTrack_l: dead IAudioTrack`
during the HDMI transition and restores as653 in the same session;653 is destructed
on normal stop with status0/underrun0. This is framework recovery, not a missing
Java destructor leak for651. [Cleanup summary](cleanup-summary.json) records both
raw IDs and this distinction. A live `dumpsys media.codec` client check was unavailable
on this Shield; no negative active-client claim is made from that unavailable service.

## Physical output — NOT VERIFIED for Beta 1

Fresh user confirmation was requested during Resident Evil ON for TCL Dolby Vision,
correct colors and Yamaha DD+, and during OFF for TCL DV/colors and Yamaha PCM/audio.
No answer was received during this run. Earlier "ja" and "Yamaha visar DD+" belong to
previous acceptance, not this Beta. Decoder/MIME/AudioTrack facts do not replace
external TV/AVR observations or prove speaker channel mapping, audible sync or color.
Thus **USER PHYSICALLY VERIFIED: none for this Beta**. Physical acceptance is PARTIAL.

## Branding, installation and restoration — RUNTIME VERIFIED

* Approved reused blue/purple triangle and Trixarr wordmark are unchanged. No artwork
  was regenerated. Android resource/signature/launcher tests pass; launcher UI exposes
  Trixarr ([launcher text](LAUNCHER-ui.txt)).
* [Cold-start frame at4.1s](cold-start-logo.png) shows the approved logo/wordmark; the
  earlier [frame at1.5s](cold-start.png) is the startup background. Existing 350ms
  animation/transition is retained; no delay/network stage was added.
* Existing Jellyfin login is preserved and library loads after installation and final
  cold start ([home](FINAL-HOME-ui.txt)). Package data directory inode652903, UID10048
  and firstInstallTime2026-09-02 23:52:47 are unchanged.
* Safe `adb install -r` only, after device/package/version/signature verification.
  No uninstall, data clear, signing change, Android global audio/display preference,
  Jellyfin server configuration or Kodi change occurred.
* Original bitstream key was **ABSENT**, effective **true / ON**. Only that app preference
  was changed temporarily, preserving other preferences. Restoration removes the key
  again and verifies presence/value. Final verification after both A/B and DV7 tests:
  **ABSENT → default ON**. [Restoration](setting-restored.json), [final readback](setting-final.json).
  No restoration blocker. All playback was stopped before cold startup/launcher checks.

## Delivered APK — TESTED, INSTALLED AND HASH VERIFIED

* `/opt/jellyfintv-artifacts/trixarr-beta-1/trixarr-0.1.0-beta.1-candidate2-debug.apk`
* SHA-256: `e4a0bf09eb64823c8f90a73268e7914a074200768dd9d63aecacee8f9640e434`
* Signer SHA-256: `f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`
* Package: `se.viktorjonsson.jellyfintv.debug`; namespace `org.jellyfin.androidtv` unchanged.
* VersionName `0.1.0-beta.1`; versionCode **10001**, previously1.
* ARM64 ELF libraries verified; FFmpeg JNI and androidx graphics path bytes match the
  previous branding APK. [Identity, hashes and native comparison](apk-verification.json),
  [APK metadata](apk-badging.txt), [before](device-before.json), [after](device-after.json).
* Installed base.apk was pulled and its hash matches candidate2 exactly. Candidate1
  and all previous artifacts remain available. No production release APK was created.

## Remaining limitations / release readiness

1. RELEASE_SIGNING_BLOCKED; this verified debug Beta safely updates the existing debug app.
2. Fresh TCL/Yamaha DV/DD+/PCM, audible channel layout and synchronization are unconfirmed.
3. One Battle After Another DV7 original-video path is unavailable in current negotiation;
   exact server reason/historical124s cause unresolved. No DV7 FEL/MEL acceptance.
4. No physical Activity recreation test, long soak, broad library matrix or network-failure
   injection. Lifecycle/error contracts are covered by the focused synthetic suites.
5. Recoverable HDMI route WriteException remains visible. No observed terminal failure,
   but repeated-route/device combinations beyond these short runs are not certified.
6. Launcher re-entry returns Home rather than claiming automatic playback continuation.

The build is ready for limited Beta testing of documented supported paths. These limits
prevent a full physical release PASS and must accompany distribution of this candidate.
