# TRIXARR_SHIELD_PROFILE_MATRIX_PARTIAL

Datum: 2026-11-10. Endast diagnostik/evidens; befintlig Media3 Player Engine används.
Ingen ny motor, Kodi-kod, produktionskod, APK, installation eller server-/HDMI-/Kodi-inställningsändring.
Se [PROFILE_MATRIX.md](PROFILE_MATRIX.md) för fil-, format-, ljud- och refreshmatris.
Status PARTIAL: inventeringen är partiell, viktiga format saknar entydigt klassificerad
provfil och nya tester saknar ännu fysisk TV/AVR-observation och Kodi-jämförelse.

## Baseline and scope

Startgren `feat/player-engine-phase-2-completion`, start-HEAD
`62c69a876152c5f278e94b782bb01c54c49d32c2`. 190 tidigare dirty-statusposter registrerade
före arbetet; ingen reset, clean eller stash. Ingen tillämplig AGENTS.md hittades i
repositoryt eller `/opt` och `/`. De tre Player Engine-arkitekturdokumenten, båda tidigare
fasacceptanserna och Shield DV v1-acceptansen har lästs i denna konversation.
Inga host-/redroid-tester räknas som fysisk HDMI-verifiering. Inga hosttester behövdes
för detta dokumentations-/enhetsuppdrag.

**HOST VERIFIED:** installerad base.apk lästes via explicit målserial och hashades
strömmande på hosten utan att någon stor mediefil kopierades. SHA-256:
`1e7eeb00ecc832f39b4542fd81251f59e91e03967bce612ff657e7ae91922526`, exakt förväntad artefakt.

**DEVICE VERIFIED:** NVIDIA SHIELD Android TV, mdarcy, Android 11/API30, arm64-v8a,
fingerprint `NVIDIA/mdarcy/mdarcy:11/RQ1A.210105.003/7825230_4387.0822:user/release-keys`.
Paket `se.viktorjonsson.jellyfintv.debug`, 0.0.0-dev.1 (code1), minSdk21/targetSdk36.
CE inode 652903 och tidigare installation/uppdatering kvar. Ingen ominstallation.
[Enhet/APK-baslinje](device-apk-baseline.txt). Media3 runtime 1.8.0.

**USER VERIFIED, tidigare M01:** användaren har i detta uppdrags uttryckliga baslinje
bekräftat Resident Evil, cirka 116 sekunder, korrekta färger, TCL Dolby Vision-logotyp
och Yamaha Dolby Digital Plus. Resultatet är DOLBY_VISION_PROFILE_5_VERIFIED för exakt
den testade filen och Shield→Yamaha RX-V6A→TCL. Ingen generalisering till andra P5-filer.
Detta kompletterar föregående rapports då saknade TV-/AVR-indikering; den äldre rapporten
skrivs inte om. DD+ bevisar inte Atmos. Föregående ADB-filmframe såg rosa/lila ut trots
korrekt fysisk bild; screen capture används inte som facit för TV:ns DV-färger.

## Inventory method and evidence levels

Endast den redan autentiserade Trixarr-sessionens UI/diagnostik samt tekniska fält ur
målprocessens loggar har använts för mediaurval. Inga kontodatabaser/preferences eller
credentials lästes. Inga tokens återanvändes för API-anrop. Ingen Jellyfin-serverändring.
Inget relevant mediemount/metadataexport hittades i kontrollerade standardplatser;
användaren tillfrågades om redan behörigt åtkomligt underlag. ffprobe finns men användes
inte eftersom inga behörigt identifierade filer/metadata-URL:er utan credential extraction
fanns tillgängliga. Inga stora mediefiler kopierades.

Jellyfin source profile, Media3 delivered format, Android output och fysisk TV/AVR
är separata bevisnivåer. Profil 7 innebär inte MEL eller FEL. Profil 8 innebär inte
8.1; AV1+DV8/HDR Unknown-metadata i M02 verifieras inte som en DV8.1-bitström.
HDR10+/Atmos/DTS:X får inte status USER VERIFIED från källsträngar eller MIME.
Filnamn/Remux-status/RPU/FEL är UNKNOWN där tekniskt underlag saknas.

## Execution protocol

