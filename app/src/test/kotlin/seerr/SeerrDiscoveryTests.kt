package org.jellyfin.androidtv.seerr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.jellyfin.androidtv.seerr.model.*
import org.jellyfin.androidtv.ui.catalog.*

class SeerrDiscoveryTests : StringSpec({
    fun api(server: MockWebServer) = OkHttpSeerrApi(server.url("api/v1/"), OkHttpClient(), { "connect.sid=synthetic" })
    fun media(id: Int, date: String?, rating: Double? = null) = SeerrMedia(id, MediaKind.Movie, "Title $id", releaseDate = date, rating = rating)
    val today = LocalDate.of(2026, 11, 21)
    "new releases exclude future unknown and older dates; upcoming excludes released" {
        val values = listOf(media(1,"2026-11-22"), media(2,"2026-11-21"), media(3,"2026-09-01"), media(4,null), media(5,"bad"), media(6,"2020-01-01"))
        SeerrDiscovery.recent(values,today).map { it.id } shouldBe listOf(2,3)
        SeerrDiscovery.upcoming(values,today).map { it.id } shouldBe listOf(1)
    }
    "top lists rank actual known ratings without inventing a global ranking" {
        SeerrDiscovery.ranked(listOf(media(1,null,4.0),media(2,null,null),media(3,null,9.0))).map { it.id } shouldBe listOf(3,1)
    }
    "popular loads only two endpoints and caches pages reused by top and new categories" {
        val server=MockWebServer();server.start()
        try {
            repeat(2) { server.enqueue(MockResponse().setBody("""{"results":[{"id":1,"title":"Title","releaseDate":"2026-11-20","voteAverage":8.0}]}""")) }
            val discovery=SeerrDiscovery(api(server),SeerrUser(42,"Synthetic",32)) { today }
            discovery.load(DiscoveryCategory.Popular).size shouldBe 2
            discovery.load(DiscoveryCategory.Top).size shouldBe 2
            discovery.load(DiscoveryCategory.New).size shouldBe 2
            server.requestCount shouldBe 2
            server.takeRequest().path shouldBe "/api/v1/discover/movies?page=1"
            server.takeRequest().path shouldBe "/api/v1/discover/tv?page=1"
        } finally {server.shutdown()}
    }
    "genre arrays are parsed and only the selected genre page is loaded" {
        val server=MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setBody("""[{"id":18,"name":"Drama"}]"""))
            server.enqueue(MockResponse().setBody("""[{"id":35,"name":"Komedi"}]"""))
            server.enqueue(MockResponse().setBody("""{"results":[{"id":8,"name":"Show","mediaType":"tv"}]}"""))
            val discovery=SeerrDiscovery(api(server),SeerrUser(1,"Synthetic",0))
            discovery.genreOptions().map { it.key } shouldBe listOf("movie:18","tv:35")
            discovery.load(DiscoveryCategory.Genres,"tv:35").single().label shouldBe "Komedi"
            server.requestCount shouldBe 3
            server.takeRequest().path shouldBe "/api/v1/discover/genreslider/movie"
            server.takeRequest().path shouldBe "/api/v1/discover/genreslider/tv"
            server.takeRequest().path shouldBe "/api/v1/discover/tv/genre/35?page=1"
        } finally {server.shutdown()}
    }
    "discovery authentication permission timeout and server errors never become empty success" {
        for (status in listOf(401,403,500)) {
            val server=MockWebServer();server.start()
            try {
                server.enqueue(MockResponse().setResponseCode(status))
                val result=runCatching { SeerrDiscovery(api(server),SeerrUser(1,"Synthetic",0)).load(DiscoveryCategory.Popular) }
                (result.exceptionOrNull() as SeerrHttpException).status shouldBe status
            } finally {server.shutdown()}
        }
    }
    "empty discovery is a valid empty state" {
        val server=MockWebServer();server.start()
        try {
            repeat(2) {server.enqueue(MockResponse().setBody("""{"results":[]}"""))}
            SeerrDiscovery(api(server),SeerrUser(1,"Synthetic",0)).load(DiscoveryCategory.Popular) shouldBe emptyList()
        } finally {server.shutdown()}
    }
    "permissions mirror Seerr OR grants without exposing administration" {
        SeerrPermissions.canRequest(32,MediaKind.Movie) shouldBe true
        SeerrPermissions.canRequest(32,MediaKind.Series) shouldBe true
        SeerrPermissions.canRequest(262144,MediaKind.Movie) shouldBe true
        SeerrPermissions.canRequest(262144,MediaKind.Series) shouldBe false
        SeerrPermissions.canRequest(524288,MediaKind.Series) shouldBe true
        SeerrPermissions.canRequest(0,MediaKind.Movie) shouldBe false
        SeerrPermissions.canRequest(2,MediaKind.Series) shouldBe true
        SeerrPermissions.canViewOwnRequests(0) shouldBe true
        SeerrPermissions.canViewRequests(0) shouldBe false
    }
    "series request validates remaining seasons and prevents repeats while allowing other seasons" {
        val server=MockWebServer();server.start()
        try {
            repeat(2) { server.enqueue(MockResponse().setBody("{}")) }
            val repository=RequestRepository(api(server),32)
            val title=SeerrMedia(8,MediaKind.Series,"Show",availability=MediaAvailability.PartiallyAvailable,seasons=listOf(
                SeerrSeason(0,"Specials",MediaAvailability.Requestable),SeerrSeason(1,"S1",MediaAvailability.Available),
                SeerrSeason(2,"S2",MediaAvailability.Requestable),SeerrSeason(3,"S3",MediaAvailability.Requestable)))
            repository.request(title,setOf(0),true).isFailure shouldBe true
            repository.request(title,setOf(1),true).isFailure shouldBe true
            repository.request(title,emptySet(),true).isFailure shouldBe true
            repository.request(title,setOf(2),false).isFailure shouldBe true
            repository.request(title,setOf(2),true).isSuccess shouldBe true
            repository.request(title,setOf(2),true).isFailure shouldBe true
            repository.request(title,setOf(3),true).isSuccess shouldBe true
            server.requestCount shouldBe 2
            server.takeRequest().body.readUtf8() shouldBe """{"mediaType":"tv","mediaId":8,"seasons":[2]}"""
        } finally {server.shutdown()}
    }
    "permission denied and already requested movies never issue POST" {
        val server=MockWebServer();server.start()
        try {
            val title=SeerrMedia(8,MediaKind.Movie,"Movie",availability=MediaAvailability.Requestable)
            RequestRepository(api(server),0).request(title,confirmed=true).isFailure shouldBe true
            RequestRepository(api(server),32).request(title.copy(availability=MediaAvailability.Requested),confirmed=true).isFailure shouldBe true
            server.requestCount shouldBe 0
        } finally {server.shutdown()}
    }
    "Seerr availability alone and title-year similarity cannot authorize play" {
        val remote=CatalogTitle("remote",TitleKind.Movie,"Same",year=2026,status=TitleStatus.Available,ids=TitleIds(tmdb=8))
        remote.primary shouldBe TitleAction.Information
        val local=remote.copy(key="local",ids=TitleIds(local="uuid"),playbackId="uuid")
        verifiedDiscoveryMatch(local,remote) shouldBe false
        verifiedDiscoveryMatch(local.copy(ids=TitleIds(local="uuid",tmdb=8)),remote) shouldBe true
        verifiedDiscoveryMatch(local.copy(ids=TitleIds(local="uuid",tmdb=9)),remote) shouldBe false
        verifiedDiscoveryMatch(local.copy(kind=TitleKind.Series,ids=TitleIds(local="uuid",tmdb=8)),remote) shouldBe false
    }
    "empty request arrays do not imply pending requests and genres survive response parsing" {
        val value=Json.parseToJsonElement("""{"id":8,"title":"Movie","genreIds":[18,35],"mediaInfo":{"status":1,"requests":[]}}""").jsonObject
        val media=SeerrMediaMapper.mapMedia(value)
        media.genreIds shouldBe listOf(18,35)
        media.availability shouldBe MediaAvailability.Unknown
    }
    "discovery errors retain distinct localized authentication permission timeout and server states" {
        org.jellyfin.androidtv.ui.catalog.discoveryError(SeerrHttpException(401)) shouldBe org.jellyfin.androidtv.R.string.discover_auth_error
        org.jellyfin.androidtv.ui.catalog.discoveryError(SeerrHttpException(403)) shouldBe org.jellyfin.androidtv.R.string.discover_permission_error
        org.jellyfin.androidtv.ui.catalog.discoveryError(SeerrHttpException(409)) shouldBe org.jellyfin.androidtv.R.string.discover_conflict
        org.jellyfin.androidtv.ui.catalog.discoveryError(java.net.SocketTimeoutException()) shouldBe org.jellyfin.androidtv.R.string.discover_timeout
        org.jellyfin.androidtv.ui.catalog.discoveryError(SeerrHttpException(500)) shouldBe org.jellyfin.androidtv.R.string.discover_server_error
    }
    "cancelling a discovery call releases its coroutine before the network timeout" {
        val server=MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job=launch(Dispatchers.IO) {api(server).discover("movies")}
            (server.takeRequest(2,java.util.concurrent.TimeUnit.SECONDS) != null) shouldBe true
            withTimeout(1000) {job.cancelAndJoin()}
            job.isCancelled shouldBe true
        } finally {server.shutdown()}
    }
    "image URLs honor Seerr public cache mode without changing server settings" {
        val server=MockWebServer();server.start()
        try {
            val client=api(server)
            server.enqueue(MockResponse().setBody("""{"cacheImages":false}"""))
            client.publicSettings()
            client.imageUrl("/poster.jpg") shouldBe "https://image.tmdb.org/t/p/w500/poster.jpg"
            client.imageUrl("/backdrop.jpg",true) shouldBe "https://image.tmdb.org/t/p/w1280/backdrop.jpg"
            server.takeRequest().method shouldBe "GET"
            server.enqueue(MockResponse().setBody("""{"cacheImages":true}"""))
            client.publicSettings()
            client.imageUrl("/poster.jpg") shouldBe server.url("imageproxy/tmdb/t/p/w500/poster.jpg").toString()
        } finally {server.shutdown()}
    }
})
