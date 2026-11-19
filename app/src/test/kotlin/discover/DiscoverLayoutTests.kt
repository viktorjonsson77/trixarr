package org.jellyfin.androidtv.ui.discover

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.seerr.model.MediaAvailability
import org.jellyfin.androidtv.seerr.model.MediaKind
import org.jellyfin.androidtv.seerr.model.SeerrMedia

class DiscoverLayoutTests : StringSpec({
	"browsing header and row group contracts are fixed" {
		DiscoverLayoutContract.RowsPerGroup shouldBe 2
		DiscoverLayoutContract.BackdropDebounceMillis shouldBe 180L
		DiscoverLayoutContract.BackdropFadeMillis shouldBe 650
		discoverHeroPresentation(media(1, "Kort", null)).description shouldBe ""
		discoverHeroPresentation(media(2, "En mycket lång titel", "Lång ".repeat(100))).title shouldBe "En mycket lång titel"
	}

	"hero text line contracts are fixed" {
		DiscoverLayoutContract.TitleMaxLines shouldBe 2
		DiscoverLayoutContract.DescriptionMaxLines shouldBe 2
	}

	"row groups always expose two rows when at least two exist" {
		groupStarts(0) shouldBe listOf(0)
		groupStarts(1) shouldBe listOf(0)
		groupStarts(2) shouldBe listOf(0)
		groupStarts(3) shouldBe listOf(0, 1)
		groupStarts(4) shouldBe listOf(0, 2)
		groupStarts(5) shouldBe listOf(0, 2, 3)
	}

	"latest selected item supplies both backdrop and metadata without changing selection order" {
		val first = DiscoverEntry(media(1, "Första", "Kort", "/first.jpg"))
		val latest = DiscoverEntry(media(2, "Senaste", "Lång", "/latest.jpg"))
		val rows = listOf("Rad" to listOf(first, latest))
		val selected = resolveDiscoverSelection(rows, 2, MediaKind.Series.name)!!
		val presentation = discoverHeroPresentation(selected.media)
		presentation.mediaKey shouldBe "Series:2"
		presentation.title shouldBe "Senaste"
		presentation.backdropUrl shouldBe "/latest.jpg"
		rows.map { it.first } shouldBe listOf("Rad")
	}

	"selection restore prefers the saved row group when media is duplicated" {
		val duplicate = DiscoverEntry(media(7, "Duplicerad", null))
		val rows = listOf(
			"Trendigt" to listOf(duplicate),
			"Filmer" to listOf(DiscoverEntry(media(8, "Film", null))),
			"Serier" to listOf(duplicate),
			"Kommande" to listOf(DiscoverEntry(media(9, "Kommande", null))),
		)

		resolveDiscoverSelectionInGroup(rows, 1, 7, MediaKind.Series.name) shouldBe duplicate
		resolveDiscoverSelectionInGroup(rows, 1, 8, MediaKind.Series.name) shouldBe null
	}
})

private fun media(id: Int, title: String, overview: String?, backdrop: String? = null) = SeerrMedia(
	id = id,
	kind = MediaKind.Series,
	title = title,
	overview = overview,
	backdropUrl = backdrop,
	availability = MediaAvailability.Requestable,
)
