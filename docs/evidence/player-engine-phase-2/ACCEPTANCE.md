# ARRSTREAMER_PLAYER_ENGINE_PHASE_2_PARTIAL

Datum: 2026-11-06. Gren: `feat/player-engine-phase-2`. Ingen push, tagg, deployment eller installation på fysisk enhet.

## Implementationsstatus

| Krav | Resultat |
| --- | --- |
| PlayerEngine, standardadapter och livscykel | Implementerat och integrerat i ordinarie video; Media3 fortsatt enda registrerade/standardmotor. |
| Motorval/init-fel/resurser | En aktiv motor, fabrik/init via manager, ändlig kandidatlista, release före fallback, cleanup-fel blockerar fortsatt användning. |
| Säker fallback | Implementerad för fabrik/synkron initial prepare, testad med syntetiska motorer. Aktiv session roteras aldrig. Befintlig serverretry kvarstår. |
| Kapabilitetskontroll | **PARTIAL:** manager kontrollerar motorns supports-kontrakt; Media3-adaptern kontrollerar att request har lokal konfiguration. Codec/profil/container-policy ligger kvar i Jellyfin DeviceProfile och Media3 track-support; ingen fullständig motor-specifik klassificering före prepare. |
| DV/HDR/audio | Eventbaserad diagnostik med separat server/ström/Android/valt format; TV/HDMI alltid NOT VERIFIED. |
| Diagnostikvy | Implementerad i sekundära spelaråtgärder + INFO/MENU för VOD. Snapshot testad; fjärrkontrollflöde under verklig videouppspelning återstår. |
| Sessionsrapportering/resume | Generation-gate och synkront init-fel skyddar prepare/start/stop. **PARTIAL verifiering:** syntetiska tester passerar; controller + Jellyfin-förhandling + transcode-offset är inte end-to-end-testade. |
| Kodi-jämförelse | Officiell 21.2-Omega-källa analyserad och länkad i arkitekturdokumentet. Ingen Kodi-kod importerad. |
| Build/APK | PASS, debug, ARM64-paketering verifierad. |
| Dokumentation | Klar för den levererade omfattningen; kvarvarande krav explicit listade. |

**Varför inte PHASE_2_PASS:** full kapabilitetspolicy per motor och sammanhängande regression för controller/sessionsrapportering/resume saknas. Hårdvarubevis saknas dessutom, men ingen DV-fix eller Shield-verifiering hävdas. Den experimentella rewrite-vägen är inte migrerad; förstudieförslaget att samla kontraktet i playback/core återstår. Detta är en fungerande delimplementation, inte en färdig native-port eller en färdig gemensam backend för båda UI-vägarna.

## Tester och build

Slutlig körning: **BUILD SUCCESSFUL**, 33 JVM-tester, 0 fel; 2 instrumenteringstester, 0 fel. [Full Gradle-logg](validation.log), [JUnit-resultat](tests/).

```bash
ANDROID_SERIAL=10.51.0.31:5555 ./gradlew \
  -I docs/evidence/player-engine-phase-2/focused-tests.gradle \
  :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.jellyfin.androidtv.playback.PlayerEngineInstrumentedTest \
  --console=plain
```

Android-enheten är **redroid 15, x86_64, API 35**, en virtuell labbenhet. Endast denna enhet fick test-APK. Inga konton eller mediefiler krävs. Testerna verifierar verklig ExoPlayer-instans, paus/seek/stopp/release, text av/ogiltigt spår, diagnostiksnapshot och laddning av paketerad FFmpeg-extension. De verifierar inte ARM64-runtime, HDMI, Dolby Vision eller fjärrkontroll under uppspelning.

JVM: 21 nya tester (15 engine, 3 diagnostik, 3 rapporteringsgate) plus 12 befintliga playback-regressioner (8 speed, 3 seek, 1 queue).

| Testkrav | Evidens och gräns |
| --- | --- |
| A lifecycle | Fake-engine kommandon + Android ExoPlayer lifecycle. |
| B selection | Första stödjande kandidat; endast en factory används när första lyckas. |
| C init failure | Factory-/prepare-fel, ändlig kandidatlista, ingen oändlig retry. |
| D safe fallback | Release sker innan nästa kandidat skapas; cleanup-fel förbjuder fallback. |
| E cleanup | Idempotent release, även efter initialize före prepare. |
| F continuity | Samma motor vid nytt prepare och play/pause/seek. |
| G resume | Samma millisekundsposition till fallback; pausad seek bevaras. Controller/transcode-offset återstår. |
| H reporting | Gate tillåter en start/stop, ingen stop för aldrig startat försök. Verklig HTTP-ordning återstår. |
| I DV | Explicit P7/P8 codec-sträng; HEVC/null/felaktig sträng ger ingen gissad profil. |
| J HDR | DV/PQ/HLG/statisk metadata och okänd HDR10+ klassificeras försiktigt. |
| K missing | UNKNOWN för saknad metadata och Media3 NO_VALUE. |
| L audio | PCM kontra encoded AudioTrack; HDMI/Atmos/DTS:X förblir NOT VERIFIED. |
| M unsupported | Ej stödd kandidat frigörs; Android ogiltig spårgrupp nekas. Full motorcodec-policy återstår. |
| N decoder failure | Sanerad decoder-/sink-/playback-felkategori utan privata exception-meddelanden. |
| O diagnostic view state | Snapshot skiljer källmetadata från valt format och döljer URL/token. Ingen verklig fjärrkontroll-/video-E2E. |
| P no duplicates | Dubbla och gamla callbacks kan inte claima prepare/start igen; stop ogiltigförklarar fördröjd start. Server-resursstädning vid sena nätverkssvar återstår. |

