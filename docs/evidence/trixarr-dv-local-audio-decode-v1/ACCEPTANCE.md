# TRIXARR_DV_LOCAL_AUDIO_DECODE_V1

Date: 2026-11-18. Branch: `feat/trixarr-dv-local-audio-decode-v1`.

FEATURE_PASS. Physical assessment is recorded below separately. No broad codec/HDMI certification is implied.

## Root cause and implementation

Baseline [safe-negotiation acceptance](../trixarr-dv-safe-negotiation-v1/ACCEPTANCE.md) and [comparison](../trixarr-vs-jellyfin-dv-comparison-v1/PLAYBACK_COMPARISON.md) were read along with the Phase 2 completion architecture. No AGENTS.md or GLOSSARY.md was found in the repository or applicable ancestor paths. Initial branch was `feat/trixarr-dv-safe-negotiation-v1`; initial dirty status had **195 entries**. New local branch created without reset, clean or stash.

Disabling Dolby bitstream previously removed AC3 and EAC3 from DirectPlayProfiles/transcoding audio declarations without probing local decoding. Jellyfin proposed Transcode/AudioCodecNotSupported; the existing DV policy rejected the unverified converted video. Media3's default audio sink independently permitted encoded output, so profile advertisement alone could not implement OFF-to-PCM.

[Architecture](../../architecture/TRIXARR_DV_LOCAL_AUDIO_DECODE_V1.md) explains the shared pure LocalAudioDecodePolicy, actual native/packaged FFmpeg/PCM probes, OFF DeviceProfile codec/channel/rate conditions and shared PCM-only sink. Empty capability lists are omitted rather than advertised as wildcard profiles; the shared DV adapter rejects an explicitly empty video profile. ON retains legacy declarations and the standard sink. OFF disables audio offload and encoded bypass; native/FFmpeg decoding uses existing PCM timing/channel processing. No video selector, DV MIME, engine, Kodi, global Android audio setting or server code change.

On this Shield, new runtime diagnostics establish **native EAC3 decoder=false, packaged FFmpeg EAC3 decoder=true**, CAN_DECODE_AUDIO=true, CAN_PASSTHROUGH_AUDIO=true. OFF is therefore implemented through the existing software audio renderer, not by falsely claiming an Android MediaCodec EAC3 decoder.

## Tests — PASS

Controlled Gradle command is in [build-inputs.json](build-inputs.json). JVM counts in [jvm-tests.json](jvm-tests.json): **90 tests, zero failures/errors/skips**: core26, Jellyfin5, app59. Android [instrumentation](android-tests.txt): **6/6 PASS** on the actual Shield, two new local-audio tests and four existing player-engine regressions. No unrelated full test suite was run.

| Requested coverage | Evidence / scope |
| --- | --- |
| A–G, V: DV5/EAC3 ON/OFF, native/software available/unavailable, unsupported audio, PCM/5.1, DD+ | New pure policy tests; actual DeviceProfile/SDK adapter and PCM-only AudioSink tests; native Android sink/profile instrumentation; physical runs below |
| H: decoder failure | Existing controller `active decoder error is terminal with one failed stop and no retry` and initialization-failure regressions; no new fallback engine or server retry |
| I–J: safe copy/audio conversion vs full conversion | Positive synthetic video-copy evidence gates and independently missing-proof rejection; full DV video transcode rejected; SDK runtime cannot prove safe converted DV |
| K–N: DV7, DV8, HDR10, SDR | Pure local-audio/DV-policy composition plus existing capability/controller fixtures; no physical certification of those media classes |
| O–Q: track switch, pause/resume, seek | Existing controller fixtures exercise track commands, same-session identity, positions and ordered reports; PCM sink delegates mappings/timestamps unchanged |
| R–U: cancellation, stale callback, reporting, cleanup | Existing controller/owner/manager regressions plus PCM sink delegation/release test and native player lifecycle instrumentation |

An intermediate new test failed because an empty codec list was treated as a wildcard; production and test were corrected, and the complete focused run then passed. Initial fixture compilation also exposed required SDK DTO constructor fields; fixtures now use synthetic typed mocks. These are development results, not hidden final failures.

## Controlled build and installation — PASS

Build directory: `/opt/jellyfintv-local-audio-build`, cloned from the previous controlled `/opt/jellyfintv-dv-safe-build`, with exactly the task files overlaid. Its earlier branding/catalog baseline is recorded in the linked prior manifest. The same five unfinished startup/refresh inputs remain excluded; their current dirty workspace content is untouched. [Input hashes](build-inputs.json) match the built source. A workspace production compilation also passed; its pre-existing unfinished startup unit test is not claimed repaired.

