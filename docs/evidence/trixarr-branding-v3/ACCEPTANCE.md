# Trixarr Branding V3 acceptance — 2026-11-19

**TRIXARR_BRANDING_V3_PARTIAL**

Branding assets, Android resource integration, build, safe update, launcher/banner,
splash, preserved login/library and GitHub publication are verified. The requested
playback-startup gate failed with a handled AudioSink DEAD_OBJECT error; no full
physical acceptance is claimed. No playback/audio policy or code was changed.

## Baseline and preserved work

* Beta baseline: `553465c3a20352bba27a44cf8bbfcfe7acd36c82`,
  `release/trixarr-beta-1`, clean `/opt/jellyfintv-beta-1`.
* Dedicated branch/worktree: `feat/trixarr-branding-v3`, `/opt/jellyfintv-branding-v3`.
* Applicable parent/repository/docs/app AGENTS.md checks found no instruction file.
* Original `/opt/jellyfintv` remains on `feat/trixarr-branding-v2` with117 dirty entries.
  Beta remains clean on its original branch; status hashes match before/after.
  See [before](preserved-worktrees-before.json) and [after](preserved-worktrees-after.json).
* No reset, clean, blanket stash, existing branch overwrite, main merge or force push.
  Previous approved design directories and all earlier APKs remain unchanged.

## Approved original — VERIFIED

The user supplied `/tmp/trixarr-approved-logo.png`. It is readable, **2172×724 RGB PNG**,
1,349,699 bytes, opaque (no alpha channel).
SHA-256 exactly matches the required value:
`22421f41e2c3f8a00d8a6b135a6aca8c4c04fc567e5ae94f3afc559bb7967e83`.

The [full-resolution master](../../design/trixarr/branding/v3/trixarr-approved-logo.png)
is copied byte-for-byte. Blue/cyan folded triangle, white metallic wordmark, tight
letter spacing, original proportions and original navy master background remain.
No logo generation, redrawing, font substitution or master recoloring was performed.

## Derived resources — IMPLEMENTED AND VERIFIED

[Asset manifest](../../design/trixarr/branding/v3/manifest.json) lists30 derivatives,
including filenames, dimensions, PNG format, SHA-256, alpha range and Android usage.
[Method/provenance](../../design/trixarr/branding/v3/README.md),
[deterministic generator](../../design/trixarr/branding/v3/derive_assets.py),
[resource verifier](../../design/trixarr/branding/v3/verify_assets.py).

* Full-canvas transparent logo and splash:2172×724, original source RGB retained.
* Triangle-only crop:385×430, with padding around the full symbol; no clipped symbol
  or wordmark. Wordmark is excluded only from triangle-only icons.
* Adaptive foreground:432×432; symbol fits the66dp safe circle. The themed monochrome
  resource derives its silhouette from the same triangle alpha, replacing the old vector.
* TV banner:320×180 plus all existing density variants. Debug/main use the approved
  design with no legacy purple/debug-badge artwork. Foreground resource:1280×720.
* Legacy launcher icons:48/72/96/144/192 pixels in both main/debug source sets.
* Icon/banner background resources use navy #010211. Transparent foregrounds have no
  opaque rectangular background; aspect-fit scaling preserves all content/proportions.
* Existing splash FIT_CENTER view, timing/animation and startup functionality are unchanged.

Transparency is an approximation from a flattened raster: source background samples
have max channel<=28; alpha is feathered between32 and64. This cannot recover the
original designer alpha or perfectly unflatten antialiased edge colors. The original
opaque master is authoritative and remains unchanged. This limitation is explicit,
not a claim that the source was transparent.

## Android integration and tests — PASS

Only Android drawable/mipmap resources and the branding instrumentation expectations
changed outside documentation/assets. Application label remains **Trixarr**. Manifest,
applicationId, namespace, signing configuration, playback/audio code, Media3, FFmpeg,
DV handling and refresh policies are unchanged from the accepted Beta baseline.

* Asset verifier:PASS — original hash/RGB, all30 hashes/dimensions, alpha corners,
  320×180 banner, unclipped triangle and adaptive safe area.
* Focused app playback JVM suite:**62 PASS**, zero failures/errors/skips. This checks
  unchanged player behavior synthetically; it does not override the runtime failure below.
* Shield branding instrumentation:**4 PASS** — labels, leanback metadata, icon/banner
  resource resolution, source resource hashes, installed package and signing certificate.
* `:app:assembleDebug` and `:app:assembleDebugAndroidTest`:PASS, JDK21 / Android SDK36.
* ARM64 ELF headers verified. `libffmpegJNI.so` and `libandroidx.graphics.path.so`
  match the previously installed Beta candidate byte-for-byte.

[Resource checks](resource-tests.txt), [JVM counts](jvm-tests.json),
[Android results](android-results.txt), [build results](build-results.txt).
No unrelated full test suite or new playback/audio implementation was introduced.

## Delivered debug APK — VERIFIED AND INSTALLED

* Path: `/opt/jellyfintv-artifacts/trixarr-branding-v3/trixarr-0.1.0-beta.1-branding-v3-debug.apk`
* SHA-256: `06283683675b1cd1028ad72f51cb306d60830a9cd8a886c07a5d03ac887c317b`
* Signer SHA-256: `f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`
* Package: `se.viktorjonsson.jellyfintv.debug`; namespace:`org.jellyfin.androidtv`.
* VersionName:`0.1.0-beta.1`; versionCode:10001, unchanged for this branding candidate.
* Installed predecessor SHA-256:
  `e4a0bf09eb64823c8f90a73268e7914a074200768dd9d63aecacee8f9640e434`.
