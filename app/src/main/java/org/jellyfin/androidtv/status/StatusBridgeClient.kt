package org.jellyfin.androidtv.status

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okio.ByteString.Companion.encodeUtf8

fun secureBridgeUrl(value: String): String? = value.trim().toHttpUrlOrNull()?.takeIf {
    it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null
}?.toString()?.trimEnd('/')

/** Reuses the existing shared Seerr transport. Redirects and cleartext are forbidden. */
class StatusBridgeClient(endpoint: String, client: OkHttpClient, private val cookie: () -> String?, private val jellyfinToken: () -> String?) {
    private val base = requireNotNull(secureBridgeUrl(endpoint)) { "HTTPS required" }
    private val transport = client.newBuilder().followRedirects(false).followSslRedirects(false).readTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun statuses(ids: List<Int> = emptyList()): StatusBatch = withContext(Dispatchers.IO) {
        require(ids.size <= 25 && ids.all { it > 0 })
        val session = requireNotNull(cookie()) { "Seerr authentication required" }
        val url = (base + "/api/v1/requests/status").toHttpUrlOrNull()!!.newBuilder()
            .apply { if (ids.isNotEmpty()) addQueryParameter("ids", ids.distinct().joinToString(",")) }.build()
        val request = Request.Builder().url(url).header("Authorization", "Seerr " + session.encodeUtf8().base64())
            .apply { jellyfinToken()?.let { header("X-Jellyfin-Token", it) } }.get().build()
        val call = transport.newCall(request)
        val response = suspendCancellableCoroutine<okhttp3.Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, error: java.io.IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(java.io.IOException("Statusanslutningen misslyckades"))) }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { continuation.resume(response) { _, value, _ -> value.close() } }
            })
        }
        response.use {
            if (!it.isSuccessful) throw BridgeFailure(it.code)
            val body = requireNotNull(it.body)
            val buffer = okio.Buffer()
            val source = body.source()
            while (buffer.size <= 2_000_000 && source.read(buffer, minOf(8192L, 2_000_001 - buffer.size)) != -1L) { }
            val bytes = buffer.readByteArray()
            require(bytes.size <= 2_000_000) { "Status response too large" }
            json.decodeFromString<StatusBatch>(bytes.decodeToString())
        }
    }
}
class BridgeFailure(val status: Int) : Exception("Status Bridge request failed")

suspend fun pollStatuses(intervalMillis: Long, fetch: suspend () -> StatusBatch, publish: (StatusBatch) -> Unit, failure: (Exception) -> Unit) {
    require(intervalMillis >= 10_000)
    while (true) {
        currentCoroutineContext().ensureActive()
        try { publish(fetch()) } catch (error: Exception) { currentCoroutineContext().ensureActive(); failure(error) }
        delay(intervalMillis)
    }
}
