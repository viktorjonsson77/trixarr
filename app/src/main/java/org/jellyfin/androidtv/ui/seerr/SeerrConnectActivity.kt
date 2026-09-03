package org.jellyfin.androidtv.ui.seerr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.seerr.OkHttpSeerrApi
import org.jellyfin.androidtv.seerr.SeerrConfigurationRepository
import org.jellyfin.androidtv.seerr.SeerrAuthStore
import org.jellyfin.androidtv.seerr.SeerrHttpException
import org.jellyfin.androidtv.seerr.SeerrSessionManager
import org.jellyfin.androidtv.seerr.SeerrUrl
import org.jellyfin.androidtv.seerr.SeerrMediaMapper
import org.jellyfin.androidtv.seerr.model.SeerrConfiguration
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinematic.CinematicScaffold
import org.jellyfin.androidtv.ui.cinematic.FocusableTvButton
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class SeerrConnectActivity : ComponentActivity() {
	private val configurations: SeerrConfigurationRepository by inject()
	private val authStore: SeerrAuthStore by inject()
	private val jellyfinSession: SessionRepository by inject()
	private var quickConnectSecret: CharArray? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			JellyfinTheme {
				val profile = jellyfinSession.currentSession.value?.let { "${it.serverId}:${it.userId}" } ?: "signed-out"
				val stored = remember(profile) { configurations.get(profile) }
				var address by remember(profile) { mutableStateOf(stored?.baseUrl.orEmpty()) }
				var username by remember { mutableStateOf("") }
				var password by remember { mutableStateOf("") }
				var allowHttp by remember(profile) { mutableStateOf(stored?.allowInsecureHttp == true) }
				var message by remember { mutableStateOf("") }
				var quickConnectCode by remember { mutableStateOf<String?>(null) }
				var connectedName by remember { mutableStateOf<String?>(null) }
				var discoveryStatus by remember { mutableStateOf<String?>(null) }
				LaunchedEffect(profile, stored?.baseUrl) {
					val configuration = stored ?: return@LaunchedEffect
					val url = SeerrUrl.normalize(configuration.baseUrl, configuration.allowInsecureHttp).getOrNull() ?: return@LaunchedEffect
					val api = OkHttpSeerrApi(url, seerrClient(), { authStore.readSession(profile) })
					SeerrSessionManager(profile, api, authStore).restore().onSuccess { user ->
						connectedName = user.displayName
						discoveryStatus = runCatching { SeerrMediaMapper.mapResults(api.discover("trending").body).size }
							.fold({ "Upptäck verifierad · $it titlar hämtade" }, { "Ansluten · Upptäck kunde inte hämtas" })
					}.onFailure { if (it is SeerrHttpException && it.status == 401) message = "Sessionen har gått ut. Logga in igen." }
				}
				CinematicScaffold {
					Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 72.dp, vertical = 44.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
						Text("Anslut till Seerr", color = Color.White)
						connectedName?.let { Text("Ansluten som $it", color = Color(0xFF79E2A8)) }
						discoveryStatus?.let { Text(it, color = Color.White.copy(.8f)) }
						if (connectedName != null) {
							Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
								FocusableTvButton(onClick = ::finish) { Text("Klar") }
								FocusableTvButton(onClick = {
									val configuration = configurations.get(profile) ?: return@FocusableTvButton
									val url = SeerrUrl.normalize(configuration.baseUrl, configuration.allowInsecureHttp).getOrNull() ?: return@FocusableTvButton
									val api = OkHttpSeerrApi(url, seerrClient(), { authStore.readSession(profile) })
									lifecycleScope.launch {
										SeerrSessionManager(profile, api, authStore).logout()
										connectedName = null
										discoveryStatus = null
										message = "Frånkopplad"
									}
								}) { Text("Koppla från") }
							}
							return@Column
						}
						Text("Ange adressen till din befintliga Seerr-server.", color = Color.White.copy(.75f))
						SeerrField(address, "Serveradress") { address = it }
						if (address.trim().startsWith("http://")) {
							Text("Den här servern använder en okrypterad lokal anslutning. Fortsätt endast om du litar på nätverket.", color = Color(0xFFFFC46B))
							FocusableTvButton(onClick = { allowHttp = !allowHttp }) { Text(if (allowHttp) "Tillåten för den här servern" else "Tillåt för den här servern") }
						}
						FocusableTvButton(onClick = {
							lifecycleScope.launch {
								message = "Kontrollerar anslutningen …"
								val normalized = SeerrUrl.normalize(address, allowHttp)
								message = normalized.fold(onSuccess = { url ->
									runCatching {
										val api = OkHttpSeerrApi(url, seerrClient(), { null })
										val status = api.status().body
										api.publicSettings()
										status["version"]?.jsonPrimitive?.contentOrNull
									}.fold({ version ->
										configurations.save(SeerrConfiguration(profile, address, allowHttp))
										if (version.isNullOrBlank()) "Anslutningen fungerar" else "Anslutningen fungerar · Seerr $version"
									}, ::safeConnectionError)
								}, onFailure = { it.message ?: "Ogiltig serveradress" })
							}
						}) { Text("Testa anslutning") }
						if (message.isNotBlank()) Text(message, color = Color.White)
						Text("Jellyfin Quick Connect", color = Color.White)
						if (quickConnectCode == null) {
							FocusableTvButton(onClick = {
								val url = SeerrUrl.normalize(address, allowHttp).getOrNull() ?: return@FocusableTvButton
								lifecycleScope.launch {
									message = "Skapar engångskod …"
									runCatching { OkHttpSeerrApi(url, seerrClient(), { null }).initiateQuickConnect() }.fold({ result ->
										quickConnectSecret?.fill('\u0000')
										quickConnectSecret = result.secret
										quickConnectCode = result.code
										message = "Godkänn koden i Jellyfin och välj sedan Slutför Quick Connect."
									}, { message = safeLoginError(it) })
								}
							}) { Text("Skapa Quick Connect-kod") }
						} else {
							Text("Kod: $quickConnectCode", color = Color.White)
							FocusableTvButton(onClick = {
								val url = SeerrUrl.normalize(address, allowHttp).getOrNull() ?: return@FocusableTvButton
								val secret = quickConnectSecret ?: return@FocusableTvButton
								val api = OkHttpSeerrApi(url, seerrClient(), { authStore.readSession(profile) })
								lifecycleScope.launch {
									message = "Kontrollerar godkännandet …"
									runCatching { api.checkQuickConnect(secret) }.fold({ approved ->
										if (!approved) message = "Koden har inte godkänts ännu."
								else message = SeerrSessionManager(profile, api, authStore).authenticateQuickConnect(secret).fold({ user ->
										configurations.save(SeerrConfiguration(profile, address, allowHttp))
										quickConnectSecret = null
										connectedName = user.displayName
										discoveryStatus = runCatching { SeerrMediaMapper.mapResults(api.discover("trending").body).size }
											.fold({ "Upptäck verifierad · $it titlar hämtade" }, { "Ansluten · Upptäck kunde inte hämtas" })
										"Ansluten som ${user.displayName}"
										}, ::safeLoginError)
									}, { message = safeLoginError(it) })
								}
							}) { Text("Slutför Quick Connect") }
						}
						Text("Logga in", color = Color.White)
						SeerrField(username, "Användarnamn eller e-postadress") { username = it }
						SeerrField(password, "Lösenord", password = true) { password = it }
						Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
							FocusableTvButton(onClick = {
								val url = SeerrUrl.normalize(address, allowHttp).getOrNull() ?: return@FocusableTvButton
								configurations.save(SeerrConfiguration(profile, address, allowHttp))
								val api = OkHttpSeerrApi(url, seerrClient(), { authStore.readSession(profile) })
								lifecycleScope.launch {
									message = "Loggar in …"
									message = SeerrSessionManager(profile, api, authStore).loginWithJellyfin(username.trim(), password.toCharArray()).fold({ user ->
										connectedName = user.displayName
										discoveryStatus = runCatching { SeerrMediaMapper.mapResults(api.discover("trending").body).size }
											.fold({ "Upptäck verifierad · $it titlar hämtade" }, { "Ansluten · Upptäck kunde inte hämtas" })
										"Ansluten som ${user.displayName}"
									}, ::safeLoginError)
									password = ""
								}
							}) { Text("Logga in med Jellyfin") }
							FocusableTvButton(onClick = {
								val url = SeerrUrl.normalize(address, allowHttp).getOrNull() ?: return@FocusableTvButton
								configurations.save(SeerrConfiguration(profile, address, allowHttp))
								val api = OkHttpSeerrApi(url, seerrClient(), { authStore.readSession(profile) })
								lifecycleScope.launch {
									message = "Loggar in …"
									message = SeerrSessionManager(profile, api, authStore).loginLocal(username.trim(), password.toCharArray()).fold({ user ->
										connectedName = user.displayName
										discoveryStatus = runCatching { SeerrMediaMapper.mapResults(api.discover("trending").body).size }
											.fold({ "Upptäck verifierad · $it titlar hämtade" }, { "Ansluten · Upptäck kunde inte hämtas" })
										"Ansluten som ${user.displayName}"
									}, ::safeLoginError)
									password = ""
								}
							}) { Text("Logga in med Seerr-konto") }
							FocusableTvButton(onClick = ::finish) { Text("Avbryt") }
						}
					}
				}
			}
		}
	}

	private fun seerrClient() = OkHttpClient.Builder()
		.connectTimeout(5, TimeUnit.SECONDS)
		.readTimeout(10, TimeUnit.SECONDS)
		.writeTimeout(10, TimeUnit.SECONDS)
		.build()

	private fun safeConnectionError(error: Throwable) = when ((error as? SeerrHttpException)?.status) {
		404 -> "Servern svarar, men API-versionen stöds inte (404)"
		else -> "Anslutningen misslyckades"
	}

	private fun safeLoginError(error: Throwable) = when ((error as? SeerrHttpException)?.status) {
		401 -> "Inloggningen nekades (401). Kontrollera uppgifterna och vald kontotyp."
		403 -> "Kontot saknar behörighet att logga in (403)."
		404 -> "Den valda inloggningsmetoden stöds inte av servern (404)."
		429 -> "För många försök. Vänta en stund (429)."
		in 500..599 -> "Seerr kunde inte slutföra inloggningen (serverfel)."
		else -> if (error is IllegalStateException) "Servern skapade ingen giltig session." else "Inloggningen misslyckades på grund av ett nätverksfel."
	}

	override fun onDestroy() {
		quickConnectSecret?.fill('\u0000')
		quickConnectSecret = null
		super.onDestroy()
	}
}

@androidx.compose.runtime.Composable
private fun SeerrField(value: String, label: String, password: Boolean = false, onChange: (String) -> Unit) {
	val keyboard = LocalSoftwareKeyboardController.current
	Column(Modifier.fillMaxWidth(.55f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
		Text(label, color = Color.White.copy(.8f))
		BasicTextField(
			value = value,
			onValueChange = { onChange(it.replace("\n", "")) },
			modifier = Modifier.fillMaxWidth().background(Color(0xFF171820)).padding(14.dp),
			textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
			visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
			keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = if (password) KeyboardType.Password else KeyboardType.Text, imeAction = ImeAction.Done),
			keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
			singleLine = true,
		)
	}
}
