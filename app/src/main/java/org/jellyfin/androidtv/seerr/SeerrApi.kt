package org.jellyfin.androidtv.seerr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SeerrHttpException(val status: Int) : Exception("Seerr request failed ($status)")

data class SeerrResponse(val body: JsonObject, val sessionCookie: String? = null)
data class SeerrQuickConnect(val code: String, val secret: CharArray)

interface SeerrApi {
	fun imageUrl(path: String, backdrop: Boolean = false): String = path
	suspend fun status(): SeerrResponse
	suspend fun publicSettings(): SeerrResponse
	suspend fun loginWithJellyfin(username: String, password: CharArray): SeerrResponse
	suspend fun loginLocal(email: String, password: CharArray): SeerrResponse
	suspend fun initiateQuickConnect(): SeerrQuickConnect
	suspend fun checkQuickConnect(secret: CharArray): Boolean
	suspend fun authenticateQuickConnect(secret: CharArray): SeerrResponse
	suspend fun currentUser(): SeerrResponse
	suspend fun logout()
	suspend fun search(query: String, page: Int = 1): SeerrResponse
	suspend fun discover(path: String, page: Int = 1): SeerrResponse
	suspend fun movie(id: Int): SeerrResponse
	suspend fun series(id: Int): SeerrResponse
	suspend fun requests(userId: Int, skip: Int = 0, take: Int = 50): SeerrResponse
	suspend fun createRequest(mediaType: String, mediaId: Int, seasons: Set<Int>? = null): SeerrResponse
}

class OkHttpSeerrApi(
	private val baseUrl: HttpUrl,
	private val client: OkHttpClient,
	private val session: () -> String?,
	private val json: Json = Json { ignoreUnknownKeys = true },
) : SeerrApi {
	private val mediaType = "application/json; charset=utf-8".toMediaType()
	@Volatile private var proxyImages = true
	override fun imageUrl(path: String, backdrop: Boolean): String {
		val apiPath = baseUrl.encodedPath.removeSuffix("api/v1/")
		val size = if (backdrop) "w1280" else "w500"
		if (!proxyImages) return "https://image.tmdb.org/t/p/$size${if (path.startsWith('/')) path else "/$path"}"
		return baseUrl.newBuilder().encodedPath("${apiPath}imageproxy/tmdb/t/p/$size${if (path.startsWith('/')) path else "/$path"}").query(null).build().toString()
	}

	override suspend fun status() = get("status", authenticated = false)
	override suspend fun publicSettings() = get("settings/public", authenticated = false).also {
		proxyImages = it.body["cacheImages"]?.jsonPrimitive?.booleanOrNull ?: true
	}
	override suspend fun currentUser() = get("auth/me")
	override suspend fun search(query: String, page: Int) = get("search", query = mapOf("query" to query, "page" to page.toString()))
	override suspend fun discover(path: String, page: Int): SeerrResponse {
		require(path in ALLOWED_DISCOVERY || Regex("(movies|tv)/genre/[0-9]+").matches(path)) { "Unsupported discovery endpoint" }
		return get("discover/$path", query = if (path.startsWith("genreslider/")) emptyMap() else mapOf("page" to page.toString()))
	}
	override suspend fun movie(id: Int) = get("movie/$id")
	override suspend fun series(id: Int) = get("tv/$id")
	override suspend fun requests(userId: Int, skip: Int, take: Int) = get(
		"request",
		query = mapOf("skip" to "$skip", "take" to "$take", "sort" to "added", "requestedBy" to "$userId"),
	)

	override suspend fun loginWithJellyfin(username: String, password: CharArray) = try {
		post("auth/jellyfin", buildJson("username" to username, "password" to password.concatToString()), authenticated = false)
	} finally { password.fill('\u0000') }

	override suspend fun loginLocal(email: String, password: CharArray) = try {
		post("auth/local", buildJson("email" to email, "password" to password.concatToString()), authenticated = false)
	} finally { password.fill('\u0000') }

	override suspend fun initiateQuickConnect(): SeerrQuickConnect {
		val body = post("auth/jellyfin/quickconnect/initiate", "{}", authenticated = false).body
		val code = body["code"]?.jsonPrimitive?.content ?: error("Missing Quick Connect code")
		val secret = body["secret"]?.jsonPrimitive?.content?.toCharArray() ?: error("Missing Quick Connect secret")
		return SeerrQuickConnect(code, secret)
	}

	override suspend fun checkQuickConnect(secret: CharArray): Boolean {
		val value = secret.concatToString()
		return get("auth/jellyfin/quickconnect/check", authenticated = false, query = mapOf("secret" to value))
			.body["authenticated"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true
	}

	override suspend fun authenticateQuickConnect(secret: CharArray) = try {
		post("auth/jellyfin/quickconnect/authenticate", buildJson("secret" to secret.concatToString()), authenticated = false)
	} finally { secret.fill('\u0000') }

	override suspend fun logout() { post("auth/logout", "{}") }

	override suspend fun createRequest(mediaType: String, mediaId: Int, seasons: Set<Int>?) = post(
		"request",
		buildString {
			append("{\"mediaType\":\"").append(if (mediaType == "tv") "tv" else "movie").append("\",\"mediaId\":").append(mediaId)
			if (!seasons.isNullOrEmpty()) append(",\"seasons\":").append(seasons.sorted().joinToString(prefix = "[", postfix = "]"))
			append('}')
		},
	)

	private suspend fun get(path: String, authenticated: Boolean = true, query: Map<String, String> = emptyMap()): SeerrResponse {
		val url = baseUrl.resolve(path)!!.newBuilder().apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
		return execute(Request.Builder().url(url).get(), authenticated)
	}

	private suspend fun post(path: String, body: String, authenticated: Boolean = true) = execute(
		Request.Builder().url(baseUrl.resolve(path)!!).post(body.toRequestBody(mediaType)), authenticated,
	)

	private suspend fun execute(builder: Request.Builder, authenticated: Boolean): SeerrResponse = withContext(Dispatchers.IO) {
		if (authenticated) session()?.let { builder.header("Cookie", it) }
		val call = client.newCall(builder.header("Accept", "application/json").build())
		val received = kotlinx.coroutines.suspendCancellableCoroutine<okhttp3.Response> { continuation ->
			continuation.invokeOnCancellation { call.cancel() }
			call.enqueue(object : okhttp3.Callback {
				override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
				override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
					continuation.resume(response) { _, value, _ -> value.close() }
				}
			})
		}
		received.use { response ->
			if (!response.isSuccessful) throw SeerrHttpException(response.code)
			val content = response.body?.string().orEmpty().ifBlank { "{}" }
			val cookie = response.headers("Set-Cookie").firstOrNull { it.startsWith("connect.sid=") }?.substringBefore(';')
			val parsed = json.parseToJsonElement(content)
			SeerrResponse(if (parsed is kotlinx.serialization.json.JsonArray) JsonObject(mapOf("results" to parsed)) else parsed as JsonObject, cookie)
		}
	}

	private fun buildJson(vararg values: Pair<String, String>) = values.joinToString(prefix = "{", postfix = "}") { (key, value) ->
		"\"$key\":" + Json.encodeToString(value)
	}

	private companion object {
		val ALLOWED_DISCOVERY = setOf("trending", "movies", "movies/upcoming", "tv", "tv/upcoming", "genreslider/movie", "genreslider/tv")
	}
}
