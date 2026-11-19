# Trixarr image quality V1

Status: **TRIXARR_IMAGE_QUALITY_PARTIAL**. Client corrections, focused tests and the
signed ARM64 debug APK are delivered. Shield screenshots verify correct artwork
selection; overall TV sharpness and long-term scrolling/memory acceptance remain
limited as described below. No claim of native 4K UI rendering is made.

## Baseline and preservation

Dedicated worktree `/opt/jellyfintv-image-quality-v1`, branch
`feat/trixarr-image-quality-v1`, based on `c443283f57afbc2db85179675c96c43d944bb63f`:
integrated Beta 1, Branding V3 and the subsequent DEAD_OBJECT investigation/tests.
Parent/repository instructions were checked; no applicable AGENTS.md was found.
The original `/opt/jellyfintv` remains on its branch with **117 dirty entries**;
Beta 1, Branding V3 and audio investigation worktrees remain untouched.
See [preservation record](preserved-worktrees.json).

No playback, audio, Dolby Vision, refresh-rate, package, namespace, signing or
branding resource code was changed. No uninstall, pm clear, user-data deletion,
cache clearing, audio/display preference change or server modification occurred.
Previously built APKs remain available under `/opt/jellyfintv-artifacts/`.

## Root causes and pipeline

1. Catalog background selection looked for `Backdrop` in `ImageTags`. Jellyfin
   supplies it in `BackdropImageTags` / `ParentBackdropImageTags`. For the measured
   movies this selected `Thumb` instead: a different image with a baked wordmark,
   then stretched behind the separately rendered title. Backdrop lists now take
   priority, retaining the existing Thumb fallback when no backdrop exists.
2. Manual catalog fanart loaded without a measured target size, retaining a
   3840×2160 bitmap for a 1920×1080 interface. Its intentionally soft detail variant
   additionally reduced every source to only **256 px** before fullscreen display.
   Fanart now waits for its actual viewport; the soft variant preserves up to
   **960 px**, with a running-sum blur retaining similar apparent softness.
3. Compose and legacy views used different Coil loaders/caches. The application
   now supplies its existing Koin loader to Coil's Singleton factory as well.
4. Requests lacked a shared size/quality policy and focus overscan. A bounded
   interceptor now runs before Coil's engine/cache lookup. It handles supported
   Jellyfin image URLs, direct TMDB and Seerr TMDB proxy URLs. Other providers,
   file fixtures, placeholders and existing transformations retain their paths.

The pipeline is Jellyfin DTO image metadata → SDK image URL/tag → Coil 3.3.0
(requested target pixels) → ArtworkRequestPolicy/ArtworkInterceptor → existing
OkHttp fetcher → existing Android bitmap/SVG/animated decoders → ImageView or
Compose Crop. Android resources continue to provide placeholders. Medium bitmap
filtering is explicit in the changed Compose artwork paths; aspect ratios are
preserved and image failures still show the existing fallback.

## Measured display and request policy

Shield Pro/mdarcy, Android 11/API30, ARM64, density **320 dpi / 2.0**. HDMI physical
output is 3840×2160, but `wm size` has an existing 1920×1080 override. App metrics
and UI bounds confirm **1920×1080** rendering. No display settings were changed.
The system upscales the entire interface, including text, for the 4K output.

Sizing uses rendered pixels, source aspect metadata when available, Crop cover,
5% card focus allowance and 32 px network size buckets. Limits are per artwork
kind, not a single global size: posters 1024×1536, thumbnails 1920×1080,
backgrounds 2560×1440, logos 1600×600, avatars 512×512. Known source dimensions
cap both requests and decoding; otherwise supported Jellyfin maxWidth/maxHeight
and INEXACT decoding avoid creating enlarged source bitmaps. Low-resolution art
must still be scaled by the UI to fill a larger view; missing detail cannot be
recovered. No per-card ImageInfo request was added.

