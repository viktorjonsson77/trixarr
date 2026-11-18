# TRIXARR_DOLBY_VISION_SAFE_NEGOTIATION_V1

Date: 2026-11-10. Branch: `feat/trixarr-dv-safe-negotiation-v1`.

**FEATURE_PASS / PHYSICAL_PARTIAL**. Latest controlled A/B: 2026-11-18, see the physical acceptance section below. Unsafe converted DV deliveries are rejected before opening a playback URL. Real video-copy/audio-transcode preservation remains UNKNOWN and is blocked; this is a documented delivery limitation, not a claim that every server lacks this capability.

## Hardware and installation — DEVICE VERIFIED

Only explicit ADB serial `100.91.172.18:5555` was used. NVIDIA SHIELD Android TV, Android 11 / API30, arm64-v8a. Previously established mdarcy fingerprint and HDMI chain Shield → Yamaha RX-V6A → TCL apply. Model/manufacturer/API/ABI were freshly checked before installation.

Package `se.viktorjonsson.jellyfintv.debug`, version `0.0.0-dev.1`, code1, min21/target36. Existing installed signer was pulled and verified before update. Two controlled candidate revisions were installed using `install -r` after passing identity/signing checks; no uninstall, clear or data deletion. First install remains 2026-09-02 23:52:47; CE data inode remains **652903**; final update 2026-11-10 21:47:15 device local. Existing authenticated session and library remained usable.

Final candidate:

* APK: `/opt/jellyfintv-artifacts/trixarr-dv-safe-negotiation-v1/trixarr-dv-safe-negotiation-v1-debug-r2.apk`
* SHA-256: `84de4c7922d9bf6a8f26885bd7dadfe3727b82e2ea7a4978aa773310e3bfd44d`
* Signer SHA-256: `f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c` (Android Debug; matches installed baseline)
* ARM64 `libffmpegJNI.so` and `libandroidx.graphics.path.so`: ELF64 / AArch64 verified. APK also contains other existing ABIs.
* Earlier candidate retained, SHA-256 `6e4dcb44dddd6ce9e21aa153daeabad88bca58f283ef1601bcf5674e7b00f407`; no previous artifact overwritten. R2 adds stricter converted-audio proof and expanded diagnostics, without changing the audio pipeline.

## Implementation — HOST VERIFIED

See [architecture](../../architecture/TRIXARR_DV_SAFE_NEGOTIATION_V1.md). Central pure policy and shared Jellyfin adapter are integrated before URL creation in legacy PlaybackManager and experimental resolver, and merged with PlayerEngineManager's existing capability decision. Explicit source requests no longer silently fall back. Audio selection is validated against request/response metadata. A rejected DV delivery bypasses subtitle retry, ends the current generation, never claims playback start, and exposes the reason in diagnostics. Server-opened live resources are closed on rejection. Coroutine cancellation is rethrown.

The existing DeviceProfile generation is unchanged: bitstream OFF excludes AC3/EAC3. No extra codec support is advertised, no bitstream preference overridden, and no global audio/display settings changed. Existing Media3 audio sink, renderer preference, AudioTrack setup, codec selector, subtitle/audio selection and refresh implementation remain intact. A delivery rejection is distinct from decoder initialization failure. Existing session cleanup and error ownership remain authoritative.

## Tests and build — HOST VERIFIED

Controlled build succeeds with Gradle/JDK21 using the separate build worktree recorded in [build-inputs.json](build-inputs.json). It includes the existing branding/catalog baseline and this task, while excluding five unfinished startup/refresh files from the prior task. Those files remain untouched in the user's workspace. The workspace-wide focused run still has the pre-existing failing test `startup stuck before decoder cannot buffer indefinitely`; it is not claimed fixed here.

Final controlled JVM run: **77 tests, 0 failures** (core17, Jellyfin5, app55); [spec counts](jvm-tests.json). Build tasks: `:playback:core:testDebugUnitTest :playback:jellyfin:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`, with the existing focused-tests init script. Android regression on this Shield and final candidate: **4/4 PASS**, [output](android-tests.txt). These cover actual adapter/codec/FFmpeg/lifecycle seams, not physical HDMI certification.

