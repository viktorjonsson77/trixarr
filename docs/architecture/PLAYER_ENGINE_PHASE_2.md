# ArrStreamer Player Engine – fas 2

Status: **ARRSTREAMER_PLAYER_ENGINE_PHASE_2_PARTIAL**. Implementationsdatum: 2026-11-06. Bas: `docs/player-engine-feasibility`, HEAD före arbetet `df6cabf15`. Arbetsgren: `feat/player-engine-phase-2`.
Användarens implementationsuppdrag ersätter förstudiedokumentets äldre godkännandestatus. Förstudien är oförändrad. Befintlig smutsig arbetskatalog bevaras; APK innehåller även det tidigare lokala arbetet.

## Arkitektur och ägarskap

Avgränsning mot förstudien: kontraktet införs i appens standardväg, inte genom att utöka rewrite-modulens PlayerBackend i core. Därmed migreras inte båda uppspelningsvägarna till ett gemensamt kontrakt i denna leverans. Detta bevarar legacy-funktionerna men lämnar ett uttryckligt konsolideringsarbete.


`app/.../ui/playback/engine/PlayerEngine.kt` definierar prepare/play/pause/stop/seek/release, tillstånd, sanerat fel, position i millisekunder och ljud-/textspårval. Kontraktet är generiskt över motorns förberedda indata; standardadaptern använder Media3 MediaItem. Surface, kö, serverförhandling och rapportering ligger kvar i sina befintliga ägare.

`Media3PlayerEngine.kt` adapterar exakt den ExoPlayer som `VideoManager` redan konfigurerar. Renderers, HTTP, extern text, PlayerView, audio focus och decoder-policy bibehålls. `VideoManager` förbereder genom `PlayerEngineManager`, skickar kommandon till samma adapter och frigör den idempotent. Befintlig spårindexmappning till Jellyfin bibehålls; adaptern validerar grupp/typ/stöd och stödjer text av. Serverburn-in hanteras fortfarande av befintlig controller.

`PlayerEngineManager` väljer endast bland registrerade fabriker, kontrollerar stöd och håller en aktiv instans. Endast Media3 registreras. Inget Kodi/native-val visas. Konstruktion eller synkron prepare-failure får gå vidare till nästa kandidat först efter lyckad release. Listan provas en gång per prepare. Cleanup-fel stänger koordinatorn för vidare användning. En redan aktiv instans byts aldrig automatiskt. Asynkrona decoderfel stannar i befintlig felväg; motorn roteras inte.

Prepare-kontraktet förbjuder att en ny kandidat startar ljud/bild eller rapporterar serverstart innan valet är klart. En fabrik som kastar innan den returnerat måste själv städa sina partiella resurser. Dagens ExoPlayer byggs genom koordinatorns initialize-fabrik före Surface-anslutning. Byggfel fångas; när ingen kandidat återstår stängs spelaren utan reportStart. Factory-listan konsumeras utan omtag, även om anroparen försöker prepare igen. En framtida motor behöver egen Surface-anslutning innan den registreras; endast den befintliga adaptern registreras nu.

`PlaybackAttemptGate.kt` ger generation-id för asynkrona server- och segment-callbacks: högst en prepare/start per försök, gamla callbacks ignoreras, stopp ogiltigförklarar fördröjd start och endast ett startat försök får en stopprapport. Synkront prepare-fel stänger uppspelningen före reportStart. Stop stoppar även en pausad/buffrande motor. Gate-testerna är syntetiska; servertransaktioner är inte end-to-end-verifierade.

`PlaybackController` är fortsatt ensam ägare av Jellyfin start/progress/stop, resume-ticks, transcode-offset och befintlig serverretry. Inga nya typer av serveranrop införs. Motorns position är den lokala tidslinjen, inte ett nytt kanoniskt servervärde. Det eliminerar en ny källa till dubbla sessioner, men är inte ett hårdvaru-/serverintegrationsbevis. Aktiva motorbyten med serveromförhandling ingår inte; de behöver en separat transaktion innan en native-motor registreras. Experimentella rewrite-spelaren och dess PlaySessionService ändras inte.

## Diagnostik och vy

`DiagnosticValues.kt` klassificerar explicit DV codec-sträng (dvhe/dvh1/dvav/dva1) och PQ/HLG/SDR. HEVC eller Main10 räcker aldrig för att härleda DV-profil. PQ utan ytterligare metadata är inte automatiskt HDR10 eller HDR10+. Okända fält visas som UNKNOWN.

`PlaybackDiagnostics.kt` tar emot Media3 AnalyticsListener-händelser och MediaCodec-outputformat via VideoFrameMetadataListener. Endast ändrat format postas till UI-tråden; ingen ny polling-loop. Diagnostik är minnesbaserad, behåller senaste värdet per fält och inga URL:er, tokens eller undantagsmeddelanden. Befintliga andra debugloggar ändras inte och kan ha annan sekretessnivå.