| UI / artwork | Actual sizing path and coverage |
| --- | --- |
| Movie/series posters, library, collections, seasons | Measured Compose card or legacy ImageView; Primary source aspect where supplied; poster cap; synthetic policy coverage |
| Continue Watching / Recently Added / recommendations | Home cards 168×98 dp = 336×196 px (some rows 96 dp high); landscape purpose plus focus allowance; runtime captures |
| Episode thumbnails | Own episode Primary retained, rather than parent show backdrop; home episode captured; detail rows use measured width |
| Search / library grids | Dynamic column widths, existing .90 / 1.6 aspect layouts; measured Compose constraint size |
| Related titles | 200×103 dp = 400×206 px; measured target policy |
| Person artwork | Legacy 150×225 dp = 300×450 px; explicit view size resolver and resize reload |
| Detail background / hero | Actual viewport 1920×1080; soft detail variant max960 wide; hero captured |
| External discovery / recommendations | Existing w500/w1280 kept when sufficient; documented TMDB `original` only if required size exceeds preset; decoded bitmap remains bounded |

## Actual server requests and responses

Supported Jellyfin GET `/Items/{id}/Images/{type}/0` and read-only ImageInfo were
probed through the Shield's existing server connection. Authentication was held
in memory and excluded from saved evidence. Source format is JPEG in these samples.

| Same item / image | Before | After request | Returned after |
| --- | --- | --- | --- |
| Backrooms hero | Thumb original, decoded3840×2160 | Backdrop max1920×1088, quality90 |1920×1080 decode; server1920×1080 at max1920×1080:530,753 B |
| Parasit home card | Thumb, decoded348×196, no explicit HTTP size | Backdrop max384×224, quality90 |384×216 JPEG,29,821 B; decoded366×206 |
| Du är varnad episode | Primary, decoded348×196, no explicit HTTP size | Primary max384×224, quality90 |384×216 JPEG,16,219 B; decoded366×206 |
| Backrooms poster probe | Primary2000×3000,2,079,714 B | max336×504, quality90 |336×504 JPEG,42,782 B |
| Resident Evil poster probe | Primary1371×2057,643,417 B | max336×504, quality90 |336×504 JPEG,38,633 B |

The HTTP dimensions for the final hero are recorded from the actual Coil key;
its returned 1920×1080 bitmap is runtime measured. The separate server response
probe used max1920×1080. Final card requests were probed at their exact parameters.
See [runtime before](before-runtime.json), [runtime after](after-runtime.json),
[server probes](server-image-probe.json), [final card responses](final-card-responses.json),
[source metadata](visible-source-images.json), [no-upscale response](no-upscale-response.json).
Jellyfin delivered higher resolution correctly: this is not a verified server
thumbnail-generation defect. Sample original Thumbs were already 1920/3840 wide;
we do not describe all original artwork as low-resolution.

Responses preserve normal caching (`Cache-Control: public`, `Vary: Accept`, Age,
Last-Modified where returned). Existing format parameters/tags remain in URLs;
quality90 applies to bounded Jellyfin requests. Relay elapsed times in the probe
include a fixed two-second stdin hold and are **not network latency benchmarks**.

## Cache and rendering

URL query dimensions and quality identify disk/network entries; explicit kind,
request and decode sizes additionally identify memory entries. Existing source
tags and transformation keys remain significant. The new soft transform has a
versioned key. Low-resolution responses therefore do not alias larger requests.
No cache was disabled or cleared. Existing cache bounds/eviction remain enabled.

The same captured home screen retained **33,779,712 bytes before** versus
**9,243,648 bytes after** (72.6% less decoded image cache). Afterward Koin and
Singleton are the same loader; their reported sizes must not be added twice.
The foreground, background and legacy surfaces share the same policy and cache.

## Tests and build

* Regression feedback: backdrop-selection fixtures were run against the old
  selection and produced two expected failures; they pass after correction.
