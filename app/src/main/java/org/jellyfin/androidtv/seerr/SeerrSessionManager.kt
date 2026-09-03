package org.jellyfin.androidtv.seerr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.seerr.model.SeerrConnectionState
import org.jellyfin.androidtv.seerr.model.SeerrUser

class SeerrSessionManager(
	private val profileKey: String,
	private val api: SeerrApi,
	private val authStore: SeerrAuthStore,
) {
	private val mutableState = MutableStateFlow<SeerrConnectionState>(SeerrConnectionState.Disconnected)
	val state = mutableState.asStateFlow()

	suspend fun restore(): Result<SeerrUser> = runCatching {
		if (authStore.readSession(profileKey) == null) error("No session")
		loadUser()
	}.onFailure { error ->
		if (error is SeerrHttpException && error.status == 401) {
			authStore.clearSession(profileKey)
			mutableState.value = SeerrConnectionState.Expired
		}
	}

	suspend fun loginWithJellyfin(username: String, password: CharArray): Result<SeerrUser> = runCatching {
		val response = api.loginWithJellyfin(username, password)
		val cookie = response.sessionCookie ?: error("Server returned no session")
		authStore.writeSession(profileKey, cookie)
		mapUser(response.body)
	}.onSuccess { mutableState.value = SeerrConnectionState.Connected(it, null) }

	suspend fun loginLocal(email: String, password: CharArray): Result<SeerrUser> = runCatching {
		val response = api.loginLocal(email, password)
		val cookie = response.sessionCookie ?: error("Server returned no session")
		authStore.writeSession(profileKey, cookie)
		mapUser(response.body)
	}.onSuccess { mutableState.value = SeerrConnectionState.Connected(it, null) }

	suspend fun authenticateQuickConnect(secret: CharArray): Result<SeerrUser> = runCatching {
		val response = api.authenticateQuickConnect(secret)
		val cookie = response.sessionCookie ?: error("Server returned no session")
		authStore.writeSession(profileKey, cookie)
		mapUser(response.body)
	}.onSuccess { mutableState.value = SeerrConnectionState.Connected(it, null) }

	suspend fun logout() {
		runCatching { api.logout() }
		authStore.clearSession(profileKey)
		mutableState.value = SeerrConnectionState.Disconnected
	}

	private suspend fun loadUser(): SeerrUser {
		val user = mapUser(api.currentUser().body)
		mutableState.value = SeerrConnectionState.Connected(user, null)
		return user
	}

	private fun mapUser(body: kotlinx.serialization.json.JsonObject): SeerrUser {
		val settings = body["settings"]?.jsonObject
		return SeerrUser(
			id = body["id"]?.jsonPrimitive?.intOrNull ?: error("Missing user id"),
			displayName = body["displayName"]?.jsonPrimitive?.content ?: body["email"]?.jsonPrimitive?.content ?: "Seerr",
			permissions = body["permissions"]?.jsonPrimitive?.longOrNull ?: settings?.get("permissions")?.jsonPrimitive?.longOrNull ?: 0,
		)
	}
}