Separata bevisnivåer:

1. **SOURCE/Jellyfin:** codec, container, upplösning, fps, bitrate, HDR-enum, DV-profil/level, primärer, transfer, matris, bitdjup; ljudcodec/profil, kanaler/layout, samplerate och bitrate per spår.
2. **Media3 ström/decoder-input:** MIME, codec-sträng, färgdata, HDR-klassificering, explicit DV-profil/level, bitrate, upplösning/fps och decoder reuse-resultat. Ingen egen demux eller RPU-parser.
3. **Android kapabiliteter:** annonserade decoder-MIME, display-HDR-typer och ljudenheters encodings. Detta är inte aktiv route eller ett fullständigt formatstödtest.
4. **Valt/konfigurerat:** faktisk decoder, hardware-flagga på API 29+, MediaCodec output-färgfält, AudioTrack encoding/samplerate/channel mask/buffer/offload. Offload likställs inte med passthrough. Automatisk passthrough-request är UNKNOWN eftersom ingen explicit begäran finns i denna integration.
5. **TV/HDMI:** alltid NOT VERIFIED. EAC3 JOC, TrueHD, DTS-HD eller en källprofil bevisar inte aktiv Atmos/DTS:X, lossless MA eller TV-DV. Äldre Android utan hardware-flagga ger UNKNOWN.

Vyn nås med informationsknappen i spelarens sekundära kontroller, INFO eller MENU för VOD. Live-TV behåller MENU för programguiden. Dialogen använder 18sp text, hög kontrast, D-pad-rullning och Close/Back. Den pausar inte, ändrar inte Surface och begär inte nytt audio focus. Position är en händelsebaserad snapshot, inte sekunduppdaterad klocka. Display/kapabiliteter uppdateras när vyn öppnas igen. Ingen fil-export införs.

Ändrade integrationer: `VideoManager.java`, `PlaybackController.java`, `PlaybackControllerHelper.kt`, `CustomPlaybackOverlayFragment.java`, `overlay/CustomPlaybackTransportControlGlue.java`. Nya tester finns i `app/src/test/kotlin/ui/playback/engine/` och `app/src/androidTest/java/org/jellyfin/androidtv/playback/`.

## Media3-analys: konkreta kodvägar

- `VideoManager.configureExoplayerBuilder`: DefaultRenderersFactory, decoder fallback=true, extension ON/PREFER, DefaultExtractorsFactory, audio offload, capability-invalidation. FFmpeg-extension ändrar ljuddekodning; den är ingen Kodi-videopipeline. Byte till PCM/software kan påverka ljudformat och belastning.
- `util/profile/deviceProfile.kt` och `MediaCodecCapabilitiesTest.kt`: serverns formatval bygger på capability-policy. Felaktigt eller konservativt annonserat stöd kan ge transcode innan decoder ens startas. `PlaybackController.buildExoPlayerOptions` slår av Direct Play och senare Direct Stream efter retries. ASS/burn-in och valt ljud måste jämföras i samma prov.
- Media3 1.8.0 `MediaCodecUtil.getAlternativeCodecMimeType` tillåter HEVC-baslager för vissa DV-profiler, inte generell P5-fallback. `getDecoderInfosSoftMatch` inkluderar alternativa decoders. Det kan ge olika decoder/MIME än källans DV-etikett; diagnostiken mäter båda. Ingen egen MIME-override införs. [Officiell MediaCodecUtil](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/MediaCodecUtil.java).
- `VideoManager` behåller PlayerView/Surface och Media3:s codec-init/reuse. Färg-/HDR-metadata från extractor till Format och sedan MediaFormat är en kontrollpunkt. Skillnader mellan source, decoder-input och output kan nu observeras. Frånvaro av metadata är inte bevis för SDR eller fel i TV:n.
- För rosa/lila bild är P5 med felaktig baslager-/färghantering en kandidat; för utebliven DV är decoder-val, DV-konfiguration, serverremux/transcode och displaykedjan kandidater. Ingen orsak har reproducerats. Inga decoder-selection-, Surface-, HDR- eller reuse-regler ändras.
- 4K Remux: bitrate-toppar, nätverk, decoder-init, buffer, droppade frames, ljudval och subtitles behöver mätas tillsammans. Nuvarande data är observationer, inte throughput-mätning eller fixerad Remux-kompatibilitet.

## Kodi-jämförelse (ingen kod importerad)

Referens: officiella Kodi **21.2-Omega**, samma låsta bas som förstudien. Det är inte ett påstående om användarens Kodi-version. Källor lästes direkt; ingen Kodi-kod eller GPL-komponent läggs till i produkten.

