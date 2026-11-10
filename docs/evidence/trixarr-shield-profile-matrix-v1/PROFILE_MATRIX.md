# Trixarr Shield profile matrix v1

Datum: 2026-11-10. Status: PARTIAL; genomförda tester dokumenterade, fysisk jämförelse och inventering ofullständiga.
PASS avser endast angiven fil, spår, tidsintervall och HDMI-kedja. NOT_TESTED innebär
att ett test saknar körning eller entydigt klassificerad provfil, inte att formatet
saknas i hela biblioteket. UNKNOWN innebär otillräcklig metadata. Ingen MEL/FEL-gissning.

Bevisnivåer: **HOST VERIFIED** = APK-/filkontroll; **DEVICE VERIFIED** = Android-/spelartillstånd;
**USER VERIFIED** = användarens fysiska TV-/AVR-observation; **NOT VERIFIED** = bevis saknas.

## Media inventory

Inventeringsväg: autentiserad Trixarr-UI och dess diagnostik. Ingen token-/credential-
extraction, inga serverändringar. Ingen behörigt tillgänglig lokal mediemapp eller
metadataexport identifierad; ffprobe finns på host men inga media-URL:er/tokens återanvänds.
Inventeringen är partiell och får inte beskrivas som en fullständig biblioteksskanning.

| Item | Jellyfin item-ID | Klassificeringsunderlag | Identifierat format |
| --- | --- | --- | --- |
| Resident Evil (UI: 93 min; katalogdatum 2026-09-16) | dae676fc-22d2-a272-784e-30b9ff521ab8 | Tidigare source + Media3-input + fysisk användarobservation | MKV, HEVC Main10, DV5 L6, EAC3 5.1, 23.976025 fps |
| Project Hail Mary (UI: 156 min, 2026-03-15) | e8d34ccd-2a41-6e72-e208-845c8251697f | M02 source + faktiskt levererat Media3-format | MKV, AV1 Main10/PQ enligt Jellyfin; DV8 L10/HDR Unknown är ej verifierad DV8.1. Levereras AVC-transcode |
| Backrooms | 5b9433f5-4f27-d3bf-7879-592d6ed2460e | M03 Jellyfin source + Media3 delivered format | MKV, HEVC Main10 DV7 L6/PQ, 84.10 Mbit/s; DTS-HD MA/DTS/AC3 source tracks; MEL/FEL UNKNOWN |
| Lee Cronin's The Mummy | e4d703d6-f047-07a8-4192-6d15eb5f1919 | M04 source + decoder format | MKV, 3840×1600 HEVC Main 8-bit SDR/BT709, EAC3 5.1, 23.976025 fps |
| The Scarecrows' Wedding | 81f02550-0d33-5853-ebd0-a2e92ce132cc | M05 source + delivered format | MKV, AVC Main 8-bit SDR/BT709 1920×1080, 24 fps; AC3 stereo 44.1 kHz |
| Insidan ut 2 | 20d28146-fdd7-4f67-5b91-681fb935b9bf | M06 source/decoder; HDR10-compatible base layer per policy | MKV HEVC Main10 DV8 L6, 3840×1606, 24 fps; EAC3 5.1. DV8.1 candidate, bitstream compatibility not directly parsed |
| One Battle After Another | a06d2851-93a8-e70f-63db-bd12ed84676a | M07 source + negotiated path | MKV HEVC Main10/PQ DV7 L6, 3840×2160/23.976025; TrueHD Atmos metadata 7.1 + AC3 5.1; MEL/FEL UNKNOWN |
| Tur | UNKNOWN | Liknande-titel-kort observerat | UNKNOWN; tillgänglighet/codec ej verifierad |

Katalogdatum/artwork kan vara felmatchade (Resident Evil); item-ID + tekniskt sourceformat
identifierar provet. Filhash, exakt filnamn och MediaSourceId är NOT VERIFIED.

## File execution matrix

