# TRIXARR_VS_JELLYFIN_DV_COMPARISON_PARTIAL

Datum: 2026-11-10. Källjämförelse och två sekventiella fysiska enhetskörningar genomförda.
**Bekräftat:** samma serverorigin/item/MediaSource/ljudspår ger olika leveransväg,
codec-MIME och Android-ljudutmatning i de två apparna. Originalets AC3/EAC3-bitstreamflagga
är av, Trixarrs är på. Båda använder Media3 1.8.0.

**Inte slutligt bevisat:** historisk orsak till rosa/lila TV-bild, exakt DV-signalerings-
förlustpunkt och färskt fysiskt TCL-/Yamaha-resultat. Historisk rotorsak **UNKNOWN**;
fysisk HDMI-/färgjämförelse **INCONCLUSIVE** utan användarobservation. Ingen DV-fix hävdas.

- [CODE_DIFF.md](CODE_DIFF.md): exakt upstream-bas, faktisk Git-diff, klassificerade kodskillnader.
- [PLAYBACK_COMPARISON.md](PLAYBACK_COMPARISON.md): källidentitet, inställningar, decoder-/MIME-/ljud-/displayresultat och hypoteser A–J.

## Baseline, instructions and preserved work

Vid detta uppdrags start: gren `feat/trixarr-playback-stability-refresh-v1`, HEAD
`69e5a6f381c0a20db73417cbd98b31546c60160b`. Status **195 dirty-poster**: de tidigare190
orelaterade posterna samt fem poster från det föregående, ännu ofärdiga implementations-
uppdraget. Det nya diagnostikuppdragets kodrestriktion ersätter fortsatt implementation.

Följande fem WIP-poster bevaras men byggs/installeras/committas inte här:

- app/src/test/kotlin/ui/playback/completion/ControllerChainTests.kt
- playback/core/src/main/kotlin/engine/PlaybackSessionOwner.kt
- app/src/main/java/org/jellyfin/androidtv/ui/playback/engine/AndroidRefreshRateController.kt
- playback/core/src/main/kotlin/engine/PlaybackStartup.kt
- playback/core/src/main/kotlin/engine/RefreshRatePolicy.kt

Ingen AGENTS.md hittades i repositoryt eller överordnade `/opt` och `/` under denna
konversation. Begärda förstudie-/fas2-/completion-/Shield-DV-/profilmatrisrapporter har
lästs. Debugging-skillen hade redan öppnats för det föregående uppdraget; jämförelsen
använder nu den verkliga samma-källa-loopen och source/decodergränsen utan nya kodprober.
Inga regressions-/Gradle-tester eller APK-byggen kördes inom detta diagnostikuppdrag.
Det tidigare implementationsuppdragets röda syntetiska test är inte ett testresultat
för denna jämförelse och lämnas oförändrat.

## Device — DEVICE VERIFIED

Enbart `adb -s 100.91.172.18:5555` användes. ADB svarade device före test.
NVIDIA SHIELD Android TV, mdarcy, Android11/API30, arm64-v8a, fingerprint
`NVIDIA/mdarcy/mdarcy:11/RQ1A.210105.003/7825230_4387.0822:user/release-keys`.
[Enhetsidentitet](device-identity.txt). Shield→Yamaha RX-V6A→TCL är angiven fysisk kedja;
färskt signalformat kräver separat fysisk observation, inte enbart codec- eller mode-ID.

## Application inventory — DEVICE VERIFIED

Original-Jellyfin identifierades via PackageManager, inte genom antaget paketnamn.
Installerade base.apk lästes skrivskyddat till temporära hostfiler för SHA/signaturkontroll;
appdata, authdatabaser och credentials lästes inte.

