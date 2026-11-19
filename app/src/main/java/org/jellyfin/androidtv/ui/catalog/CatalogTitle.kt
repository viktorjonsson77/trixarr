package org.jellyfin.androidtv.ui.catalog

/** UI data, independent of transport DTOs and backend names. */
data class CatalogTitle(
    val key: String,
    val kind: TitleKind,
    val name: String,
    val overview: String = "",
    val poster: String? = null,
    val backdrop: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val certification: String? = null,
    val runtimeTicks: Long? = null,
    val positionTicks: Long = 0,
    val watched: Boolean = false,
    val favorite: Boolean = false,
    val playbackId: String? = null,
    val ids: TitleIds = TitleIds(),
    val status: TitleStatus = TitleStatus.Unknown,
    val releaseDate: String? = null,
    val language: String? = null,
    val productionStatus: String? = null,
    val people: List<TitlePerson> = emptyList(),
    val studios: List<String> = emptyList(),
    val seasons: List<TitleSeason> = emptyList(),
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val seriesName: String? = null,
    val requestAllowed: Boolean = false,
) {
    val progress: Float
        get() =
            runtimeTicks
                ?.takeIf { it > 0 }
                ?.let { (positionTicks.toDouble() / it).coerceIn(0.0, 1.0).toFloat() } ?: 0f

    val minutesRemaining: Long?
        get() =
            runtimeTicks
                ?.takeIf { positionTicks in 1 until it }
                ?.let { (it - positionTicks + 599_999_999L) / 600_000_000L }

    val primary: TitleAction
        get() =
            when {
                playbackId != null ->
                    if (positionTicks > 0 && !watched) TitleAction.Resume else TitleAction.Play
                kind == TitleKind.Series && ids.local != null -> TitleAction.Episodes
                status == TitleStatus.Requestable && requestAllowed -> TitleAction.Request
                else -> TitleAction.Information
            }
}

enum class TitleKind {
    Movie,
    Series,
    Episode,
}

enum class TitleStatus {
    Available,
    Requestable,
    Sending,
    Requested,
    Processing,
    Partial,
    Declined,
    Error,
    Unknown,
}

enum class TitleAction(val label: String) {
    Play("Spela upp"),
    Resume("Fortsätt titta"),
    Request("Begär"),
    Episodes("Välj avsnitt"),
    Information("Mer information"),
}

data class TitleIds(
    val local: String? = null,
    val tmdb: Int? = null,
    val tvdb: Int? = null,
    val imdb: String? = null,
)

data class TitlePerson(
    val name: String,
    val role: String? = null,
    val image: String? = null,
    val job: String? = null,
)

data class TitleSeason(
    val key: String,
    val number: Int,
    val name: String,
    val count: Int? = null,
    val requestable: Boolean = false,
)

fun sameTitle(a: CatalogTitle, b: CatalogTitle): Boolean {
    if (a.kind != b.kind) return false
    if (a.ids.local != null && a.ids.local == b.ids.local) return true
    if (a.kind == TitleKind.Episode) return a.key == b.key
    val ids =
        listOf(
            a.ids.tmdb?.toString() to b.ids.tmdb?.toString(),
            a.ids.tvdb?.toString() to b.ids.tvdb?.toString(),
            a.ids.imdb?.lowercase() to b.ids.imdb?.lowercase(),
        )
    if (ids.any { (x, y) -> x != null && x == y }) return true
    // Conflicting authoritative IDs must never be collapsed by a similar name.
    if (ids.any { (x, y) -> x != null && y != null && x != y }) return false
    return a.year != null &&
        a.year == b.year &&
        a.name.isNotBlank() &&
        a.name.lowercase().filter(Char::isLetterOrDigit) ==
            b.name.lowercase().filter(Char::isLetterOrDigit)
}

