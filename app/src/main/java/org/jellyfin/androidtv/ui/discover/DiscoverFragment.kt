package org.jellyfin.androidtv.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
			var focusedBounds by remember { mutableStateOf<Rect?>(null) }
			var dwellMediaKey by remember { mutableStateOf<String?>(null) }
			var screenActive by remember { mutableStateOf(true) }
			var displayedBackdropUrl by remember { mutableStateOf<String?>(null) }
			val contentFocus = remember { FocusRequester() }
			val columnState = rememberLazyListState()
			val coroutineScope = rememberCoroutineScope()
			val dwellController = remember(coroutineScope) { DiscoverDwellController(coroutineScope) { dwellMediaKey = it } }
			DisposableEffect(Unit) {
				val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
					screenActive = event != androidx.lifecycle.Lifecycle.Event.ON_PAUSE && event != androidx.lifecycle.Lifecycle.Event.ON_STOP
					if (!screenActive) dwellMediaKey = null
				}
				lifecycle.addObserver(observer)
				onDispose { lifecycle.removeObserver(observer); dwellController.dismiss() }
			}
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
				val restored = resolveDiscoverSelection(rows, selectedId, selectedKind)
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
			val selectedKey = focused?.media?.let(::discoverMediaKey)
			val selectedBackdropUrl = focused?.media?.backdropUrl
			LaunchedEffect(selectedKey, selectedBackdropUrl) {
				if (selectedBackdropUrl == null) return@LaunchedEffect
				if (displayedBackdropUrl != null) delay(DiscoverLayoutContract.BackdropDebounceMillis)
				displayedBackdropUrl = selectedBackdropUrl
			}
			LaunchedEffect(selectedKey, focusedBounds, columnState.isScrollInProgress, screenActive, state) {
				dwellMediaKey = null
				dwellController.update(selectedKey, focusedBounds != null, columnState.isScrollInProgress, screenActive && state is DiscoverState.Content)
			}
			CinematicScaffold {
				val heroPresentation = focused?.media?.let(::discoverHeroPresentation)
				var rootSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
				Box(Modifier.fillMaxSize().onGloballyPositioned { rootSize = it.size }.onPreviewKeyEvent { event ->
					if (event.type == KeyEventType.KeyDown && event.key in DiscoverLayoutContract.DismissKeys) { dwellController.dismiss(); dwellMediaKey = null }
					false
				}) {
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
								LazyColumn(Modifier.fillMaxWidth().weight(1f), state = columnState, verticalArrangement = Arrangement.spacedBy(14.dp)) {
								itemsIndexed(current.rows, key = { _, row -> row.first }) { rowIndex, (title, media) ->
									Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
										Text(title, color = Color.White, fontSize = 19.sp, modifier = Modifier.padding(horizontal = 40.dp))
										LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
										itemsIndexed(media, key = { _, item -> "${item.media.kind}:${item.media.id}" }) { itemIndex, item ->
											val selected = item.media.id == selectedId && item.media.kind.name == selectedKind
											SeerrMediaCard(
												item.media,
								onFocus = {
													dwellController.dismiss(); dwellMediaKey = null
													focusedBounds = null
													focused = item
													selectedId = item.media.id
													selectedKind = item.media.kind.name
												},
												onClick = { openDiscoverEntry(item, navigation) },
												modifier = (if (selected || (selectedId == null && rowIndex == 0 && itemIndex == 0)) Modifier.focusRequester(contentFocus) else Modifier)
													.onGloballyPositioned { coordinates -> if (item.media.let(::discoverMediaKey) == selectedKey) focusedBounds = coordinates.boundsInRoot() },
												cardWidth = DiscoverLayoutContract.CardWidth,
												reserveMetadataSpace = true,
											)
										}
										}
									}
								}
								item { Row(Modifier.padding(horizontal = 40.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
									FocusableTvButton(onClick = { navigation.navigate(Destinations.seerrRequests) }) { Text("Mina begäranden") }
									FocusableTvButton(onClick = { navigation.navigate(Destinations.personalMedia(PersonalMediaMode.History)) }) { Text(getString(R.string.jellyfin_tv_watch_history)) }
								} }
								item { Spacer(Modifier.height(220.dp)) }
								}
							}
						}
					}
					val density = LocalDensity.current
					val popup = remember(focusedBounds, rootSize, density) {
						with(density) { focusedBounds?.let { calculateFloatingInfoPosition(it, rootSize.width.toFloat(), rootSize.height.toFloat(), DiscoverLayoutContract.PopupWidth.toPx(), DiscoverLayoutContract.PopupHeight.toPx(), DiscoverLayoutContract.SafeMargin.toPx(), DiscoverLayoutContract.AnchorGap.toPx()) } }
					}
					AnimatedVisibility(
						visible = dwellMediaKey != null && dwellMediaKey == heroPresentation?.mediaKey && popup != null,
						enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = .98f),
						exit = fadeOut(tween(90)),
					) {
						if (heroPresentation != null && popup != null) FloatingDiscoverInfo(heroPresentation, popup)
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

internal object DiscoverLayoutContract {
	val CardWidth = 104.dp
	val PopupWidth = 360.dp
	val PopupHeight = 145.dp
	val SafeMargin = 32.dp
	val AnchorGap = 12.dp
	const val DwellMillis = 800L
	const val BackdropDebounceMillis = 180L
	const val BackdropFadeMillis = 900
	const val TitleMaxLines = 2
	const val DescriptionMaxLines = 2
	val DismissKeys = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.Back)
}

