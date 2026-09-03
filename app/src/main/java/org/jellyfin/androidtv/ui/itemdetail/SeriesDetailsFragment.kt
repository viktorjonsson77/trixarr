package org.jellyfin.androidtv.ui.itemdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.CinematicItemContextMenu
import org.jellyfin.androidtv.ui.home.episodeLabel
import org.jellyfin.androidtv.ui.home.playbackProgress
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.sdk.TrailerUtils
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.koin.android.ext.android.inject
import java.util.Locale
import java.util.UUID

data class CinematicSeriesState(
	val series: BaseItemDto? = null,
	val seasons: List<BaseItemDto> = emptyList(),
	val selectedSeason: BaseItemDto? = null,
	val episodes: List<BaseItemDto> = emptyList(),
	val nextUp: BaseItemDto? = null,
	val loading: Boolean = true,
)

enum class SeriesPrimaryAction { Continue, PlayNext, Trailer, Favorite, Watched }

fun seriesPrimaryActions(nextUp: BaseItemDto?, hasTrailer: Boolean): List<SeriesPrimaryAction> = buildList {
	if (nextUp != null) add(if ((nextUp.userData?.playbackPositionTicks ?: 0L) > 0L) SeriesPrimaryAction.Continue else SeriesPrimaryAction.PlayNext)
	if (hasTrailer) add(SeriesPrimaryAction.Trailer)
	add(SeriesPrimaryAction.Favorite)
	add(SeriesPrimaryAction.Watched)
}

fun orderedSeasons(seasons: Collection<BaseItemDto>): List<BaseItemDto> = seasons.sortedWith(
	compareBy<BaseItemDto> { it.indexNumber ?: Int.MAX_VALUE }.thenBy { it.name.orEmpty() }
)

class SeriesDetailsFragment : Fragment() {
	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val state = MutableStateFlow(CinematicSeriesState())

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		val current by state.collectAsState()
		JellyfinTheme { CinematicSeriesScreen(current, ::selectSeason) }
	}

	override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val id = UUID.fromString(requireArguments().getString("ItemId"))
		lifecycleScope.launch {
			val loaded = withContext(Dispatchers.IO) {
				val series = api.userLibraryApi.getItem(id).content
				val seasons = orderedSeasons(api.tvShowsApi.getSeasons(seriesId = id, fields = ItemRepository.itemFields).content.items)
				val selected = seasons.firstOrNull()
				val episodes = selected?.let { api.tvShowsApi.getEpisodes(seriesId = id, seasonId = it.id, fields = ItemRepository.itemFields).content.items } ?: emptyList()
				val next = api.tvShowsApi.getNextUp(seriesId = id, fields = ItemRepository.itemFields, limit = 1).content.items.firstOrNull()
				CinematicSeriesState(series, seasons, selected, episodes, next, false)
			}
			state.value = loaded
			loaded.series?.let(backgroundService::setBackground)
		}
	}

	private fun selectSeason(season: BaseItemDto) {
		val series = state.value.series ?: return
		lifecycleScope.launch {
			val episodes = withContext(Dispatchers.IO) {
				api.tvShowsApi.getEpisodes(seriesId = series.id, seasonId = season.id, fields = ItemRepository.itemFields).content.items
			}
			state.value = state.value.copy(selectedSeason = season, episodes = episodes)
		}
	}
}

