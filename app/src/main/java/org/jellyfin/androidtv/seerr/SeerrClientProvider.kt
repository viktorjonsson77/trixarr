package org.jellyfin.androidtv.seerr

import okhttp3.OkHttpClient
import org.jellyfin.androidtv.seerr.model.SeerrConfiguration
import java.util.concurrent.TimeUnit

/** Creates API facades over one shared transport and the active profile's encrypted session. */
class SeerrClientProvider(
	private val configurations: SeerrConfigurationRepository,
	private val authStore: SeerrAuthStore,
	private val client: OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(5, TimeUnit.SECONDS)
		.readTimeout(10, TimeUnit.SECONDS)
		.writeTimeout(10, TimeUnit.SECONDS)
		.build(),
) {
	fun configuration(profileKey: String): SeerrConfiguration? = configurations.get(profileKey)

	fun api(profileKey: String): SeerrApi? {
		val configuration = configurations.get(profileKey) ?: return null
		return api(configuration)
	}

	fun api(configuration: SeerrConfiguration): SeerrApi? {
		val url = SeerrUrl.normalize(configuration.baseUrl, configuration.allowInsecureHttp).getOrNull() ?: return null
		return OkHttpSeerrApi(url, client, { authStore.readSession(configuration.profileKey) })
	}

	suspend fun authenticated(profileKey: String): Result<Pair<SeerrApi, org.jellyfin.androidtv.seerr.model.SeerrUser>> {
		val api = api(profileKey) ?: return Result.failure(IllegalStateException("Seerr is not configured"))
		return SeerrSessionManager(profileKey, api, authStore).restore().map { api to it }
	}
}