Tester körs sekventiellt. Föregående motor stoppas/frigörs innan nästa fil startas.
Rätt detaljsida kontrolleras före Play, INFO öppnar diagnostiken, UI-text fångas utan
credentials, och pause/stop samt ExoPlayer Release kontrolleras efter test.
Inga automatiska retries, codec-overrides eller policyändringar görs.
Vid fel eller ADB-/autentiseringsförlust stoppas berörd sekvens.

M01 är återanvänd tidigare fysisk körning, inte en ny omkörning.
M02–M07 är sex nya fysiska försök: fem nådde faktisk uppspelning och ett (M07) avbröts efter starttimeout. Deras format-/profilutmatning kan inte markeras
PASS om den annonserade källan byts genom serverleveransen. Klientens `Transcode`
säger att leveransen konverteras; video copy/encode måste verifieras separat där både
källa och leverans är HEVC. Byte AV1→AVC i M02 belägger däremot videokonvertering.

## Results and limitations

### M02 Project Hail Mary

Jellyfin item `e8d34ccd-2a41-6e72-e208-845c8251697f`, detaljsida 156 minuter/2026-03-15.
Källa: MKV, AV1 Main, 3840×2160, 10 bit, 24 fps, cirka 9.79 Mbit/s,
bt2020/PQ/bt2020nc, HDR Unknown, dvProfile8/dvLevel10. Audio #1 EAC3/Atmos-sträng,
5.1, 48 kHz, 768 kbit/s. Klassificeringen är Jellyfin-metadata, inte container/bitströmsanalys.

Leverans Transcode, vald video AVC med OMX.Nvidia.h264.decode och ljud audio/eac3-joc.
Förhandlingens orsaker: VideoCodecNotSupported, VideoProfileNotSupported,
VideoResolutionNotSupported, VideoRangeTypeNotSupported. Dessa är serverns anförda
orsaker, inte oberoende bevis för hårdvarans fullständiga stödmatris.
End-snapshot: PAUSED, position 94093 ms, buffered 22948 ms, dropped frames 0,
codec-/sink-/playbackfel UNKNOWN. Ingen namngiven felhändelse observerad i insamlad logg.
Startbegäran UTC 18:55:30; paus UTC 18:57:12; stop 18:57:34.807;
ExoPlayer Release 18:57:34.824. Före/under/efter: mode49, 59.94006 Hz; ingen växling.
Det visar cirka 94 sekunders funktion i vald transcodeväg, inte native AV1, DV8.1 eller Atmos-output.

[Start](M02-start.txt), [interim](M02-interim.txt), [end](M02-end.txt),
[loggar](M02-logcat.txt), [begränsade förhandlingsfält](M02-negotiation-fields.json).
Fysisk bild/HDR-/AVR-indikering efterfrågad, ännu NOT VERIFIED.

### M03 Backrooms

Jellyfin item `5b9433f5-4f27-d3bf-7879-592d6ed2460e`, detaljsida 110 minuter/2026-05-27.
Källa: MKV, HEVC Main10 3840×2160 23.976025 fps, cirka 84.10 Mbit/s, bt2020/PQ,
HDR Unknown, DV7 L6 enligt Jellyfin. MEL/FEL UNKNOWN. Källa audio #2/#4 DTS-HD MA
5.1, #3/#5 DTS core 5.1, #6 AC3 stereo; sourceformat är inte bevis för faktisk utmatning.

Leverans Transcode, förhandlingsorsaker AudioCodecNotSupported och
VideoRangeTypeNotSupported; valt AudioStreamIndex2. Levererad video är HEVC,
hvc1.2.4.H153.B0, 3840×2160, 23.976 fps, BT2020/PQ Limited 10-bit.
Decoder OMX.Nvidia.h265.decode; DV codec-sträng saknas. Levererad bitratefält cirka
88.94 Mbit/s är formatmetadata, inte uppmätt nätverksthroughput. Videospårets copy/encode
är NOT VERIFIED. Ingen DV7, MEL/FEL-behandling eller fullständig HDR10-klassning härleds.

