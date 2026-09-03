package org.jellyfin.androidtv.seerr

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.seerr.model.MediaAvailability
import org.jellyfin.androidtv.seerr.model.MediaKind
import org.jellyfin.androidtv.seerr.model.ProviderIds
import org.jellyfin.androidtv.seerr.model.SeerrMedia
import org.jellyfin.androidtv.seerr.model.SeerrSeason

object SeerrMediaMapper {
	fun mapResults(response: JsonObject): List<SeerrMedia> = response["results"]?.jsonArray.orEmpty().mapNotNull { runCatching { mapMedia(it.jsonObject) }.getOrNull() }

	fun mapMedia(value: JsonObject): SeerrMedia {
		val mediaType = value.string("mediaType") ?: value.string("type")
		val kind = if (mediaType == "tv" || value["name"] != null) MediaKind.Series else MediaKind.Movie
		val id = value.int("id") ?: value.int("tmdbId") ?: error("Missing media id")
		val mediaInfo = value["mediaInfo"] as? JsonObject
		return SeerrMedia(
			id = id,
			kind = kind,
			title = value.string(if (kind == MediaKind.Series) "name" else "title") ?: value.string("title") ?: value.string("name") ?: "",
			year = (value.string(if (kind == MediaKind.Series) "firstAirDate" else "releaseDate") ?: "").take(4).toIntOrNull(),
			overview = value.string("overview"),
			posterUrl = image(value.string("posterPath")),
			backdropUrl = image(value.string("backdropPath"), backdrop = true),
			providerIds = ProviderIds(tmdb = id, tvdb = value.int("tvdbId"), imdb = value.string("imdbId")),
			availability = if (mediaInfo == null) MediaAvailability.Requestable else mapAvailability(mediaInfo.int("status"), mediaInfo["requests"] != null),
			seasons = value["seasons"]?.jsonArray.orEmpty().mapNotNull { season ->
				val item = season.jsonObject
				val number = item.int("seasonNumber") ?: return@mapNotNull null
				SeerrSeason(number, item.string("name") ?: "Säsong $number", mapAvailability(item.int("status"), false))
			},
		)
	}

	fun mapAvailability(status: Int?, requested: Boolean): MediaAvailability = when (status) {
		1 -> MediaAvailability.Unknown
		2 -> MediaAvailability.Processing
		3 -> MediaAvailability.PartiallyAvailable
		4, 5 -> MediaAvailability.Available
		else -> if (requested) MediaAvailability.Requested else MediaAvailability.Unknown
	}

	private fun image(path: String?, backdrop: Boolean = false) = path?.let { "https://image.tmdb.org/t/p/${if (backdrop) "w1280" else "w500"}$it" }
	private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.content
	private fun JsonObject.int(key: String) = get(key)?.jsonPrimitive?.intOrNull
}