| Test ID | Media item | Container | Resolution | Video codec | HDR format | DV profile | DV level | MEL/FEL | Audio / layout | Frame rate | Expected behavior | Actual behavior | Result | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M01 (carried baseline) | Resident Evil / dae676fc… | MKV | 3832×1600 | HEVC Main10, 10 bit | DV | 5 | 6 | N/A | EAC3, 5.1, 48 kHz | 23.976025 | DV colors/output, encoded DD+, safe stop | 116 s DirectPlay; OMX.Nvidia.DOVI.decode; dvhe.05.06; correct TV colors, TCL DV logo and Yamaha DD+ confirmed by user | PASS | [Prior evidence](../trixarr-shield-dolby-vision-v1/ACCEPTANCE.md); user physical confirmation in new task |
| M02 | Project Hail Mary / e8d34ccd… | MKV source | 3840×2160 source | AV1 Main, 10 bit source | PQ/bt2020 metadata; HDR Unknown | Source says 8; NOT VERIFIED | Source says 10 | UNKNOWN | EAC3 source 5.1; delivered EAC3-JOC | 24.0 source | Observe negotiated path; no assumption of native AV1 or DV8.1 | 94 s; Transcode to video/avc, OMX.Nvidia.h264.decode; 0 dropped frames; physical output pending | PARTIAL | [Start](M02-start.txt), [end](M02-end.txt), [logs](M02-logcat.txt) |
| M03 | Backrooms / 5b9433f5… | MKV source | 3840×2160 | HEVC Main10 | PQ; source HDR Unknown | 7 (Jellyfin); no DV in delivered format | 6 source | UNKNOWN | DTS-HD MA 5.1 selected source #2 → AAC-LC → PCM16 5.1 | 23.976025 source | Observe P7 delivery, high-bitrate stability, audio conversion | Transcode session; OMX.Nvidia.h265.decode / hvc1.2.4.H153.B0; no verified DV; 315 s before pause, 0 dropped frames; +30 s seek and resume work; safe release | PARTIAL | [Start](M03-start.txt), [interim](M03-interim.txt), [logs](M03-logcat.txt) |
| M04 | Lee Cronin's The Mummy / e4d703d6… | MKV | 3840×1600 | HEVC Main, 8 bit | SDR BT709 | UNKNOWN / N/A | UNKNOWN | N/A | EAC3 5.1, 48 kHz | 23.976025 | SDR colors/no HDR activation, DirectPlay, encoded DD+ | 114 s, 0 dropped; DirectPlay, OMX.Nvidia.h265.decode, EAC3 output; physical observation pending | PARTIAL | [Start](M04-start.txt), [logs](M04-logcat.txt) |
| M05 | The Scarecrows' Wedding / 81f02550… | MKV | 1920×1080 | AVC Main, 8 bit | SDR BT709 | UNKNOWN / N/A | UNKNOWN | N/A | AC3 stereo, 44.1 kHz | 24 | SDR DirectPlay + encoded AC3 | 105 s, 0 dropped, OMX.Nvidia.h264.decode, AC3 AudioTrack; physical output pending | PARTIAL | [End](M05-end.txt), [logs](M05-logcat.txt) |
| M06 | Insidan ut 2 / 20d28146… | MKV | 3840×1606 | HEVC Main10 | DV | 8; 8.1 candidate per compatibility policy | 6 | N/A | EAC3 5.1, 48 kHz | 24 | DV output/colors, compatible HDR10 separately | 94 s, 0 dropped; DirectPlay, OMX.Nvidia.DOVI.decode; physical verification pending | PARTIAL | [Start](M06-start.txt), [logs](M06-logcat.txt) |
| M07 | One Battle After Another / a06d2851… | MKV | 3840×2160 | HEVC Main10 | PQ, HDR Unknown | 7 (Jellyfin) | 6 | UNKNOWN | TrueHD Atmos metadata 7.1/48 kHz source #1; AC3 5.1 #2 | 23.976025 | Start negotiated delivery and capture output | Initial BUFFERING for about 124 s, position0/buffer0; no decoder; controlled stop/release, no retry | FAIL | [Start](M07-start.txt), [interim](M07-interim.txt), [logs](M07-logcat.txt) |

