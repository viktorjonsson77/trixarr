package org.jellyfin.androidtv.seerr.model

enum class MediaKind { Movie, Series }

enum class MediaAvailability {
	InLibrary, Requestable, Requested, Processing, PartiallyAvailable, Available, Declined, Failed, Unknown,
}
data class ProviderIds(
	val tmdb: Int? = null,
	val tvdb: Int? = null,
	val imdb: String? = null,
)

data class SeerrMedia(
	val id: Int,
	val kind: MediaKind,
	val title: String,
	val year: Int? = null,
	val overview: String? = null,
	val originalTitle: String? = null,
	val releaseDate: String? = null,
	val runtimeMinutes: Int? = null,
	val certification: String? = null,
	val rating: Double? = null,
	val genres: List<String> = emptyList(),
	val genreIds: List<Int> = emptyList(),
	val cast: List<String> = emptyList(),
	val studios: List<String> = emptyList(),
	val originalLanguage: String? = null,
	val productionStatus: String? = null,
	val people: List<SeerrPerson> = emptyList(),
	val posterUrl: String? = null,
	val backdropUrl: String? = null,
	val providerIds: ProviderIds = ProviderIds(tmdb = id),
	val availability: MediaAvailability = MediaAvailability.Unknown,
	val seasons: List<SeerrSeason> = emptyList(),
	val related: List<SeerrMedia> = emptyList(),
	val requestIds: Set<Int> = emptySet(),
)

data class SeerrPerson(val name: String, val role: String? = null, val image: String? = null, val job: String? = null)

data class SeerrSeason(
	val number: Int,
	val name: String,
	val availability: MediaAvailability,
)

data class SeerrUser(
	val id: Int,
	val displayName: String,
	val permissions: Long,
)

data class SeerrRequest(
	val id: Int,
	val media: SeerrMedia,
	val status: RequestStatus,
	val createdAt: String,
	val seasons: Set<Int> = emptySet(),
)

enum class RequestStatus { Requested, AwaitingApproval, Approved, Processing, PartiallyAvailable, Available, Declined, Failed, Unknown }

data class SeerrConfiguration(
	val profileKey: String,
	val baseUrl: String,
	val allowInsecureHttp: Boolean = false,
)

sealed interface SeerrConnectionState {
	data object Disconnected : SeerrConnectionState
	data class Connected(val user: SeerrUser, val version: String?) : SeerrConnectionState
	data object Expired : SeerrConnectionState
}