Levererat ljud AAC-LC 6 kanaler/48 kHz, OMX.google.aac.decoder → PCM16 encoding2,
channel mask252, buffer144000, offloadfalse, ingen encoded passthrough vald.
DTS-HD MA-utmatning är därmed inte bekräftad; valt levererat spår är AAC.
Hög bitrate motiverade att förlänga detta representativa test till cirka fem minuter.
Det är en stabilitetskontroll av vald leverans, inte belagd Remux Direct Play.
Efter cirka 315 sekunder faktisk uppspelning: PAUSED på 315495 ms, buffered14959 ms,
0 dropped frames. FF-knapp gav +30 s till 345492 ms med pausläget bevarat.
Resume gav PLAYING på 348319 ms, buffered18171 ms, fortfarande 0 dropped frames.
Stop UTC19:04:00.949, Release19:04:00.990. Ingen namngiven codec-/playbackkrasch observerad.
Mode49 kvar före/under/efter. Detta belägger klientstabilitet på vald leverans i testintervallet,
inte fysisk A/V-synk, native DV7/FEL, DTS-HD MA output eller fem minuters Direct Play Remux.
[End/pause](M03-end.txt), [seek](M03-seek.txt), [resume](M03-interim.txt).

[Start](M03-start.txt), [interim](M03-interim.txt), [loggar](M03-logcat.txt),
[förhandlingsfält](M03-negotiation-fields.json). Fysisk TV/AVR-observation efterfrågad.

### M04 Lee Cronin's The Mummy

Item e4d703d6-f047-07a8-4192-6d15eb5f1919; detaljsida 135 minuter/2026-04-15 kontrollerad.
Source: MKV, HEVC Main 8-bit, 3840×1600, 23.976025 fps, 20.46 Mbit/s, BT709 primärer/
transfer/matris, SDR. EAC3 5.1/48 kHz/640 kbit/s. DirectPlay, OMX.Nvidia.h265.decode,
video/hevc och audio/eac3 med encoded EAC3 AudioTrack. Fysisk no-HDR-kontroll efterfrågad.
[Start](M04-start.txt), [loggar](M04-logcat.txt). Efter 114206 ms i end-snapshot: PAUSED, buffered50207 ms och dropped frames0.
Stop UTC19:06:33.034, Release19:06:33.049; mode49 kvar. Ingen namngiven decoder-/
playbackfelhändelse i insamlat material. [End](M04-end.txt).
SDR-färger/no-HDR och faktisk Yamaha-indikering är fortfarande NOT VERIFIED.

### M05 The Scarecrows' Wedding

Item 81f02550-0d33-5853-ebd0-a2e92ce132cc, detaljsida före Play:97 minuter/2026-03-13.
Source MKV, H264 Main/8-bit SDR BT709, 1920×1080, 24 fps, 9.996 Mbit/s; AC3 stereo,
44.1 kHz, 192 kbit/s. DirectPlay med OMX.Nvidia.h264.decode, encoded AC3 output.
End PAUSED, position 104761 ms, buffered50231 ms, 0 dropped. Pause UTC19:08:41.077,
Stop19:08:56.320, Release19:08:56.333. Mode49 kvar. Två UI-uttag under rörlig dialog
misslyckades; paused-uttaget lyckades. Ingen playbackomstart gjordes.
[End](M05-end.txt), [loggar](M05-logcat.txt). Fysisk SDR/no-HDR/AC3-output NOT VERIFIED.

### M06 Insidan ut 2

Item 20d28146-fdd7-4f67-5b91-681fb935b9bf; detaljsida96 minuter/2024-06-11 före Play.
Source MKV, HEVC Main10, 3840×1606, 24 fps, 24.736 Mbit/s, bt2020/PQ, profil8/level6.
EAC3/Atmos-källsträng, 5.1/48 kHz/768 kbit/s. DirectPlay med video/dolby-vision och
OMX.Nvidia.DOVI.decode; EAC3 AudioTrack. Policy hdr10Compatibility=SUPPORTED.
I granskad PlayerCapabilityPolicy kräver detta compatibility-ID1 för profil7/8;
AndroidCapabilityProbe använder Jellyfins dvBlSignalCompatibilityId. Detta är en
**policybaserad inferens om deklarerat ID1**, inte direkt container-/bitströmsanalys.
DV8.1 är därför kandidat/partiellt klassificerad, inte en allmänt verifierad profilklass.
Fysisk DV/color/AVR-observation efterfrågad. HDR10-fallback har inte tvingats eller testats.
[Start](M06-start.txt), [loggar](M06-logcat.txt). End PAUSED, position 93517 ms, buffered42107 ms, 0 dropped. Pause UTC19:10:54.639,
Stop19:11:02.808, Release19:11:02.823; mode49 kvar. Ingen namngiven codec-/playbackfelhändelse
observerad. [End](M06-end.txt). Fysisk DV-/AVR-observation kvarstår.