| App | Package | Version/code | First install | Last update | Appdata evidence |
| --- | --- | --- | --- | --- | --- |
| Trixarr | se.viktorjonsson.jellyfintv.debug | 0.0.0-dev.1 /1 | 2026-09-02 23:52:47 | 2026-11-10 19:31:00 | CE inode652903; UI authenticated; playback works |
| Original Jellyfin | org.jellyfin.androidtv | 0.19.10 /191099 | 2026-01-28 21:49:04 | 2026-09-16 22:49:37 | CE inode409886; UI authenticated; playback works |
| Kodi | org.xbmc.kodi | 21.2 /2102000 | 2026-11-04 22:41:13 | 2026-11-04 22:41:13 | CE inode522522 registered; content not read here |

Install/update dates are PackageManager's reported strings; device-local time UTC+1
was correlated during testing. [Full technical inventory](application-inventory.json).

| App | APK SHA-256 | Signer certificate SHA-256 |
| --- | --- | --- |
| Trixarr | 1e7eeb00ecc832f39b4542fd81251f59e91e03967bce612ff657e7ae91922526 | f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c |
| Jellyfin | dbc7a0ea8d757815e9619fac5e128391e37d501083f7c8b0ee5f25a5a33eee0c | 2eca5acd52d4f718a178361a3489cd1322392b2ca62e8d72a3c4027af94a3d8c |
| Kodi | e4c84bf738cac846ec4b4c6b58cc4a3ee669320d3061b6e80cf14fa61bc2c465 | f517b44b5db5e62a6c1ec55ba47526db7de0d61f6ba26a7987520e293499b8d5 |

apksigner verified each sampled APK. Trixarr signer DN: Android Debug; Jellyfin signer
DN: CN Android / O Google Inc.; Kodi: XBMC Foundation. These are observed certificate
identities, not a new independent Play Store/provenance attestation. No installation,
update, uninstall, pm clear or reset. Correct Trixarr hash confirms unfinished local
startup/refresh changes are not the deployed app.

## Source comparison result

Exact Trixarr forkpoint is upstream `v0.19.10`/`984181a3d6ab14e9a6d2dcc850c582e1c138bd95`.
Frozen compared Trixarr source is69e5a6f38; its tracked playback production files match
2dbc338bf, the APK completion-test commit. Same declared Media3 1.8.0; runtime verified
1.8.0 in both Init/Release logs. No version metadata files were found by the initial
APK META-INF probe; that unsuccessful probe is not used as version proof.

Confirmed code changes include the explicit DV HEVC-safety filter, pre-prepare capability
policy, terminal decoder errors instead of automatic retry, session/report ownership and
explicit MediaSource version selection. The ExoPlayer wrapper itself is structural.
Diagnostics do not rewrite HDR/CSD/Surface. The source-profile builder, PlaybackInfo/
URL branch logic, renderer/extractor setup and old refresh algorithm remain unchanged
at the reviewed seams. See CODE_DIFF for path/function/commit and actual impact.

## Sequential playback result

Same Resident Evil item `dae676fc-22d2-a272-784e-30b9ff521ab8`, same MediaSource
`dae676fc22d2a272784e30b9ff521ab8`, same source audio1, matching serverorigin hash.
The unique source video#0 is identified in Trixarr metadata; original's independent
source-video index/raw file hash is not directly captured. Strict full stream-/binary-
identity therefore remains INCONCLUSIVE if those additional proofs are required.

| Observation | Original Jellyfin J01 | Trixarr T01 | Evidence |
| --- | --- | --- | --- |
| Actual playback duration | 94.371s | 91.681s | DEVICE VERIFIED stop position |
| Playback method | Transcode | DirectPlay | DEVICE VERIFIED |
| Decoder / MIME | OMX.Nvidia.h265.decode /video/hevc | OMX.Nvidia.DOVI.decode /video/dolby-vision | DEVICE VERIFIED native metrics |
| Delivered DV signaling | NOT VERIFIED; generic HEVC configured | dvhe.05.06 /profile5 level6 | DEVICE VERIFIED Trixarr input |
| Android audio | PCM16,6-channel mask,48kHz | EAC3,6-channel mask,48kHz | DEVICE VERIFIED; receiver output not inferred |
| Cleanup | Stop + ExoPlayer Release | Stop + ExoPlayer Release | DEVICE VERIFIED |
| Display rate |49→40→49 |49→40→49 (restoration observed later than immediate stop sample) | DEVICE VERIFIED Android modes; HDMI signal NOT VERIFIED |
| Physical TCL colors/DV, Yamaha format, smoothness | NOT VERIFIED for J01 | NOT VERIFIED for new T01 | USER observation requested, no reply captured |

