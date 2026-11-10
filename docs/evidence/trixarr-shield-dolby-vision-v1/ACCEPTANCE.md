# TRIXARR_SHIELD_DV_DIAGNOSTIC_PARTIAL

Datum: 2026-11-10. Kontrollerad fysisk förkontroll via enbart `adb -s 100.91.172.18:5555`.
Godkänd debuguppdatering, appstart, inloggning, bibliotek, fysisk Shield-uppspelning,
diagnostikvy och decoderloggar är VERIFIED. Kärnkraven för DIAGNOSTIC_PASS i avsnitt 15
är uppfyllda. Uppdragets samlade status är PARTIAL eftersom faktisk TV/AVR-output och
samma-fil-jämförelse i Kodi återstår; ingen Dolby Vision-fix eller slutlig rotorsak hävdas.

## Baslinje och bevisnivå

Gren `feat/player-engine-phase-2-completion`, HEAD `f671adb9ee818c0e9b2e7f9496b2e44897770ca7`.
Omfattande befintliga tracked/untracked branding-, katalog-, startup-, resurs- och dokumentationsändringar bevarade.
Ingen AGENTS.md finns i repositoryt eller `/opt` och `/`.
Alla sex begärda arkitektur-/acceptansdokument lästa. Implementationen granskad via
Media3PlayerEngine, VideoManager, PlaybackDiagnostics och controllerkopplingar.
Gemensamt core-kontrakt, sessionsägare och ordnad rapportering finns; endast Media3
registreras i standardvägen. Diagnostikdialogen nås via INFO/MENU för VOD.
Capability-policy begränsar obekräftad DV→HEVC-fallback. Detta är källgranskning,
inte en verifiering av fysisk uppspelning, nätverksrapportering eller HDMI.
Inga hosttester körda eller räknade som hårdvarubevis.

## Device identity — VERIFIED

- ADB: `device`. Modell SHIELD Android TV, tillverkare NVIDIA, produkt/enhet mdarcy.
- Android 11, SDK 30, primär ABI arm64-v8a; även armeabi-v7a/armeabi.
- Fingerprint: `NVIDIA/mdarcy/mdarcy:11/RQ1A.210105.003/7825230_4387.0822:user/release-keys`.
- Identiteten överensstämmer med angiven Shield TV Pro.
- Android rapporterar aktivt mode 49: 3840×2160, 59.94006 Hz.
- Tillgängliga lägen inkluderar 2160p 23.976/24/25/29.970/30/50/59.940/60 Hz.
- Displayens annonserade HDR-typer: [1, 2] (Dolby Vision, HDR10).
- Vendor XML deklarerar OMX.Nvidia.DOVI.decode samt secure-variant för video/dolby-vision,
  och OMX.Nvidia.h265.decode samt secure-variant för video/hevc.
- `dumpsys media.codec` svarar `Can't find service: media.codec`. XML är deklarationer;
  full runtime MediaCodecList/profil-/level-enumerering är NOT VERIFIED.
- Yamaha RX-V6A → TCL är användaruppgift. Fysisk koppling och faktisk DV-signal: NOT VERIFIED.

Se [sanerade enhetsobservationer](device-observations.txt). Inga konton, tokens eller privata medie-URL:er samlades in.

## APK identity — VERIFIED

Artefakt: `/opt/jellyfintv-artifacts/player-engine-phase-2-completion/arrstreamer-player-engine-phase-2-completion-debug.apk`.

- SHA-256: `1e7eeb00ecc832f39b4542fd81251f59e91e03967bce612ff657e7ae91922526` — matchar.
- Paket: `se.viktorjonsson.jellyfintv.debug`.
- Version 0.0.0-dev.1, versionCode 1, minSdk 21, targetSdk 36.
- Signer SHA-256: `f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c`.
- apksigner verifierar v1/v2, en Android Debug-signer. Verktygets JAR/META-INF-varningar
  om osignerade metadata kvarstår; v2-verifieringen passerar.
