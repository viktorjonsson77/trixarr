# Code comparison: Trixarr versus Jellyfin Android TV

Datum: 2026-11-10. Endast läsning/diagnostik. Produktionskod har inte ändrats i detta uppdrag.

## Exact source baseline and installed binaries

Upstream-bas: Jellyfin Android TV **v0.19.10**, commit
`984181a3d6ab14e9a6d2dcc850c582e1c138bd95`. Detta är den sista upstream-committen
före forkens första egna first-parent-commit `bd6a0a258` i den aktuella historiken.
`git merge-base HEAD v0.19.10` ger samma fullständiga SHA. De 16 first-parent-commitsen
fram till `df6cabf15` avser huvudsakligen UI/Seerr; Player Engine-commits följer dessa.
[Officiell release](https://github.com/jellyfin/jellyfin-androidtv/releases/tag/v0.19.10)
pekar också på `984181a`.

Fryst Trixarr-källreferens: `69e5a6f381c0a20db73417cbd98b31546c60160b`.
Ingen tracked produktionsdifferens finns mellan APK-acceptansens `2dbc338bf` och denna
referens inom appens Java/Kotlin, playback och versionskatalogen. Installerad Trixarr-APK
matchar den tidigare accepterade fas2-completion-artefaktens hash exakt.

Viktig gräns: APK:n byggdes med tidigare lokala branding-/UI-ändringar och kan inte
reproduceras från en ren commit ensam. Delvis påbörjad startup/refresh-kod finns nu
okommitterad i arbetskatalogen, men är **inte installerad** och ingår inte i denna
jämförelse. Den frysta Git-referensen används i stället för att jämföra WIP med appen.
Installerad Jellyfin är version0.19.10/191099; en reproducerbar binär→exakt Git-commit-
attestering har inte genomförts. Källjämförelsen avser versionens officiella tagg.

Kommandon:

```sh
git merge-base HEAD v0.19.10
git log --first-parent --oneline v0.19.10..df6cabf15
git diff v0.19.10 69e5a6f38 -- <relevant paths>
git diff 2dbc338bf 69e5a6f38 -- app/src/main/java playback gradle/libs.versions.toml
```

[Blobidentiteter](source-blob-comparison.json) belägger vilka granskade filer som är
byte-identiska. [Fokuserad faktisk Git-diff utan kontextrader](focused-code.diff) omfattar VideoManager,
selector, adapter, capability probe och policy. [APK-dex-metoden](T01-installed-selector-dex.txt)
bekräftar att den installerade Trixarr-artefakten innehåller HEVC-filtergrenen.

## Behavioral comparison

Sökvägar nedan är från repositoryroten; paketprefixet `app/src/main/java/org/jellyfin/androidtv/`
förkortas till `app/…/`. Klassificering avser vad koden gör, inte bevisad orsak till tidigare TV-färgfel.

| Area / path | Function/class | Relevant commit | Actual difference | Classification | Possible playback impact / observed relevance |
| --- | --- | --- | --- | --- | --- |
| gradle/libs.versions.toml | androidx-media3 | Upstream984181a; frozen69e5a6f38 | Both1.8.0; runtime Init/Release also1.8.0 in both apps | STRUCTURAL CHANGE ONLY (other dependency additions) | No Media3-version explanation in this installed pair |
| Same version catalog | jellyfin-androidx-media | Same baseline | Both FFmpeg extension1.8.0+1 declared | STRUCTURAL CHANGE ONLY / no version delta | Actual selected audio decoder in original remains UNKNOWN |
| Same version catalog / app dependencies | OkHttp/Seerr/test dependencies | 440e8747e and later fork work | Explicit OkHttp4.12 and test dependencies added; Media3 unchanged | UNKNOWN IMPACT | Resolved original OkHttp runtime not independently compared; no demonstrated link to DV |
| app/…/ui/playback/VideoManager.java | configureExoplayerBuilder | e10b7cdbb | Trixarr adds setMediaCodecSelector(capabilityCodecSelector) | CONFIRMED BEHAVIOR CHANGE | Can remove unsafe HEVC candidates; does not force DV MIME, create DV metadata or reorder remaining decoders |
| app/…/ui/playback/engine/CapabilityCodecSelector.kt | configure/getDecoderInfos | e10b7cdbb | Blocks video/hevc queries for source-declared, unconverted DV without confirmed safe fallback; delegates every other query | CONFIRMED BEHAVIOR CHANGE | P5 direct path cannot silently use ordinary HEVC. Guard is disabled for server-converted delivery, so it is not a demonstrated explanation for the current original-app transcode difference |
| playback/core/…/engine/PlayerCapabilityPolicy.kt; app/…/engine/AndroidCapabilityProbe.kt | evaluate/media/device | e10b7cdbb | Source/declaration evidence can block unsupported direct input; P5 forbids generic HDR10 fallback; P7/P8 need compatibilityID1 for safe base-layer fallback | CONFIRMED BEHAVIOR CHANGE | Safety gate, not tone mapping, RPU injection, P7→P8 conversion or FEL processing. Does not request server transcoding |
| app/…/ui/playback/engine/Media3PlayerEngine.kt | prepare/play/pause/seek/release | e1bd960fb; moved core contract e10b7cdbb | Delegates to the same configured ExoPlayer; prepare remains setMediaItem + prepare | STRUCTURAL CHANGE ONLY | PlayerEngine name/interface alone does not change decoder, renderer, container parser or HDMI output |
| app/…/ui/playback/VideoManager.java | configureExoplayerBuilder | Upstream unchanged code except selector | Same decoder fallback=true, extension ON/PREFER according to setting, DefaultTrackSelector, audio offload settings | STRUCTURAL CHANGE ONLY / no configuration delta here | No new video engine or audio passthrough implementation |
| Same file; app/…/di/PlaybackModule.kt | DefaultExtractorsFactory / DefaultMediaSourceFactory / HTTP factory | Upstream byte-identical factory configuration | Same TS search size, constant-bitrate seeking, DefaultDataSource + OkHttp source factory | STRUCTURAL CHANGE ONLY / unchanged selection machinery | Different negotiated URL/delivery can still select a different demux/MediaSource path; no custom DV extractor introduced |
| app/…/util/profile/deviceProfile.kt | createDeviceProfile | Upstream byte-identical blob | No fork code delta. ac3Enabled=false filters both AC3 and EAC3; audioBehaviour can further restrict codecs | CONFIRMED BEHAVIOR CHANGE (runtime configuration, not source delta) | Original UI bitstream unchecked versus Trixarr checked explains different advertised audio compatibility; original reports AudioCodecNotSupported |
| app/…/util/profile/MediaCodecCapabilitiesTest.kt | DV/HEVC capability tests | Upstream byte-identical blob | No fork delta in capability heuristics | STRUCTURAL CHANGE ONLY / unchanged | Same hardware and same helper do not force identical profiles when stored settings differ |
| app/…/ui/playback/PlaybackManager.kt | getVideoStreamInfo/createStreamInfo | Upstream byte-identical blob | Same PlaybackInfo negotiation and DirectPlay/DirectStream/Transcode branch selection | STRUCTURAL CHANGE ONLY / unchanged | Current delivery difference follows server response/profile, not rewritten negotiation code |
| app/…/ui/playback/PlaybackController.java; PlaybackLauncher.kt; VideoQueueManager.kt | getCurrentMediaSource / selectedMediaSourceId | 905c2988a | Explicit version/source selection added before existing fallback | CONFIRMED BEHAVIOR CHANGE | Could choose different files in another test; current IDs match, so not supporting this explanation here |
| app/…/ui/playback/PlaybackController.java | playerErrorEncountered | e10b7cdbb | Original retries by disabling DirectPlay then DirectStream; Trixarr decoder error is terminal without retry | CONFIRMED BEHAVIOR CHANGE | Can change delivery after an error. No retry sequence appears in the current comparison |
| playback/core/…/engine/PlaybackSessionOwner.kt; OrderedPlaybackReporter.kt; controller/reporting helper | generation/claim/ordered reporting | e10b7cdbb | One session/generation, stale callback gates and ordered report attempts | CONFIRMED BEHAVIOR CHANGE | Ownership/lifecycle/reporting protection; no color conversion or new decoder selection from abstraction itself |
| app/…/ui/playback/PlaybackControllerHelper.kt; Media3PlayerEngine.kt | audio/subtitle selection | e1bd960fb/e10b7cdbb | Validated group selection through adapter; same Media3 TrackSelectionOverride mechanism | CONFIRMED BEHAVIOR CHANGE | Invalid group selection is rejected; current source audio index1 matches between apps |
| app/…/ui/playback/VideoManager.java; PlaybackDiagnostics.kt | VideoFrameMetadataListener / diagnostics | e1bd960fb/e10b7cdbb | Reads MediaFormat, AnalyticsListener and capability evidence; INFO/MENU dialog added | DIAGNOSTIC CHANGE ONLY | Does not rewrite HDR metadata, MIME, CSD or Surface configuration |
| app/…/ui/playback/VideoManager.java; app/src/main/res/layout | PlayerView/Surface | Compared tags; layout diff empty | Same PlayerView.setPlayer and ExoPlayer-managed Surface; added listener is read-only | STRUCTURAL CHANGE ONLY / no rendering delta demonstrated | No custom SurfaceTexture, shader or DV color conversion implemented |
| app/…/ui/playback/PlaybackController.java; CustomPlaybackOverlayFragment.java | findBestDisplayMode/setRefreshRate / closePlayer | Existing upstream code retained | Same window preferredDisplayModeId selection and reset to0 on close | STRUCTURAL CHANGE ONLY / no new refresh algorithm in installed APK | Both existing settings now select Scale on device; both requested/observed mode40 in this comparison |
| playback/core; playback/jellyfin/…/PlaySessionService.kt; playback/media3/exoplayer/…/ExoPlayerBackend.kt | experimental backend contract/session/track cleanup | e10b7cdbb | Shared contract, ownership and track/lifecycle behavior changed in experimental path | CONFIRMED BEHAVIOR CHANGE | Not the active VideoManager/controller path identified in J01/T01, so not evidence for this DV difference |

The [Media3 1.8.0 implementation](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/MediaCodecUtil.java)
already excludes generic HEVC alternatives for explicitly identified DV P5/P7.
Thus Trixarr's additional filter is not evidence that upstream necessarily treats an
explicit `video/dolby-vision` P5 input as ordinary HEVC. In J01 the configured MIME was
actually `video/hevc`; this is a different input case.

## What the code and current device traces establish

1. Same Media3 runtime; different negotiated delivery and decoder input.
2. Same source-profile builder; different stored AC3/EAC3 bitstream flag.
3. Original's reported transcode reason is audio compatibility; source audio index remains1.
4. Trixarr receives DirectPlay/DV; original reaches a generic HEVC path and PCM output.
5. The precise point at which DV configuration/signaling is absent or interpreted differently
   in original's converted delivery is not proved by these logs. RPU/CSD/container bytes
   and server FFmpeg copy/encode decisions were not captured.
6. No matched-settings A/B was performed because settings changes were prohibited.
   The new unfinished startup/refresh code was neither built nor installed in this task.
