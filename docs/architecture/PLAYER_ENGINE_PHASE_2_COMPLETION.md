# Player Engine Phase 2 completion

## Implemented architecture

The legacy TV player remains the default. The shared `PlayerEngine<T>`,
`PlayerEngineManager`, `PlayerCapabilityPolicy`, `PlaybackSessionOwner` and
`OrderedPlaybackReporter` now live in `playback/core/.../engine`. The former
app-only attempt gate is removed. `PlayerBackend` extends the same engine contract;
`ExoPlayerBackend` implements track selection and idempotent resource release.
`BackendService` ignores callbacks from detached backends and releases replaced
backends. `PlaybackManager` owns the core session owner and releases its services.
No Kodi code, library, installation, native engine or release key was added.

Legacy chain:
`PlaybackController -> PlaybackSessionOwner -> VideoManager -> PlayerEngineManager
-> Media3PlayerEngine`. `ReportingHelper` builds Jellyfin DTOs and sends them through
the ordered reporter. The experimental core chain uses `PlaySessionService` with
the same owner/reporter implementation. These are separate player contexts, not two
owners for the same playback.

## Capability policy

`AndroidCapabilityProbe` maps Jellyfin source metadata and Android codec/display
declarations into plain data. Policy considers container, video codec, DV profile
and level, compatibility ID, HDR, bit depth, resolution/rate, audio decoder and
channel metadata, Android API, display HDR and registered engine availability.
The result separates SUPPORTED, UNSUPPORTED, UNKNOWN and NOT_VERIFIED evidence from
ALLOWED, UNSUPPORTED and REQUIRES_VERIFICATION playback permission. Unknown metadata
allows a diagnostic playback attempt; explicit missing known video decoder or
unsupported geometry blocks it. Policy never requests transcoding. Transcoded
source metadata is not used as evidence about delivered tracks.

DV decoder availability, HEVC decoder availability, HDR10 compatibility and actual
HDMI output are separate facts. P5 never permits generic HEVC fallback. P7/P8 require
explicit compatibility ID 1 plus an HEVC decoder before permitting HDR10 base-layer
fallback; this does not claim successful FEL reconstruction or DV output. Display
HDR declarations never prove active TV output. Channel layout presence is metadata
availability, not verification of speaker mapping. Missing audio decoding support
stays UNKNOWN because passthrough/FFmpeg may still be possible.

`CapabilityCodecSelector` preserves Media3 decoder ordering but excludes HEVC queries
for source-declared DV without confirmed compatible base-layer fallback. It resets
for SDR and transcoded delivery. This is a narrow safety restriction, not a color
conversion fix. Evidence: Media3 1.8.0
[`MediaCodecUtil.getAlternativeCodecMimeType`](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/MediaCodecUtil.java)
permits a P8 HEVC alternate while excluding P5/P7. A selector unit test and a pinned
Media3 Android regression test protect these decisions. No Kodi code was copied.

## Ownership, controller and reporting

Each context has a UUID, monotonically increasing generation, item identity, engine,
resume/current position, state, reporting state and terminal reason. Preparation,
logical start and terminal cleanup are claimed once. Cleanup failure prevents a new
session. Late segment/server callbacks check generation; player action callbacks
check Media3 timeline media ID as well. Stop/completion/error finish the owner before
reporting. Decoder errors terminate without automatic server/engine retry.

Pause/seek preserve paused intent. Server-relative nonseekable delivery has a separate
timeline offset; initial resume is not added to ordinary absolute Media3 positions.
The fragment saves item/resume/paused state, terminates its old controller on
recreation, and pauses in the background. Preparation finishing in the background
cannot start audio until foreground. Existing TV controls remain in place.

Only the initially registered Media3 engine is available in production. Manager
fallback is bounded and synchronous, before successful preparation, to a distinct
registered engine. Failed candidates are released before another is constructed;
cleanup failure prohibits fallback. There is no active-session engine switch.
Asynchronous decoder initialization failure is terminal because no alternative
production engine exists. Future engines must implement the core contract, register
explicitly and supply UI/surface integration and capability evidence before selection.

Start/progress/stop DTOs capture their item/stream/position. Ordered accepted start
and stop intents survive view destruction, while obsolete progress is dropped.
Late callbacks cannot enqueue a new report for a terminal generation. HTTP failure
is recorded without creating playback or retrying a start. This provides one logical
send attempt, not a guarantee that a remote server receives every request. Core
reporting retains the old stream stop DTO even after its queue changes.

## Diagnostics

Existing TV-readable diagnostic dialog now includes session/generation, policy
facts/reasons, engine selection, decoder selection restriction, fallback, reporting,
resume/current position and terminal reason. SOURCE FORMAT, DEVICE CAPABILITY,
SELECTED DECODER and ACTUAL PLAYER STATE remain separate. TV/HDMI DV, Atmos and DTS:X
remain NOT VERIFIED. Initialization failure has a diagnostic path even when the
player builder fails. No diagnostic polling loop was introduced.

## Validation and limits

See [acceptance evidence](../evidence/player-engine-phase-2-completion/ACCEPTANCE.md)
for exact commands, results, APK and hashes. Synthetic controller fixtures execute
production controller, owner, manager, Jellyfin metadata mapper and reporting DTO
construction; UI/codec/HTTP boundaries are mocked. Coverage includes Direct Play,
Direct Stream, transcoding, DV5/7/8, HDR10/unknown and unsupported formats, initial
failure/fallback, reporting failure, pause/seek/resume, cancellation, late segment
callbacks, completion, background preparation and audio/subtitle selection.
Core tests cover declaration policy, backend detachment and cleanup failure.

Remaining acceptance gaps: controller fixtures enter after PlaybackInfo negotiation;
server negotiation, nonseekable transcode restart and actual Activity recreation do
not yet have complete automated end-to-end fixtures. Recreation is tested as two
controller contexts with checkpoint transfer, not Android ActivityScenario. The
experimental core PlaySessionService is compile-checked and shares tested primitives,
but does not yet have equivalent full service/queue regression coverage. It starts
its owner at PLAYING, so pre-start error ownership there remains incomplete. Late
server-created live streams may still need explicit remote close on cancellation.
These gaps prevent claiming full completion PASS.

No physical Shield/TV/AVR verification was performed. Required manual checks include
4K Remux sustained bitrate, DV5/7/8 color/output, HDR10/HLG/HDR10+, refresh switching,
TrueHD/DTS-HD/Atmos/DTS:X passthrough, D-pad diagnostic readability, lifecycle
recreation during buffering and live/transcoded seek. ARM64 packaging alone does not
verify ARM64 execution. Source DV metadata missing/mislabeled by the server remains
an input limitation; actual Media3 diagnostics must be inspected.

## Changed files

Production changes are limited to the new core engine package; core
`PlaybackManager`, `BackendService`, `PlayerBackend`, `PlayerService`;
`playback/jellyfin/.../PlaySessionService`; `playback/media3/exoplayer/.../ExoPlayerBackend`;
and the app's `PlaybackController`, `CustomPlaybackOverlayFragment`, `VideoManager`,
`ReportingHelper` and engine package. Tests are in core `engine`, app
`ui/playback/completion`, updated existing engine suites, and
`PlayerEngineInstrumentedTest`. The two completion documents, focused Gradle test
filter and JVM result summary are new. No unrelated dirty files were staged.
