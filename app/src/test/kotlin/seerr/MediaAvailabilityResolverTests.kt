package org.jellyfin.androidtv.seerr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.seerr.model.MediaAvailability
import org.jellyfin.androidtv.seerr.model.MediaKind
import org.jellyfin.androidtv.seerr.model.ProviderIds
import org.jellyfin.androidtv.seerr.model.SeerrMedia

class MediaAvailabilityResolverTests : StringSpec({
	fun local(title: String = "Arrival", year: Int = 2016, ids: ProviderIds) = LocalMedia("local", MediaKind.Movie, title, year, ids)
	fun external(title: String = "Arrival", year: Int = 2016, ids: ProviderIds) = SeerrMedia(ids.tmdb ?: 1, MediaKind.Movie, title, year, providerIds = ids)

	"deduplicates TMDb and lets Jellyfin win" {
		val result = MediaAvailabilityResolver.merge(listOf(local(ids = ProviderIds(tmdb = 329865))), listOf(external(ids = ProviderIds(tmdb = 329865))))
		result shouldHaveSize 1
		(result.single() is UnifiedMedia.InLibrary) shouldBe true
	}
	"deduplicates TVDb and IMDb" {
		MediaAvailabilityResolver.merge(listOf(local(ids = ProviderIds(tvdb = 1))), listOf(external(ids = ProviderIds(tvdb = 1)))) shouldHaveSize 1
		MediaAvailabilityResolver.merge(listOf(local(ids = ProviderIds(imdb = "tt1"))), listOf(external(ids = ProviderIds(imdb = "TT1")))) shouldHaveSize 1
	}
	"uses exact normalized title and year only as fallback" {
		MediaAvailabilityResolver.merge(listOf(local(title = "The Arrival!", ids = ProviderIds())), listOf(external(title = "The Arrival", ids = ProviderIds()))) shouldHaveSize 1
		MediaAvailabilityResolver.merge(listOf(local(year = 2016, ids = ProviderIds())), listOf(external(year = 2024, ids = ProviderIds()))) shouldHaveSize 2
	}
	"unknown and partial statuses cannot be requested" {
		MediaAvailabilityResolver.canRequest(MediaAvailability.Unknown, true, true) shouldBe false
		MediaAvailabilityResolver.canRequest(MediaAvailability.PartiallyAvailable, true, true) shouldBe false
		MediaAvailabilityResolver.canRequest(MediaAvailability.Requestable, true, true) shouldBe true
	}
})