M01 was executed in the preceding task, not rerun here. Its 61 cumulative dropped frames,
59.94006 Hz display mode and bounded duration remain limitations; no long-run/frame-perfect
claim. User confirmation in this task adds TCL DV logo and Yamaha DD+ to the prior
report's previously missing physical indicators. Atmos was not confirmed by DD+.

## Required format coverage

| Coverage ID | Format | Identified file/test | Result | Reason / limitation |
| --- | --- | --- | --- | --- |
| A | Dolby Vision Profile 5 | Resident Evil / M01 | PASS | Tested file only; USER VERIFIED DV logo/colors, DEVICE VERIFIED DV decoder |
| B | Dolby Vision Profile 7 MEL | None identified | NOT_TESTED | MEL classification UNKNOWN; no classified sample |
| C | Dolby Vision Profile 7 FEL | None identified | NOT_TESTED | FEL classification UNKNOWN; no classified sample; logo would not prove full EL processing |
| D | Dolby Vision Profile 8.1 | Insidan ut 2 / M06 candidate | PARTIAL | Source profile8 + hdr10Compatibility SUPPORTED; compatibility-ID1 inferred from code policy, not direct bitstream parse; DV activation and fallback separately NOT VERIFIED |
| E | HDR10 | M03 delivered PQ/static-metadata candidate | PARTIAL | Output BT2020/PQ and static-info key present; raw metadata not inspected; physical HDR10 NOT VERIFIED |
| F | HDR10+ | None classified | NOT_TESTED | Display reports HDR types 1/2 only; no actual HDR10+ output test |
| G | SDR | Lee Cronin's The Mummy / M04 | PARTIAL | 114 s DirectPlay with 0 dropped frames; physical no-HDR observation pending |
| H | HEVC Main10 | Resident Evil / M01 | PASS | Bounded DV5 case only, not a separate HDR10/SDR test |
| I | AV1 | Project Hail Mary / M02 | PARTIAL | Source metadata AV1; actual decoding AVC after server transcode; native AV1 NOT VERIFIED |
| J | 4K MKV Remux | None classified | NOT_TESTED | 4K MKV alone does not prove Remux; no classified Remux DirectPlay test. M03 ran five minutes on converted delivery |
| K | TrueHD Atmos | One Battle After Another / M07 source | PARTIAL | Source identified; no native TrueHD/Atmos output verified |
| L | DTS-HD MA | Backrooms M03 source #2 | PARTIAL | Source identified, delivered AAC/PCM; DTS-HD MA passthrough not tested |
| M | DTS:X | None identified | NOT_TESTED | No source/output/AVR evidence |
| N | EAC3 Atmos | M01 source profile; M02 audio/eac3-joc | PARTIAL | Source/JOC signaling is not Atmos at AVR; M01 AVR displayed DD+ |
| O | 23.976 fps | Resident Evil / M01 | PARTIAL | Playback verified; display stayed 59.94006 Hz, auto switching not demonstrated |

## Audio matrix

| Format | Source evidence | Android output | Yamaha indication | Result |
| --- | --- | --- | --- | --- |
| AC3 | M05 stereo 44.1 kHz selected; M03 source #6 unselected | M05 encoded AC3 | NOT VERIFIED | PARTIAL |
| EAC3 | M01 EAC3 5.1 48 kHz | encoding 6, mask 252, offload false | USER VERIFIED Dolby Digital Plus | PASS (M01 file/track) |
| EAC3 Atmos | M01 profile string; M02 delivered EAC3-JOC | See M02 snapshots | M01 DD+ only; M02 NOT VERIFIED | PARTIAL |
| TrueHD | M07 selected source #1, 7.1/48 kHz | No selected decoder/output before 120-second startup abort | NOT VERIFIED | PARTIAL |
| TrueHD Atmos | M07 source profile string | No output before 120-second startup abort | NOT VERIFIED | PARTIAL |
| DTS | M03 source #3/#5 | Not selected | NOT VERIFIED | NOT_TESTED |
| DTS-HD MA | M03 #2 selected source, also #4 | Converted AAC-LC → PCM16, mask252 | NOT VERIFIED | PARTIAL |
| DTS:X | None | NOT VERIFIED | NOT VERIFIED | NOT_TESTED |
| PCM | M03 delivered AAC-LC decoded to PCM | PCM16 encoding2, 5.1 mask252/48 kHz | NOT VERIFIED | PARTIAL (PCM output, not PCM source) |