- Universal APK med arm64-v8a, armeabi-v7a, x86, x86_64. ARM64 är inkluderad, inte exklusiv ABI.
- ARM64 libffmpegJNI.so och libandroidx.graphics.path.so: readelf ELF64/AArch64.
- Manifest granskat: ArrStreamer Debug, Leanback StartupActivity, INTERNET, nätverk/Wi-Fi,
  mikrofon, foreground service/media playback, wake lock, boot, TV EPG och intern receiver-permission.
  Touchscreen är inte obligatorisk. Ingen ombyggnad utförd.

Se [signatur och manifest](apk-verification.txt).

## Existing installation / installation gate — VERIFIED after approval

| Paket | Version (code) | Första installation | Senaste uppdatering | Appdata |
| --- | --- | --- | --- | --- |
| se.viktorjonsson.jellyfintv.debug | 0.0.0-dev.1 (1) | 2026-09-02 23:52:47 | 2026-09-28 23:17:53 | VERIFIED: CE inode, shared_prefs och databases finns |
| org.jellyfin.androidtv | 0.19.10 (191099) | 2026-01-28 21:49:04 | 2026-09-16 22:49:37 | VERIFIED: registrerat CE inode |
| org.xbmc.kodi | 21.2 (2102000) | 2026-11-04 22:41:13 | 2026-11-04 22:41:13 | VERIFIED: registrerat CE inode |

Tiderna är PackageManagers rapporterade strängar; tidszon ej verifierad.
Inget separat Trixarr-/ArrStreamer-namngivet paket hittades i paketnamnsökningen;
debugpaketet ovan är det uttryckliga målet. Även dess instrumenteringspaket finns installerat och lämnades orört.
Appdata för Jellyfin/Kodi konstaterades via PackageManager, innehållet lästes inte.

Installerade base.apk hämtades skrivskyddat till tillfälliga hostfiler och signaturverifierades:

| Paket | APK SHA-256 | Signer SHA-256 |
| --- | --- | --- |
| Debug | 5054a9ae447855a2fa98954eaafa9b656a24e191a6dc867fdf3803cc173c8d9d | f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c |
| Jellyfin | dbc7a0ea8d757815e9619fac5e128391e37d501083f7c8b0ee5f25a5a33eee0c | 2eca5acd52d4f718a178361a3489cd1322392b2ca62e8d72a3c4027af94a3d8c |
| Kodi | e4c84bf738cac846ec4b4c6b58cc4a3ee669320d3061b6e80cf14fa61bc2c465 | f517b44b5db5e62a6c1ec55ba47526db7de0d61f6ba26a7987520e293499b8d5 |

Paket, signer och versionskod matchade testartefakten; den tidigare APK-hashen skilde sig.
Installationen pausades först enligt avsnitt 4. Användaren gav sedan uttryckligt godkännande
("ja, jag godkänner det") till den föreslagna uppdateringen med bevarade data.

### Approved physical installation and launch

- Device identity och artefaktens SHA-256 kontrollerades igen före installation.
- `adb -s 100.91.172.18:5555 install -r <verified artifact>`: `Performing Streamed Install`, `Success`.
- Ingen uninstall, pm clear, reset, nätverksändring eller serverkonfigurationsändring.
- Installerad base.apk lästes tillbaka och SHA-256 matchar exakt den godkända artefakten.
- Efter installation: version 0.0.0-dev.1 (1), firstInstallTime fortsatt 2026-09-02 23:52:47,
  lastUpdateTime 2026-11-10 19:31:00 (PackageManager lokal tid; enhetsklockan motsvarar UTC+1).
- CE inode fortsatt 652903; shared_prefs och databases finns kvar. Detta verifierar bevarad
  datakatalog, inte att varje databaspost eller tidigare autentisering är användbar.
- Appstart begärd vid enhets-UTC 18:31:09: `am start -W` gav Status ok, WARM,
  TotalTime 2221 ms, WaitTime 2243 ms. Process 18144 observerades.
- Skärmbilden visade kontoväljaren "Vem tittar?" och befintligt konto/server.
- Under navigering observerades Androids RecentsTvActivity i förgrunden. Orsak okänd;
  inget verifierat appkraschbevis. Appen togs tillbaka med samma explicita aktivitet,
  Status ok, COLD, TotalTime 2839 ms, WaitTime 2840 ms.
