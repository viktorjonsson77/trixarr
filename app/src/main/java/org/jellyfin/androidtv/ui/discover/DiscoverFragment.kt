package org.jellyfin.androidtv.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.cinematic.CinematicScaffold
import org.jellyfin.androidtv.ui.cinematic.EmptyState
import org.jellyfin.androidtv.ui.cinematic.ErrorState
import org.jellyfin.androidtv.ui.cinematic.FocusableTvButton
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.browsing.PersonalMediaMode
import org.koin.compose.koinInject
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.seerr.SeerrClientProvider
import org.jellyfin.androidtv.seerr.SeerrRepository
import org.jellyfin.androidtv.seerr.model.SeerrMedia
import org.jellyfin.androidtv.ui.seerr.SeerrMediaCard
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.search.toLocalMedia
import org.jellyfin.androidtv.seerr.MediaAvailabilityResolver
import org.jellyfin.androidtv.seerr.UnifiedMedia
import java.util.UUID

class DiscoverFragment : Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		JellyfinTheme {
			val navigation = koinInject<NavigationRepository>()
			val session = koinInject<SessionRepository>()
			val clients = koinInject<SeerrClientProvider>()
			val jellyfinApi = koinInject<ApiClient>()
			val currentSession by session.currentSession.collectAsState()
			val profile = currentSession?.let { "${it.serverId}:${it.userId}" }.orEmpty()
			var state by remember(profile) { mutableStateOf<DiscoverState>(DiscoverState.Loading) }
			var focused by remember { mutableStateOf<DiscoverEntry?>(null) }
			var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
			var selectedKind by rememberSaveable { mutableStateOf<String?>(null) }
			val contentFocus = remember { FocusRequester() }
			val columnState = rememberLazyListState()
			fun load() { state = DiscoverState.Loading }
			LaunchedEffect(profile, state is DiscoverState.Loading) {
				if (state !is DiscoverState.Loading) return@LaunchedEffect
				val authenticated = clients.authenticated(profile).getOrElse { state = DiscoverState.Disconnected; return@LaunchedEffect }
				val repository = SeerrRepository(authenticated.first)
				val sections = listOf("trending", "movies", "tv", "movies/upcoming", "tv/upcoming")
				val values = sections.map { section -> async { section to repository.discover(section).getOrDefault(emptyList()) } }.awaitAll().toMap()
				val rawRows = listOf(
					"Trendigt just nu" to values["trending"].orEmpty(),
					"Populära filmer" to values["movies"].orEmpty(),
					"Populära serier" to values["tv"].orEmpty(),
					"Kommande filmer" to values["movies/upcoming"].orEmpty(),
					"Kommande serier" to values["tv/upcoming"].orEmpty(),
				).filter { it.second.isNotEmpty() }
				val local = runCatching {
					withContext(Dispatchers.IO) {
						jellyfinApi.itemsApi.getItems(includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES), recursive = true, limit = 500, fields = ItemRepository.itemFields).content.items.mapNotNull(::toLocalMedia)
					}
				}.getOrDefault(emptyList())
				val rows = rawRows.map { (title, media) ->
					title to MediaAvailabilityResolver.merge(local, media).mapNotNull { merged ->
						when (merged) {
							is UnifiedMedia.InLibrary -> merged.external?.let { DiscoverEntry(it.copy(availability = org.jellyfin.androidtv.seerr.model.MediaAvailability.InLibrary), merged.local.id) }
							is UnifiedMedia.External -> DiscoverEntry(merged.media)
						}
					}
				}.filter { it.second.isNotEmpty() }
				state = if (rows.isEmpty()) DiscoverState.Error else DiscoverState.Content(rows)
				val restored = rows.asSequence().flatMap { it.second.asSequence() }
					.firstOrNull { it.media.id == selectedId && it.media.kind.name == selectedKind }
				focused = restored ?: rows.firstOrNull()?.second?.firstOrNull()
				focused?.let { selected ->
					selectedId = selected.media.id
					selectedKind = selected.media.kind.name
				}
			}
			LaunchedEffect(state) {
				if (state is DiscoverState.Content && focused != null) {
					withFrameNanos { }
					runCatching { contentFocus.requestFocus() }
				}
			}
			CinematicScaffold {
				Box(Modifier.fillMaxSize()) {
					focused?.media?.backdropUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
					Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(.82f), Color.Transparent))))
					LazyColumn(Modifier.fillMaxSize(), state = columnState, verticalArrangement = Arrangement.spacedBy(14.dp)) {
						item { MainToolbar(MainToolbarActiveButton.Discover, downFocusRequester = contentFocus) }
						when (val current = state) {
							DiscoverState.Loading -> item { EmptyState("Upptäck", "Hämtar filmer och serier …") }
							DiscoverState.Disconnected -> item { EmptyState("Anslut till Seerr", getString(R.string.jellyfin_tv_seerr_not_connected), action = { FocusableTvButton(onClick = { startActivity(android.content.Intent(requireContext(), org.jellyfin.androidtv.ui.seerr.SeerrConnectActivity::class.java)) }) { Text("Anslut Seerr") } }) }
							DiscoverState.Error -> item { ErrorState("Upptäck kunde inte hämtas", "Kontrollera anslutningen och försök igen.", ::load, "Försök igen") }
							is DiscoverState.Content -> {
								focused?.let { hero -> item { DiscoverHero(hero.media) { openDiscoverEntry(hero, navigation) } } }
								itemsIndexed(current.rows, key = { _, row -> row.first }) { rowIndex, (title, media) ->
									Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
										Text(title, color = Color.White, fontSize = 19.sp, modifier = Modifier.padding(horizontal = 40.dp))
										LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
										itemsIndexed(media, key = { _, item -> "${item.media.kind}:${item.media.id}" }) { itemIndex, item ->
											val selected = item.media.id == selectedId && item.media.kind.name == selectedKind
											SeerrMediaCard(
												item.media,
												onFocus = {
													focused = item
													selectedId = item.media.id
													selectedKind = item.media.kind.name
												},
												onClick = { openDiscoverEntry(item, navigation) },
												modifier = if (selected || (selectedId == null && rowIndex == 0 && itemIndex == 0)) Modifier.focusRequester(contentFocus) else Modifier,
											)
										}
										}
									}
								}
								item { Row(Modifier.padding(horizontal = 40.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
									FocusableTvButton(onClick = { navigation.navigate(Destinations.seerrRequests) }) { Text("Mina begäranden") }
									FocusableTvButton(onClick = { navigation.navigate(Destinations.personalMedia(PersonalMediaMode.History)) }) { Text(getString(R.string.jellyfin_tv_watch_history)) }
								} }
							}
						}
					}
				}
			}
		}
	}
}

