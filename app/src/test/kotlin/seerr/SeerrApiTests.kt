package org.jellyfin.androidtv.seerr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jellyfin.androidtv.seerr.model.MediaAvailability
import org.jellyfin.androidtv.seerr.model.MediaKind
import org.jellyfin.androidtv.seerr.model.SeerrMedia

class SeerrApiTests : StringSpec({
	"login captures only the session cookie and clears password memory" {
		val server = MockWebServer(); server.start()
		try {
			server.enqueue(MockResponse().setResponseCode(200).addHeader("Set-Cookie", "connect.sid=safe-test-value; HttpOnly").setBody("{\"id\":1}"))
			val password = "temporary".toCharArray()
			val api = OkHttpSeerrApi(server.url("api/v1/"), OkHttpClient(), { null })
			api.loginWithJellyfin("viewer", password).sessionCookie shouldBe "connect.sid=safe-test-value"
			password.all { it == '\u0000' } shouldBe true
			server.takeRequest().path shouldBe "/api/v1/auth/jellyfin"
		} finally { server.shutdown() }
	}

	"authenticated calls restore the cookie and logout uses POST" {
		val server = MockWebServer(); server.start()
		try {
			server.enqueue(MockResponse().setBody("{\"id\":1}")); server.enqueue(MockResponse().setBody("{}"))
			val api = OkHttpSeerrApi(server.url("api/v1/"), OkHttpClient(), { "connect.sid=test" })
			api.currentUser(); api.logout()
			server.takeRequest().getHeader("Cookie") shouldBe "connect.sid=test"
			server.takeRequest().method shouldBe "POST"
		} finally { server.shutdown() }
	}

	"request requires confirmation and one in-flight mutation" {
		val fake = BlockingApi()
		val repository = RequestRepository(fake)
		val media = SeerrMedia(1, MediaKind.Movie, "Test", availability = MediaAvailability.Requestable)
		repository.request(media, confirmed = false).isFailure shouldBe true
		supervisorScope {
			val first = async { repository.request(media, confirmed = true) }
			delay(30)
			repository.request(media, confirmed = true).isFailure shouldBe true
			first.await().isSuccess shouldBe true
		}
		fake.calls shouldBe 1
		repository.generation() shouldBe 1
	}
})

private class BlockingApi : SeerrApi {
	var calls = 0
	override suspend fun status() = TODO()
	override suspend fun createRequest(mediaType: String, mediaId: Int, seasons: Set<Int>?) = kotlinx.coroutines.delay(100).let { calls++; SeerrResponse(kotlinx.serialization.json.JsonObject(emptyMap())) }
	override suspend fun publicSettings() = TODO()
	override suspend fun loginWithJellyfin(username: String, password: CharArray) = TODO()
	override suspend fun loginLocal(email: String, password: CharArray) = TODO()
	override suspend fun initiateQuickConnect() = TODO()
	override suspend fun checkQuickConnect(secret: CharArray) = TODO()
	override suspend fun authenticateQuickConnect(secret: CharArray) = TODO()
	override suspend fun currentUser() = TODO()
	override suspend fun logout() = Unit
	override suspend fun search(query: String, page: Int) = TODO()
	override suspend fun discover(path: String, page: Int) = TODO()
	override suspend fun movie(id: Int) = TODO()
	override suspend fun series(id: Int) = TODO()
	override suspend fun requests(userId: Int, skip: Int, take: Int) = TODO()
}
