package org.jellyfin.androidtv.ui.discover

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.jellyfin.androidtv.seerr.model.MediaAvailability
import org.jellyfin.androidtv.seerr.model.MediaKind
import org.jellyfin.androidtv.seerr.model.SeerrMedia

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiscoverLayoutTests : StringSpec({
	"floating information has no permanent hero or row measurement contract" {
		DiscoverLayoutContract.DwellMillis shouldBe 800L
		DiscoverLayoutContract.BackdropDebounceMillis shouldBe 180L
		DiscoverLayoutContract.BackdropFadeMillis shouldBe 900
		discoverHeroPresentation(media(1, "Kort", null)).description shouldBe ""
		discoverHeroPresentation(media(2, "En mycket lång titel", "Lång ".repeat(100))).title shouldBe "En mycket lång titel"
	}

	"hero text line contracts are fixed" {
		DiscoverLayoutContract.TitleMaxLines shouldBe 2
		DiscoverLayoutContract.DescriptionMaxLines shouldBe 2
	}

	"popup is centered above a normal card" {
		val result = calculateFloatingInfoPosition(Rect(700f, 500f, 820f, 680f), 1920f, 1080f, 360f, 145f, 32f, 12f)
		result.above shouldBe true
		result.x shouldBe 580f
		result.y shouldBe 343f
		result.anchorX shouldBe 180f
	}

	"popup falls below when there is no room above" {
		val result = calculateFloatingInfoPosition(Rect(700f, 80f, 820f, 260f), 1920f, 1080f, 360f, 145f, 32f, 12f)
		result.above shouldBe false
		result.y shouldBe 272f
	}

	"popup clamps to horizontal safe area while anchor follows card" {
		val left = calculateFloatingInfoPosition(Rect(8f, 500f, 112f, 680f), 1920f, 1080f, 360f, 145f, 32f, 12f)
		left.x shouldBe 32f
		left.anchorX shouldBe 28f
		val right = calculateFloatingInfoPosition(Rect(1810f, 500f, 1914f, 680f), 1920f, 1080f, 360f, 145f, 32f, 12f)
		right.x shouldBe 1528f
		right.anchorX shouldBe 334f
	}

	"dwell appears only after 800 ms of stable focus" {
		runTest {
			var shown: String? = null
			val controller = DiscoverDwellController(kotlinx.coroutines.CoroutineScope(StandardTestDispatcher(testScheduler))) { shown = it }
			controller.update("Movie:1", boundsMeasured = true, scrolling = false, active = true)
			runCurrent()
			advanceTimeBy(799)
			shown shouldBe null
			advanceTimeBy(1)
			runCurrent()
			shown shouldBe "Movie:1"
		}
	}

	"new focus and navigation cancellation prevent stale dwell" {
		runTest {
			val shown = mutableListOf<String>()
			val controller = DiscoverDwellController(this, shown::add)
			controller.update("Movie:1", true, false, true)
			advanceTimeBy(400)
			controller.update("Series:2", true, false, true)
			advanceTimeBy(799)
			shown shouldBe emptyList()
			controller.dismiss()
			advanceTimeBy(1)
			shown shouldBe emptyList()
		}
	}

	"scroll lifecycle and missing bounds prevent dwell" {
		runTest {
			val shown = mutableListOf<String>()
			val controller = DiscoverDwellController(this, shown::add)
			controller.update("Movie:1", false, false, true)
			advanceTimeBy(800)
			controller.update("Movie:1", true, true, true)
			advanceTimeBy(800)
			controller.update("Movie:1", true, false, false)
			advanceTimeBy(800)
			shown shouldBe emptyList()
		}
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