| Requested cases | Evidence |
| --- | --- |
| DV5 EAC3 ON/OFF, unsupported audio | Policy + SDK adapter: declared EAC3 accepted; excluded EAC3 rejected even if server claims DirectPlay |
| Copy available/unavailable, unsafe full transcode | Independent server/copy/container/profile/converted-audio proof gates; actual SDK conversion has insufficient proof and is rejected |
| DV7 MEL/FEL | Profile7 original permitted subject to existing capability checks; conversion rejected; MEL/FEL processing never inferred |
| DV8.1 HDR10 fallback | Existing profile8 compatibility tests retained; converted fallback not automatically enabled or represented as DV |
| Unknown profile/missing metadata | Unknown remains visible; missing metadata cannot authorize converted video |
| Server rejects DirectPlay/only transcode | Real createStreamInfo seam rejects before URL/API access |
| MediaSource/audio changes | Exact identity and requested track mismatch tests |
| Decoder failure/cancellation/stale/reporting/resume/cleanup | Existing controller-chain regressions plus new manager/owner tests; rejected delivery has no start/stop report and no fallback engine |
| DV/audio/seek/refresh regression | Existing synthetic controller/codec tests; physical observations below remain separate |

## Physical observations — separate video and audio

The user's update explicitly verifies the following **USER VERIFIED** observations. They belong to the tested baseline configurations, not automatic certification of every new APK or every DV file.

| Application / setting | TCL / colors | Yamaha RX-V6A |
| --- | --- | --- |
| Original Jellyfin 0.19.10, bitstream ON | Dolby Vision working, correct colors | **PCM** |
| Trixarr, existing working configuration | Dolby Vision working, correct colors | **Dolby Digital Plus (DD+)** |

**Audio-output difference VERIFIED; root cause UNKNOWN.** Both apps use Media3 1.8.0. The observation does not establish Atmos or prove that the setting itself forces encoded output.

Fresh runtime sequences were sequential, with no overlapping playback:

| Test | Results | Boundary |
| --- | --- | --- |
| J02 original Jellyfin, existing user-enabled bitstream | Same Resident Evil item/static MediaSource, audio index1; DirectPlay; Nvidia DOVI / video/dolby-vision; PCM16 48kHz, native channelMask63 | DEVICE VERIFIED. Playback 21:44:27–21:44:55 device local, ~26s media time; targeted audio probe, not a 90s stability acceptance test |
| N01 final Trixarr candidate, existing setting | Resident Evil source `dae676fc22d2a272784e30b9ff521ab8`; source DV5; DirectPlay; Nvidia DOVI / video/dolby-vision, 3832×1600; E_AC3 48kHz / native channelMask63, DIRECT flag; stop at media position86433ms and ExoPlayer release | DEVICE VERIFIED. 21:49:05–21:50:35 local. New-candidate physical TCL/Yamaha observation NOT VERIFIED |
| N01 display | Mode49 before, mode40 during, restored49 after stop | DEVICE VERIFIED Android mode state; physical HDMI refresh NOT VERIFIED |
| Bitstream OFF on new candidate | Not performed; explicit approval requested and pending | NOT VERIFIED. Setting was not changed |

N01 diagnostics visibly showed server DirectPlay/DirectStream/Transcoding flags true and selected audio1. Those flags describe available delivery candidates, not proof that converted DV preserves signaling. Full XML diagnostic capture failed because UIAutomator could not reach idle state; the relevant screenshot fields are explicitly transcribed in [N01 observation](N01-diagnostics-observation.txt), with actual decoder/audio independently captured in metrics. Dropped-frame totals and complete buffering history were not exported and remain NOT VERIFIED. No crash or decoder failure appears in the retained targeted logs.

## Audio root-cause assessment

J02 now independently establishes DirectPlay and Nvidia DOVI with original bitstream ON; earlier ON evidence had lacked fresh logs. The static item/MediaSource and selected audio index match the prior EAC3 source. However, original's selected compressed audio input format/renderer and byte-identical received EAC3 stream have **not** been independently captured. Consequently PCM is not attributed to a specific audio sink configuration or server transformation.

Source comparison against upstream v0.19.10 (`984181a3d6ab14e9a6d2dcc850c582e1c138bd95`) previously established unchanged DefaultRenderersFactory/default sink, extension ON/PREFER setting, offload preferences and audio attributes. J02 loads the FFmpeg renderer, which does not prove selection. Both server-side audio transcoding and client-side decoding must be assessed from the actual delivery and input format; the older OFF converted test cannot be reused as proof for this ON direct-play run. Capture original input EAC3 format/selected audio renderer, FFmpeg preference, active route and track overrides next. No audio-engine rewrite or speculative sink change was made.

