package org.jellyfin.androidtv.seerr

import org.jellyfin.androidtv.seerr.model.MediaAvailability
import org.jellyfin.androidtv.seerr.model.MediaKind
import org.jellyfin.androidtv.seerr.model.ProviderIds
import org.jellyfin.androidtv.seerr.model.SeerrMedia

data class LocalMedia(
	val id: String,
	val kind: MediaKind,
	val title: String,
	val year: Int?,
	val providerIds: ProviderIds,
)

sealed interface UnifiedMedia {
	val title: String
	data class InLibrary(val local: LocalMedia, val external: SeerrMedia? = null) : UnifiedMedia { override val title = local.title }
	data class External(val media: SeerrMedia) : UnifiedMedia { override val title = media.title }
}
object MediaAvailabilityResolver {
	fun merge(local: List<LocalMedia>, external: List<SeerrMedia>): List<UnifiedMedia> {
		val remaining = external.toMutableList()
		val localResults = local.map { item ->
			val match = remaining.firstOrNull { same(item, it) }
			if (match != null) remaining.remove(match)
			UnifiedMedia.InLibrary(item, match)
		}
		return localResults + remaining.map(UnifiedMedia::External)
	}

	fun canRequest(availability: MediaAvailability, authenticated: Boolean, permitted: Boolean) =
		authenticated && permitted && availability == MediaAvailability.Requestable

	private fun same(local: LocalMedia, external: SeerrMedia): Boolean {
		if (local.kind != external.kind) return false
		val a = local.providerIds; val b = external.providerIds
		if (a.tmdb != null && a.tmdb == b.tmdb) return true
		if (a.tvdb != null && a.tvdb == b.tvdb) return true
		if (!a.imdb.isNullOrBlank() && a.imdb.equals(b.imdb, ignoreCase = true)) return true
		return normalize(local.title) == normalize(external.title) && local.year != null && local.year == external.year
	}

	private fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
}
