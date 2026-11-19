package org.jellyfin.androidtv.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
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
import org.koin.compose.koinInject
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.seerr.SeerrClientProvider
import org.jellyfin.androidtv.seerr.SeerrRepository
import org.jellyfin.androidtv.seerr.model.SeerrMedia
import org.jellyfin.androidtv.ui.seerr.SeerrMediaCard
import org.jellyfin.androidtv.ui.seerr.StatusChip
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
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
			var groupIndex by rememberSaveable { mutableIntStateOf(0) }
			var rowPositions by rememberSaveable { mutableStateOf(IntArray(0)) }
			var displayedBackdropUrl by remember { mutableStateOf<String?>(null) }
			val contentFocus = remember { FocusRequester() }
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
				if (rowPositions.size != rows.size) rowPositions = IntArray(rows.size)
				val starts = groupStarts(rows.size)
				val restoredGroup = groupIndex.coerceIn(0, starts.lastIndex.coerceAtLeast(0))
				val restored = resolveDiscoverSelectionInGroup(rows, restoredGroup, selectedId, selectedKind)
					?: resolveDiscoverSelection(rows, selectedId, selectedKind)
				focused = restored ?: rows.firstOrNull()?.second?.firstOrNull()
				focused?.let { selected ->
					selectedId = selected.media.id
					selectedKind = selected.media.kind.name
					val row = rows.indexOfFirst { (_, entries) -> selected in entries }.coerceAtLeast(0)
					val selectedStillInRestoredGroup = rows
						.drop(starts.getOrElse(restoredGroup) { 0 })
						.take(DiscoverLayoutContract.RowsPerGroup)
						.any { (_, entries) -> selected in entries }
					if (!selectedStillInRestoredGroup) groupIndex = starts.indexOfLast { it <= row }.coerceAtLeast(0)
				}
			}
			LaunchedEffect(state, groupIndex) {
				if (state is DiscoverState.Content && focused != null) {
					withFrameNanos { }
					runCatching { contentFocus.requestFocus() }
				}
			}
			val selectedKey = focused?.media?.let(::discoverMediaKey)
			val selectedBackdropUrl = focused?.media?.backdropUrl
			LaunchedEffect(selectedKey, selectedBackdropUrl) {
				if (selectedBackdropUrl == null) return@LaunchedEffect
				if (displayedBackdropUrl != null) delay(DiscoverLayoutContract.BackdropDebounceMillis)
				displayedBackdropUrl = selectedBackdropUrl
			}
			CinematicScaffold {
				val heroPresentation = focused?.media?.let(::discoverHeroPresentation)
				Box(Modifier.fillMaxSize()) {
					Crossfade(targetState = displayedBackdropUrl, animationSpec = tween(DiscoverLayoutContract.BackdropFadeMillis), label = "DiscoverBackdrop") { backdropUrl ->
						backdropUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
					}
					Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black, Color.Black.copy(.82f), Color.Transparent))))
					Column(Modifier.fillMaxSize()) {
						MainToolbar(MainToolbarActiveButton.Discover, downFocusRequester = contentFocus)
						when (val current = state) {
							DiscoverState.Loading -> Box(Modifier.weight(1f)) { EmptyState("Upptäck", "Hämtar filmer och serier …") }
							DiscoverState.Disconnected -> Box(Modifier.weight(1f)) { EmptyState("Anslut till Seerr", getString(R.string.jellyfin_tv_seerr_not_connected), action = { FocusableTvButton(onClick = { startActivity(android.content.Intent(requireContext(), org.jellyfin.androidtv.ui.seerr.SeerrConnectActivity::class.java)) }) { Text("Anslut Seerr") } }) }
							DiscoverState.Error -> Box(Modifier.weight(1f)) { ErrorState("Upptäck kunde inte hämtas", "Kontrollera anslutningen och försök igen.", ::load, "Försök igen") }
							is DiscoverState.Content -> {
								DiscoverBrowseHeader(heroPresentation)
								val starts = groupStarts(current.rows.size)
								val safeGroup = groupIndex.coerceIn(0, starts.lastIndex.coerceAtLeast(0))
								val groupStart = starts.getOrElse(safeGroup) { 0 }
								val visibleRows = current.rows.drop(groupStart).take(DiscoverLayoutContract.RowsPerGroup)
								Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(DiscoverLayoutContract.RowGap)) {
									visibleRows.forEachIndexed { slot, (title, media) ->
										val rowIndex = groupStart + slot
										val initialPosition = rowPositions.getOrElse(rowIndex) { 0 }.coerceIn(0, media.lastIndex.coerceAtLeast(0))
										val rowState = remember(rowIndex) { LazyListState(initialPosition) }
										Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
											Text(title, color = Color.White, fontSize = 17.sp, maxLines = 1, modifier = Modifier.height(22.dp).padding(horizontal = 40.dp))
											LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
											itemsIndexed(media, key = { _, item -> "${item.media.kind}:${item.media.id}" }) { itemIndex, item ->
											val selected = item.media.id == selectedId && item.media.kind.name == selectedKind
											SeerrMediaCard(
												item.media,
												onFocus = {
													rowPositions = rowPositions.copyOf(current.rows.size).also { it[rowIndex] = itemIndex }
													focused = item
													selectedId = item.media.id
													selectedKind = item.media.kind.name
												},
												onClick = { openDiscoverEntry(item, navigation) },
												modifier = (if (selected || (selectedId == null && rowIndex == 0 && itemIndex == 0)) Modifier.focusRequester(contentFocus) else Modifier)
													.onPreviewKeyEvent { event ->
														if (event.type != KeyEventType.KeyDown || event.key !in setOf(Key.DirectionUp, Key.DirectionDown)) return@onPreviewKeyEvent false
														val nextGroup = (safeGroup + if (event.key == Key.DirectionDown) 1 else -1).coerceIn(0, starts.lastIndex)
														if (nextGroup == safeGroup) return@onPreviewKeyEvent false
														val nextRow = (starts[nextGroup] + slot).coerceAtMost(current.rows.lastIndex)
														val nextMedia = current.rows[nextRow].second
														val nextItem = nextMedia[rowPositions.getOrElse(nextRow) { 0 }.coerceIn(0, nextMedia.lastIndex)]
														groupIndex = nextGroup
														focused = nextItem
														selectedId = nextItem.media.id
														selectedKind = nextItem.media.kind.name
														true
													},
												cardWidth = DiscoverLayoutContract.CardWidth,
												reserveMetadataSpace = true,
												landscape = true,
											)
											}
											}
										}
									}
								}
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