* `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk
  ./gradlew -I docs/evidence/trixarr-image-quality-v1/focused-tests.gradle
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` — PASS.
* **122 JVM tests PASS**, zero skipped/failures/errors: 16 new artwork tests plus
  106 relevant catalog/startup/player regressions. Covers densities, source aspect,
  low/high resolution, poster/landscape/background limits, crop cover, focus,
  URL/memory identities, revision/format preservation and external URL handling.
* **16 Android tests PASS**: four artwork rendering/fallback/resize/blur cases,
  four existing branding/resource cases and eight existing real Media3/local-audio
  capability/lifecycle regressions. Capture harness adds one passing probe.
* The resize fixture retains an already sufficient 800×1200 local bitmap; it
  verifies a new completed load on resize and adequate resolution, not mandatory
  bitmap growth. An earlier assertion incorrectly required growth and was corrected.
* Soft transform1920×1080→960×540 measured **147 ms**, output allocation2,073,600 B
  on Shield. Work runs in Coil's loading path; this is not a main-thread timing claim.
* Branding V3 verifier PASS: approved master/hash, 30 asset hashes/dimensions,
  transparency, banner and adaptive-icon bounds. APK compilation PASS.

The capture harness reports JUnit OK, followed by shell exit139 on this Nvidia
stack. Earlier investigation localized this to UID2000 app_process vendor EGL
shutdown (`NvEglDeinit/NvMutex`), separately from Trixarr UID10048. Captures are
validated by loaded artwork/cache entries; no application playback failure is
inferred from this shell teardown. A sleeping-device empty capture was excluded.

Final artifact: `/opt/jellyfintv-artifacts/trixarr-image-quality-v1/trixarr-0.1.0-beta.1-image-quality-v1-candidate3-debug.apk`

* SHA-256: `b8b6fff69f9f4832327dc5f6449f294f1ed04a713d4e4777c4daabf2bc19c03f`
* Signer SHA-256: `f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`
* Package `se.viktorjonsson.jellyfintv.debug`, namespace `org.jellyfin.androidtv`,
  version `0.1.0-beta.1`, versionCode10001; existing debug signer unchanged.
* ARM64 libffmpegJNI.so and libandroidx.graphics.path.so verified ELF64/AArch64,
  byte-identical to installed baseline. [APK verification](apk-verification.json).

## Shield / physical assessment

Safe `adb install -r` succeeded after matching package/certificate/version/native
checks. Data inode652903, UID10048 and firstInstall2026-09-02 23:52:47 were preserved.
Existing Jellyfin login and library loaded without authentication prompts.
Bitstream remains absent/defaultON; no audio/display preferences were changed.

[Before screenshot](before.png) and [after screenshot](after.png) show the same
Backrooms item/home layout. Agent visual inspection verifies correct photographic
Backdrop selection, removal of the duplicate baked title and normal text/art
composition. Cards can use a different image of the same item after the source
fix, so this is **not a pixel-for-pixel sharpness or compression-score comparison**.
Both screenshots were uniformly downsampled from the 4K compositor capture to the
actual1920×1080 UI size. Original 4K captures remain beside the APK artifacts.

User physical sharpness/scrolling confirmation: **not yet received**. Larger HTTP
requests alone are not accepted as proof of perceived improvement. Native4K UI,
all-library source quality, every season/collection/search presentation and a
long-duration memory/scroll soak are not physically certified by this bounded run.

## Performance measurements and remaining acceptance gates

The first immediate batched-key smoke (26 commands without pacing) produced
43/58 janky frames. It is retained as a diagnostic observation, not a realistic
scroll-rate benchmark. A controlled before/after run installed the verified V3
baseline then this candidate, keeping application data, and used the same home
navigation (two Down, twelve Right, twelve Left, 350 ms pacing). Both variants
started with an eight-second loading period. The final candidate was restored.

[First comparison](scroll-benchmark.json): V3 95/1548 janky frames (6.14%), candidate
306/1495 (20.47%). Median7→8 ms; p90 13→19 ms; p95 23→24 ms; p99 81→69 ms.
Post-sequence PSS349,579→373,126 KiB, RSS470,208→489,764 KiB. This single short
sample shows a pacing concern despite lower retained image-cache bytes. Process
memory includes UI, native allocations and caches, so it is not interchangeable
with Coil cache size. No OOM or app failure was observed in these sequences.