@Composable
private fun CinematicSeriesScreen(state: CinematicSeriesState, onSeasonSelected: (BaseItemDto) -> Unit) {
	val series = state.series ?: return
	val context = LocalContext.current
	val playbackHelper = org.koin.compose.koinInject<PlaybackHelper>()
	val mutationRepository = org.koin.compose.koinInject<ItemMutationRepository>()
	val itemLauncher = org.koin.compose.koinInject<ItemLauncher>()
	val api = org.koin.compose.koinInject<ApiClient>()
	val playbackLauncher = org.koin.compose.koinInject<org.jellyfin.androidtv.ui.playback.PlaybackLauncher>()
	val scope = androidx.compose.runtime.rememberCoroutineScope()
	var contextItem by remember { mutableStateOf<BaseItemDto?>(null) }
	val hasTrailer = remember(series) { TrailerUtils.hasPlayableTrailers(context, series) }
	val actionColors = ButtonDefaults.colors(
		containerColor = Color(0xD91A1B1F),
		contentColor = Color.White,
		focusedContainerColor = Color(0xFF432453),
		focusedContentColor = Color.White,
	)
	LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
		item {
			Column(Modifier.padding(start = 48.dp, top = 54.dp, end = 48.dp).width(900.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(series.name.orEmpty(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
				val facts = listOfNotNull(series.productionYear?.toString(), series.officialRating, series.communityRating?.let { String.format(Locale.ROOT, "★ %.1f", it) }, series.genres?.take(3)?.joinToString(" · ")).filter { it.isNotBlank() }
				if (facts.isNotEmpty()) Text(facts.joinToString("   "), color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
				series.overview?.let { Text(it, color = Color.White.copy(alpha = .9f), fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis) }
				Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					seriesPrimaryActions(state.nextUp, hasTrailer).forEach { action ->
						val label = when (action) {
							SeriesPrimaryAction.Continue -> stringResource(R.string.jellyfin_tv_resume)
							SeriesPrimaryAction.PlayNext -> stringResource(R.string.jellyfin_tv_play_next_episode)
							SeriesPrimaryAction.Trailer -> stringResource(R.string.lbl_play_trailers)
							SeriesPrimaryAction.Favorite -> if (series.userData?.isFavorite == true) stringResource(R.string.lbl_remove_favorite) else stringResource(R.string.lbl_add_favorite)
							SeriesPrimaryAction.Watched -> if (series.userData?.played == true) stringResource(R.string.lbl_mark_unplayed) else stringResource(R.string.lbl_mark_played)
						}
						Button(colors = actionColors, onClick = {
							when (action) {
								SeriesPrimaryAction.Continue, SeriesPrimaryAction.PlayNext -> state.nextUp?.let { playbackHelper.retrieveAndPlay(it.id, false, if (action == SeriesPrimaryAction.Continue) (it.userData?.playbackPositionTicks ?: 0L) / 10_000L else 0L, context) }
								SeriesPrimaryAction.Favorite -> scope.launch { mutationRepository.setFavorite(series.id, series.userData?.isFavorite != true) }
								SeriesPrimaryAction.Watched -> scope.launch { mutationRepository.setPlayed(series.id, series.userData?.played != true) }
								SeriesPrimaryAction.Trailer -> scope.launch {
									if ((series.localTrailerCount ?: 0) > 0) {
										playbackLauncher.launch(context, api.userLibraryApi.getLocalTrailers(series.id).content)
									} else TrailerUtils.getExternalTrailerIntent(context, series)?.let(context::startActivity)
								}
							}
						}) { Text(label) }
					}
				}
			}
		}
		item { Text(stringResource(R.string.jellyfin_tv_seasons), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 48.dp)) }
		item {
			LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 48.dp)) {
				items(state.seasons, key = { it.id }) { season -> Button(colors = actionColors, onClick = { onSeasonSelected(season) }) { Text(season.name.orEmpty()) } }
			}
		}
		items(state.episodes, key = { it.id }) { episode ->
			SeriesEpisodeCard(episode, onClick = { itemLauncher.launch(BaseItemDtoBaseRowItem(episode), null, context) }, onLongClick = { contextItem = episode })
		}
	}
	contextItem?.let { CinematicItemContextMenu(it) { contextItem = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesEpisodeCard(episode: BaseItemDto, onClick: () -> Unit, onLongClick: () -> Unit) {
	val api = org.koin.compose.koinInject<ApiClient>()
	var focused by remember { mutableStateOf(false) }
	var longPressHandled by remember { mutableStateOf(false) }
	Row(
		modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth().height(112.dp).clip(RoundedCornerShape(12.dp))
			.background(if (focused) Color(0xDD25202A) else Color(0xB3151619))
			.border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFF8B5CF6) else Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp))
			.onFocusChanged { focused = it.isFocused }
			.onPreviewKeyEvent { event ->
				if (event.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_DPAD_CENTER) false
				else if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0) {
					longPressHandled = true
					true
				} else if (event.type == KeyEventType.KeyUp && longPressHandled) {
					longPressHandled = false
					onLongClick()
					true
				} else false
			}
			.clickable(onClick = onClick).focusable().padding(8.dp),
		horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
	) {
		val image = episode.itemImages[ImageType.PRIMARY]
		Box(Modifier.width(160.dp).height(90.dp).clip(RoundedCornerShape(8.dp))) {
			AsyncImage(url = image?.getUrl(api), blurHash = image?.blurHash, aspectRatio = 16f / 9f, scaleType = ImageView.ScaleType.CENTER_CROP, modifier = Modifier.fillMaxSize())
			val progress = episode.playbackProgress()
			if (progress > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(3.dp).background(Color(0xFF00A4DC)))
		}
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
			Text(episode.episodeLabel() ?: episode.name.orEmpty(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
			val runtime = episode.runTimeTicks?.div(600_000_000L)?.let { "$it min" }
			val meta = listOfNotNull(runtime, episode.productionYear?.toString()).joinToString(" · ")
			if (meta.isNotEmpty()) Text(meta, color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
			episode.overview?.let { Text(it, color = Color.White.copy(alpha = .78f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
		}
	}
}