Both apps were opened with existing authenticated sessions. No concurrent playback.
Original displayed Resident Evil with DV/Atmos badges before Play; these are source UI
metadata, not actual HDMI/AVR proof. New Trixarr end snapshot had0 dropped frames and
37.756s buffered. Original's dropped-frame counter was not captured. Native final audio
records showed underrun0, but an AudioTrack dead/recreate warning occurred in J01.
No app/decoder crash was observed in the retained evidence. No retry was initiated.

Prior Resident Evil baseline remains **USER VERIFIED**: correct colors, TCL DV logo,
Yamaha DD+, approximately116s. It is not substituted for new physical observations.
In particular, screen capture can misrepresent DV color; no frame is used as physical proof.

## Root cause assessment

**Current negotiation divergence — strongly supported:** the same `ac3Enabled` profile
code excludes EAC3 when the Dolby Digital bitstream checkbox is off. That checkbox is
observed off in original and on in Trixarr. J01 explicitly reports AudioCodecNotSupported,
requests AAC/MP3 conversion and configures HEVC; T01 DirectPlays DV+EAC3. This connects
configuration→profile/negotiation→different delivered MIME/decoder/output without
attributing the result to a new player engine or a Media3 upgrade.

**Historical wrong-color root cause — UNKNOWN:** no paired historical trace exists;
no fresh physical color/DV observation or authorized matched-settings A/B is available.
The precise server/demux/container DV-config/RPU loss or interpretation boundary is not
proved. Original's actual delivered compressed audio codec and full MediaFormat/CSD
are not independently captured. Same-profile policy code does not imply same behavior
when saved settings differ. Trixarr's filter is relevant safety behavior but does not
prove it caused the benefit in this case; converted streams bypass that direct-source
restriction. Detailed supporting/contradicting evidence and next checks are in hypotheses A–J.

No setting was toggled to strengthen a hypothesis. A reversal test of the original
bitstream flag, if later wanted, requires separate approval and restoration of its prior
value. No intervention is claimed as performed.

## Logging and limitations

Target-process logcat tags were limited to Media3/ExoPlayer/controller, MediaCodec,
ACodec/CCodec/OMX, AudioTrack and Surface. Display was read separately. media.metrics
was narrowed to each exact UID/package and test-time codec/audio records; older or
unrelated records are excluded from saved evidence. No global log level or logcat clear.
Authenticated URLs were handled in memory only; saved fields are technical allowlist
and ID/server hashes. No credential extraction or server calls using extracted tokens.

A later J01 log re-read returned empty and briefly replaced its first artifact. Exact
already captured lines and the identity JSON were restored from the tool transcript;
[J01-logcat.txt](J01-logcat.txt) explicitly labels itself as recovered excerpts, not a
complete continuous log. The separately captured UID/time-scoped J01 metrics remain
available and independently confirm codec MIME/audio configuration/release.

UI screenshots were temporary and are not committed because they include personal
library context. Missing raw CSD/RPU, original decoded-format dump, independent video
index/file hash, current physical observations and matched-settings intervention limit
acceptance. These are not filled with guesses or with host tests.

## Acceptance and Git

Source comparison: completed for the reviewed exact-version seams. Device playback
comparison: completed, with matched origin/item/MediaSource/audio and documented caveats.
Physical format/color comparison and historical root cause: incomplete/inconclusive.
Overall COMPARISON_PASS is not claimed.

Only this evidence directory is added. No production change, build, installation,
app-setting write, server/Kodi/global-display change, push, tag or deployment. All195
pre-existing dirty status entries (190 prior +5 unfinished implementation) must remain
unchanged after the focused local evidence commit. The comparison's commit is reported
in the final response; it contains only this directory.
