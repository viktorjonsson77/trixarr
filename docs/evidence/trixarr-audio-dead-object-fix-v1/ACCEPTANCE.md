# Trixarr Audio DEAD_OBJECT investigation and recovery V1

**TRIXARR_AUDIO_DEAD_OBJECT_FIX_PASS** — the task's transient-failure path:
HDMI-route invalidation is verified, and playback recovered without restarting the
application or losing user data. **No production correction was needed or applied.**
This is a bounded recovery verification, not a guarantee that HDMI interruptions or
first-write failures cannot recur. The original V3 failure remains a real historical
failure; it is not relabeled successful and no permanent race-prevention fix is claimed.

## Baseline / scope

* Baseline: Beta1 + Branding V3, commit `9ed83d86cffcede063335112a890b09d43277087`.
* Dedicated branch/worktree: `fix/trixarr-audio-dead-object-v1`,
  `/opt/jellyfintv-audio-dead-object-v1`.
* Applicable parent/repository instructions were checked; no AGENTS.md or glossary
  was found. Existing Beta1, V3 and PlayerEngine/session evidence informed this work.
* Original worktree remains at117 dirty entries; Beta1 and V3 worktrees remain clean
  on their original branches. [Preservation record](preserved-worktrees.json).
* No production code, DeviceProfile, AudioSink, codec selection, DV safety policy,
  refresh policy, build configuration, package identity or signing key was changed.
* No uninstall, pm clear, user-data/cache deletion, installation, application restart,
  audio/display preference change or unsafe fallback was performed in this task.

## APK differential — VERIFIED

[APK comparison](apk-comparison.json) compares the actual signed artifacts:

| Candidate | SHA-256 |
| --- | --- |
| Accepted Beta1 candidate2 | `e4a0bf09eb64823c8f90a73268e7914a074200768dd9d63aecacee8f9640e434` |
| Branding V3 | `06283683675b1cd1028ad72f51cb306d60830a9cd8a886c07a5d03ac887c317b` |

**Every DEX, every packaged native library and the binary AndroidManifest.xml are
byte-identical.** Source differences outside documentation were exclusively branding
resources and branding instrumentation expectations ([diff inventory](v3-runtime-diff.txt)).
No unexpected runtime or build-configuration change was introduced by V3. This rules
out changed executable playback code as the cause; it does not claim identical HDMI
hardware timing between runs.

Current installed base.apk was pulled and matches V3 exactly. No new APK was built:
only tests/documentation changed, so the requested conditional build is unnecessary.
[Installed artifact](installed-apk.json):
`/opt/jellyfintv-artifacts/trixarr-branding-v3/trixarr-0.1.0-beta.1-branding-v3-debug.apk`.
Package remains `se.viktorjonsson.jellyfintv.debug`, version0.1.0-beta.1/code10001.
The existing verified debug signer is unchanged:
`f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`.

## Exact observed failure and cause

Primary cause: **transient loss/recreation of Android's HDMI output route invalidated
AudioTrack during startup, before its first successful encoded-audio write.**
This is not evidence that branding changed playback, that a decoder failed to initialize,
or that a previously released app-owned track was reused across sessions.

Android AudioService retains these HDMI route events for type0x400 / aux1_audio:

| Event, device-local 2026-11-19 UTC+1 | Original terminal attempt | Successful controlled attempt |
| --- | --- | --- |
| HDMI DEVICE_STATE_UNAVAILABLE | 21:39:12.438 | 21:46:48.697 |
| HDMI DEVICE_STATE_AVAILABLE | 21:39:12.924 | 21:46:48.780 |
| Route absence | **486 ms** | **83 ms** |
| Sink callback | -6, recoverable=false, BUFFERING, position0 | -6, recoverable=true, READY, position342ms |
| Outcome | Terminal error; normal stop/release | Existing Media3 renderer recovery, then continued playback |

[Route snapshot/history](audio-route-snapshot.txt), [controlled route transition](repro-route-transition.txt),
[original V3 log](../trixarr-branding-v3/PLAYBACK-logcat.txt).

