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
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SearchViewModel(
	private val searchRepository: SearchRepository
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
			val outcomes = groups.map { (stringRes, itemKinds) ->
				async {
					val result = searchRepository.search(trimmed, itemKinds)
					result.map { SearchResultGroup(stringRes, it) }
				}
			}.awaitAll()
			val failure = outcomes.firstOrNull { it.isFailure }?.exceptionOrNull()
			if (failure != null) {
				_searchStateFlow.value = CinematicSearchState.Error(trimmed)
			} else {
				val results = outcomes.mapNotNull(Result<SearchResultGroup>::getOrNull).filter { it.items.isNotEmpty() }
				_searchResultsFlow.value = results
				_searchStateFlow.value = if (results.isEmpty()) CinematicSearchState.NoResults(trimmed) else CinematicSearchState.Results(trimmed, results)
			}
		}

		return true
	}
}

sealed interface CinematicSearchState {
	data object EmptyQuery : CinematicSearchState
	data class Loading(val query: String) : CinematicSearchState
	data class NoResults(val query: String) : CinematicSearchState
	data class Error(val query: String) : CinematicSearchState
	data class Results(val query: String, val groups: List<SearchResultGroup>) : CinematicSearchState
}