fun mergeTitles(values: List<CatalogTitle>): List<CatalogTitle> {
    val result = mutableListOf<CatalogTitle>()
    values.forEach { value ->
        val index = result.indexOfFirst { sameTitle(it, value) }
        if (index < 0) result += value
        else {
            val old = result[index]
            val local = if (value.ids.local != null) value else old
            val other = if (local === value) old else value
            result[index] =
                local.copy(
                    key = old.key,
                    ids =
                        TitleIds(
                            local.ids.local ?: other.ids.local,
                            local.ids.tmdb ?: other.ids.tmdb,
                            local.ids.tvdb ?: other.ids.tvdb,
                            local.ids.imdb ?: other.ids.imdb,
                        ),
                    poster = local.poster ?: other.poster,
                    backdrop = local.backdrop ?: other.backdrop,
                    overview = local.overview.ifBlank { other.overview },
                    genres = local.genres.ifEmpty { other.genres },
                    releaseDate = local.releaseDate ?: other.releaseDate,
                    status = if (local.ids.local != null) local.status else other.status,
                    requestAllowed = local.requestAllowed || other.requestAllowed,
                )
        }
    }
    return result
}

fun retainedIndex(keys: List<String>, selected: String?, previousIndex: Int): Int =
    keys.indexOf(selected).takeIf { it >= 0 }
        ?: previousIndex.coerceIn(0, (keys.size - 1).coerceAtLeast(0))

fun relevantEpisode(episodes: List<CatalogTitle>): CatalogTitle? =
    episodes.firstOrNull { it.positionTicks > 0 && !it.watched }
        ?: episodes.firstOrNull { !it.watched }
        ?: episodes.firstOrNull()

data class CatalogCategory(val name: String, val titles: List<CatalogTitle>)

data class CatalogRow(val key: String, val name: String, val titles: List<CatalogTitle>, val categories: List<CatalogCategory> = emptyList())

fun visibleRows(rows: List<CatalogRow>) = rows.filter { it.titles.isNotEmpty() || it.categories.isNotEmpty() }

fun recommendationSeeds(history: List<CatalogTitle>) =
    history.filter { it.watched || it.positionTicks > 0 }.distinctBy { it.key }.take(4)

data class PlaybackStart(val itemId: String, val positionMillis: Long)

fun playbackStart(title: CatalogTitle): PlaybackStart? =
    title.playbackId?.let {
        PlaybackStart(it, if (title.watched) 0L else title.positionTicks.coerceAtLeast(0) / 10_000L)
    }

/** All category names and members come from the loaded metadata, never a static taxonomy. */
fun buildCatalogSections(kind: TitleKind?, local: List<CatalogTitle>, resume: List<CatalogTitle>,
    trending: List<CatalogTitle>, popular: List<CatalogTitle>, upcoming: List<CatalogTitle>): List<CatalogRow> {
    fun typed(values: List<CatalogTitle>) = mergeTitles(values).filter { if (kind == null) it.kind != TitleKind.Episode else it.kind == kind }
    val all = typed(local + trending + popular + upcoming)
    fun categories(select: (CatalogTitle) -> List<String>) = all.flatMap(select).filter(String::isNotBlank).distinct().sorted()
        .map { label -> CatalogCategory(label, all.filter { label in select(it) }) }
    val noun = when (kind) { TitleKind.Movie -> "filmer"; TitleKind.Series -> "serier"; else -> "titlar" }
    return visibleRows(listOf(
        CatalogRow("new", if (kind == null) "Nya releaser" else "Nya $noun", typed(local).sortedByDescending { it.releaseDate ?: it.year?.toString().orEmpty() }),
        CatalogRow("resume", "Fortsätt titta", typed(resume)),
        CatalogRow("trending", "Trendigt", typed(trending)),
        CatalogRow("popular", "Populärt", typed(popular)),
        CatalogRow("genres", "Genrer", emptyList(), categories { it.genres }),
        CatalogRow("studios", "Studior", emptyList(), categories { it.studios }),
        CatalogRow("upcoming", "Kommer snart", typed(upcoming)),
        CatalogRow("all", "Alla $noun", all),
    ))
}
