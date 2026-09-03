package org.jellyfin.androidtv.ui.seerr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.seerr.*
import org.jellyfin.androidtv.seerr.model.*
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinematic.*
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.koin.compose.koinInject
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.search.toLocalMedia
import java.util.UUID

class SeerrDetailsFragment : Fragment() {
	companion object {
		const val ARG_ID = "seerr_id"
		const val ARG_KIND = "seerr_kind"
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		JellyfinTheme {
			val id = requireArguments().getInt(ARG_ID)
			val kind = runCatching { MediaKind.valueOf(requireArguments().getString(ARG_KIND).orEmpty()) }.getOrDefault(MediaKind.Movie)
			val session = koinInject<SessionRepository>()
			val clients = koinInject<SeerrClientProvider>()
			val authStore = koinInject<SeerrAuthStore>()
			val navigation = koinInject<NavigationRepository>()
			val jellyfinApi = koinInject<ApiClient>()
			val currentSession by session.currentSession.collectAsState()
			val profile = currentSession?.let { "${it.serverId}:${it.userId}" }.orEmpty()
			var state by remember(id, kind) { mutableStateOf<DetailsState>(DetailsState.Loading) }
			var retry by remember { mutableIntStateOf(0) }
			LaunchedEffect(id, kind, retry) {
				val connected = clients.authenticated(profile).getOrElse { state = DetailsState.Expired; return@LaunchedEffect }
				SeerrRepository(connected.first).details(kind, id)
					.onSuccess { media ->
						val localId = runCatching {
							withContext(Dispatchers.IO) {
								val itemType = if (kind == MediaKind.Movie) BaseItemKind.MOVIE else BaseItemKind.SERIES
								val candidates = jellyfinApi.itemsApi.getItems(searchTerm = media.title, includeItemTypes = setOf(itemType), recursive = true, fields = ItemRepository.itemFields).content.items
								val merged = MediaAvailabilityResolver.merge(candidates.mapNotNull(::toLocalMedia), listOf(media))
								merged.filterIsInstance<UnifiedMedia.InLibrary>()
									.firstOrNull { it.external != null }
									?.local?.id
							}
						}.getOrNull()
						state = DetailsState.Content(if (localId != null) media.copy(availability = MediaAvailability.InLibrary) else media, connected.second, connected.first, localId)
					}
					.onFailure { state = if (it is SeerrHttpException && it.status == 401) DetailsState.Expired else DetailsState.Error }
			}
			when (val current = state) {
				DetailsState.Loading -> CinematicScaffold { EmptyState("Hämtar information", "Vänta ett ögonblick …") }
				DetailsState.Error -> CinematicScaffold { ErrorState("Informationen kunde inte hämtas", "Seerr svarade inte som väntat.", { retry++ }, "Försök igen") }
				DetailsState.Expired -> CinematicScaffold { EmptyState("Sessionen har gått ut", "Logga in på Seerr igen.", action = { FocusableTvButton(onClick = { startActivity(android.content.Intent(requireContext(), SeerrConnectActivity::class.java)) }) { Text("Logga in igen") } }) }
				is DetailsState.Content -> SeerrDetailsScreen(current.media, current.user, current.api, current.localId, onOpen = { navigation.navigate(Destinations.seerrDetails(it.id, it.kind.name)) }, onOpenLocal = { idValue -> navigation.navigate(Destinations.cinematicItemDetails(UUID.fromString(idValue))) }, onExpired = { authStore.clearSession(profile); state = DetailsState.Expired }, onUpdate = { state = current.copy(media = it) })
			}
		}
	}
}

private sealed interface DetailsState {
	data object Loading : DetailsState
	data object Error : DetailsState
	data object Expired : DetailsState
	data class Content(val media: SeerrMedia, val user: SeerrUser, val api: SeerrApi, val localId: String?) : DetailsState
}

