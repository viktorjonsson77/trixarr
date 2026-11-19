# Trixarr Beta 1 — 0.1.0-beta.1

Candidate: signed **debug Beta**, versionCode **10001**, package
`se.viktorjonsson.jellyfintv.debug`. This is not a production release.
`RELEASE_SIGNING_BLOCKED`: no release signing configuration is available. The release
applicationId also differs from the installed debug identity. No keys were created,
replaced or exposed; the existing debug certificate safely updated the Shield app.

## Integration

Branch `release/trixarr-beta-1`, isolated worktree `/opt/jellyfintv-beta-1`.
Starting HEAD `1d7407710293a829c482b56baf5d1f5099eaba3f` on
`feat/trixarr-branding-v2`; original worktree had **117** dirty entries.
The original branch and all dirty work remain in `/opt/jellyfintv`.

Accepted ancestry, rather than indiscriminate experimental branch merges:

| Dependency | Included commit |
| --- | --- |
| PlayerEngine completion / session ownership | 69e5a6f38 |
| Playback stability / refresh rate baseline | 73fcd6052 |
| DV-safe negotiation | 70929c016 |
| Local E-AC3 decoding | d6a411e74 / dc6319664 |
| Trixarr product naming | bb87e8d6d |
| Approved triangle branding | fa456080c |
| Branding validation / evidence | 40ebb861c / 1d7407710 |
| Verified dirty catalog, startup and package overlay | bc9d6533b |
| Beta stability fixes | 45db472d6 |
| Beta version metadata | 163580d4b |

The verified branding APK had been built with local app/catalog/startup work not
committed in its branch. That dependency is now committed explicitly (60 files).
The overlay inventory records original input hashes. Two unfinished alternative
refresh-rate classes were excluded; the existing controller/window implementation
remains the single production refresh path. The unfinished startup owner model was
completed and connected to that existing session owner. No playback engine, codec
selection, DV fallback policy, audio sink, FFmpeg binary or logo was replaced.

## Fixed defects

* **Unbounded startup:** a deterministic controller test reproduced buffering forever
  before decoder readiness. A generation-bound Handler watchdog now observes the
  existing owner's monotonic phase timers. Negotiation is bounded to 45 seconds;
  source/stream preparation to 90 seconds per phase; decoder/first frame to 30 seconds
  per phase. Bytes do not reset deadlines. Background time is excluded. Timeout
  terminates through normal cleanup/reporting and closes playback, without retries.
  Track, load, decoder and first-frame events provide bounded diagnostic state.
* **Capability probe callback lifecycle:** a disposable context-bound Media3 sink
  could register callbacks on main while recording a null playback Looper on a worker.
  Capability probes now use a synchronous route snapshot and a context-free probe
  sink. The real playback renderer sink remains context-aware. A worker-thread Android
  regression drains callbacks and verifies real E-AC3 / multichannel capability.
* **Stale AudioTrack release diagnostics:** Media3 releases tracks asynchronously.
  A late old-track release erased replacement output details. A failing Android
  regression demonstrated this, including identical configs. Diagnostics now clear
  output only when the final initialized track has released. Runtime verification
  shows the replacement E-AC3 output correctly after recovery.

The recurring `AudioSink.WriteException` was captured as `-6` / DEAD_OBJECT,
`isRecoverable=true`, position 0, player BUFFERING during HDMI refresh switching.
Media3 creates a replacement track and playback progresses. This observed instance
is a recoverable output interruption, not a terminal playback failure or evidence
of a crash. No exceptions are suppressed and no new audio recovery loop was added.
The cause of an earlier user video restart remains unknown.

## Validation and delivery

See [acceptance and artifact identity](../evidence/trixarr-beta-1/ACCEPTANCE.md).
Status labels distinguish IMPLEMENTED, TESTED, RUNTIME VERIFIED,
USER PHYSICALLY VERIFIED and NOT VERIFIED. Physical TV/AVR output cannot be inferred
from Android decoder or AudioTrack metadata.

No push, tag, GitHub release or production deployment was performed. Earlier APKs
remain intact. Only a compatible in-place debug update was installed; no uninstall,
`pm clear`, data deletion, server setting change or global audio/display change occurred.

## Candidate result and known limits

**TRIXARR_BETA_1_PARTIAL** — BETA_BUILD_PASS for this debug candidate;
PHYSICAL_BETA_PASS is not achieved. 137 JVM and 12 Shield Android tests pass.
Resident Evil DV5 Direct Play works with both E-AC3 DIRECT and local FFmpeg PCM16
5.1/48 kHz. DV8 L6 Direct Play also starts after rejected DV7 sessions; pause,
resume, seek, stop/replay and 23.976/24 Hz matching/restoration are runtime verified.
Original app bitstream preference was restored to absent/default ON and verified.
Fresh TCL/Yamaha output, audible sync/channel mapping and actual Activity recreation
remain unverified. Launcher re-entry returns Home; automatic in-place playback
continuation is not accepted. One Battle After Another still proposes unsafe DV7
transcoding and is rejected early; its exact server reason and original 124-second
startup cause remain unresolved. Full DV7 FEL/MEL support is not claimed. Short
supported playback runs observed no terminal playback failure; route-recovery
WriteExceptions remain visible and a long soak has not been performed.

Final artifact: `/opt/jellyfintv-artifacts/trixarr-beta-1/trixarr-0.1.0-beta.1-candidate2-debug.apk`.

APK SHA-256: `e4a0bf09eb64823c8f90a73268e7914a074200768dd9d63aecacee8f9640e434`.

Signer SHA-256: `f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`.

The installed Shield base.apk was pulled and matches this exact hash. Existing login,
package data inode, UID and first-install timestamp are preserved. Candidate 1 and
previous artifacts have not been overwritten. No push, tag or publication occurred.
