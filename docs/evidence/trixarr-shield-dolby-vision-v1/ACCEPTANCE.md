# TRIXARR_SHIELD_DV_DIAGNOSTIC_BLOCKED

Datum: 2026-11-10. Kontrollerad fysisk förkontroll via enbart `adb -s 100.91.172.18:5555`.
Uppdatering godkänd av användaren och installerad. Appstart VERIFIED; inloggning och bibliotek VERIFIED efter användaråtgärd; uppspelning BLOCKED av ännu inte entydigt identifierad film/version. Inget DIAGNOSTIC_PASS.

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
Ingen media startad. Användaren anger "Resident evil"; exakt film/år/version återstår.
Den tillfälliga UI-bilden sparas inte i evidensen eftersom den visar personligt bibliotek.

## Playback, decoder, audio and Kodi — BLOCKED / NOT VERIFIED

| Bevisområde | Resultat |
| --- | --- |
| Trixarr launch / respons / fjärrnavigation | VERIFIED start och svar på ADB DPAD_CENTER; fysisk fjärrkontroll NOT VERIFIED |
| Jellyfin auth / bibliotek | VERIFIED efter användarinloggning: MainActivity visar startsida och bibliotekskort |
| Diagnostikvy på Shield | NOT VERIFIED; källkoppling finns |
| Exakt medieobjekt | BLOCKED: användaren anger Resident evil; exakt film/år, item-ID och källversion ännu ej fastställda |
| SOURCE METADATA | NOT VERIFIED: container, codec, DV profile/level, compatibility-ID/HDR10-baslager, primärer, transfer, bitdjup och ljudcodec |
| DEVICE CAPABILITY | VERIFIED enhets-/display-/vendor-deklarationer enligt ovan; runtime codec-profiler NOT VERIFIED |
| SELECTED DECODER | NOT VERIFIED; ingen decoder initierad i testet |
| ACTUAL PLAYER STATE | NOT VERIFIED; ingen uppspelning startad |
| HDMI OUTPUT | NOT VERIFIED; displaykapabilitet bevisar inte aktiv DV/HDR |
| Media3 version | 1.8.0 enligt granskad baslinje; aktiv runtime-version NOT VERIFIED |
| MediaFormat, DV-konfiguration, CSD, HDR-metadata, Surface | NOT VERIFIED |
| Decoderfel, dropped frames, underruns | NOT VERIFIED; inget genomfört test |
| Playback method, valda ljud-/textspår | NOT VERIFIED |
| Audio decoder, passthrough requested/selected, encoding, kanaler, AudioTrack/fel | NOT VERIFIED |
| Yamaha Atmos/DTS:X-indikering | NOT VERIFIED; separat fysisk observation krävs |
| Kodi | VERIFIED installerad 21.2; jämförande uppspelning BLOCKED |

Ingen automatisk codec-override eller ändring av decoder-/Kodi-policy gjord.
Kodi loggar, konfiguration och privata bibliotek lästes inte. Inga interna Kodi-beslut härleds.

## Logs and timestamps

Enhetsklockan avlästes till `Tue Nov 10 18:24:15 UTC 2026` under förkontrollen.
[device-observations.txt](device-observations.txt) har hostens UTC-insamlingstid.
App launch är VERIFIED enligt tiderna ovan. Playback start, decoder init, first frame,
playback errors och playback stop: NOT APPLICABLE för körningen eftersom ingen media startades.
Se [installations-/startobservationer](install-launch-observations.txt) för sanerad kontroll
av AndroidRuntime:E efter start: inga FATAL EXCEPTION-markörer eller målpaketomnämnanden.
Detta utesluter inte nativefel eller andra orsaker till den observerade aktivitetsväxlingen.
En första logcat-fråga avvisades på grund av tidsargumentets quoting; endast den korrigerade
frågans lyckade resultat används. Relevanta playback-logcat-loggar är BLOCKED, inte fångade. Ingen fullständig logcat-dump
eller logcat-rensning utförd. De sparade observationerna är begränsade till tekniska
identitets-/kapabilitetsuppgifter och manifest; inga credentials eller känsliga URL:er.

## Root cause hypothesis and remaining tests

Orsak är NOT VERIFIED. Befintlig baslinje anger hypoteserna DV-profil-/baslagerhantering,
förlorad DV-konfiguration vid demux/remux, annan serverleverans och displaykedjans signalering.
Inga nya fysiska playbackdata finns som rangordnar dessa. Deklarerad DV-decoder och HDR-stöd
visar endast att DV annonseras. De bevisar inte att DV används eller att HEVC valts fel.

1. Inloggningen är slutförd av användaren och verifierad i UI. Den godkända uppdateringen är genomförd; ingen ytterligare installation behövs.
2. Användaren har angett Resident evil; exakt film/år och källversion behöver bekräftas, helst genom vald detaljsida eller Jellyfin item-ID.
3. Efter användarinloggning: bibliotek, fysisk D-pad och diagnostikvy.
   Saknad inloggning kräver användaråtgärd utan att credentials loggas.
4. Kontrollerad uppspelning: källmetadata → leveransmetod → decoder MIME/CSD/format → player state,
   inklusive tidsstämplade sanerade loggar, bildfel, dropped frames och underruns.
5. Ljudspår/AudioTrack/passthrough samt faktisk TV-DV/HDR och Yamaha-ingångsindikering.
6. Samma källversion/spår i Kodi utan konfigurationsändring; användarnavigering vid behov.
7. Refresh före/under/efter och kontrollerat stopp; inga HDMI-slutsatser från hosttester.

## Git

Endast detta uppdrags evidenskatalog tillkommer. Ingen spelarmotor eller annan kod ändrad.
Ingen push eller tag; enda deployment är uttryckligt godkänd debug-APK på rätt Shield. Lokal evidenscommit får bara innehålla denna katalog;
befintlig smutsig arbetskatalog ska förbli oförändrad. Rapporten är färdig för den blockerade
förkontrollens omfattning; den utgör inte färdig uppspelningsacceptans.