Utvecklingsfel som rättats: en unreachable Java-return, syntetiska SDK-fixtures som behövde mocks och Kotest/JVM-target-kompatibla exception-assertions. Gradles `--tests` gav ett Kotest descriptor-discovery-fel; miljövariabelfiltret gav en bredare appkörning än avsett. Slutkörningen använder explicit init-script och omfattar bara sex playback-specifikationer. Inga fullständiga projekt-/release-testsviter kördes. Kvarvarande byggvarningar: befintlig Java 8-target på JDK 21, Gradle-deprecations och bakåtkompatibla Display-API:er.

## APK

- Stabil artefakt: `/opt/jellyfintv-artifacts/player-engine-phase-2/arrstreamer-player-engine-phase-2-debug.apk`
- Gradle-output: `/opt/jellyfintv/app/build/outputs/apk/debug/jellyfin-tv-v0.0.0-dev.1-debug.apk`
- SHA-256: `a95f4b43d86c1a8f12ab25e31efa17a2058c701d6e176aaebdd99c2572f13a88`
- Package: `se.viktorjonsson.jellyfintv.debug`
- Version: `0.0.0-dev.1`, versionCode `1`; min SDK 21, target SDK 36.
- `apksigner verify`: PASS, Android Debug-certifikat. Release-signering oförändrad; ingen ny produktionsnyckel.
- Universal APK: arm64-v8a, armeabi-v7a, x86 och x86_64. [APK-metadata](apk-info.txt).
- ARM64: `libffmpegJNI.so`, `libandroidx.graphics.path.so`; ELF64/AArch64. [ELF/DT_NEEDED](native-packaging.txt).
- DT_NEEDED består av Android-systembibliotek: libc, libm, libdl och för FFmpeg även libandroid/liblog. Inga saknade paketerade native-beroenden identifierade statiskt. Gradles dependency/duplicate-class-kontroller passerade; FFmpeg laddades på x86_64-labbenheten. Full ARM64-runtime kan bara verifieras på relevant Android-enhet.
- APK innehåller även användarens tidigare lokala branding-/UI-ändringar. Endast fas 2-filer ingår i de nya commitsen; APK kan inte reproduceras från dessa commits ensamma utan den bevarade lokala basen.

## Kvarvarande verifiering och begränsningar

1. Bygg motor-specifik supportbedömning med Supported/Unsupported/Unknown för faktiskt codec/container/DV-profil och vald ljud-/textleverans, kopplad till serverprofilen.
2. Lägg till sammanhängande controller-fixture med kontrollerad PlaybackInfo/rapportering: nätverksfel, sena svar, resume/transcode-offset, spårbyte och avbruten förberedelse. Gate-testerna bevisar bara lokalt callback-/rapportägarskap.
3. Verifiera UI med D-pad under syntetisk uppspelning; inga screenshots eller riktiga videoplayback-test kördes här.
4. På fysisk Shield: DV P5/P7 MEL/P7 FEL/P8, HDR10/HDR10+/HLG, 4K Remux, AC3/EAC3/JOC/DTS/DTS-HD MA/DTS:X/TrueHD/Atmos, seek/resume och flera start/stopp. Dokumentera TV:s HDR-indikering och AVR:s inkommande ljudformat, samt bildfrekvens före/under/efter.
5. Samla standardvägens och rewrites kontrakt i core först när legacy-funktionerna kan behållas. Registrera ingen native-motor innan Surface-, session-, kapabilitets- och licensstrategin är verifierad.

Inga kända röda tester i slutversionen. Rosa/lila bild och utebliven DV är fortfarande **odiagnostiserade på fysisk hårdvara**, inte lösta.

Lokala implementation-/testcommits: `f4d9df0d8`, `e1bd960fb`, `9220f2904`. Dokumentationscommit följer dessa. Arbetskatalogen förblir smutsig av tidigare orelaterat arbete; fas 2-filerna är committade separat.
