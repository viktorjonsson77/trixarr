package org.jellyfin.androidtv.seerr

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object SeerrUrl {
	fun normalize(input: String, allowInsecureHttp: Boolean = false): Result<HttpUrl> = runCatching {
		val candidate = input.trim().trimEnd('/')
		val withScheme = if ("://" in candidate) candidate else "https://$candidate"
		val url = withScheme.toHttpUrlOrNull() ?: error("Ogiltig serveradress")
		require(url.scheme == "https" || allowInsecureHttp && url.scheme == "http") { "HTTPS krävs" }
		require(url.username.isEmpty() && url.password.isEmpty()) { "Inloggningsuppgifter får inte ingå i adressen" }
		require(url.query == null && url.fragment == null) { "Serveradressen får inte innehålla query eller fragment" }
		url.newBuilder().addPathSegments("api/v1/").build()
	}
}
object SeerrLogSanitizer {
	private val secret = Regex("(?i)(password|token|api[-_]?key|cookie|authorization)(\\s*[=:]\\s*)([^\\s,;]+)")
	private val query = Regex("([?&])[^#\\s]+")
	fun redact(message: String): String = message.replace(secret, "$1$2<redacted>").replace(query, "<query-redacted>")
}