## Refresh matrix

| Source fps | Sample | Before | During | After | Result |
| --- | --- | --- | --- | --- | --- |
| 23.976025 | M01 baseline; M03 / M04 / M07 new | 59.94006 Hz | 59.94006 Hz | mode49 / 59.94006 Hz for new tests; prior M01 after-state absent | PARTIAL: no automatic switch; M07 never played |
| 24 | M02 / M05 / M06 | mode49 / 59.94006 Hz | mode49 / 59.94006 Hz | mode49 / 59.94006 Hz | PARTIAL: no automatic switch |
| 25 | None classified | — | — | — | NOT_TESTED |
| 50 | None classified | — | — | — | NOT_TESTED |
| 59.94 | None classified | — | — | — | NOT_TESTED |
| 60 | None classified | — | — | — | NOT_TESTED |

Permanent Android-display settings were not changed. No NOT_SUPPORTED result is inferred
from missing samples or declarations alone. Kodi comparisons remain NOT_TESTED pending
manual same-file/same-audio selection, without configuration changes.

## Physical observations and Kodi

| Test | Picture | TCL signal | Yamaha incoming format | Evidence level |
| --- | --- | --- | --- | --- |
| M01 prior Resident Evil | Correct colors | Dolby Vision logo | Dolby Digital Plus | USER VERIFIED in explicit baseline of this task |
| M02 Project Hail Mary | Not supplied | Not supplied | Not supplied | NOT VERIFIED; observation requested |
| M03 Backrooms | Not supplied | Not supplied | Not supplied | NOT VERIFIED; observation requested |
| M04 The Mummy | Not supplied | No-HDR not confirmed | Not supplied | NOT VERIFIED; observation requested |
| M05 The Scarecrows' Wedding | Not supplied | No-HDR not confirmed | AC3 not physically confirmed | NOT VERIFIED |
| M06 Insidan ut 2 | Not supplied | DV activation not confirmed | Not supplied | NOT VERIFIED; observation requested |
| M07 One Battle After Another | No playback before abort | NOT VERIFIED | NOT VERIFIED | DEVICE VERIFIED startup failure; no physical-format claim |
| K01 same-file Kodi comparison | No controlled sample | NOT VERIFIED | NOT VERIFIED | NOT_TESTED: Kodi has no library/existing video source; user navigation needed |

Kodi21.2 startup/UI is DEVICE VERIFIED. Files view showed only parent/Add videos;
no source was added. [Available startup log](kodi-available-log.txt) contains UI PCM
sink initialization only, not movie output. No same-file/same-track comparison occurred.

## Bounded conclusions

M02/M03/M04/M05/M06 achieved playback, with 0 cumulative dropped frames at their recorded
end checkpoints. This does not prove every frame, zero underruns, physical sync or long-run
support for their source formats. M07's 0 dropped frames is not a stability result: no
media played. M03 is the five-minute high-bitrate candidate; Remux identity and native
DirectPlay remain NOT_TESTED. No qualified DTS:X, HDR10+, 25/50/59.94/60 fps or MEL/FEL
sample has been identified in this partial inventory; they are not claimed absent from
the library. Raw files were not parsed with ffprobe/libdovi. Explicit HDR10 fallback from
M06 was not forced and is NOT_TESTED. No permanent display/audio/decoder settings changed.