Gradle test tasks, `:app:assembleDebug` and `:app:assembleDebugAndroidTest` passed with JDK21 / Android SDK36. Package `se.viktorjonsson.jellyfintv.debug`, version `0.0.0-dev.1`, code1, min21/target36. Both ARM64 native libraries (`libffmpegJNI.so`, `libandroidx.graphics.path.so`) were verified ELF64 / AArch64. Other existing ABIs remain packaged.

* APK: `/opt/jellyfintv-artifacts/trixarr-dv-local-audio-decode-v1/trixarr-dv-local-audio-decode-v1-debug.apk`
* APK and freshly installed base.apk SHA-256: `4091dc4e72557756cbc579fca53d189896fd6c80a535f951225a9ef7a488bcd9`
* Signer SHA-256: `f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`, exact match with freshly pulled installed baseline APK.
* Explicit ADB serial only: `100.91.172.18:5555`; NVIDIA SHIELD Android TV / mdarcy, Android11/API30, arm64-v8a.
* `adb install -r` succeeded. First install remains `2026-09-02 23:52:47`; CE data inode remains **652903**. Update time `2026-11-18 21:59:48` device local. Existing authenticated library/details playback worked. No uninstall, clear, data deletion, signer/package change or previous APK overwrite.

[Machine-readable identity](device-installation.json). Device installation and test package updates are local debug validation, not a production deployment. No push or tag.

## Shield A/B — DEVICE VERIFIED / PHYSICAL_PARTIAL

Original `pref_bitstream_ac3` was absent, effective **ON**. A ran unchanged. B used only a temporary boolean false in the app's default preferences, written with `run-as` while the process was stopped and verified before a fresh player process. No other preference or Android/server/Kodi setting was changed. Both complete diagnostic snapshots independently identify Resident Evil source `dae676fc22d2a272784e30b9ff521ab8`, selected source audio **index1**, HEVC Main10/DV5 level6, EAC3 5.1/48kHz. Media3 track ID2 is a separate identifier, not a different Jellyfin track.

All times below are device local UTC+1.

| Observation | A — bitstream ON | B — bitstream OFF |
| --- | --- | --- |
| Server delivery | **DirectPlay**, source audio SUPPORTED, allowed=true | **DirectPlay**, source audio SUPPORTED, allowed=true |
| Server candidate flags DP/DS/TC | true / true / true | true / true / true |
| Delivered video | **video/dolby-vision**, **dvhe.05.06**, 3832×1600, P5/L6 | Identical video MIME/codec/geometry/profile; released native codec metrics also confirm MIME |
| Video decoder | **OMX.Nvidia.DOVI.decode**, hardware=true | **OMX.Nvidia.DOVI.decode**, hardware=true |
| Actual selected compressed audio | **audio/eac3**, 6 channels, 48000Hz | **audio/eac3**, 6 channels, 48000Hz; no server audio conversion |
| Audio decoder | UNKNOWN in diagnostics; encoded sink path, no local decoder claimed | **ffmpegLavc60.3.100-eac3** |
| AudioTrack output | **EAC3**, encoding6, 48kHz, Java channelConfig252/native mask63, six channels, DIRECT | **PCM16**, encoding2, 48kHz, Java channelConfig252/native mask63, six channels; no encoded output; offload=false |
| Local decode / passthrough capabilities | native=false / packaged FFmpeg=true / encoded=true; enabled=true | Same declarations; enabled=false; selected LOCAL PCM |
| Playback extent | First session22:00:44–22:01:41 stopped at54011ms. Another ON session started22:02:04, paused at20309ms for diagnostics, resumed22:03:45, ended final snapshot **109086ms** | Start22:05:58, AudioTrack start22:06:02.216, paused22:08:11 at **129694ms**, normal stop22:08:29.510 |
| End snapshot reporting | **PROGRESS_SENT** | **PROGRESS_SENT** |
| Dropped video frames | **0** in final ON session snapshot | **1** cumulative in final OFF snapshot |
| Decoder / playback error fields | No named decoder/terminal playback error; **Audio sink WriteException** retained in final panel | Decoder/audio sink/playback fields UNKNOWN: no named error in panel/targeted logs |
| AudioTrack recovery | Initial encoded tracks were recreated during startup; first ON final stop underrun0 | Native warning `dead IAudioTrack, PCM`, successful restore of track466 after initial track464; final stop underrun0, destructor status0 |
| Dolby Vision preservation | Original network delivery and actual decoder/input verified; user TV confirmation below | Original network delivery and same actual DV5 video verified; fresh TV confirmation pending |
| TCL physical | User answered **“ja”** to the prompt asking about Dolby Vision indicator and correct colors | **NOT VERIFIED**: requested during playback, no response at recording time |
| Yamaha physical | User explicitly reported **“Yamaha visar DD+”** | **NOT VERIFIED**: requested exact displayed format; runtime PCM is not substituted for an AVR observation |
| Physical A/V sync | Not independently measured | Requested, **NOT VERIFIED** |

