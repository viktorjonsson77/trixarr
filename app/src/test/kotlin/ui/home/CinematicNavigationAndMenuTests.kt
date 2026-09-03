package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.androidtv.ui.itemdetail.SeriesPrimaryAction
import org.jellyfin.androidtv.ui.itemdetail.orderedSeasons
import org.jellyfin.androidtv.ui.itemdetail.seriesPrimaryActions
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarDestination
import org.jellyfin.androidtv.ui.shared.toolbar.mainToolbarDestinations
import org.jellyfin.androidtv.ui.shared.toolbar.matchingLibraries
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.UserItemDataDto

class CinematicNavigationAndMenuTests : StringSpec({
	"top navigation has exactly the five requested destinations" {
		mainToolbarDestinations() shouldContainExactly listOf(
			MainToolbarDestination.Home,
			MainToolbarDestination.Discover,
			MainToolbarDestination.Movies,
			MainToolbarDestination.Series,
			MainToolbarDestination.Search,
		)
	}

	"movie and series libraries resolve by collection type without fixed ids" {
		val movieA = library(CollectionType.MOVIES, "Movies A")
		val movieB = library(CollectionType.MOVIES, "Movies B")
		val series = library(CollectionType.TVSHOWS, "Series")
		matchingLibraries(listOf(movieA, series, movieB), CollectionType.MOVIES) shouldContainExactly listOf(movieA, movieB)
		matchingLibraries(listOf(movieA, series, movieB), CollectionType.TVSHOWS) shouldContainExactly listOf(series)
	}

	"context menu only offers resume and trailer when supported" {
		contextActions(media(progress = 42L), hasTrailer = true) shouldContainExactly listOf(
			CinematicContextAction.Resume,
			CinematicContextAction.PlayFromBeginning,
			CinematicContextAction.Details,
			CinematicContextAction.Trailer,
			CinematicContextAction.MarkPlayed,
			CinematicContextAction.AddToList,
		)
		contextActions(media(), hasTrailer = false) shouldContainExactly listOf(
			CinematicContextAction.PlayFromBeginning,
			CinematicContextAction.Details,
			CinematicContextAction.MarkPlayed,
			CinematicContextAction.AddToList,
		)
	}

	"destructive server deletion is not exposed by cinematic context menu" {
		contextActions(media(), false).map { it.name } shouldNotContain "Delete"
	}

	"shuffle and overflow are absent from series actions" {
		seriesPrimaryActions(nextUp = media(), hasTrailer = true) shouldContainExactly listOf(
			SeriesPrimaryAction.PlayNext,
			SeriesPrimaryAction.Trailer,
			SeriesPrimaryAction.Favorite,
			SeriesPrimaryAction.Watched,
		)
	}

	"seasons are ordered by server index and missing indexes sort last" {
		val second = season(2, "Season 2")
		val specials = season(0, "Specials")
		val unknown = season(null, "Unknown")
		orderedSeasons(listOf(second, unknown, specials)) shouldContainExactly listOf(specials, second, unknown)
	}
})

private fun library(type: CollectionType, name: String) = mockk<BaseItemDto>(relaxed = true) {
	every { collectionType } returns type
	every { this@mockk.name } returns name
}

private fun media(progress: Long = 0L, played: Boolean = false): BaseItemDto {
	val data = mockk<UserItemDataDto>(relaxed = true) {
		every { playbackPositionTicks } returns progress
		every { this@mockk.played } returns played
	}
	return mockk(relaxed = true) { every { userData } returns data }
}

private fun season(index: Int?, name: String) = mockk<BaseItemDto>(relaxed = true) {
	every { indexNumber } returns index
	every { this@mockk.name } returns name
}
