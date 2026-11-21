package org.jellyfin.androidtv.seerr

import java.time.LocalDate
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.*
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.seerr.model.*

enum class DiscoveryCategory(val label: Int) {
    New(R.string.discover_new), Upcoming(R.string.discover_upcoming), Requests(R.string.discover_requests),
    Popular(R.string.discover_popular), Top(R.string.discover_top), Genres(R.string.discover_genres)
}
data class DiscoveryGenre(val key: String, val name: String, val path: String)
data class DiscoveryRow(val key: String, val label: String, val media: List<SeerrMedia>)

/** Only the selected category is fetched. Successful pages are reused for three minutes. */
class SeerrDiscovery(private val api: SeerrApi, private val user: SeerrUser, private val today: () -> LocalDate = LocalDate::now) {
    private val cache = mutableMapOf<String, Pair<Long, List<SeerrMedia>>>()
    private suspend fun page(path: String): List<SeerrMedia> {
        cache[path]?.takeIf { System.nanoTime() - it.first < 180_000_000_000L }?.let { return it.second }
        val result = SeerrMediaMapper.mapResults(api.discover(path).body, api::imageUrl)
        coroutineContext.ensureActive()
        cache[path] = System.nanoTime() to result
        return result
    }
    private var genres: List<DiscoveryGenre>? = null
    suspend fun genreOptions(): List<DiscoveryGenre> {
        genres?.let { return it }
        val result = listOf("movie" to "movies", "tv" to "tv").flatMap { (kind, path) ->
            api.discover("genreslider/$kind").body["results"]?.jsonArray.orEmpty().mapNotNull { entry ->
                val value = entry.jsonObject
                val id = value["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val name = value["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                DiscoveryGenre("$kind:$id", name, "$path/genre/$id")
            }
        }
        genres = result
        return result
    }
    suspend fun load(category: DiscoveryCategory, genreKey: String? = null): List<DiscoveryRow> {
        fun row(key: String, values: List<SeerrMedia>) = DiscoveryRow(key, key, values.groupBy { it.kind to it.id }.values.map { group -> group.first().copy(requestIds = group.flatMap { it.requestIds }.toSet()) })
        return when (category) {
            DiscoveryCategory.New -> listOf(row("new_movies", recent(page("movies"), today())), row("new_tv", recent(page("tv"), today())))
            DiscoveryCategory.Upcoming -> listOf(row("upcoming_movies", upcoming(page("movies/upcoming"), today())), row("upcoming_tv", upcoming(page("tv/upcoming"), today())))
            DiscoveryCategory.Popular -> listOf(row("popular_movies", page("movies")), row("popular_tv", page("tv")))
            DiscoveryCategory.Top -> listOf(row("top_movies", ranked(page("movies"))), row("top_tv", ranked(page("tv"))))
            DiscoveryCategory.Requests -> {
                if (!SeerrPermissions.canViewOwnRequests(user.permissions)) throw SeerrHttpException(403)
                val requested = SeerrRepository(api).requests(user.id).getOrThrow().map { it.media.copy(requestIds = setOf(it.id)) }
                listOf(row("my_requests", requested), row("request_movies", page("movies").filter { it.availability == MediaAvailability.Requestable && SeerrPermissions.canRequest(user.permissions, it.kind) }), row("request_tv", page("tv").filter { it.availability == MediaAvailability.Requestable && SeerrPermissions.canRequest(user.permissions, it.kind) }))
            }
            DiscoveryCategory.Genres -> {
                val options = genreOptions()
                val selected = options.firstOrNull { it.key == genreKey } ?: options.firstOrNull()
                if (selected == null) emptyList() else listOf(DiscoveryRow("genre:${selected.key}", selected.name, page(selected.path)))
            }
        }.filter { it.media.isNotEmpty() }
    }
    companion object {
        fun recent(values: List<SeerrMedia>, today: LocalDate) = values.filter { value ->
            val date = value.releaseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            date != null && !date.isAfter(today) && !date.isBefore(today.minusDays(90))
        }.sortedByDescending { it.releaseDate }
        fun upcoming(values: List<SeerrMedia>, today: LocalDate) = values.filter { value ->
            value.releaseDate?.let { runCatching { LocalDate.parse(it).isAfter(today) }.getOrDefault(false) } == true
        }.sortedBy { it.releaseDate }
        fun ranked(values: List<SeerrMedia>) = values.filter { (it.rating ?: 0.0) > 0 }.sortedByDescending { it.rating }
    }
}