- DPAD_CENTER (23) från kontoväljaren gav login/Quick Connect-vyn, observerad senast
  vid enhets-UTC 18:32:04. UI svarar på ADB-injicerad fjärrknapp; fysisk fjärrkontroll NOT VERIFIED.
- **Autentiserad Jellyfin-anslutning BLOCKED:** appen visar "Vänligen logga in".
  Uppdragets avsnitt 7 kräver stopp och användarinloggning. Inga credentials hämtade,
  inga inloggningskoder sparade i evidensen och inga automatiska autentiseringsförsök gjorda.
- Skärmbilder granskades tillfälligt på hosten men sparas inte i repositoryt eftersom
  de innehåller server-/konto-/Quick Connect-uppgifter.

### Login gate resolved by user

Användaren meddelade att inloggningen slutförts. Vid enhets-UTC 2026-11-10 18:34:00:
ADB `device`, SHIELD Android TV/mdarcy, målappens MainActivity i förgrunden och process
18558. Visuell kontroll visar startsidan med bibliotekskort och innehållsmetadata.
Autentiserad UI-/biblioteksåtkomst är VERIFIED; ingen credentials-inspektion eller
serverändring gjord. Kontoväljarens tidigare login-blockerare är därmed löst.
Detta var observationen före playback. Användaren angav sedan "Resident evil" och bekräftade att endast en sådan titel finns i biblioteket; objektet identifierades därefter enligt nedan.
Den tillfälliga UI-bilden sparas inte i evidensen eftersom den visar personligt bibliotek.

## Controlled playback — VERIFIED

Användaren identifierade bibliotekets enda "Resident Evil" som mål. Sökningen visade
ett spelbart bibliotekskort och andra katalogträffar med "Begär". Den spelbara postens
detaljsida kontrollerades före Play: Resident Evil, 93 minuter, angivet premiärdatum
2026-09-16, tillgänglig. Ingen versionsväljare observerades. En oavsiktligt öppnad
"Welcome to Raccoon City"-katalogträff var ej tillgänglig och lämnades utan begäran/uppspelning.
Bakgrundsbilden för målposten tycks höra till en annan del i serien; metadata kan vara
felmatchad. Årtal/artwork används därför inte som bevis för filmfilens riktiga identitet.

Efter start extraherades endast item-ID från målprocessens playback-URL i minnet:
`dae676fc-22d2-a272-784e-30b9ff521ab8`. Hela URL:en sparades/visades inte.
Källfilhash, MediaSourceId och exakt filnamn är NOT VERIFIED. Vid Kodi-jämförelse måste
källfilen matchas; titellikhet ensam räcker inte.

| Bevisnivå | Observation |
| --- | --- |
| SOURCE METADATA | Jellyfin: MKV, HEVC Main 10, 3832×1600, 23.976025 fps, 24,197,784 bit/s, 10 bit, DOVI profil 5 / level 6 |
| SOURCE COLOR | Primärer, transfer och matris UNKNOWN i Jellyfin och Media3 ColorInfo UNKNOWN |
| HDR10 compatibility | Policy UNSUPPORTED för P5; ingen generisk HEVC/HDR10-baslagerfallback tillåten |
| DEVICE CAPABILITY | Policy ALLOWED; DV/HEVC-decoder och annonserat display-HDR-stöd SUPPORTED; runtime MIME-lista innehåller video/dolby-vision och video/hevc |
| DELIVERY | Dialog: DirectPlay över HTTP; slutlogg Play method: Direct. Tidig logg "Internal player would direct stream" är en preliminär bedömning, inte bevis för slutlig remux. Ingen servertranscode eller motorfallback observerad |
| SELECTED DECODER | OMX.Nvidia.DOVI.decode, Android hardware acceleration=true |
| DECODER INPUT | video/dolby-vision, dvhe.05.06, profil/level (5,6), 3832×1600 |
| ACTUAL PLAYER STATE | PLAYING → READY / PAUSED → kontrollerat stopp/release |
| MEDIA3 | Jellyfin Media3, AndroidX Media3 1.8.0 bekräftad både i dialog och Init/Release-logg |
| OUTPUT MEDIAFORMAT | video/raw, 3832×1600, color-standard=1, color-transfer=3, color-range=1, profil/level UNKNOWN |
| HDR metadata | MediaCodec output: hdr-static-info finns, hdr10-plus-info finns inte. Rå metadata/CSD/RPU ej fångade |
| HDMI OUTPUT | NOT VERIFIED; varken output-MIME, DV-codecval eller displayens stödlista bevisar TV:ns aktiva signal |
| DISPLAY MODE | Före/under test: Android mode 49, 3840×2160, 59.94006 Hz; ingen refreshväxling observerad |