internal fun discoverMediaKey(media: SeerrMedia) = "${media.kind}:${media.id}"

internal class DiscoverDwellController(
	private val scope: CoroutineScope,
	private val onShow: (String) -> Unit,
) {
	private var job: Job? = null
	private var generation = 0L
	var visibleKey: String? = null
		private set

	fun update(key: String?, boundsMeasured: Boolean, scrolling: Boolean, active: Boolean) {
		dismiss()
		if (key == null || !boundsMeasured || scrolling || !active) return
		val expectedGeneration = generation
		job = scope.launch {
			delay(DiscoverLayoutContract.DwellMillis)
			if (expectedGeneration == generation) {
				visibleKey = key
				onShow(key)
			}
		}
	}

	fun dismiss() {
		generation++
		job?.cancel()
		job = null
		visibleKey = null
	}
}

internal data class FloatingInfoPosition(val x: Float, val y: Float, val anchorX: Float, val above: Boolean)

internal fun calculateFloatingInfoPosition(card: Rect, rootWidth: Float, rootHeight: Float, popupWidth: Float, popupHeight: Float, safe: Float, gap: Float): FloatingInfoPosition {
	val x = (card.center.x - popupWidth / 2f).coerceIn(safe, (rootWidth - safe - popupWidth).coerceAtLeast(safe))
	val aboveY = card.top - popupHeight - gap
	val belowY = card.bottom + gap
	val above = aboveY >= safe || belowY + popupHeight > rootHeight - safe
	val y = (if (above) aboveY else belowY).coerceIn(safe, (rootHeight - safe - popupHeight).coerceAtLeast(safe))
	return FloatingInfoPosition(x, y, (card.center.x - x).coerceIn(18f, popupWidth - 18f), above)
}

private fun openDiscoverEntry(entry: DiscoverEntry, navigation: NavigationRepository) {
	val localId = entry.localId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
	if (localId != null) navigation.navigate(Destinations.cinematicItemDetails(localId))
	else navigation.navigate(Destinations.seerrDetails(entry.media.id, entry.media.kind.name))
}

@Composable
private fun FloatingDiscoverInfo(presentation: DiscoverHeroPresentation, position: FloatingInfoPosition) {
	Box(Modifier.offset { IntOffset(position.x.toInt(), position.y.toInt()) }.width(DiscoverLayoutContract.PopupWidth).height(DiscoverLayoutContract.PopupHeight + 10.dp)) {
		Column(Modifier.width(DiscoverLayoutContract.PopupWidth).height(DiscoverLayoutContract.PopupHeight).shadow(10.dp, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(Color(0xD91A2035)).border(1.dp, Color(0xFFB99CFF), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
			Text(presentation.title, color = Color.White, fontSize = 17.sp, lineHeight = 19.sp, maxLines = DiscoverLayoutContract.TitleMaxLines, overflow = TextOverflow.Ellipsis)
			if (presentation.metadata.isNotBlank()) Text(presentation.metadata, color = Color.White.copy(.82f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
			if (presentation.description.isNotBlank()) Text(presentation.description, color = Color.White.copy(.86f), fontSize = 12.sp, lineHeight = 15.sp, maxLines = DiscoverLayoutContract.DescriptionMaxLines, overflow = TextOverflow.Ellipsis)
			Spacer(Modifier.weight(1f))
			Text("OK  Mer information", color = Color.White.copy(.9f), fontSize = 12.sp)
		}
		Canvas(Modifier.offset { IntOffset((position.anchorX - 8.dp.toPx()).toInt(), if (position.above) DiscoverLayoutContract.PopupHeight.roundToPx() - 1 else -9.dp.roundToPx()) }.size(16.dp, 10.dp)) {
			val path = Path().apply { if (position.above) { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height) } else { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height) }; close() }
			drawPath(path, Color(0xD91A2035))
		}
	}
}
