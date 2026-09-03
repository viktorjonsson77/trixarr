package org.jellyfin.androidtv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber

enum class HomeCardFormat { LANDSCAPE, POSTER }
enum class HomeRowKind { RESUME, NEXT_UP, LATEST_MOVIES, LATEST_SERIES }

data class CinematicHomeRow(
	val kind: HomeRowKind,
	val titleRes: Int,
	val items: List<BaseItemDto>,
	val format: HomeCardFormat,
)

data class CinematicHomeState(
	val loading: Boolean = true,
	val rows: List<CinematicHomeRow> = emptyList(),
	val hero: BaseItemDto? = null,
	val error: Throwable? = null,
)

fun BaseItemDto.isHeroMedia(): Boolean = type in setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE, BaseItemKind.VIDEO)

fun orderedVisibleRows(rows: Collection<CinematicHomeRow>): List<CinematicHomeRow> = rows
	.filter { it.items.isNotEmpty() }
	.sortedBy { row ->
		when (row.kind) {
			HomeRowKind.RESUME -> 0
			HomeRowKind.NEXT_UP -> 1
			HomeRowKind.LATEST_MOVIES -> 2
			HomeRowKind.LATEST_SERIES -> 3
		}
	}

fun firstValidHero(rows: Collection<CinematicHomeRow>): BaseItemDto? =
	orderedVisibleRows(rows).asSequence().flatMap { it.items.asSequence() }.firstOrNull(BaseItemDto::isHeroMedia)

fun retainedHero(current: BaseItemDto?, focused: BaseItemDto?): BaseItemDto? =
	focused?.takeIf(BaseItemDto::isHeroMedia) ?: current

class HomeViewModel(
	private val api: ApiClient,
	private val userViewsRepository: UserViewsRepository,
) : ViewModel() {
	private val _state = MutableStateFlow(CinematicHomeState())
	val state = _state.asStateFlow()

	init { refresh() }

	fun refresh() {
		viewModelScope.launch {
			_state.value = _state.value.copy(loading = true, error = null)
			runCatching { loadRows() }.fold(
				onSuccess = { rows ->
					val visibleRows = orderedVisibleRows(rows)
					val currentHero = _state.value.hero?.takeIf(BaseItemDto::isHeroMedia)
					_state.value = CinematicHomeState(false, visibleRows, currentHero ?: firstValidHero(visibleRows))
				},
				onFailure = { error ->
					Timber.e(error, "Unable to load cinematic home")
					_state.value = _state.value.copy(loading = false, error = error)
				},
			)
		}
	}

	fun selectHero(item: BaseItemDto) {
		_state.value = _state.value.copy(hero = retainedHero(_state.value.hero, item))
	}

	private suspend fun loadRows(): List<CinematicHomeRow> = withContext(Dispatchers.IO) { coroutineScope {
		val views = userViewsRepository.views.first()
		val movieViews = views.filter { it.collectionType == CollectionType.MOVIES }
		val seriesViews = views.filter { it.collectionType == CollectionType.TVSHOWS }
		val resume = async {
			runCatching { api.itemsApi.getResumeItems(fields = ItemRepository.browseFields, imageTypeLimit = 1, limit = HOME_ITEM_LIMIT, mediaTypes = listOf(MediaType.VIDEO), includeItemTypes = listOf(BaseItemKind.EPISODE, BaseItemKind.MOVIE), excludeActiveSessions = true).content.items }
				.getOrElse { error -> Timber.w(error, "Unable to load Resume row"); emptyList() }
		}
		val nextUp = async {
			runCatching { api.tvShowsApi.getNextUp(fields = ItemRepository.browseFields, imageTypeLimit = 1, limit = HOME_ITEM_LIMIT, enableResumable = false).content.items }
				.getOrElse { error -> Timber.w(error, "Unable to load Next Up row"); emptyList() }
		}
		val latestMovies = async {
			runCatching { movieViews.flatMap { view -> api.userLibraryApi.getLatestMedia(fields = ItemRepository.browseFields, imageTypeLimit = 1, limit = HOME_ITEM_LIMIT, parentId = view.id, groupItems = true).content }.distinctBy { it.id }.take(HOME_ITEM_LIMIT) }
				.getOrElse { error -> Timber.w(error, "Unable to load latest movies"); emptyList() }
		}
		val latestSeries = async {
			runCatching { seriesViews.flatMap { view -> api.userLibraryApi.getLatestMedia(fields = ItemRepository.browseFields, imageTypeLimit = 1, limit = HOME_ITEM_LIMIT, parentId = view.id, groupItems = true).content }.distinctBy { it.seriesId ?: it.id }.take(HOME_ITEM_LIMIT) }
				.getOrElse { error -> Timber.w(error, "Unable to load latest series"); emptyList() }
		}
		listOf(
			CinematicHomeRow(HomeRowKind.RESUME, R.string.lbl_continue_watching, resume.await(), HomeCardFormat.LANDSCAPE),
			CinematicHomeRow(HomeRowKind.NEXT_UP, R.string.lbl_next_up, nextUp.await(), HomeCardFormat.LANDSCAPE),
			CinematicHomeRow(HomeRowKind.LATEST_MOVIES, R.string.jellyfin_tv_latest_movies, latestMovies.await(), HomeCardFormat.POSTER),
			CinematicHomeRow(HomeRowKind.LATEST_SERIES, R.string.jellyfin_tv_latest_series, latestSeries.await(), HomeCardFormat.POSTER),
		)
	} }

	private companion object { const val HOME_ITEM_LIMIT = 30 }
}