| Område | Kodi | ArrStreamer och praktisk betydelse |
| --- | --- | --- |
| MKV/DV | [DVDDemuxFFmpeg.cpp](https://github.com/xbmc/xbmc/blob/21.2-Omega/xbmc/cores/VideoPlayer/DVDDemuxers/DVDDemuxFFmpeg.cpp) läser FFmpeg side-data inklusive DV-konfiguration och HDR-information. | Media3 DefaultExtractorsFactory. Samma container kan få olika DV-konfiguration efter demux. Jämför codec-sträng och profiler mot servermetadata. |
| P7/P8 och MIME | [DVDVideoCodecAndroidMediaCodec.cpp](https://github.com/xbmc/xbmc/blob/21.2-Omega/xbmc/cores/VideoPlayer/DVDCodecs/Video/DVDVideoCodecAndroidMediaCodec.cpp), HEVC-grenen i Open, skiljer DV-kapabiliteter/profiler och kan välja P8 vid konvertering av P7. | Ingen motsvarande konvertering införs. En fungerande Kodi-fil kan därför vara ett annat decoder-inputformat. |
| Bitström/CSD | [BitstreamConverter.cpp](https://github.com/xbmc/xbmc/blob/21.2-Omega/xbmc/utils/BitstreamConverter.cpp) har valbar libdovi-RPU-konvertering och EL-filtrering. Codec-klassen konverterar hvcC till Annex B och sätter csd-0. | Media3 sköter extractor/initializationData. P7→P8 är inte full FEL-rekonstruktion; licensbeslut och bitströmstester krävs före eventuell separat implementation. |
| HDR/Surface | Samma Kodi codec-klass bygger statisk HDR-info, color-transfer och har VideoView-/SurfaceTexture-vägar. | PlayerView/Media3 äger Surface och HDR-propagation. Outputmetadata är nu synlig; faktisk TV-signal måste observeras separat. |
| Ljud | [AESinkAUDIOTRACK.cpp](https://github.com/xbmc/xbmc/blob/21.2-Omega/xbmc/cores/AudioEngine/Sinks/AESinkAUDIOTRACK.cpp) mappar TrueHD/DTS-HD och provar AudioTrack/IEC61937-konfigurationer. | Media3 DefaultAudioSink avgör PCM/encoded/offload enligt Android-routen. AudioTrack-config observeras; inget påstående om AVR-insignal. |
| Refresh | [XBMCApp.cpp](https://github.com/xbmc/xbmc/blob/21.2-Omega/xbmc/platform/android/activity/XBMCApp.cpp), SetDisplayMode, applicerar mode/refresh på UI-tråd och väntar på displayförändring. | PlaybackController har egen lägesmatchning och preferredDisplayModeId. 23.976/24, återställning och faktisk växling kräver fysisk kontroll. |

## Begränsningar och nästa steg

- Standardvägen är integrerad; rewrite och externa spelare har inte denna diagnostikvy.
- Ingen native-motor, aktiv-session engine-fallback, RPU/FEL-parser, egen HDMI-verifiering eller DV-fix.
- Fullständigt Android codec profile/level-stöd, audio active-route och direkt-playback capability-query är fortsatt förbättringsarbete; annonserade MIME/encodings är tydligt märkta.
- Fysisk Shield → Yamaha → TCL: DV P5/P7 MEL/P7 FEL/P8, HDR10/HLG/HDR10+, 4K Remux, alla ljudformat, spårbyte, seek/resume, upprepade start/stopp, TV/AVR-insignal och 23.976/24 behöver verifieras. Host/redroid är inte Shield-bevis.
- Sessionsrapportering över nätverk och transcode-offset vid fel/omstart behöver en kontrollerad Jellyfin-fixture eller testserver. Inga produktionskonton/serverändringar har använts.

Test-/byggresultat, APK och SHA-256 finns i [ACCEPTANCE.md](../evidence/player-engine-phase-2/ACCEPTANCE.md). Release-signering är oförändrad. Inga nycklar, pushar, taggar eller installationer på fysisk enhet ingår.

Slutverifiering: 33 fokuserade JVM-tester och 2 Android-tester PASS; debug-build PASS. Package `se.viktorjonsson.jellyfintv.debug`, version `0.0.0-dev.1` (1), ARM64-bibliotek verifierade statiskt. APK `/opt/jellyfintv-artifacts/player-engine-phase-2/arrstreamer-player-engine-phase-2-debug.apk`, SHA-256 `a95f4b43d86c1a8f12ab25e31efa17a2058c701d6e176aaebdd99c2572f13a88`. Full motor-specifik kapabilitetspolicy och controller/session/resume-E2E återstår; därför PARTIAL trots gröna leveranstester.