internal data class DiscoverEntry(val media: SeerrMedia, val localId: String? = null)

internal data class DiscoverHeroPresentation(
	val mediaKey: String,
	val title: String,
	val metadata: String,
	val description: String,
	val backdropUrl: String?,
	val availability: org.jellyfin.androidtv.seerr.model.MediaAvailability,
)

internal fun discoverHeroPresentation(media: SeerrMedia) = DiscoverHeroPresentation(
	mediaKey = discoverMediaKey(media),
	title = media.title,
	metadata = listOfNotNull(media.year?.toString(), if (media.kind.name == "Movie") "Film" else "Serie").joinToString("  •  "),
	description = media.overview.orEmpty(),
	backdropUrl = media.backdropUrl,
	availability = media.availability,
)

internal fun resolveDiscoverSelection(rows: List<Pair<String, List<DiscoverEntry>>>, selectedId: Int?, selectedKind: String?) =
	rows.asSequence().flatMap { it.second.asSequence() }
		.firstOrNull { it.media.id == selectedId && it.media.kind.name == selectedKind }

internal fun resolveDiscoverSelectionInGroup(rows: List<Pair<String, List<DiscoverEntry>>>, groupIndex: Int, selectedId: Int?, selectedKind: String?): DiscoverEntry? {
	val start = groupStarts(rows.size).getOrNull(groupIndex) ?: return null
	return rows.asSequence().drop(start).take(DiscoverLayoutContract.RowsPerGroup)
		.flatMap { it.second.asSequence() }
		.firstOrNull { it.media.id == selectedId && it.media.kind.name == selectedKind }
}

internal object DiscoverLayoutContract {
	val CardWidth = 112.dp
	val HeaderHeight = 112.dp
	val RowGap = 10.dp
	const val RowsPerGroup = 2
	const val BackdropDebounceMillis = 180L
	const val BackdropFadeMillis = 650
	const val TitleMaxLines = 2
	const val DescriptionMaxLines = 2
}

internal fun discoverMediaKey(media: SeerrMedia) = "${media.kind}:${media.id}"

internal fun groupStarts(rowCount: Int): List<Int> {
	if (rowCount <= DiscoverLayoutContract.RowsPerGroup) return listOf(0)
	val starts = (0 until rowCount step DiscoverLayoutContract.RowsPerGroup).toMutableList()
	if (starts.last() + DiscoverLayoutContract.RowsPerGroup > rowCount) starts[starts.lastIndex] = rowCount - DiscoverLayoutContract.RowsPerGroup
	return starts.distinct()
}

private fun openDiscoverEntry(entry: DiscoverEntry, navigation: NavigationRepository) {
	val localId = entry.localId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
	if (localId != null) navigation.navigate(Destinations.cinematicItemDetails(localId))
	else navigation.navigate(Destinations.seerrDetails(entry.media.id, entry.media.kind.name))
}

@Composable
private fun DiscoverBrowseHeader(presentation: DiscoverHeroPresentation?) {
	Box(Modifier.fillMaxWidth().height(DiscoverLayoutContract.HeaderHeight).padding(horizontal = 40.dp)) {
		Crossfade(presentation, animationSpec = tween(180), label = "DiscoverHeader") { current ->
			if (current != null) Column(Modifier.fillMaxHeight().fillMaxWidth(.52f)) {
				Box(Modifier.height(46.dp), contentAlignment = Alignment.BottomStart) { Text(current.title, color = Color.White, fontSize = 22.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
				Box(Modifier.height(18.dp), contentAlignment = Alignment.CenterStart) { Text(current.metadata, color = Color.White.copy(.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
				Box(Modifier.height(32.dp), contentAlignment = Alignment.TopStart) { Text(current.description, color = Color.White.copy(.86f), fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
				Row(Modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					StatusChip(current.availability)
					Text("OK  Öppna titel", color = Color.White.copy(.82f), fontSize = 12.sp)
				}
			}
		}
	}
}
