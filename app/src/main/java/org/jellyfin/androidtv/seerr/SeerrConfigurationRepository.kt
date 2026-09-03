package org.jellyfin.androidtv.seerr

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.seerr.model.SeerrConfiguration

interface SeerrConfigurationRepository {
	fun get(profileKey: String): SeerrConfiguration?
	fun save(configuration: SeerrConfiguration)
	fun remove(profileKey: String)
}

class SharedPreferencesSeerrConfigurationRepository(
	context: Context,
	private val authStore: SeerrAuthStore,
) : SeerrConfigurationRepository {
	private val preferences = context.getSharedPreferences("seerr_configuration", Context.MODE_PRIVATE)
	private val json = Json { ignoreUnknownKeys = true }

	override fun get(profileKey: String): SeerrConfiguration? = preferences.getString(profileKey, null)?.let {
		runCatching { json.decodeFromString<StoredConfiguration>(it).toDomain(profileKey) }.getOrNull()
	}

	override fun save(configuration: SeerrConfiguration) {
		val previous = get(configuration.profileKey)
		val normalized = SeerrUrl.normalize(configuration.baseUrl, configuration.allowInsecureHttp).getOrThrow()
		val apiSuffix = "api/v1/"
		val configurationPath = normalized.encodedPath.removeSuffix(apiSuffix).ifBlank { "/" }
		val origin = normalized.newBuilder().encodedPath(configurationPath).build().toString().trimEnd('/')
		if (previous != null && previous.baseUrl != origin) authStore.clearSession(configuration.profileKey)
		preferences.edit { putString(configuration.profileKey, json.encodeToString(StoredConfiguration(origin, configuration.allowInsecureHttp))) }
	}

	override fun remove(profileKey: String) {
		authStore.clearSession(profileKey)
		preferences.edit { remove(profileKey) }
	}

	@Serializable
	private data class StoredConfiguration(val baseUrl: String, val allowInsecureHttp: Boolean) {
		fun toDomain(profileKey: String) = SeerrConfiguration(profileKey, baseUrl, allowInsecureHttp)
	}
}
