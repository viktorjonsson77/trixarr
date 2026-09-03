package org.jellyfin.androidtv.ui.itemdetail

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.androidtv.ui.browsing.HistoryDateGroup
import org.jellyfin.androidtv.ui.browsing.historyDateGroup
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.time.LocalDate
import java.time.LocalDateTime

class CinematicDetailModelTests : StringSpec({
	"missing metadata does not fabricate detail facts" {
		val item = mockk<BaseItemDto>(relaxed = true) {
			every { type } returns BaseItemKind.MOVIE
			every { productionYear } returns null
			every { officialRating } returns null
			every { runTimeTicks } returns null
			every { communityRating } returns null
			every { genres } returns null
		}
		detailFacts(item) shouldBe ""
	}

	"history grouping uses the local calendar day" {
		val today = LocalDate.of(2026, 9, 3)
		historyDateGroup(LocalDateTime.of(2026, 9, 3, 0, 1), today) shouldBe HistoryDateGroup.Today
		historyDateGroup(LocalDateTime.of(2026, 9, 2, 23, 59), today) shouldBe HistoryDateGroup.Yesterday
		historyDateGroup(LocalDateTime.of(2026, 8, 30, 12, 0), today) shouldBe HistoryDateGroup.ThisWeek
		historyDateGroup(LocalDateTime.of(2026, 8, 1, 12, 0), today) shouldBe HistoryDateGroup.Older
	}

	"detail availability is provider independent" {
		DetailAvailability.entries.map { it.name } shouldBe listOf("InLibrary", "Requestable", "Requested", "Processing", "PartiallyAvailable", "Unavailable")
	}
})