[Första diagnostiksnapshot](playback-diagnostics.txt) fångades ur dialogens UI-text.
[Snapshot i paus](playback-diagnostics-paused.txt) fångades efter cirka 116 sekunder.
Dialogen öppnades med INFO (165), stängdes med Back och blockerade inte playback.
ADB DPAD-navigation, sökinmatning, Play, Pause och Stop fungerade. Fysisk fjärrkontroll
och fysisk TV-läsbarhet är NOT VERIFIED. En senare UI-dump under rörlig uppspelning
misslyckades med `could not get idle state`; inget resultat härleddes från den.

Första snapshot: engine position 6951 ms, session position 6642 ms, PLAYING,
PROGRESS_SENT, buffered 58780 ms, 50 dropped frames. Paussnapshot: engine 115759 ms,
session 115751 ms, PAUSED, buffered 37552 ms, 61 dropped frames. Detta är kumulativa
räknare, inte en mätning av tappade frames per sekund. Inga namngivna playback-/codec-/
audiosink-fel registrerades i snapshoten; felraderna stod UNKNOWN. Underrun-antal och
felfri långvarig uppspelning är NOT VERIFIED. Ingen automatiserad seek gjord.

ADB-skärmbilden från filmens videoyta såg rosa/lila ut. Detta är en observation av
screen capture, inte verifierat färgfel på HDMI/TV. Screen capture kan återge DV-färg
annorlunda än TV-utmatningen; Användaren återkopplade efter testet "Det ser bra ut". Detta dokumenteras som
**användarverifierad korrekt synlig bild på TV:n** för denna körning. Den rosa/lila
ADB-skärmbilden reproducerar därmed inte det påstådda TV-färgfelet. TV:ns uttryckliga
DV-indikering och Yamahas inkommande ljudformat är fortfarande NOT VERIFIED.
Ingen filmframe sparas i repositoryt. SurfaceUtils-loggen visar nativeWindow 3832×1600,
rotation 0, usage 0x2900 och färgformat 0x106 → 0x12b. Dessa vendorvärden tolkas inte
som bevis för DV-utmatning eller ett specifikt fel.

### Audio — configured state VERIFIED; HDMI NOT VERIFIED

Källans audio #1: EAC3, profilsträng "Dolby Digital Plus + Dolby Atmos", 768000 bit/s,
6 kanaler/5.1, 48000 Hz. Vald Media3-track id 2: audio/eac3, 6 kanaler, 48000 Hz.
Media3 track-id och Jellyfin-index är olika namnrymder; ingen gissad direkt indexlikhet.
AudioTrack encoding 6 (EAC3), channel mask 252, 48000 Hz, buffer 192000 bytes,
offload=false. Encoded AudioTrack vald; passthrough requested UNKNOWN (automatic policy).
Ljuddecoder UNKNOWN, vilket inte bevisar ett fel vid encoded output. AudioTrack-värdena
bevisar inte AVR-mottagning eller Atmos. Yamaha-ingångsformat/Atmos/DTS:X NOT VERIFIED.

### Kodi comparison — BLOCKED pending manual navigation

Kodi 21.2 är installerad. Ingen Kodi-konfiguration ändrad eller privat bibliotek läst.
Användaren har ombetts starta exakt samma fil i Kodi efter Trixarr-stoppet. Kodi playback
method, decoder, DV-hantering, HDR-output, refresh och audio är tills dess NOT VERIFIED.
Kodis interna decoderbeslut härleds inte från Trixarrs eller generella Android-loggar.

## Sanitized logs and event times

[Codec-/Surface-loggar](playback-logcat.txt), [playback/stopp-loggar](playback-stop-logcat.txt)
och [install/startobservationer](install-launch-observations.txt) är begränsade till
målprocess och relevanta tags. URL-/token-/credential-/privata sökvägsrader filtrerades
före lagring; inga fullständiga logcat-dumpar eller logcat-rensningar gjordes.
Android-loggarnas lokaltid motsvarar UTC+1 under körningen.