In the original attempt, AudioTrack697 was created at21:39:12.450 while HDMI was
unavailable. Metrics record E-AC3, DIRECT, 6 channels/native mask63, 48 kHz, routed
DeviceId687, native state **STATE_STOPPED** at creation. No successful start event is
recorded for that track. It was destructed at21:39:13.253 in STATE_FLUSHED/status0;
ExoPlayer stopped at position0 and released at21:39:13.351. These are native metrics
states, not a retrospective Java AudioTrack.getState() measurement.
[Original metrics](../trixarr-branding-v3/PLAYBACK-media-metrics.txt).

The pinned Media3 **1.8.0 DefaultAudioSink.drainOutputBuffer** implementation explains
the recoverability distinction: for a dead object on non-offloaded output it marks
recovery possible after successful audio frames have been written. Before that it
retains a nonrecoverable write exception and eventually reports a terminal renderer
error. The original DIRECT track and recoverable=false match the first-write case;
the later attempt has already written audio before the short route loss.
[Upstream pinned source](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java).
No new retry or recovery policy was introduced.

The newly captured exact exception is **AudioSink.WriteException: AudioTrack write
failed: -6**, through DefaultAudioSink.drainOutputBuffer:1349 → processBuffers:1231
→ handleBuffer:1094 → MediaCodecAudioRenderer.processOutputBuffer:832, then renderer
exception wrapping:843 and MediaCodecRenderer.bypassRender. The logged format is
E-AC3/6ch/48k. [Full captured recoverable stack](recovered-audio-stack.txt).
The exception's temporary format_supported=NO_UNSUPPORTED_TYPE snapshot during route
recovery does not establish a permanently unsupported codec: E-AC3 DIRECT is observed
before/after recovery. Audio uses encoded bypass, not an initialized E-AC3 MediaCodec.

**The original nonrecoverable full stack was not retained in logcat.** Its existing
numeric callbacks, route history, metrics, terminal callback and release remain the
original evidence. The new full stack belongs to the later recoverable occurrence;
it is not presented as a recovered original stack.

The physical reason HDMI disconnected (refresh handshake, DV/HDR signaling, or an
external device action) is not conclusively distinguished. The original display-mode
request and Nvidia DOVI initialization precede the disconnect. No unsupported claim
about TV/AVR power state, cable faults or a particular firmware defect is made.

## Hypotheses / controlled reproduction

Ranked hypotheses presented before additional route/lifecycle checks:

1. Transient HDMI route loss: predicts a disconnect coincident with writes and a
   subsequent successful replay with unchanged code. **Supported by system history.**
2. Stale session/resource lifecycle: predicts retained tracks or an old session reused
   by replay. **Not supported:** tracks destructed, ExoPlayer released, new session IDs.
3. Decoder initialization failure: predicts decoder/codec-init errors instead of an
   AudioTrack write failure. **Not supported:** Nvidia DOVI initialization and subsequent
   unchanged Direct Play work; the captured exception is in audio output bypass.

One bounded original-configuration startup probe was run, then an explicit stop/replay
case. **0/2 controlled attempts reproduced the terminal startup failure**; both
encountered recoverable HDMI-route write interruptions and continued. The historical
terminal sample remains1 failed attempt, not rewritten as a passing sample. No stress
loop, injected HDMI outage, automatic app retry or speculative production workaround
was used. The task explicitly permits transient recovery verification instead of a fix.

`repro_once.py --trace` is a fast, red-capable classification loop for the exact captured
symptom: original terminal -6/position0 sample returns2/RED; recovered sample returns0/GREEN.
[Verified verdicts](feedback-loop-results.json). This does not manufacture a new
hardware reproduction. The script also supplies a single-attempt live path with movie/UI
checks, bounded wait and no app restart/retry. On a live GREEN the caller must normally
stop playback after inspection; it does not kill the app or change preferences.

## Shield recovery and lifecycle — RUNTIME VERIFIED

Device: NVIDIA SHIELD TV Pro/mdarcy, Android11/API30/ARM64,
explicit ADB target100.91.172.18:5555. Same Resident Evil source/audio1, DV5 L6.
Original bitstream setting remains absent/default **ON** throughout.