@Composable
private fun SeerrDetailsScreen(media: SeerrMedia, user: SeerrUser, api: SeerrApi, localId: String?, onOpen: (SeerrMedia) -> Unit, onOpenLocal: (String) -> Unit, onExpired: () -> Unit, onUpdate: (SeerrMedia) -> Unit) {
	var dialog by remember(media.id) { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }
	val initialFocus = remember { FocusRequester() }
	LaunchedEffect(media.id, localId) {
		withFrameNanos { }
		runCatching { initialFocus.requestFocus() }
	}
	Box(Modifier.fillMaxSize().background(CinematicColors.Background)) {
		media.backdropUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
		Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(.88f), Color.Transparent))))
		Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
			Text("‹  Jellyfin", color = Color.White, fontSize = 18.sp)
			Spacer(Modifier.height(20.dp))
			Text(media.title, color = Color.White, fontSize = 38.sp)
			if (!media.originalTitle.isNullOrBlank() && media.originalTitle != media.title) Text(media.originalTitle, color = CinematicColors.TextSecondary, fontSize = 14.sp)
			Text(listOfNotNull(media.releaseDate ?: media.year?.toString(), media.runtimeMinutes?.let { "${it / 60} h ${it % 60} min" }, media.certification, media.rating?.let { "★ %.1f".format(it) }).joinToString("  •  "), color = Color.White.copy(.78f))
			if (media.genres.isNotEmpty()) Text(media.genres.joinToString("  "), color = CinematicColors.TextSecondary)
			media.overview?.let { Text(it, color = Color.White.copy(.84f), maxLines = 4, modifier = Modifier.fillMaxWidth(.43f)) }
			StatusChip(media.availability)
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				val allowed = MediaAvailabilityResolver.canRequest(media.availability, true, SeerrPermissions.canRequest(user.permissions, media.kind))
				if (localId != null) FocusableTvButton(onClick = { onOpenLocal(localId) }, modifier = Modifier.focusRequester(initialFocus)) { Text("Visa i biblioteket") }
				else if (allowed) FocusableTvButton(onClick = { dialog = true }, modifier = Modifier.focusRequester(initialFocus)) { Text("Begär") }
				else Text(availabilityLabel(media.availability) ?: "Kan inte begäras", color = CinematicColors.TextSecondary)
			}
			error?.let { Text(it, color = CinematicColors.Warning) }
			if (media.cast.isNotEmpty()) Text("Medverkande  ${media.cast.joinToString(", ")}", color = CinematicColors.TextSecondary, maxLines = 2)
			if (media.studios.isNotEmpty()) Text("Studio / nätverk  ${media.studios.joinToString(", ")}", color = CinematicColors.TextSecondary, maxLines = 1)
			if (media.seasons.any { it.number > 0 }) Text(media.seasons.filter { it.number > 0 }.joinToString("   ") { "${it.name}: ${availabilityLabel(it.availability) ?: "Kan begäras"}" }, color = Color.White.copy(.8f), maxLines = 2)
			if (media.related.isNotEmpty()) {
				Text("Liknande titlar", color = Color.White, fontSize = 19.sp)
				LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) { items(media.related, key = { "${it.kind}:${it.id}" }) { item -> SeerrMediaCard(item, {}, { onOpen(item) }) } }
			}
		}
	}
	if (dialog) RequestDialog(media, api, onDismiss = { dialog = false }, onSuccess = { dialog = false; error = null; onUpdate(media.copy(availability = MediaAvailability.Requested)) }, onExpired = onExpired, onError = { error = it })
}

@Composable
private fun RequestDialog(media: SeerrMedia, api: SeerrApi, onDismiss: () -> Unit, onSuccess: () -> Unit, onExpired: () -> Unit, onError: (String) -> Unit) {
	val cancelFocus = remember { FocusRequester() }
	val scope = rememberCoroutineScope()
	var sending by remember { mutableStateOf(false) }
	val seasons = remember(media) { media.seasons.filter { it.number > 0 } }
	var selected by remember(media.id) { mutableStateOf(if (media.kind == MediaKind.Series) emptySet() else emptySet<Int>()) }
	LaunchedEffect(Unit) {
		withFrameNanos { }
		runCatching { cancelFocus.requestFocus() }
	}
	Dialog(onDismissRequest = { if (!sending) onDismiss() }) {
		Column(Modifier.width(560.dp).background(CinematicColors.Surface, RoundedCornerShape(CinematicDimensions.DialogCorner)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
			Text("Begär ${media.title}", color = Color.White, fontSize = 27.sp)
			Text(if (media.kind == MediaKind.Movie) "Filmen läggs till bland dina begäranden efter din bekräftelse." else "Välj de säsonger du vill begära.", color = CinematicColors.TextSecondary)
			if (media.kind == MediaKind.Series) seasons.forEach { season ->
				val checked = season.number in selected
				val selectable = season.availability == MediaAvailability.Requestable
				Row(Modifier.fillMaxWidth().background(if (checked) Color(0xFF352342) else CinematicColors.SurfaceRaised, RoundedCornerShape(8.dp)).clickable(enabled = selectable && !sending) { selected = if (checked) selected - season.number else selected + season.number }.focusable(selectable).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
					Text(if (checked) "✓ ${season.name}" else season.name, color = Color.White)
					Text(availabilityLabel(season.availability) ?: "Kan begäras", color = CinematicColors.TextSecondary)
				}
			}
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				FocusableTvButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus), enabled = !sending) { Text("Avbryt") }
				FocusableTvButton(onClick = {
					if (media.kind == MediaKind.Series && selected.isEmpty()) return@FocusableTvButton
					sending = true
					scope.launch {
						RequestRepository(api).request(media, selected, confirmed = true).fold(
							onSuccess = { onSuccess() },
							onFailure = { failure ->
								sending = false
								if ((failure as? SeerrHttpException)?.status == 401) onExpired()
								else if ((failure as? SeerrHttpException)?.status == 409) onSuccess()
								else onError(requestError(failure))
							},
						)
					}
				}, enabled = !sending && (media.kind == MediaKind.Movie || selected.isNotEmpty())) { Text(if (sending) "Skickar …" else "Bekräfta begäran") }
			}
		}
	}
}

private fun requestError(error: Throwable): String = when ((error as? SeerrHttpException)?.status) {
	401 -> "Sessionen har gått ut. Logga in igen."
	403 -> "Du saknar behörighet att begära titeln."
	409 -> "Titeln är redan begärd. Statusen uppdateras."
	429 -> "För många försök. Vänta en stund."
	in 500..599 -> "Seerr kunde inte slutföra begäran. Försök igen."
	else -> "Begäran kunde inte skickas. Kontrollera anslutningen."
}