## Remaining limits

* Historical 2026-11-10 limitation: OFF testing awaited approval. Explicit authorization was supplied for the 2026-11-18 A/B below; OFF and restoration are now device verified. Fresh physical observations remain pending.
* Real copy/remux DV-preservation evidence is absent; unsafe/unknown DV conversion is deliberately unavailable. Unsupported audio may therefore result in a clear unsupported-path error rather than playback.
* New-candidate DV8/HDR10/SDR, seek/resume and full audio-format physical regressions remain unverified; synthetic regressions passed. No general Profile5/7/FEL/8 compatibility claim is made.
* Existing live-stream cancellation outside the new rejection path was not redesigned. No new fallback/session/report loop was introduced.
* Physical DD+ baseline is preserved by unchanged pipeline and E_AC3 DIRECT runtime output; new-candidate AVR verification is still required.

## Git

Implementation/architecture commit: `37bc370db`. Evidence is a separate local documentation commit. No push/tag. All 195 pre-existing dirty status entries, including the five unfinished prior-task files, are preserved. The isolated build directory remains available for reproduction; it is not a deployment target.

## TRIXARR_DV_SAFE_NEGOTIATION_PHYSICAL_AB_V1 — 2026-11-18

**TRIXARR_DV_SAFE_NEGOTIATION_PHYSICAL_PARTIAL**. Both controlled attempts completed and original setting restoration verified. Physical TCL/Yamaha observations were requested during each attempt; no answer has been received at recording time. No physical observations from earlier tests are attributed to these runs.

### Identity and controlled variable

Explicit serial `100.91.172.18:5555` only. Initial connect reported authentication failure, then `adb devices -l` reported authorized `device`; no device setting was changed to resolve access. Fresh properties: SHIELD Android TV / mdarcy, Android 11, arm64-v8a. Installed package `se.viktorjonsson.jellyfintv.debug`, version `0.0.0-dev.1`, code 1, min21/target36, first install 2026-09-02 23:52:47, last update 2026-11-10 21:47:15. Installed base.apk SHA-256 freshly read: `84de4c7922d9bf6a8f26885bd7dadfe3727b82e2ea7a4978aa773310e3bfd44d`, exact match to the recorded R2 artifact. No install, uninstall, pm clear, production edit or build.

Original `pref_bitstream_ac3` was **ABSENT** in the app default preferences: effective **ON**, using the existing boolean default `true`. A ran unchanged. After A's normal player release, the app was force-stopped and only that preference was temporarily set to boolean `false` using the app's `run-as` access; persisted readback verified OFF, and a fresh process loaded it for B. Direct activity launches into settings were denied by Android (non-exported activity / calling package UID); no access control was changed. No Android global audio, Jellyfin server, Kodi, codec-selection or DV-policy settings changed.

Both launches opened the exact Resident Evil item `dae676fc-22d2-a272-784e-30b9ff521ab8`; diagnostics independently confirmed identical MediaSource `dae676fc22d2a272784e30b9ff521ab8` and audio index **1**. Source metadata in B: MKV, HEVC Main10, 3832×1600, DV Profile 5 Level 6, EAC3 6 channels / 5.1 / 48 kHz. No source or track switching, automatic retry, fallback bypass, seek or quality changes.

### ON/OFF comparison — DEVICE VERIFIED

Times below are device local (UTC+1); retained logs include host UTC capture time.

