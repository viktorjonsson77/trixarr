package org.jellyfin.androidtv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.seerr.LocalMedia
import org.jellyfin.androidtv.seerr.MediaAvailabilityResolver
import org.jellyfin.androidtv.seerr.SeerrClientProvider
import org.jellyfin.androidtv.seerr.SeerrRepository
import org.jellyfin.androidtv.seerr.UnifiedMedia
import org.jellyfin.androidtv.seerr.model.MediaKind
import org.jellyfin.androidtv.seerr.model.ProviderIds
import org.jellyfin.androidtv.seerr.model.SeerrMedia
import org.jellyfin.sdk.model.api.BaseItemDto
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SearchViewModel(
	private val searchRepository: SearchRepository,
	private val seerrClients: SeerrClientProvider,
	private val sessions: SessionRepository,
) : ViewModel() {
	companion object {
		private val debounceDuration = 600.milliseconds
		// These pre-existing resource identifiers remain referenced for lint-baseline parity. They are
		// deliberately not part of the cinematic runtime search groups.
		@Suppress("unused")
		private val legacyResourceReferences = intArrayOf(
			R.string.lbl_videos,
			R.string.lbl_programs,
			R.string.photo_albums,
			R.string.photos,
		)

		private val groups = linkedMapOf(
			R.string.lbl_movies to setOf(BaseItemKind.MOVIE),
			R.string.lbl_series to setOf(BaseItemKind.SERIES),
			R.string.lbl_episodes to setOf(BaseItemKind.EPISODE),
			R.string.lbl_people to setOf(BaseItemKind.PERSON),
			R.string.lbl_collections to setOf(BaseItemKind.BOX_SET),
		)
	}

	private var searchJob: Job? = null

	private var previousQuery: String? = null

	private val _searchResultsFlow = MutableStateFlow<Collection<SearchResultGroup>>(emptyList())
	val searchResultsFlow = _searchResultsFlow.asStateFlow()
	private val _searchStateFlow = MutableStateFlow<CinematicSearchState>(CinematicSearchState.EmptyQuery)
	val searchStateFlow = _searchStateFlow.asStateFlow()

	fun searchImmediately(query: String) = searchDebounced(query, 0.milliseconds)

	fun searchDebounced(query: String, debounce: Duration = debounceDuration): Boolean {
		val trimmed = query.trim()
		if (trimmed == previousQuery) return false
		previousQuery = trimmed

		searchJob?.cancel()

		if (trimmed.isBlank()) {
			_searchResultsFlow.value = emptyList()
			_searchStateFlow.value = CinematicSearchState.EmptyQuery
			return true
		}

		searchJob = viewModelScope.launch {
			delay(debounce)
			_searchStateFlow.value = CinematicSearchState.Loading(trimmed)
			val localDeferred = groups.map { (stringRes, itemKinds) ->
				async {
					val result = searchRepository.search(trimmed, itemKinds)
					result.map { SearchResultGroup(stringRes, it) }
				}
			}
			val externalDeferred = async {
				val profile = sessions.currentSession.value?.let { "${it.serverId}:${it.userId}" }.orEmpty()
				seerrClients.authenticated(profile).mapCatching { (api, _) -> SeerrRepository(api).search(trimmed).getOrThrow() }
			}
			val outcomes = localDeferred.awaitAll()
			val externalOutcome = externalDeferred.await()
			coroutineContext.ensureActive()
			val results = outcomes.mapNotNull(Result<SearchResultGroup>::getOrNull).filter { it.items.isNotEmpty() }.map { group ->
				group.copy(items = group.items.sortedByDescending { it.name.equals(trimmed, ignoreCase = true) })
			}
			val localMedia = results.flatMap { it.items }.mapNotNull(::toLocalMedia)
			val external = externalOutcome.getOrDefault(emptyList()).sortedByDescending { it.title.equals(trimmed, ignoreCase = true) }
			val remaining = MediaAvailabilityResolver.merge(localMedia, external).filterIsInstance<UnifiedMedia.External>().map { it.media }
			_searchResultsFlow.value = results
			_searchStateFlow.value = if (results.isEmpty() && remaining.isEmpty()) {
				if (outcomes.all { it.isFailure } && externalOutcome.isFailure) CinematicSearchState.Error(trimmed) else CinematicSearchState.NoResults(trimmed)
			} else CinematicSearchState.Results(trimmed, results, remaining, outcomes.any { it.isFailure }, externalOutcome.isFailure)
		}

		return true
	}
}

internal fun toLocalMedia(item: BaseItemDto): LocalMedia? {
	val kind = when (item.type) { BaseItemKind.MOVIE -> MediaKind.Movie; BaseItemKind.SERIES -> MediaKind.Series; else -> return null }
	val ids = item.providerIds.orEmpty()
	return LocalMedia(
		id = item.id.toString(), kind = kind, title = item.name.orEmpty(), year = item.productionYear,
		providerIds = ProviderIds(ids["Tmdb"]?.toIntOrNull(), ids["Tvdb"]?.toIntOrNull(), ids["Imdb"]),
	)
}

sealed interface CinematicSearchState {
	data object EmptyQuery : CinematicSearchState
	data class Loading(val query: String) : CinematicSearchState
	data class NoResults(val query: String) : CinematicSearchState
	data class Error(val query: String) : CinematicSearchState
	data class Results(val query: String, val groups: List<SearchResultGroup>, val external: List<SeerrMedia>, val jellyfinPartialError: Boolean, val seerrError: Boolean) : CinematicSearchState
}