The same sequence was repeated after warming images to distinguish first-load
work from sustained rendering. See [warm comparison](scroll-benchmark-warm.json).
Results and interpretation follow below. No speculative playback or global
rendering setting change is used to hide a performance limitation.

The warmed candidate had **92/1605 janky frames (5.73%)**, versus warmed V3
**176/1562 (11.27%)**. Median7 ms in both, p90 17→11 ms, p95 22→21 ms,
p99 32 ms in both. GPU p90 18→13 ms. Candidate warm-pass PSS367,922→372,023 KiB
and RSS484,260→486,204 KiB stayed roughly level over this short sample; this is
not a leak/soak certification. The sustained regression hypothesis was not supported
by the warm comparison. First-load image work remains a documented limitation;
there is no blanket smoothness or excessive-memory/OOM guarantee from two short
sequences. Source/cold cache differences and changing remote catalog data limit
strict benchmarking. No prefetch mechanism or cache bypass was introduced.

## Playback preservation smoke and delivery

One actual Resident Evil startup was performed with the existing bitstream ON
configuration, without changing preferences. The initial UI harness ran before an
accessibility root was ready and did not click Play. Once the details screen was
verified, a single Play action reached DirectPlay, selected OMX.Nvidia.DOVI.decode,
and progressed to **23,456 ms**. Back stopped playback normally and ExoPlayer
released. A recoverable AudioSink DEAD_OBJECT/-6 occurred during startup at position0;
playback recovered. This is consistent with the documented pre-existing HDMI-route
limitation, not proof that the image changes caused or fixed it. The full cause was
not re-investigated in this artwork task. The harness's time-format error affected
log collection only; subsequent PID-filtered logs retain the actual session.
See [playback signal](playback-regression.txt) and [codec lines](playback-codec-output.txt).
No new TCL/Yamaha physical output confirmation or PCM mode test is claimed.

The final installed APK was pulled back and its SHA-256 matched the delivered
candidate. [Final Shield identity/data/settings](shield-final.json) verifies the
original UID, data directory inode and first-install timestamp, unchanged package
and absent/default-ON bitstream setting. Library login remained accessible.

Implementation commit: `cce0bee5c94c3e98b36231e79e7b920cf4706e0e`.
Dedicated GitHub destination: `viktorjonsson77/trixarr`, branch
`feat/trixarr-image-quality-v1`. Documentation/evidence is a separate focused commit.
No main merge, tag, force-push or GitHub release is authorized/performed.

Remaining gates: user-confirmed TV artwork quality, broader poster/season/collection/
search visual comparisons, cold-load pacing and a long scrolling/memory soak.
Existing 1080p UI upscaling and low-resolution source artwork remain physical limits.
External TMDB original-preset downloads are decoded within bounds but their transfer
sizes were not measured in this Jellyfin-focused probe. Release signing remains the
existing debug configuration; this is an updated Beta candidate, not a production
release APK. Feature implementation/build/tests pass; overall acceptance is PARTIAL.

## API / library references

* [Coil 3.3.0 engine/cache ordering](https://github.com/coil-kt/coil/blob/3.3.0/coil-core/src/commonMain/kotlin/coil3/intercept/EngineInterceptor.kt)
* [Coil size-chain contract](https://github.com/coil-kt/coil/blob/3.3.0/coil-core/src/commonMain/kotlin/coil3/intercept/RealInterceptorChain.kt)
* [Android INEXACT decoding](https://github.com/coil-kt/coil/blob/3.3.0/coil-core/src/androidMain/kotlin/coil3/decode/BitmapFactoryDecoder.kt)
* [TMDB documented image sizes](https://developer.themoviedb.org/docs/image-basics)
* [Seerr TMDB image proxy route](https://github.com/seerr-team/seerr/blob/develop/server/routes/imageproxy.ts)
