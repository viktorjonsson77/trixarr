package org.jellyfin.androidtv.seerr

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.androidtv.seerr.model.MediaAvailability
import org.jellyfin.androidtv.seerr.model.MediaKind
import org.jellyfin.androidtv.seerr.model.SeerrMedia

class SeerrRepository(private val api: SeerrApi) {
	suspend fun search(query: String) = runCatching { SeerrMediaMapper.mapResults(api.search(query).body, api::imageUrl) }
	suspend fun discover(section: String) = runCatching { SeerrMediaMapper.mapResults(api.discover(section).body, api::imageUrl) }
	suspend fun details(kind: MediaKind, id: Int) = runCatching {
		SeerrMediaMapper.mapMedia(if (kind == MediaKind.Movie) api.movie(id).body else api.series(id).body, api::imageUrl)
	}
	suspend fun requests(userId: Int) = runCatching {
		val requests = SeerrMediaMapper.mapRequests(api.requests(userId).body, api::imageUrl)
		coroutineScope {
			requests.map { request ->
				async {
					val details = runCatching {
						if (request.media.kind == MediaKind.Movie) api.movie(request.media.id).body else api.series(request.media.id).body
					}.mapCatching { SeerrMediaMapper.mapMedia(it, api::imageUrl) }.getOrNull()
					if (details == null) request else request.copy(media = details.copy(availability = request.media.availability))
				}
			}.awaitAll()
		}
	}
}

class RequestRepository(private val api: SeerrApi) {
	private val mutation = Mutex()
	private val submitted = mutableSetOf<Pair<org.jellyfin.androidtv.seerr.model.MediaKind, Int>>()
	private val cacheGeneration = java.util.concurrent.atomic.AtomicLong()
	fun generation() = cacheGeneration.get()

	suspend fun request(media: SeerrMedia, selectedSeasons: Set<Int> = emptySet(), confirmed: Boolean): Result<Unit> {
		if (!confirmed) return Result.failure(IllegalStateException("Explicit confirmation required"))
		if (media.availability != MediaAvailability.Requestable) return Result.failure(IllegalStateException("Media is not requestable"))
		if (media.kind == MediaKind.Series && selectedSeasons.isEmpty()) return Result.failure(IllegalStateException("Select at least one season"))
		if (!mutation.tryLock()) return Result.failure(IllegalStateException("Request already in progress"))
		return try {
				runCatching {
					check((media.kind to media.id) !in submitted) { "Request already submitted" }
					api.createRequest(if (media.kind == MediaKind.Movie) "movie" else "tv", media.id, selectedSeasons.takeIf { it.isNotEmpty() })
					submitted += media.kind to media.id
					cacheGeneration.incrementAndGet()
					Unit
				}
		} finally { mutation.unlock() }
	}
}