| Observation | A — bitstream ON | B — bitstream OFF |
| --- | --- | --- |
| Attempt / duration | 21:40:50 launch; audio start21:40:53.544; stop21:42:29.755 at **95221ms** media position (~96s active) | 21:43:30 launch; rejected before first media; stop21:43:59.668 at **0ms**. A 90s playback was impossible after policy rejection; no retry |
| Server-selected delivery | **DirectPlay** | **Transcode** proposed; **not played** |
| Server candidate flags DP / DS / TC | true / true / true | false / false / true |
| Selected video decoder | **OMX.Nvidia.DOVI.decode** | None created; diagnostics UNKNOWN / player IDLE |
| Actual video MIME | **video/dolby-vision**, 3832×1600 (released codec metrics) | No delivered video / MIME; source DV5 is not proof of output |
| AudioTrack | **AUDIO_FORMAT_E_AC3**, 48000Hz, native channelMask63, AUDIO_OUTPUT_FLAG_DIRECT; started successfully | None created; no rendered audio output |
| DV preservation | Original DirectPlay and actual DV decoder/MIME verified; physical HDMI activation pending | Preservation UNKNOWN; conversion **rejected**, no unsafe converted video rendered |
| Audio-only transcoding | No conversion selected | **Not demonstrated**: videoCopyVerified=false, containerSignalingVerified=false, convertedAudioCompatibility=UNKNOWN. Cannot label the proposed transcode audio-only |
| Policy / error | No decoder failure, fatal crash or playback error in retained targeted logs | Allowed=false; terminal **UNSAFE_DV_DELIVERY**; session ERROR, reporting NOT_STARTED, fallback NONE |
| Cleanup | ExoPlayer Release; AudioTrack flush/stop/destructor status0, underrun0; codec lifetime ends | ExoPlayer Release, stop at0, no codec/AudioTrack records for app UID since B start |
| TCL DV indicator / colors | Requested; **NOT VERIFIED** | Requested; **NOT VERIFIED**, playback rejected |
| Yamaha displayed format | Requested; **NOT VERIFIED** (E-AC3 runtime output does not substitute for AVR observation) | Requested; **NOT VERIFIED**, no media audio started |

B rejection text: “No verified Dolby Vision-preserving delivery. Video conversion is blocked; choose supported audio or another source”. The actual player stayed IDLE with zero buffered media; session reporting never started. This verifies rejection of an unproven converted DV path, not a successful OFF video/audio playback comparison. Video cannot be claimed unchanged between running A/B outputs because B produced no video.

### Captures and limitations

* [A negotiation screenshot](A_ON-negotiation.png) shows selected source, DirectPlay, candidate flags, audio1 and source DV5. A UIAutomator full diagnostic export timed out; fields outside this screenshot are not represented as a captured full panel.
* [A filtered log](A_ON-logcat.txt) and [A app-UID codec/AudioTrack metrics](A_ON-media-metrics.txt) establish actual decoder, MIME, output and release. No full buffering history or dropped-frame total was exported.
* [B full diagnostic text](B_OFF-diagnostics.txt), [B screenshot](B_OFF-negotiation.png), [B filtered log](B_OFF-logcat.txt), [B app-UID metrics](B_OFF-media-metrics.txt) establish same identity/track, server proposal, rejection, no output and release. The empty B metrics body means no matching app-UID codec/audio records in that interval.
* Server delivery decisions are captured through the client's real Jellyfin negotiation diagnostics; no independent server session/API or ffmpeg process audit was performed. Candidate availability flags are not actual delivered-stream proof.
* Targeted logs omit lines containing URLs, auth/token/key/password/cookie terms or media storage paths. No credentials, authentication files or raw network payloads are retained. No new production tests were needed for this evidence-only physical exercise.

### Restoration — VERIFIED

After B's normal stop/release, the app was stopped and the temporary `pref_bitstream_ac3` entry removed, restoring its original absence and effective ON value. Fresh readback verified absence; parsed preference tree matched the saved pre-change tree **exactly**, including all unrelated preferences. A second independent read verified `ABSENT / true (app default)` at host20:44:14Z. The installed app was reopened to the Resident Evil details screen without starting playback. Both test players had released before restoration; there is no claim of an independently audited server cleanup. **RESTORATION_BLOCKED does not apply.**

Remaining acceptance blockers: fresh user confirmation of A TCL Dolby Vision indicator, correct colors and Yamaha format; B's physical no-playback/AVR behavior; successful DV-preserving audio-only conversion remains unproven and safely unavailable for this negotiated OFF path. No full physical PASS is claimed.

### Evidence commit and workspace

This section and new A_ON/B_OFF captures are committed together as a focused local documentation commit (discover with `git log -1 --format=%H -- docs/evidence/trixarr-dv-safe-negotiation-v1/ACCEPTANCE.md`). No push or tag. Unrelated pre-existing dirty work is preserved and checked against the initial status snapshot after committing.