private sealed interface DiscoverState {
	data object Loading : DiscoverState
	data object Disconnected : DiscoverState
	data object Error : DiscoverState
	data class Content(val rows: List<Pair<String, List<DiscoverEntry>>>) : DiscoverState
}

private data class DiscoverEntry(val media: SeerrMedia, val localId: String? = null)

private fun openDiscoverEntry(entry: DiscoverEntry, navigation: NavigationRepository) {
	val localId = entry.localId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
	if (localId != null) navigation.navigate(Destinations.cinematicItemDetails(localId))
	else navigation.navigate(Destinations.seerrDetails(entry.media.id, entry.media.kind.name))
}

@Composable
private fun DiscoverHero(media: SeerrMedia, onDetails: () -> Unit) {
	Column(Modifier.padding(horizontal = 40.dp).fillMaxWidth(.48f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text(media.title, color = Color.White, fontSize = 34.sp)
		Text(listOfNotNull(media.year?.toString(), if (media.kind.name == "Movie") "Film" else "Serie").joinToString("  •  "), color = Color.White.copy(.78f))
		media.overview?.let { Text(it, color = Color.White.copy(.82f), maxLines = 3) }
		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			FocusableTvButton(onClick = onDetails) { Text(if (media.availability == org.jellyfin.androidtv.seerr.model.MediaAvailability.Requestable) "Begär" else "Mer information") }
		}
	}
}
