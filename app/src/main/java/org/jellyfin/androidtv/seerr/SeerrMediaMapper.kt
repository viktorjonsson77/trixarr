package org.jellyfin.androidtv.seerr

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.seerr.model.MediaAvailability
import org.jellyfin.androidtv.seerr.model.MediaKind
import org.jellyfin.androidtv.seerr.model.ProviderIds
import org.jellyfin.androidtv.seerr.model.SeerrMedia
import org.jellyfin.androidtv.seerr.model.SeerrSeason
import org.jellyfin.androidtv.seerr.model.SeerrRequest
import org.jellyfin.androidtv.seerr.model.RequestStatus

object SeerrMediaMapper {
	fun mapResults(response: JsonObject, imageUrl: (String, Boolean) -> String = { path, _ -> path }): List<SeerrMedia> = response["results"]?.jsonArray.orEmpty().mapNotNull { runCatching { mapMedia(it.jsonObject, imageUrl) }.getOrNull() }

	fun mapRequests(response: JsonObject, imageUrl: (String, Boolean) -> String = { path, _ -> path }): List<SeerrRequest> = response["results"]?.jsonArray.orEmpty().mapNotNull { element ->
			runCatching {
				val value = element.jsonObject
				val mediaValue = value["media"]?.jsonObject ?: return@runCatching null
				val media = mapMedia(mediaValue, imageUrl).copy(
					availability = mapAvailability(mediaValue.int("status"), requested = true),
				)
			SeerrRequest(
				id = value.int("id") ?: error("Missing request id"),
				media = media,
				status = mapRequestStatus(value.int("status"), media.availability),
				createdAt = value.string("createdAt").orEmpty(),
				seasons = value["seasons"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.int("seasonNumber") }.toSet(),
			)
		}.getOrNull()
	}.filterNotNull()

	fun mapMedia(value: JsonObject, imageUrl: (String, Boolean) -> String = { path, _ -> path }): SeerrMedia {
		val mediaType = value.string("mediaType") ?: value.string("type")
		val kind = if (mediaType == "tv" || value["name"] != null) MediaKind.Series else MediaKind.Movie
		val id = value.int("tmdbId") ?: value.int("id") ?: error("Missing media id")
		val mediaInfo = value["mediaInfo"] as? JsonObject
		val seasonAvailability = mediaInfo?.get("seasons")?.jsonArray.orEmpty().associate { season ->
			val item = season.jsonObject
			(item.int("seasonNumber") ?: -1) to mapAvailability(item.int("status"), item["requests"] != null)
		}
		return SeerrMedia(
			id = id,
			kind = kind,
			title = value.string(if (kind == MediaKind.Series) "name" else "title") ?: value.string("title") ?: value.string("name") ?: "",
			year = (value.string(if (kind == MediaKind.Series) "firstAirDate" else "releaseDate") ?: "").take(4).toIntOrNull(),
			overview = value.string("overview"),
			originalTitle = value.string(if (kind == MediaKind.Series) "originalName" else "originalTitle"),
			releaseDate = value.string(if (kind == MediaKind.Series) "firstAirDate" else "releaseDate"),
			runtimeMinutes = value.int("runtime") ?: value["episodeRunTime"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.intOrNull,
			certification = value.string("certification") ?: value.string("contentRating"),
			rating = value["voteAverage"]?.jsonPrimitive?.doubleOrNull,
			genres = value["genres"]?.jsonArray.orEmpty().mapNotNull { runCatching { it.jsonObject.string("name") }.getOrNull() },
			cast = (value["credits"] as? JsonObject)?.get("cast")?.jsonArray.orEmpty().take(8).mapNotNull { runCatching { it.jsonObject.string("name") }.getOrNull() },
			studios = (value["productionCompanies"]?.jsonArray.orEmpty() + value["networks"]?.jsonArray.orEmpty()).mapNotNull { runCatching { it.jsonObject.string("name") }.getOrNull() }.distinct(),
			posterUrl = value.string("posterPath")?.let { imageUrl(it, false) },
			backdropUrl = value.string("backdropPath")?.let { imageUrl(it, true) },
			providerIds = ProviderIds(tmdb = id, tvdb = value.int("tvdbId"), imdb = value.string("imdbId")),
			availability = if (mediaInfo == null) MediaAvailability.Requestable else mapAvailability(mediaInfo.int("status"), mediaInfo["requests"] != null),
			seasons = value["seasons"]?.jsonArray.orEmpty().mapNotNull { season ->
				val item = season.jsonObject
				val number = item.int("seasonNumber") ?: return@mapNotNull null
				SeerrSeason(number, item.string("name") ?: "Säsong $number", seasonAvailability[number] ?: MediaAvailability.Requestable)
			},
			related = listOf("recommendations", "similar").flatMap { key ->
				(value[key] as? JsonObject)?.get("results")?.jsonArray.orEmpty().mapNotNull { runCatching { mapMedia(it.jsonObject, imageUrl) }.getOrNull() }
			}.distinctBy { it.kind to it.id },
		)
	}

	fun mapAvailability(status: Int?, requested: Boolean): MediaAvailability = when (status) {
		1 -> if (requested) MediaAvailability.Requested else MediaAvailability.Unknown
		2 -> MediaAvailability.Requested
		3 -> MediaAvailability.Processing
		4 -> MediaAvailability.PartiallyAvailable
		5 -> MediaAvailability.Available
		else -> if (requested) MediaAvailability.Requested else MediaAvailability.Unknown
	}

	fun mapRequestStatus(status: Int?, availability: MediaAvailability): RequestStatus = when {
		availability == MediaAvailability.Available -> RequestStatus.Available
		availability == MediaAvailability.PartiallyAvailable -> RequestStatus.PartiallyAvailable
		availability == MediaAvailability.Processing -> RequestStatus.Processing
		status == 1 -> RequestStatus.AwaitingApproval
		status == 2 -> RequestStatus.Approved
		status == 3 -> RequestStatus.Declined
		status == 4 -> RequestStatus.Failed
		status == 5 -> RequestStatus.Available
		else -> RequestStatus.Unknown
	}

	private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.content
	private fun JsonObject.int(key: String) = get(key)?.jsonPrimitive?.intOrNull
}