| Check | Controlled attempt REPRO | Explicit REPLAY |
| --- | --- | --- |
| App process | PID26782, same as original V3 failure | PID26782 |
| Session UUID | 4fe9f8b3-593c-4202-b84d-4a524a6002c5 | 6120ea47-97d6-43b6-a125-8e362de9a538 |
| Delivery | DirectPlay | DirectPlay |
| Video | OMX.Nvidia.DOVI.decode; video/dolby-vision; dvhe.05.06 | Same |
| Audio | E-AC3 DIRECT, 6ch/48k; no local decoder selected | Same |
| Pause/resume | Position82.243s →114.317s; PROGRESS_SENT | 47.952s →137.195s |
| Final position | 114.317s | 137.195s |
| Dropped video frames at captured final snapshots | 0 | 0 |
| Terminal playback error | None observed | None observed |
| AudioTrack lifecycle | 709/717 created, both destructed | 725/733 created, both destructed |
| Stop | Normal ExoPlayer Release | Normal ExoPlayer Release |

Each playback context has its own owner/session UUID; generation1 in a new context
is not a second simultaneous owner. Failed-session cleanup predates these starts.
Reporting, normal stop and fresh-session construction work without resurrecting the
old track. [Cleanup results](cleanup-results.json), `REPRO-*`, `RESUMED-*`, `REPLAY-*`.

The process's recorded start time predates the original failure; process continuity
is corroborated by PID26782 and elapsed process age770.9s at sampling (CLK_TCK100).
[Process record](same-process-recovery.json). No force-stop or APK installation was
needed to recover. This verifies both explicit recovery from the previous terminal
session and Media3's in-session recovery from a subsequent recoverable write error.

During playback the existing refresh matching selected mode22, 3840×2160@23.976025.
After normal stop, active/default mode31 is restored ([display readback](display-after-stop.txt)).
Branding V3 source/resources remain exact ([brand verifier](branding-preservation.txt)).
DV-safe negotiation, FFmpeg/local PCM, passthrough and refresh implementations are
byte-identical to the accepted baseline. No OFF-mode device test was added in this task;
prior PCM5.1 acceptance is preserved, not claimed as a newly performed PCM test.

Data inode652903, UID10048, first-install time, last-update time, version and original
bitstream preference are unchanged ([before](device-before.json), [after](device-after.json),
[prefs before](preferences-before.json), [prefs after](preferences-after.json)).
Existing authenticated navigation and media loading continued; no user-data loss observed.
TV Dolby Vision colors/indicator and Yamaha's physical display were not newly confirmed;
all new output claims above refer to Android/runtime evidence.

## Focused regressions — TESTED

**65 app JVM tests PASS**, zero failures/errors/skips: baseline62 plus3 new cases.
[Counts](jvm-results.json), [command/result](jvm-command-result.txt).

* Real Media3 WriteException → production diagnostics: both recoverable=false and
  true preserve error code/flag, without turning a raw sink callback into a terminal
  player error or stopping/releasing the player. Renderer terminal callbacks remain
  authoritative; no warning is hidden.
* Production controller/owner/reporting chain: terminal error performs exactly one
  failed stop with no automatic retry; explicit replay gets a fresh identity; an old
  start callback is ignored; subsequent pause/resume/stop reports normally.

These tests mock hardware/UI/HTTP boundaries and do not synthesize a physical HDMI
handshake. They protect diagnostics and session recovery, not a nonexistent new
AudioSink recovery algorithm. No Android instrumentation run or new APK was needed;
the unchanged installed APK was the physical test subject throughout.

## Commits / publication

Repository: `viktorjonsson77/trixarr`.
Dedicated branch: `fix/trixarr-audio-dead-object-v1`.
Focused tests commit: `be21d55fae2dfbca837a6b2cdec7cb7156d3535c`.
[Published tests](https://github.com/viktorjonsson77/trixarr/commit/be21d55fae2dfbca837a6b2cdec7cb7156d3535c),
[publication verification](github-tests-publication.json).
Evidence is committed/pushed subsequently on the same branch; final delivery SHA is
reported to the user. No merge to main, force push, tag or release publication.

## Remaining limitations

* Transient route failures may recur. This work verifies recovery under the task's
  transient-case condition; it does not prevent all first-write DEAD_OBJECT failures
  or add an automatic retry of a terminal session.
* Exact external HDMI-disconnect trigger and the original full nonrecoverable stack
  are not verified. The captured current stack is explicitly identified as recoverable.
* Two successful controlled starts are not a long-term soak or a broad hardware matrix.
* No new bitstream-OFF/PCM device run or external TCL/Yamaha observation was performed.
* Existing Beta limitations (production release signing, full DV7/FEL/MEL, actual
  Activity recreation, historical buffering cause) remain outside this focused task.