* Installed base.apk was pulled after testing: exact match to the V3 candidate hash.

[APK verification](apk-verification.json), [metadata](apk-badging.txt).
This is a debug Beta build, not a production-signed release. The prior Beta1 APK remains.

## Shield — branding PASS; playback startup FAILED

Device verified before installation: NVIDIA SHIELD Android TV, mdarcy / Shield Pro,
Android11 / API30, ARM64, ADB `100.91.172.18:5555`. Candidate signature matched the
installed package and version was update-compatible before `adb install -r`.
No uninstall, pm clear, data deletion, cache deletion or audio/display preference
change occurred. App data inode652903, UID10048 and first-install time
2026-09-02 23:52:47 remain unchanged ([before](device-before.json), [after](device-after.json)).

| Check | Result / evidence |
| --- | --- |
| Launcher icon resources | PASS; new triangle foreground/legacy assets and adaptive references resolve in Android instrumentation |
| TV launcher banner | PASS after one launcher-process restart; [refreshed launcher](launcher-refreshed.png) shows the approved V3 design |
| Splash artwork | PASS; [frame at4.4s](splash-visible.png) shows complete V3 triangle/wordmark without rectangular matte or cropping |
| Existing login | PASS; no authentication flow required after update/cold start |
| Library | PASS; [home UI](HOME-ui.txt), [library screenshot](library.png) |
| Package/name | PASS; [App Info](app-info.png) retains Trixarr/debug identity |
| Playback startup | FAILED; Resident Evil session terminated at position0 with AudioSink WriteException -6 |
| Failure cleanup | ExoPlayer Release observed; player returned to details; no automatic retry |
| Preferences | App default preferences SHA-256 identical before/after; original absent bitstream key remains default ON |

The launcher initially retained the V2 banner ([before refresh](launcher-before-refresh.png)).
A single `am force-stop com.google.android.tvlauncher` followed by HOME refreshed it.
Only that process was restarted: launcher cache/data and preferences were not deleted
or edited. The [early splash frame](splash-fade.png) is during the existing fade-in;
the fully visible frame is the acceptance image.

### Playback failure — observed, not fixed in this branding task

One Resident Evil startup attempt was performed. Nvidia DOVI decoder initialization
was observed, then AudioTrack E-AC3 DIRECT creation (6ch/48k). At21:39:12.832 and
21:39:13.262, diagnostics reported `code=-6 recoverable=false state=2 positionMs=0`.
At21:39:13.299 the player reported an error, normal controller cleanup stopped at0,
and ExoPlayer released at21:39:13.351. This is a handled playback-start failure;
it is not reported as an application process crash or a successful playback session.
[Filtered log](PLAYBACK-logcat.txt), [Android metrics](PLAYBACK-media-metrics.txt),
[returned details](PLAYBACK-FAILURE-DETAIL-ui.txt).

The earlier Beta acceptance recorded recoverable DEAD_OBJECT route transitions;
this attempt is specifically **nonrecoverable** and must not be conflated with them.
No causal link to branding is established. Playback code, native libraries and audio
preferences are unchanged. The affected test was stopped; no automatic retry, unsafe
fallback or audio/display preference change was attempted. The user was asked whether
TCL/Yamaha were on and the Shield input selected; that physical state is unconfirmed
at report time. A successful playback start under verified output conditions remains
required for full V3 physical acceptance. Existing Beta physical confirmations do not
replace this failed V3 startup result. No fresh TV/AVR picture/audio assertion is made.

[Preference file before](preferences-file-before.json), [after](preferences-file-after.json),
[bitstream before](preferences-before.json), [after](preferences-after.json).

## GitHub publication — VERIFIED

Repository: **viktorjonsson77/trixarr**.
Dedicated branch: **feat/trixarr-branding-v3**.
Published implementation commit: **5d7cc3747fa12272f76905018a72f7f155e36096**.

[Published assets](https://github.com/viktorjonsson77/trixarr/tree/feat/trixarr-branding-v3/docs/design/trixarr/branding/v3)
and [implementation commit](https://github.com/viktorjonsson77/trixarr/commit/5d7cc3747fa12272f76905018a72f7f155e36096).
`git ls-remote` verified the published implementation SHA; the evidence documentation
is published as a subsequent focused commit on the same branch. Its final SHA is
reported in the delivery response. [Publication record](github-implementation.json).

Ordinary Git push was used to publish the existing local Beta ancestry and focused
branding commit; the connector does not push a local Git object graph. Existing
origin/upstream remote URLs remain unchanged. No force push, main merge, tag, GitHub
release publication or overwrite of previous approved design directories occurred.

## Remaining limitations

1. **Playback startup failure blocks full V3 physical PASS.** Output-device state and
   the DEAD_OBJECT startup failure need separate verification/investigation; no playback
   change is included in this branding-only task.
2. Transparent derivatives approximate the opaque raster edges; original master is intact.
3. Launcher may retain the previous banner until its process refreshes; verified refresh
   requires no clearing of data or settings.
4. Existing Beta limits remain: release signing unavailable, incomplete DV7/FEL/MEL and
   actual Activity recreation verification, long-term test pending, historical buffering
   cause unknown. No additional format or stability acceptance is implied by branding tests.