### M07 One Battle After Another — FAIL (bounded startup)

Item a06d2851-93a8-e70f-63db-bd12ed84676a; detaljsida161 minuter/2025-09-23 före Play.
Source MKV/HEVC Main10 3840×2160/23.976025, 56.54 Mbit/s, bt2020/PQ, DV7 L6, HDR Unknown.
TrueHD-källspår1:profil "Dolby TrueHD + Dolby Atmos", 7.1/48 kHz/2.839 Mbit/s;
AC3-spår2:5.1/48 kHz/640 kbit/s. MEL/FEL UNKNOWN.
Förhandlingen valde Transcode, audio1 och subtitle54/SubtitleMethod Encode;
orsaker AudioCodecNotSupported, VideoRangeTypeNotSupported. Ingen policy/spårinställning ändrades.

Startbegäran UTC19:11:33, ExoPlayer Init19:11:33.593. Start/interim/end visar
PLAYER State BUFFERING, position0, buffer0, ingen vald video-/audio-decoder och inget
AudioTrack-format. End fångades cirka två minuter efter start. Sekvensen avbröts med
Stop UTC19:13:37.886 och Release19:13:37.905, cirka124 sekunder efter Init.
FAIL avser "faktisk uppspelning inom cirka120 sekunder", inte ett fastställt decoderfel
eller bevis för att native TrueHD/DV7 är omöjligt. Sessionägarens logiska PLAYING och
START_SENT ska inte förväxlas med engine PLAYER State BUFFERING. 0 dropped frames när
0 frames spelats är inget stabilitets-PASS. Ingen automatisk retry/ominstallation.

[Start](M07-start.txt), [interim](M07-interim.txt), [end](M07-end.txt),
[loggar](M07-logcat.txt), [förhandlingsfält](M07-negotiation-fields.json).
Root cause NOT VERIFIED: stoppet sker före decoderinit; serverkonvertering/undertext-encode
är kontrollpunkter men ingen server-/nätverks-/subtitleorsak fastställs utan mer evidens.
Inga privata serverloggar eller globala loggnivåer användes.

### Missing coverage

DV7 MEL och FEL saknar entydigt klassificerad provfil. Fysisk DV8.1/HDR10/HDR10+-output, fysisk SDR/no-HDR,
TrueHD/Atmos-utmatning, DTS:X och 4K MKV Remux Direct Play är ej verifierade. DTS-HD MA finns
som källprofil i M03 men levereras AAC; det kan inte ge passthrough-PASS.
HDR-typer [1, 2] annonseras av displayen (DV/HDR10); HDR10+ annonseras inte i denna
stödlista. Ett saknat fysiskt test märks NOT_TESTED, inte automatiskt NOT_SUPPORTED.
AV1 saknas i annonserad decoder-MIME-lista och M02 transkodas; inga stöd/policyändringar.

### Refresh and audio continuity

23.976/24-källor observeras på 59.94006 Hz. Inget automatiskt refreshbyte har visats.
Ingen ändring av permanent Android-displayläge. Vid oförändrat läge kan faktisk
lägesåterställning efter ett byte inte verifieras. Provfiler för 25/50/59.94/60 fps
saknar klassificering. Fysisk ljudkontinuitet och synk för nya tester är NOT VERIFIED.

### Kodi comparison

Kodi 21.2/versionCode 2102000 kontrollerades igen. Efter M07:s dokumenterade Release
startades Kodi UTC19:13:57 med explicit serial och dess resolverade .Splash-aktivitet.
Status ok, COLD, 731 ms, org.xbmc.kodi/.Main. UI visar tomt Movies-bibliotek; Files-vyn
visar endast parent/Add videos, ingen befintlig källa. Ingen källa lades till och ingen
konfiguration ändrades. Kodi lämnades utan aktiv mediauppspelning.

