package org.jellyfin.androidtv.ui.catalog

import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.seerr.RequestRepository
import org.jellyfin.androidtv.seerr.SeerrApi
import org.jellyfin.androidtv.seerr.SeerrClientProvider
import org.jellyfin.androidtv.seerr.SeerrMediaMapper
import org.jellyfin.androidtv.seerr.SeerrPermissions
import org.jellyfin.androidtv.seerr.SeerrRepository
import org.jellyfin.androidtv.seerr.model.MediaAvailability
import org.jellyfin.androidtv.seerr.model.MediaKind
import org.jellyfin.androidtv.seerr.model.RequestStatus
import org.jellyfin.androidtv.seerr.model.SeerrMedia
import org.jellyfin.androidtv.seerr.model.SeerrUser
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.androidtv.util.apiclient.seriesPrimaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest

class CatalogRepository(
    private val api: ApiClient,
    private val clients: SeerrClientProvider,
    private val profile: String,
) {
    private var local: List<CatalogTitle> = emptyList()
    private val localDtos = java.util.concurrent.ConcurrentHashMap<String, BaseItemDto>()
    private val remoteDtos = java.util.concurrent.ConcurrentHashMap<String, SeerrMedia>()
    private var connection: Pair<SeerrApi, SeerrUser>? = null
    private var requestRepository: RequestRepository? = null
    private val discoveryCache =
        java.util.concurrent.ConcurrentHashMap<String, List<CatalogTitle>>()
    private var resumeCache: List<CatalogTitle> = emptyList()
    private var recommendations: List<CatalogTitle>? = null

    suspend fun connect() {
        connection = clients.authenticated(profile).getOrNull()
        requestRepository = connection?.let { RequestRepository(it.first) }
    }

    private suspend fun <T> optional(block: suspend () -> T): T? =
        try {
            block()
        } catch (_: Exception) {
            coroutineContext.ensureActive()
            null
        }

    suspend fun library(): List<CatalogTitle> =
        withContext(Dispatchers.IO) {
            if (local.isNotEmpty()) return@withContext local
            val values = mutableListOf<CatalogTitle>()
            var offset = 0
            do {
                val page =
                    api.itemsApi
                        .getItems(
                            includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            startIndex = offset,
                            limit = 200,
                            fields = ItemRepository.itemFields + org.jellyfin.sdk.model.api.ItemFields.STUDIOS,
                            sortBy = setOf(ItemSortBy.DATE_CREATED),
                            sortOrder = setOf(SortOrder.DESCENDING),
                        )
                        .content
                        .items
                values +=
                    page
                        .filter {
                            it.locationType != org.jellyfin.sdk.model.api.LocationType.VIRTUAL
                        }
                        .map(::mapLocal)
                offset += page.size
            } while (page.size == 200)
            local = mergeTitles(values)
            local
        }

    suspend fun browse(page: Int): List<CatalogTitle> {
        val stored = library().drop((page - 1) * 20).take(20)
        val trending = discover(page)
        return mergeTitles(
            (0 until maxOf(stored.size, trending.size)).flatMap { index ->
                listOfNotNull(trending.getOrNull(index), stored.getOrNull(index))
            }
        )
    }

    suspend fun home(): List<CatalogRow> = coroutineScope {
        val resume = async {
            optional {
                    withContext(Dispatchers.IO) {
                        api.itemsApi
                            .getResumeItems(
                                fields = ItemRepository.itemFields,
                                limit = 30,
                                mediaTypes = setOf(MediaType.VIDEO),
                                includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                            )
                            .content
                            .items
                            .map(::mapLocal)
                    }
                }
                ?.also { resumeCache = it } ?: resumeCache
        }
        val trend = async { discover(1, "trending") }
        val upcoming = async {
            (discover(1, "movies/upcoming") + discover(1, "tv/upcoming")).filter { title ->
                title.releaseDate?.let { it > java.time.LocalDate.now().toString() } == true
            }
        }
        val history = resume.await()
        if (recommendations == null) {
            val watched =
                optional {
                        withContext(Dispatchers.IO) {
                            api.itemsApi
                                .getItems(
                                    includeItemTypes =
                                        setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                    recursive = true,
                                    isPlayed = true,
                                    fields = ItemRepository.itemFields,
                                    sortBy = setOf(ItemSortBy.DATE_PLAYED),
                                    sortOrder = setOf(SortOrder.DESCENDING),
                                    limit = 8,
                                )
                                .content
                                .items
                                .map(::mapLocal)
                        }
                    }
                    .orEmpty()
            recommendations =
                mergeTitles(
                        recommendationSeeds(history + watched).flatMap { seed ->
                            optional { similar(seed) }.orEmpty()
                        }
                    )
                    .filter { candidate -> (history + watched).none { sameTitle(it, candidate) } }
                    .shuffled(java.util.Random(profile.hashCode().toLong()))
                    .take(24)
        }
        visibleRows(
            listOf(
                CatalogRow("resume", "Fortsätt titta", history),
                CatalogRow("latest", "Nytt på servern", local.take(30)),
                CatalogRow("trending", "Trendigt just nu", trend.await()),
                CatalogRow("upcoming", "Kommer snart", mergeTitles(upcoming.await())),
                CatalogRow("recommendations", "För dig", recommendations.orEmpty()),
            )
        )
    }

    suspend fun discover(page: Int, section: String = "trending"): List<CatalogTitle> {
        val connected = connection ?: return emptyList()
        val cacheKey = "$section:$page"
        return optional {
                SeerrMediaMapper.mapResults(
                        connected.first.discover(section, page).body,
                        connected.first::imageUrl,
                    )
                    .map(::mapRemote)
                    .map(::resolveLocal)
            }
            ?.also { discoveryCache[cacheKey] = it } ?: discoveryCache[cacheKey].orEmpty()
    }

    suspend fun sections(kind: TitleKind?): List<CatalogRow> = coroutineScope {
        val home = async { home() }
        val trending = async { discover(1) }
        val popularMovies = async { if (kind != TitleKind.Series) discover(1, "movies") else emptyList() }
        val popularSeries = async { if (kind != TitleKind.Movie) discover(1, "tv") else emptyList() }
        val upcomingMovies = async { if (kind != TitleKind.Series) discover(1, "movies/upcoming") else emptyList() }
        val upcomingSeries = async { if (kind != TitleKind.Movie) discover(1, "tv/upcoming") else emptyList() }
        buildCatalogSections(kind, local, home.await().firstOrNull { it.key == "resume" }?.titles.orEmpty(),
            trending.await(), popularMovies.await() + popularSeries.await(), upcomingMovies.await() + upcomingSeries.await())
    }

    private fun resolveLocal(title: CatalogTitle): CatalogTitle =
        local.firstOrNull { sameTitle(it, title) }?.let { mergeTitles(listOf(title, it)).single() }
            ?: title

    suspend fun search(query: String): List<CatalogTitle> = coroutineScope {
        val localResults = async {
            optional {
                withContext(Dispatchers.IO) {
                    api.itemsApi
                        .getItems(
                            searchTerm = query,
                            includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            limit = 100,
                            fields = ItemRepository.itemFields,
                        )
                        .content
                        .items
                        .map(::mapLocal)
                }
            }
        }
        val remote = async {
            connection?.let { c ->
                optional {
                    SeerrMediaMapper.mapResults(c.first.search(query).body, c.first::imageUrl)
                        .map(::mapRemote)
                }
            }
        }
        val a = localResults.await()
        val b = remote.await()
        check(a != null || b != null) { "Search unavailable" }
        mergeTitles(a.orEmpty() + b.orEmpty()).sortedByDescending { it.name.equals(query, true) }
    }

    suspend fun mine(): List<CatalogTitle> {
        val requested =
            connection
                ?.let { c ->
                    SeerrRepository(c.first).requests(c.second.id).getOrNull()?.map { request ->
                        mapRemote(request.media)
                            .copy(
                                status =
                                    when (request.status) {
                                        RequestStatus.Declined -> TitleStatus.Declined
                                        RequestStatus.Failed -> TitleStatus.Error
                                        RequestStatus.Processing,
                                        RequestStatus.Approved -> TitleStatus.Processing
                                        RequestStatus.Available -> TitleStatus.Available
                                        RequestStatus.PartiallyAvailable -> TitleStatus.Partial
                                        else -> TitleStatus.Requested
                                    }
                            )
                            .let(::resolveLocal)
                    }
                }
                .orEmpty()
        return mergeTitles(local.filter { it.favorite } + requested)
    }

    suspend fun details(title: CatalogTitle): CatalogTitle {
        if (title.ids.local != null)
            return withContext(Dispatchers.IO) {
                val item = api.userLibraryApi.getItem(UUID.fromString(title.ids.local)).content
                var result = mapLocal(item).copy(key = title.key)
                if (result.kind == TitleKind.Series) {
                    val seasons =
                        api.tvShowsApi
                            .getSeasons(seriesId = item.id, fields = ItemRepository.itemFields)
                            .content
                            .items
                    val next =
                        api.tvShowsApi
                            .getNextUp(
                                seriesId = item.id,
                                fields = ItemRepository.itemFields,
                                limit = 1,
                            )
                            .content
                            .items
                            .firstOrNull()
                            ?.let(::mapLocal)
                    val playable =
                        next
                            ?: seasons.firstOrNull()?.let { season ->
                                api.tvShowsApi
                                    .getEpisodes(
                                        seriesId = item.id,
                                        seasonId = season.id,
                                        fields = ItemRepository.itemFields,
                                    )
                                    .content
                                    .items
                                    .firstOrNull()
                                    ?.let(::mapLocal)
                            }
                    result =
                        result.copy(
                            seasons =
                                seasons
                                    .sortedBy { it.indexNumber }
                                    .map {
                                        TitleSeason(
                                            it.id.toString(),
                                            it.indexNumber ?: 0,
                                            it.name.orEmpty(),
                                            it.childCount,
                                        )
                                    },
                            playbackId = playable?.playbackId,
                            positionTicks = playable?.positionTicks ?: 0,
                            runtimeTicks = playable?.runtimeTicks,
                            watched = playable?.watched == true,
                        )
                }
                result
            }
        val c = connection ?: error("Connection unavailable")
        val kind = if (title.kind == TitleKind.Movie) MediaKind.Movie else MediaKind.Series
        return mapRemote(
                SeerrRepository(c.first).details(kind, requireNotNull(title.ids.tmdb)).getOrThrow()
            )
            .let(::resolveLocal)
            .copy(key = title.key)
    }

    suspend fun episodes(title: CatalogTitle, season: TitleSeason): List<CatalogTitle> =
        withContext(Dispatchers.IO) {
            api.tvShowsApi
                .getEpisodes(
                    seriesId = UUID.fromString(title.ids.local),
                    seasonId = UUID.fromString(season.key),
                    fields = ItemRepository.itemFields,
                )
                .content
                .items
                .sortedBy { it.indexNumber }
                .map(::mapLocal)
        }

    suspend fun similar(title: CatalogTitle): List<CatalogTitle> =
        if (title.ids.local != null)
            withContext(Dispatchers.IO) {
                api.libraryApi
                    .getSimilarItems(
                        GetSimilarItemsRequest(
                            itemId = UUID.fromString(title.ids.local),
                            fields = ItemRepository.itemFields,
                            limit = 18,
                        )
                    )
                    .content
                    .items
                    .filter { it.type in setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES) }
                    .map(::mapLocal)
            }
        else remoteDtos[title.key]?.related.orEmpty().map(::mapRemote).map(::resolveLocal)

    suspend fun request(title: CatalogTitle, seasons: Set<Int>): Result<Unit> {
        val media =
            remoteDtos[title.key]
                ?: return Result.failure(IllegalStateException("Title unavailable"))
        return requestRepository?.request(media, seasons, confirmed = true)
            ?: Result.failure(IllegalStateException("Connection unavailable"))
    }

    fun dto(title: CatalogTitle) = title.ids.local?.let(localDtos::get)

    fun updateFavorite(title: CatalogTitle, favorite: Boolean) {
        local = local.map { if (sameTitle(it, title)) it.copy(favorite = favorite) else it }
    }

    private fun mapLocal(item: BaseItemDto): CatalogTitle {
        localDtos[item.id.toString()] = item
        val ids = item.providerIds.orEmpty().mapKeys { it.key.lowercase() }
        val kind =
            when (item.type) {
                BaseItemKind.SERIES -> TitleKind.Series
                BaseItemKind.EPISODE -> TitleKind.Episode
                else -> TitleKind.Movie
            }
        val tmdb = ids["tmdb"]?.toIntOrNull()
        return CatalogTitle(
            key =
                if (tmdb != null && kind != TitleKind.Episode) "$kind:tmdb:$tmdb"
                else "$kind:local:${item.id}",
            kind = kind,
            name = item.name.orEmpty(),
            overview = item.overview.orEmpty(),
            posterAspectRatio = item.primaryImageAspectRatio,
            poster = (item.itemImages[ImageType.PRIMARY] ?: item.seriesPrimaryImage)?.getUrl(api),
            backdrop = item.catalogBackdropImage()?.getUrl(api),
            year = item.productionYear,
            genres = item.genres.orEmpty(),
            certification = item.officialRating,
            runtimeTicks = item.runTimeTicks,
            positionTicks = item.userData?.playbackPositionTicks ?: 0,
            watched = item.userData?.played == true,
            favorite = item.userData?.isFavorite == true,
            playbackId =
                item.id.toString().takeIf {
                    (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE) &&
                        item.locationType != org.jellyfin.sdk.model.api.LocationType.VIRTUAL
                },
            ids = TitleIds(item.id.toString(), tmdb, ids["tvdb"]?.toIntOrNull(), ids["imdb"]),
            status = TitleStatus.Available,
            releaseDate = item.premiereDate?.toString()?.take(10),
            productionStatus = item.status,
            people =
                item.people.orEmpty().map {
                    TitlePerson(
                        it.name.orEmpty(),
                        it.role,
                        it.primaryImage?.getUrl(api),
                        it.type?.toString(),
                    )
                },
            studios = item.studios.orEmpty().mapNotNull { it.name },
            episodeNumber = item.indexNumber,
            seasonNumber = item.parentIndexNumber,
            seriesName = item.seriesName,
        )
    }

    private fun mapRemote(media: SeerrMedia): CatalogTitle {
        val kind = if (media.kind == MediaKind.Movie) TitleKind.Movie else TitleKind.Series
        val key = "$kind:tmdb:${media.id}"
        remoteDtos[key] = media
        return CatalogTitle(
            key,
            kind,
            media.title,
            media.overview.orEmpty(),
            media.posterUrl,
            media.backdropUrl,
            media.year,
            media.genres,
            media.certification,
            runtimeTicks = media.runtimeMinutes?.toLong()?.times(600_000_000L),
            ids =
                TitleIds(
                    tmdb = media.id,
                    tvdb = media.providerIds.tvdb,
                    imdb = media.providerIds.imdb,
                ),
            status =
                when (media.availability) {
                    MediaAvailability.InLibrary,
                    MediaAvailability.Available -> TitleStatus.Available
                    MediaAvailability.Requestable -> TitleStatus.Requestable
                    MediaAvailability.Requested -> TitleStatus.Requested
                    MediaAvailability.Processing -> TitleStatus.Processing
                    MediaAvailability.PartiallyAvailable -> TitleStatus.Partial
                    MediaAvailability.Declined -> TitleStatus.Declined
                    MediaAvailability.Failed -> TitleStatus.Error
                    else -> TitleStatus.Unknown
                },
            releaseDate = media.releaseDate,
            language =
                media.originalLanguage?.let {
                    java.util.Locale.forLanguageTag(it)
                        .getDisplayLanguage(java.util.Locale.forLanguageTag("sv"))
                },
            productionStatus = media.productionStatus,
            people =
                media.people
                    .map { TitlePerson(it.name, it.role, it.image, it.job) }
                    .ifEmpty { media.cast.map { TitlePerson(it) } },
            studios = media.studios,
            seasons =
                media.seasons.map {
                    TitleSeason(
                        it.number.toString(),
                        it.number,
                        it.name,
                        requestable = it.availability == MediaAvailability.Requestable,
                    )
                },
            requestAllowed =
                connection?.second?.let {
                    SeerrPermissions.canRequest(it.permissions, media.kind)
                } == true,
        )
    }
}
