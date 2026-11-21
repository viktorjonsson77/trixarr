# Beta 1.1 acceptance — 2026-11-21

**TRIXARR_BETA_1_1_PARTIAL** — **BETA_1_1_BUILD_PASS**.
**BETA_1_1_PHYSICAL_PASS: NOT_VERIFIED**. Suitable for continued Beta acceptance,
not an assertion of full physical playback acceptance or long-term stability.

## BUILD_PASS

Integration baseline and complete branch ancestry: [release record](../../releases/TRIXARR_BETA_1_1.md).
All branches are already ancestors of `0b7e5016e`; no merges required. Existing
PlayerEngine/Media3, DV5/8 negotiation/runtime paths, passthrough/local decoding,
refresh matching, cleanup, Branding V3, image policy/cache and AudioSink tests included.
Only version metadata changed outside delivery documentation/evidence.

- JDK21, SDK36. `./gradlew -I docs/evidence/trixarr-image-quality-v1/focused-tests.gradle :app:testDebugUnitTest :playback:core:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` PASS.
- App: **122 tests**, core: **29 tests**, zero failures/errors/skips. Covers artwork
  selection, cache identities, sizing, catalog/navigation, startup, player lifecycle,
  seek/stop/replay, session ownership/cleanup, DV safe policy, local audio, refresh
  and AudioSink diagnostics/recovery regressions. Synthetic coverage is not physical output.
- Shield Android instrumentation: **16 PASS**, artwork rendering/loading/fallback,
  branding resource hashes/labels/package/signer, real Media3 lifecycle and local
  audio capability/packaged FFmpeg checks. [results](android-tests.txt).
- Approved master SHA256 `22421f41e2c3f8a00d8a6b135a6aca8c4c04fc567e5ae94f3afc559bb7967e83`;
  all 30 derivatives verified. Existing triangle/wordmark/icon/banner/splash retained.
- APK: `/opt/jellyfintv-artifacts/trixarr-beta-1.1/trixarr-0.1.1-beta.1-debug.apk`
- APK SHA256 `982f5a81c93f99cfd5437d533bf1311d1ab4791f3fe62a2fb91797aad368a927`
- Signer SHA256 `f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`
- Package `se.viktorjonsson.jellyfintv.debug`, version `0.1.1-beta.1`, code `10101`.
- ARM64 ELF64/AArch64 verified for FFmpeg JNI and AndroidX path libraries.
- Installed candidate pulled/read back: exact delivered APK SHA256 match.

### Build/test investigation

I inadvertently started two Gradle builds against the new worktree. The duplicate
failed creating a resource intermediate directory while the original was building.
No source workaround was applied. After the original finished, one sequential run
passed all requested tasks. The original build also ran aggregate core variants;
counts above report debug only, avoiding duplicate debug/release test counting.
The retained build log is the unambiguous sequential verification. No failing test
was suppressed or skipped. Existing focused app filter is explicit; unrelated app
unit tests and all UI instrumentation classes were not claimed as run.

The ad-hoc live-playback harness had an ADB date quoting bug, causing its logcat
capture to fail *after* launching playback (also on replay and PCM runs). Its exit
status is not a playback verdict. Fresh sanitized PID logs and UID media metrics
were captured separately; harness failures retained for transparency.

## RUNTIME_PASS — bounded checks only

Target verified: NVIDIA SHIELD Android TV/mdarcy Pro, Android11/API30, ARM64,
ADB `100.91.172.18:5555`. Signature equality and increasing version checked before
`adb install -r`; app and instrumentation update succeeded. No uninstall, pm clear,
cache/data deletion, or server configuration change.

UID10048, data inode652903 and first-install time2026-09-02 23:52:47 unchanged.
Authentication file hash changed during use (not claimed byte-identical); authenticated
movie details and final catalog home load without login. Default preferences hash
is identical before/after, including after temporary local PCM test. Bitstream key
restored to its original ABSENT/default ON state. No permanent preference change.

Bounded Resident Evil direct playback: NVIDIA DOVI decoder selected; E-AC3 DIRECT,
6 channels / 48kHz in UID10048 metrics. Transient recoverable AudioSink -6 occurred,
followed by recovery and advancing playback without app restart. Pause at16.2s,
resume, stop at47.2s and ExoPlayer release observed. Explicit replay starts again
and subsequently releases. Temporary bitstream OFF test produces PCM16, channel
mask63 (5.1), 48kHz, active AudioTrack; playback advances to4.8s and releases.
Default display mode31 restored after playback; playback selected mode22 (23.976Hz).
See [runtime](runtime-log.txt), [later sessions](runtime-final.txt),
[metrics](media-metrics.txt), [restoration](refresh-final.txt).

Seek command logged both an unsupported-seek warning and a subsequent position
advance from16s to47s. **Runtime seek acceptance remains NOT_VERIFIED** rather than
claiming an unequivocal pass. No speculative code change made.
DV profile5/8 policy tests pass; current physical format/profile matrix is not rerun.

## USER_PHYSICAL_PASS — supplied acceptance only

User: “Images look much better. Scrolling also feels good.”
**IMAGE_QUALITY_USER_PASS** and **NAVIGATION_USER_PASS** apply to the included
unchanged Image Quality V1 implementation. Every artwork type has not been
individually verified. No additional image changes introduced.

## NOT_VERIFIED / remaining limitations

Fresh user confirmation of visible DV/HDR, audible E-AC3/PCM output, launcher/splash
appearance, and full playback regression on the TV/AVR is outstanding. Branding
resource tests pass but cannot establish physical appearance. No user observation
is invented. Existing prior Beta physical acceptance remains historical evidence.
Runtime seek needs focused follow-up. HDMI interruptions can still occur; long-term
stability is not yet verified. DV7/FEL/MEL, Activity recreation and broad artwork
visual matrices retain prior limits. Release signing remains a separate task.
No main merge, tag or public release performed. Existing worktrees preserved;
original worktree still has117 unrelated dirty entries (preservation record linked).
