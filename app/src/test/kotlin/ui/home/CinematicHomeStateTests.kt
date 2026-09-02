package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto

class CinematicHomeStateTests : StringSpec({
	"first media item initializes hero and library folders are skipped" {
		val folder = homeItem(BaseItemKind.COLLECTION_FOLDER, "Movies")
		val movie = homeItem(BaseItemKind.MOVIE, "Arrival")
		firstValidHero(listOf(row(HomeRowKind.LATEST_MOVIES, listOf(folder, movie)))) shouldBe movie
	}

	"top navigation or folder focus retains the current hero" {
		val movie = homeItem(BaseItemKind.MOVIE, "Arrival")
		retainedHero(movie, null) shouldBe movie
		retainedHero(movie, homeItem(BaseItemKind.COLLECTION_FOLDER, "Movies")) shouldBe movie
	}

	"empty resume and next up rows are hidden and latest moves up" {
		val movie = homeItem(BaseItemKind.MOVIE, "Arrival")
		val visible = orderedVisibleRows(listOf(row(HomeRowKind.NEXT_UP), row(HomeRowKind.LATEST_MOVIES, listOf(movie)), row(HomeRowKind.RESUME)))
		visible.map { it.kind } shouldBe listOf(HomeRowKind.LATEST_MOVIES)
	}

	"row order and card formats are cinematic" {
		val item = homeItem(BaseItemKind.MOVIE, "Arrival")
		val rows = orderedVisibleRows(listOf(row(HomeRowKind.LATEST_SERIES, listOf(item)), row(HomeRowKind.RESUME, listOf(item)), row(HomeRowKind.NEXT_UP, listOf(item)), row(HomeRowKind.LATEST_MOVIES, listOf(item))))
		rows.map { it.kind } shouldBe listOf(HomeRowKind.RESUME, HomeRowKind.NEXT_UP, HomeRowKind.LATEST_MOVIES, HomeRowKind.LATEST_SERIES)
		rows.take(2).map { it.format } shouldBe listOf(HomeCardFormat.LANDSCAPE, HomeCardFormat.LANDSCAPE)
		rows.drop(2).map { it.format } shouldBe listOf(HomeCardFormat.POSTER, HomeCardFormat.POSTER)
	}

	"episode code is padded and contains no false separators" {
		val episode = homeItem(BaseItemKind.EPISODE, "Pilot", "Example", 1, 3)
		episode.episodeLabel() shouldBe "S01E03 · Pilot"
		homeItem(BaseItemKind.MOVIE, "Arrival").episodeLabel() shouldBe null
	}

	"progress is bounded and missing runtime produces zero" {
		val data = mockk<UserItemDataDto>(relaxed = true) { every { playbackPositionTicks } returns 50L }
		val item = homeItem(BaseItemKind.MOVIE, "Arrival", userData = data, runtime = 100L)
		item.playbackProgress() shouldBe .5f
		homeItem(BaseItemKind.MOVIE, "Arrival", userData = data).playbackProgress() shouldBe 0f
	}

	"all empty rows result in no visible placeholders" {
		orderedVisibleRows(HomeRowKind.entries.map(::row)).shouldBeEmpty()
	}
})

private fun row(kind: HomeRowKind, items: List<BaseItemDto> = emptyList()) = CinematicHomeRow(
	kind = kind,
	titleRes = R.string.lbl_movies,
	items = items,
	format = if (kind == HomeRowKind.RESUME || kind == HomeRowKind.NEXT_UP) HomeCardFormat.LANDSCAPE else HomeCardFormat.POSTER,
)

private fun homeItem(
	type: BaseItemKind,
	name: String,
	seriesName: String? = null,
	season: Int? = null,
	episode: Int? = null,
	userData: UserItemDataDto? = null,
	runtime: Long? = null,
) = mockk<BaseItemDto>(relaxed = true) {
	every { this@mockk.type } returns type
	every { this@mockk.name } returns name
	every { this@mockk.seriesName } returns seriesName
	every { parentIndexNumber } returns season
	every { indexNumber } returns episode
	every { this@mockk.userData } returns userData
	every { runTimeTicks } returns runtime
}