[kodi-available-log.txt](kodi-available-log.txt): Kodi 21.2 ARM64 på Shield/API30,
UI-audiosink44100Hz/float/2ch PCM. Detta är startup-/UI-ljud, **inte filmens audio output**.
Samma fil och ljudspår kunde inte matchas i denna Kodi-UI. Manuell användaråtgärd
begärd utan inställningsändring. Kontrollerad Kodi-playback/decoder/TV-/AVR-output
är NOT_TESTED. Ingen Kodi-decoder härleds från Trixarrs loggar.

## Logging, failures and remaining gates

Endast målprocessens relevanta tags: PlaybackController, VideoManager, ExoPlayerImpl,
MediaCodec/ACodec/CCodec/OMXClient, AudioTrack, SurfaceUtils och AndroidRuntime.
Displayläge läses separat. UI-verktyget kunde inte få idle state under M05:s rörliga
playback; diagnostikdialogen bekräftades visuellt och textuttaget lyckades därefter i paus utan playbackomstart.
Detta är ett insamlingsfel, ingen playback-omkörning eller belagd spelarkrasch. Kodis befintliga startup-logg lästes separat med strikt tekniskt urval; inga inställnings-/credentialfiler lästes. Globala loggnivåer och logcat-buffertar ändras inte.
URL-/credential-/personliga path-rader utesluts före lagring. Endast vitlistade tekniska
förhandlingsfält och item-ID extraheras; autentiseringsfält läses inte ut eller sparas.
Loggarnas device-local tid är UTC+1; snapshots har host-UTC. Exakt first-frame-tid,
rå CSD/RPU/HDR-data och underrunräknare saknas i befintlig instrumentering.

Källmetadata med HDR Unknown/DV-profile, servertranscode och ofullständig fysisk
observation är verifieringsbegränsningar, inte i sig decoderkrascher.
M04/M05:s SDR-output har liksom M03 en närvarande hdr-static-info-nyckel i vendor-
MediaFormat; nyckelns närvaro är därför inte i sig bevis för meningsfull HDR10-metadata
eller aktiv HDR. Rå innehållsdata lästes inte. HDR10+ saknas i dessa metadataflaggor;
ingen faktisk HDR10+-signal hävdas.

Inga format markeras PASS enbart från metadata. Ingen automatisk omkörning eller fix.
Full inventering behöver behörigt tillgänglig metadata eller manuell provfilnavigering.
USER VERIFIED-observationer behövs för nya tests färger/HDR/AVR samt Kodi-jämförelse.

## Git

Endast denna evidenskatalog skapas. Ingen push, tag eller deployment. Lokal evidenscommit skapas efter granskning av dessa resultat; tidigare 190 dirty-poster kontrolleras oförändrade efter commit.

## Acceptance gates and remaining work

| Gate | Status |
| --- | --- |
| Device/APK baseline | HOST VERIFIED hash, DEVICE VERIFIED identity/version |
| Matrix created, observed sample inventory | HOST VERIFIED documents; DEVICE VERIFIED partial UI inventory |
| Representative execution/decoders/audio config | DEVICE VERIFIED M02–M06 playback and M07 bounded startup failure |
| Five-minute stability | DEVICE VERIFIED M03 selected converted delivery; Remux DirectPlay NOT VERIFIED |
| Refresh before/during/after | DEVICE VERIFIED mode49 unchanged on new tests; switching/restoration after an actual change NOT VERIFIED |
| Physical HDMI | USER VERIFIED M01 only; M02–M07 physical format indications NOT VERIFIED |
| Kodi same-file/same-track | NOT VERIFIED / NOT_TESTED; empty source list requires user action |
| Full MEL/FEL, HDR10+, DTS:X and fps coverage | NOT VERIFIED / NOT_TESTED; qualified samples needed |

Remaining actions: supply authorized technical inventory/qualified samples, physical
TV/AVR observations for new tests, and manual same-file/same-track Kodi access.
M07 needs read-only server negotiation/transcode evidence before any repeat is justified.
No code fix, decoder override, display change or server configuration change is proposed
from these incomplete observations. PROFILE_MATRIX_PASS is not claimed.
