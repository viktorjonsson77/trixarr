package org.jellyfin.androidtv.ui.discover

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.seerr.model.MediaAvailability
import org.jellyfin.androidtv.seerr.model.MediaKind
import org.jellyfin.androidtv.seerr.model.SeerrMedia

class DiscoverLayoutTests : StringSpec({
	"hero and row viewport dimensions are independent of metadata" {
		DiscoverLayoutContract.TitleHeight + DiscoverLayoutContract.MetadataHeight +
			DiscoverLayoutContract.DescriptionHeight + DiscoverLayoutContract.ActionHeight shouldBe DiscoverLayoutContract.HeroHeight
		DiscoverLayoutContract.rowViewportStart(48.dp) shouldBe 202.dp
		discoverHeroPresentation(media(1, "Kort", null)).let {
			DiscoverLayoutContract.rowViewportStart(48.dp) shouldBe 202.dp
			it.description shouldBe ""
		}
		discoverHeroPresentation(media(2, "En mycket lång titel som behöver två reserverade rader", "Lång ".repeat(100))).let {
			DiscoverLayoutContract.rowViewportStart(48.dp) shouldBe 202.dp
			it.title shouldBe "En mycket lång titel som behöver två reserverade rader"
		}
	}

	"hero text line contracts are fixed" {
		DiscoverLayoutContract.TitleMaxLines shouldBe 2
		DiscoverLayoutContract.DescriptionMaxLines shouldBe 3
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
})

private fun media(id: Int, title: String, overview: String?, backdrop: String? = null) = SeerrMedia(
	id = id,
	kind = MediaKind.Series,
	title = title,
	overview = overview,
	backdropUrl = backdrop,
	availability = MediaAvailability.Requestable,
)
