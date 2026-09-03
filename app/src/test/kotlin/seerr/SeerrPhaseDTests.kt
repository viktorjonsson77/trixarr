package org.jellyfin.androidtv.seerr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jellyfin.androidtv.seerr.model.*

class SeerrPhaseDTests : StringSpec({
	"request list maps nested media status and request terminal states" {
		val body = Json.parseToJsonElement("""{"results":[
			{"id":1,"status":2,"createdAt":"2026-09-02T12:00:00.000Z","media":{"mediaType":"movie","tmdbId":10,"status":3}},
			{"id":2,"status":4,"createdAt":"2026-09-02T12:00:00.000Z","media":{"mediaType":"tv","tmdbId":20,"status":1},"seasons":[{"seasonNumber":2}]}
		]}""").jsonObject
		val requests = SeerrMediaMapper.mapRequests(body)
		requests[0].status shouldBe RequestStatus.Processing
		requests[1].status shouldBe RequestStatus.Failed
		requests[1].seasons shouldBe setOf(2)
	}

	"discovery maps real fields and all verified media states" {
		val body = Json.parseToJsonElement("""{"results":[{"id":7,"mediaType":"movie","title":"Signal","releaseDate":"2026-02-03","posterPath":"/p.jpg","mediaInfo":{"status":4}}]}""").jsonObject
		val media = SeerrMediaMapper.mapResults(body).single()
		media.title shouldBe "Signal"
		media.year shouldBe 2026
		media.availability shouldBe MediaAvailability.PartiallyAvailable
		SeerrMediaMapper.mapAvailability(1, true) shouldBe MediaAvailability.Requested
		SeerrMediaMapper.mapAvailability(2, false) shouldBe MediaAvailability.Requested
		SeerrMediaMapper.mapAvailability(3, false) shouldBe MediaAvailability.Processing
		SeerrMediaMapper.mapAvailability(5, false) shouldBe MediaAvailability.Available
		SeerrMediaMapper.mapAvailability(99, false) shouldBe MediaAvailability.Unknown
	}

	"series details expose only applicable requestable seasons" {
		val body = Json.parseToJsonElement("""{"id":9,"name":"North","mediaType":"tv","seasons":[{"seasonNumber":0,"name":"Specials"},{"seasonNumber":1,"name":"Season 1"}],"mediaInfo":{"status":4,"seasons":[{"seasonNumber":1,"status":2}]}}""").jsonObject
		val media = SeerrMediaMapper.mapMedia(body)
		media.seasons shouldHaveSize 2
		media.seasons.single { it.number == 1 }.availability shouldBe MediaAvailability.Requested
	}

	"request API sends movie once and serializes selected seasons" {
		val server = MockWebServer(); server.start()
		try {
			server.enqueue(MockResponse().setBody("{}")); server.enqueue(MockResponse().setBody("{}"))
			val api = OkHttpSeerrApi(server.url("api/v1/"), OkHttpClient(), { "connect.sid=test" })
			api.createRequest("movie", 11)
			api.createRequest("tv", 12, setOf(3, 1))
			server.takeRequest().body.readUtf8() shouldBe "{\"mediaType\":\"movie\",\"mediaId\":11}"
			server.takeRequest().body.readUtf8() shouldBe "{\"mediaType\":\"tv\",\"mediaId\":12,\"seasons\":[1, 3]}"
		} finally { server.shutdown() }
	}

	"request errors preserve server status for 401 403 409 and 5xx" {
		listOf(401, 403, 409, 500).forEach { status ->
			val server = MockWebServer(); server.start()
			try {
				server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
				val result = runCatching { OkHttpSeerrApi(server.url("api/v1/"), OkHttpClient(), { "connect.sid=test" }).createRequest("movie", 1) }
				(result.exceptionOrNull() as SeerrHttpException).status shouldBe status
			} finally { server.shutdown() }
		}
	}

	"own requests are filtered by authenticated user id" {
		val server = MockWebServer(); server.start()
		try {
			server.enqueue(MockResponse().setBody("{\"results\":[]}"))
			OkHttpSeerrApi(server.url("api/v1/"), OkHttpClient(), { "connect.sid=test" }).requests(42)
			server.takeRequest().requestUrl?.queryParameter("requestedBy") shouldBe "42"
		} finally { server.shutdown() }
	}

	"external artwork is routed through Seerr and preserves a path prefix" {
		val server = MockWebServer(); server.start()
		try {
			val api = OkHttpSeerrApi(server.url("seerr/api/v1/"), OkHttpClient(), { null })
			api.imageUrl("/poster.jpg") shouldBe server.url("seerr/imageproxy/tmdb/t/p/w500/poster.jpg").toString()
			api.imageUrl("/backdrop.jpg", backdrop = true) shouldBe server.url("seerr/imageproxy/tmdb/t/p/w1280/backdrop.jpg").toString()
		} finally { server.shutdown() }
	}

	"session expiry clears only the active profile and logout clears its secret" {
		val store = MemoryAuthStore().apply { writeSession("a", "cookie-a"); writeSession("b", "cookie-b") }
		val expired = SessionApi(SeerrHttpException(401))
		SeerrSessionManager("a", expired, store).restore().isFailure shouldBe true
		store.readSession("a") shouldBe null
		store.readSession("b") shouldBe "cookie-b"
		SeerrSessionManager("b", SessionApi(), store).logout()
		store.readSession("b") shouldBe null
	}
})

private class MemoryAuthStore : SeerrAuthStore {
	private val values = mutableMapOf<String, String>()
	override fun readSession(profileKey: String) = values[profileKey]
	override fun writeSession(profileKey: String, cookie: String) { values[profileKey] = cookie }
	override fun clearSession(profileKey: String) { values.remove(profileKey) }
	override fun clearAll() = values.clear()
}

private class SessionApi(private val failure: Throwable? = null) : SeerrApi {
	override suspend fun currentUser(): SeerrResponse {
		failure?.let { throw it }
		return SeerrResponse(Json.parseToJsonElement("{\"id\":1,\"displayName\":\"Viewer\",\"permissions\":0}").jsonObject)
	}
	override suspend fun logout() = Unit
	override suspend fun status() = TODO()
	override suspend fun publicSettings() = TODO()
	override suspend fun loginWithJellyfin(username: String, password: CharArray) = TODO()
	override suspend fun loginLocal(email: String, password: CharArray) = TODO()
	override suspend fun initiateQuickConnect() = TODO()
	override suspend fun checkQuickConnect(secret: CharArray) = TODO()
	override suspend fun authenticateQuickConnect(secret: CharArray) = TODO()
	override suspend fun search(query: String, page: Int) = TODO()
	override suspend fun discover(path: String, page: Int) = TODO()
	override suspend fun movie(id: Int) = TODO()
	override suspend fun series(id: Int) = TODO()
	override suspend fun requests(userId: Int, skip: Int, take: Int) = TODO()
	override suspend fun createRequest(mediaType: String, mediaId: Int, seasons: Set<Int>?) = TODO()
}