The first ON stop/restart was not an agent-requested retry: its cause is **UNKNOWN**. The user was asked whether the remote was used; no answer has been received. The second session was intentionally paused/resumed by the agent for diagnostics and reached109s of media. Do not describe A as one uninterrupted90s run or attribute the extra start to a decoder failure without evidence. The ON AudioSink WriteException and startup track recreation are recorded, not hidden as an error-free result. No unsafe converted delivery or video-decoder fallback was used.

B reached129.7s with original EAC3 over the network, actual local FFmpeg decoding and multichannel PCM. It had one startup AudioTrack restore and one dropped video frame. This is a bounded successful runtime sample, not a general sustained-bitrate/A/V-sync certification or proof that every PCM channel reaches the correct physical speaker.

[ON full diagnostic text](A_ON-diagnostics.txt), [ON panel screenshot](A_ON-diagnostics.png), [ON filtered log](A_ON-logcat.txt), [ON metrics](A_ON-media-metrics.txt).
[OFF full diagnostic text](B_OFF-diagnostics.txt), [OFF panel screenshot](B_OFF-diagnostics.png), [OFF filtered log](B_OFF-logcat.txt), [OFF metrics](B_OFF-media-metrics.txt).
[Physical observation provenance](physical-observations.json).

The existing negotiation panel deliberately retains DV preservation `NOT_VERIFIED` for original video because it cannot certify physical HDMI. Here original delivery, DV decoder/input and user observations are reported as separate evidence. Server decisions are the actual PlaybackInfo-derived client diagnostics, not an independent server/ffmpeg audit. Candidate Transcoding=true is availability, not a selected transcode. The old baseline OFF transcode rejection remains historical evidence; new synthetic tests retain the unsafe-conversion rejection gates. No unsafe transcode was deliberately forced on the installed candidate.

## Stop, cleanup and restoration

B stopped normally at129694ms and ExoPlayer Release was logged22:08:29.533. Native metrics establish video-codec completion with `video/dolby-vision`, AudioTrack flush/stop/destructor status0 and underrun0. The first ON session also has normal Release/codec/AudioTrack cleanup evidence. For the final ON session, the attempted back navigation did not yield a captured final Release before the app was force-stopped for preference mutation. That process exited before B started; **graceful final ON reporting/cleanup is not claimed verified**. No overlap between old ON and B player processes occurred.

After B normal release, the app was stopped, the temporary key removed and original **ABSENT / effective ON** restored. Canonical parsed preference trees match **exactly**, including all unrelated keys/values. Fresh independent reads after reopening the Resident Evil details screen verified absence again. The new details-only PID differs from both test player PIDs. No playback was started after restoration. [Restoration record](restoration.json). **Restoration VERIFIED; RESTORATION_BLOCKED does not apply.** Independent Jellyfin server-session cleanup was not audited.

## Acceptance and remaining limits

**FEATURE_PASS / PHYSICAL_PARTIAL — TRIXARR_DV_LOCAL_AUDIO_DECODE_PARTIAL.** Local decoding policy, real capability advertisement, shared Media3 PCM integration, unchanged original DV video, ON encoded output, focused tests and ARM64 APK are verified. Physical PASS is withheld pending fresh OFF TCL/color/Yamaha/AV-sync observations, clarification of A's unexpected stop/restart, and the final ON graceful-cleanup gap. ON TV/DD+ observations do not certify OFF.

Broader physical DV7/FEL/DV8/HDR10/SDR/seek/track-switch/HDMI-route regressions remain outside this one-source physical sample; synthetic regressions passed. Conservative codec/channel/rate probing may reject unusual formats that could decode. Capability declarations do not guarantee every stream; PCM does not retain Atmos object metadata. There is no new unsafe fallback or assertion of safe real video-copy/audio-transcode support. The new audio capability policy is not a duplicate video/session engine.

Implementation and architecture commit: **`d6a411e74`**. Evidence is a separate focused local commit. All original195 unrelated dirty status entries are preserved; no reset/clean/stash/push/tag. The controlled build inputs and APK intentionally exclude the same five unfinished prior-task files as the accepted R2 build. This task does not claim to repair their pre-existing workspace unit-test failure.