| Händelse | Tid / bevis |
| --- | --- |
| App launch | Enhets-UTC 18:31:09, am start Status ok; tidigare avsnitt |
| Playback Play begärd | Enhets-UTC 18:37:27 via DPAD_CENTER på Spela upp |
| ExoPlayer Init | Lokal 19:37:28.735 (UTC 18:37:28.735) |
| Buffering | Lokal 19:37:29.224 |
| Decoder / Surface init | Lokal 19:37:31.081 OMX.Nvidia.DOVI.decode |
| DV configuration | Lokal 19:37:31.115: Setting Dolby Vision Specific attributes to Video Decoder |
| First frame | NOT VERIFIED exakt eventtid; outputformat/PLAYING-snapshot och senare videobild observerade |
| Pause begärd | Enhets-UTC 18:39:27 via MEDIA_PAUSE; snapshot PAUSED |
| Playback error | Ingen namngiven händelse i insamlade snapshots/loggar; frånvaro bevisar inte fullständig felfrihet |
| Stop | Lokal 19:39:46.815: stop called at 115751 |
| Release | Lokal 19:39:46.848: ExoPlayer Release |

Efter MEDIA_STOP återgick UI till samma films detaljsida. Stoppets fjärr-HTTP-mottagning
på Jellyfin är NOT VERIFIED; PROGRESS_SENT avser klientens rapporteringstillstånd.
MainActivity observerades även under playback eftersom spelaren ligger i aktivitetens
fragment. Ett tidigt antagande att aktivitetsnamnet betydde stopp korrigerades genom
skärmkontroll och loggar; ingen sådan stopp-/kraschhändelse hävdas.

## Root cause hypothesis and remaining tests

**Verifierad avgränsning:** den nya debugkörningen använder DV MIME, dvhe.05.06 och
NVIDIAs DV-decoder med uttrycklig DV-konfiguration. Hypotesen "P5 behandlas som vanlig
HEVC" stöds alltså inte för denna körning. Källans DV-profil finns kvar i decoder-input.
Det bevisar inte att den äldre Jellyfin-installationen valde samma decoder eller att
DV-konfiguration/RPU och HDMI-signalering blev korrekta hela vägen.

**Öppna hypoteser:** skillnader i äldre klientens demux/codecval, saknad eller annorlunda
DV-konfiguration/CSD/RPU, vendor-output/Surface-färghantering och DV-signalering i
Shield→Yamaha→TCL. Output-färgfälten 1/3/1 är en kontrollpunkt men inte ensamma diagnos
av förlorad DV; DV-vendorpipeline kan ha annan semantik. Ingen kodändring föreslås utan
jämförelse. Dolby Vision är inte rapporterat fixat.

1. Korrekt synlig bild är bekräftad av användaren. TCL:s uttryckliga Dolby Vision-indikering och inkommande ljudindikering på Yamaha återstår.
2. Samma fil/spår i Kodi: kontrollera källidentitet och samla uttrycklig decoder-/outputdiagnostik.
3. Vid behov äldre Jellyfin-klient med samma item, fil, spår och leveransmetod; ingen automatisk ominstallation.
4. CSD/DV configuration record/RPU, raw HDR-metadata och exakt first-frame-tid saknas i befintlig instrumentering.
5. Längre bitrate-/frame-drop-/underrun-test, seek/resume, fysisk D-pad och refresh före/under/efter.
6. Inga hosttester räknas som fysisk HDMI-verifiering. Ingen policy-/server-/Kodi-/nätverksändring gjord.

## Git

Endast detta uppdrags evidenskatalog tillkommer. Ingen spelarmotor eller annan kod ändrad.
Ingen push eller tag; enda deployment är uttryckligt godkänd debug-APK på rätt Shield. Lokal evidenscommit får bara innehålla denna katalog;
befintlig smutsig arbetskatalog ska förbli oförändrad. Rapporten dokumenterar genomfört test och kvarvarande fysiska jämförelser; den utgör inte bevis för korrekt DV-/Atmos-utmatning.
