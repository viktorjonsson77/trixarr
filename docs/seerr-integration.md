# Seerr-integration

## Verifierad API-källa

Integrationen följer Seerrs officiella OpenAPI 3.0-definition `seerr-api.yml` från
`seerr-team/seerr` (develop, granskad 2026-09-03). Definitionens API-version är
1.0.0. Utvecklingsvärden når inte testserverns LAN-origin, men NVIDIA SHIELD når
värden och appens eget anslutningstest har verifierat det verkliga HTTP-API:t.

Använda read-only-endpoints är `GET /api/v1/status`, `GET /api/v1/settings/public`,
`GET /api/v1/auth/me`, `GET /api/v1/search`, `GET /api/v1/discover/trending`,
film-/serieupptäckt och kommande, `GET /api/v1/movie/{id}`,
`GET /api/v1/tv/{id}` samt `GET /api/v1/request`. Mutationerna begränsas till
explicit login/logout och en användarbekräftad `POST /api/v1/request`.

## Autentisering och session

Standardflödet är en riktig användarsession från `POST /api/v1/auth/jellyfin` eller
`POST /api/v1/auth/local`. Lösenord finns endast i en tillfällig `CharArray`, nollas
direkt efter anropet och lagras eller loggas aldrig. Endast `connect.sid`-cookien
sparas, krypterad med en icke-exporterbar AES-GCM-nyckel i Android Keystore och
separerad per Jellyfin-profil. `GET /auth/me` återställer sessionen. HTTP 401 rensar
sessionen och kräver ny login; logout anropar servern och rensar alltid den lokala
hemligheten. Byte eller borttagning av serverkonfiguration rensar den tidigare
sessionen för den aktuella Jellyfin-profilen.

En global API-nyckel används inte: Seerrs dokumentation beskriver den som en
administrativ integrationsnyckel, vilket bryter mot principen om minsta behörighet.

## Behörigheter och status

Behörighetsbitar verifieras mot Seerrs `server/lib/permissions.ts`. Film kräver
`REQUEST` + `REQUEST_MOVIE`, serie kräver `REQUEST` + `REQUEST_TV`; `ADMIN` uppfyller
kontrollen. Kontroller som användaren saknar rätt till visas inte.

Seerrs mediestatus mappas till interna modeller: okänd, behandlas, delvis tillgänglig
och tillgänglig. Avsaknad av `mediaInfo` i ett giltigt sökresultat betyder att titeln
kan begäras. En okänd status är aldrig automatiskt begärbar. Begärandestatus hålls
separat och översätts till Begärd, Väntar på godkännande, Godkänd, Behandlas,
Delvis tillgänglig, Tillgänglig, Avslagen eller Misslyckades.

## Deduplicering och cache

Jellyfin matchas först på riktiga TMDb-, TVDb- och IMDb-ID:n. Normaliserad titel och
exakt år används endast som reserv. Jellyfin-objektet vinner, inklusive bild och
detaljroute, och kan aldrig visa Begär. Delvis tillgängliga serier hålls separata från
helt tillgängliga. Cache ska vara profil- och serverseparerad och invalidiseras efter
en lyckad mutation.

## Transport, lagring och offline

HTTPS använder OkHttp-klientens normala certifikat- och hostname-verifiering. HTTP nekas i
nätverkslagret om inte användaren uttryckligen har aktiverat osäker lokal anslutning
för just serverprofilen. Det finns ingen automatisk nedgradering, trust-all eller
avstängd hostname-verifiering. Serveradress och cleartext-val sparas i vanlig lokal
serverkonfiguration; hemligheten sparas separat i Keystore-skyddad lagring.

Seerr-fel får inte blockera Jellyfin. Upptäck faller tillbaka på Jellyfin, sökning
behåller resultat från den tjänst som fungerar och UI visar ett kort, redigerat fel.
Loggredigering tar bort lösenord, token, cookies, authorization-värden och URL-query.
Ingen global serveraktivitet visas; användaraktivitet härleds endast från verifierade
förändringar i den inloggade användarens egna begäranden.

## Liveverifiering

Testmålets adress och autentiseringsuppgifter lagras inte i Git eller denna fil.
På NVIDIA SHIELD har användaren uttryckligen godkänt cleartext för just sin sparade
serverprofil och loggat in med Jellyfin-flödet. `GET /auth/me` återställde sessionen
efter ominstallation/processomstart och `GET /discover/trending` returnerade 20
verkliga titlar. Jellyfin-sessionen förblev aktiv. Logcat visade ingen 401-loop,
cleartext- eller TLS-blockering, krasch, ANR eller hemlighet. Ingen mediebegäran eller
annan servermutation utfördes. En verklig begäran får inte skapas innan användaren
uttryckligen har godkänt en specifik titel och, för serie, specifika säsonger.
