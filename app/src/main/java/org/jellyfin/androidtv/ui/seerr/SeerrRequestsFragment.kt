package org.jellyfin.androidtv.ui.seerr

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.seerr.SeerrClientProvider
import org.jellyfin.androidtv.seerr.SeerrPermissions
import org.jellyfin.androidtv.seerr.SeerrRepository
import org.jellyfin.androidtv.seerr.model.SeerrRequest
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinematic.*
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class SeerrRequestsFragment : Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: android.os.Bundle?) = content {
		JellyfinTheme {
			val session = koinInject<SessionRepository>()
			val clients = koinInject<SeerrClientProvider>()
			val navigation = koinInject<NavigationRepository>()
			val currentSession by session.currentSession.collectAsState()
			val profile = currentSession?.let { "${it.serverId}:${it.userId}" }.orEmpty()
			var state by remember { mutableStateOf<RequestsState>(RequestsState.Loading) }
			val firstRequest = remember { FocusRequester() }
			var retry by remember { mutableIntStateOf(0) }
			LaunchedEffect(profile, retry) {
				val connected = clients.authenticated(profile).getOrElse { state = RequestsState.Expired; return@LaunchedEffect }
				if (!SeerrPermissions.canViewOwnRequests(connected.second.permissions)) { state = RequestsState.Forbidden; return@LaunchedEffect }
				SeerrRepository(connected.first).requests(connected.second.id)
					.onSuccess { state = if (it.isEmpty()) RequestsState.Empty else RequestsState.Content(it) }
					.onFailure { state = RequestsState.Error }
			}
			LaunchedEffect(state) { if (state is RequestsState.Content) { withFrameNanos { }; runCatching { firstRequest.requestFocus() } } }
			CinematicScaffold {
				LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
					item { MainToolbar(MainToolbarActiveButton.Discover, downFocusRequester = firstRequest) }
					item { Text("Mina begäranden", color = Color.White, fontSize = 34.sp, modifier = Modifier.padding(horizontal = 40.dp)) }
					when (val current = state) {
						RequestsState.Loading -> item { EmptyState("Hämtar begäranden", "Vänta ett ögonblick …") }
						RequestsState.Empty -> item { EmptyState("Inga begäranden", "Dina kommande begäranden visas här.") }
						RequestsState.Forbidden -> item { EmptyState("Mina begäranden är inte tillgängliga", "Kontot saknar behörighet att läsa begäranden.") }
						RequestsState.Expired -> item { EmptyState("Sessionen har gått ut", "Logga in på Seerr igen.") }
						RequestsState.Error -> item { ErrorState("Begäranden kunde inte hämtas", "Jellyfin fungerar fortfarande som vanligt.", { retry++ }, "Försök igen") }
						is RequestsState.Content -> items(current.items, key = { it.id }) { request -> RequestRow(request, if (request == current.items.first()) Modifier.focusRequester(firstRequest) else Modifier) { navigation.navigate(Destinations.seerrDetails(request.media.id, request.media.kind.name)) } }
					}
				}
			}
		}
	}
}

private sealed interface RequestsState {
	data object Loading : RequestsState
	data object Empty : RequestsState
	data object Forbidden : RequestsState
	data object Expired : RequestsState
	data object Error : RequestsState
	data class Content(val items: List<SeerrRequest>) : RequestsState
}

@Composable
private fun RequestRow(request: SeerrRequest, modifier: Modifier = Modifier, onClick: () -> Unit) {
	FocusableTvButton(onClick = onClick, modifier = modifier.padding(horizontal = 40.dp).fillMaxWidth(.66f)) {
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
			AsyncImage(model = request.media.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(64.dp).aspectRatio(2f / 3f).background(CinematicColors.SurfaceRaised, RoundedCornerShape(8.dp)))
			Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text(request.media.title, color = Color.White, fontSize = 18.sp)
				Text(requestStatusLabel(request.status), color = CinematicColors.Warning)
				Text(formatDate(request.createdAt), color = CinematicColors.TextSecondary, fontSize = 12.sp)
				if (request.seasons.isNotEmpty()) Text(request.seasons.sorted().joinToString(prefix = "Säsonger: "), color = CinematicColors.TextSecondary, fontSize = 12.sp)
			}
		}
	}
}

private fun formatDate(value: String): String = runCatching {
	DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag("sv-SE"))
		.format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrDefault(value.take(10))
