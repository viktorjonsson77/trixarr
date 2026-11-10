# TRIXARR_SHIELD_DV_DIAGNOSTIC_BLOCKED

Datum: 2026-11-10. Kontrollerad fysisk förkontroll via enbart `adb -s 100.91.172.18:5555`.
Installation och uppspelningssekvens stoppade vid befintlig installation; inget DIAGNOSTIC_PASS.

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

## Existing installation / installation gate — BLOCKED

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

Paket, signer och versionskod matchar testartefakten för debugappen, men APK-hashen skiljer sig.
En uppdatering skulle ersätta befintlig appkod med samma versionskod. Databas-/inställningskompatibilitet
med den befintliga installationen är inte belagd. Enligt uppdragets avsnitt 4 stoppas
installationen före påverkan på befintlig installation, i väntan på uttryckligt godkännande.
Ingen install, uninstall, pm clear, reset, launch, nätverksändring eller serverändring utförd.

## Playback, decoder, audio and Kodi — BLOCKED / NOT VERIFIED

| Bevisområde | Resultat |
| --- | --- |
| Trixarr launch / respons / fjärrnavigation | BLOCKED av installationsgrinden |
| Jellyfin auth / bibliotek | NOT VERIFIED; inga credentials lästa |
| Diagnostikvy på Shield | NOT VERIFIED; källkoppling finns |
| Exakt medieobjekt | BLOCKED: titel, item-ID och källversion saknas |
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
App launch, playback start, decoder init, first frame, playback errors och playback stop:
NOT APPLICABLE för denna förkontroll eftersom sekvensen aldrig startade.
Relevanta playback-logcat-loggar är BLOCKED, inte fångade. Ingen fullständig logcat-dump
eller logcat-rensning utförd. De sparade observationerna är begränsade till tekniska
identitets-/kapabilitetsuppgifter och manifest; inga credentials eller känsliga URL:er.

## Root cause hypothesis and remaining tests

Orsak är NOT VERIFIED. Befintlig baslinje anger hypoteserna DV-profil-/baslagerhantering,
förlorad DV-konfiguration vid demux/remux, annan serverleverans och displaykedjans signalering.
Inga nya fysiska playbackdata finns som rangordnar dessa. Deklarerad DV-decoder och HDR-stöd
visar endast att DV annonseras. De bevisar inte att DV används eller att HEVC valts fel.

1. Användaren behöver godkänna konkret uppdatering av befintligt debugpaket utan avinstallation/data clear.
2. Användaren behöver identifiera exakt felande film/version, helst Jellyfin item-ID.
3. Efter godkänd installation: launch, befintlig inloggning, bibliotek, D-pad och diagnostikvy.
   Saknad inloggning kräver användaråtgärd utan att credentials loggas.
4. Kontrollerad uppspelning: källmetadata → leveransmetod → decoder MIME/CSD/format → player state,
   inklusive tidsstämplade sanerade loggar, bildfel, dropped frames och underruns.
5. Ljudspår/AudioTrack/passthrough samt faktisk TV-DV/HDR och Yamaha-ingångsindikering.
6. Samma källversion/spår i Kodi utan konfigurationsändring; användarnavigering vid behov.
7. Refresh före/under/efter och kontrollerat stopp; inga HDMI-slutsatser från hosttester.

## Git

Endast detta uppdrags evidenskatalog tillkommer. Ingen spelarmotor eller annan kod ändrad.
Ingen push, tag eller deployment. Slutlig lokal evidenscommit får bara innehålla denna katalog;
befintlig smutsig arbetskatalog ska förbli oförändrad. Rapporten är färdig för den blockerade
förkontrollens omfattning; den utgör inte färdig uppspelningsacceptans.
