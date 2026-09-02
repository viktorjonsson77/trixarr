package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

class CinematicHomeHeroTests : StringSpec({
	"missing title produces no hero" {
		item().toCinematicHeroMetadata() shouldBe null
	}

	"missing optional metadata is omitted" {
		val metadata = item(name = "Arrival").toCinematicHeroMetadata()!!
		metadata.title shouldBe "Arrival"
		metadata.subtitle shouldBe null
		metadata.facts.shouldBeEmpty()
		metadata.overview shouldBe null
	}

	"episode uses series and episode titles" {
		val metadata = item(type = BaseItemKind.EPISODE, name = "Pilot", seriesName = "Example").toCinematicHeroMetadata()!!
		metadata.title shouldBe "Example"
		metadata.subtitle shouldBe "Pilot"
	}
})

private fun item(
	name: String? = null,
	type: BaseItemKind = BaseItemKind.VIDEO,
	seriesName: String? = null,
) = mockk<BaseItemDto>(relaxed = true) {
	every { this@mockk.name } returns name
	every { this@mockk.type } returns type
	every { this@mockk.seriesName } returns seriesName
	every { productionYear } returns null
	every { runTimeTicks } returns null
	every { officialRating } returns null
	every { communityRating } returns null
	every { overview } returns null
}
