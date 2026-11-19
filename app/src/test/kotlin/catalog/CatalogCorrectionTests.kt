package org.jellyfin.androidtv.ui.catalog

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CatalogCorrectionTests : StringSpec({
    "nested back precedes opening menu and modals precede nested navigation" {
        catalogBackAction(false, nested = true) shouldBe CatalogBackAction.PreviousScreen
        catalogBackAction(true, nested = true) shouldBe CatalogBackAction.ConfirmExit
        catalogBackAction(true, modalOpen = true, nested = true) shouldBe CatalogBackAction.DismissOverlay
    }
    "catalog sections are typed and category membership comes only from metadata" {
        val movie = CatalogTitle("movie", TitleKind.Movie, "Film", genres = listOf("Drama"), studios = listOf("Studio A"))
        val series = CatalogTitle("series", TitleKind.Series, "Serie", genres = listOf("Komedi"), studios = listOf("Studio B"))
        listOf(TitleKind.Movie, TitleKind.Series).forEach { kind ->
            val sections = buildCatalogSections(kind, listOf(movie, series), emptyList(), listOf(series, movie), emptyList(), emptyList())
            sections.flatMap { it.titles }.all { it.kind == kind } shouldBe true
            sections.flatMap { it.categories }.flatMap { it.titles }.all { it.kind == kind } shouldBe true
            sections.none { it.key in listOf("resume", "popular", "upcoming") } shouldBe true
            sections.first { it.key == "studios" }.categories.single().name shouldBe if (kind == TitleKind.Movie) "Studio A" else "Studio B"
        }
        buildCatalogSections(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList()) shouldBe emptyList()
    }
    "full metadata retains all genres and overview without placeholder fields" {
        val title = CatalogTitle("request", TitleKind.Movie, "Titel", overview = "Fullständig beskrivning", genres = listOf("A", "B", "C"), requestAllowed = true, status = TitleStatus.Requestable)
        title.primary shouldBe TitleAction.Request
        metadataLines(title).any { it == "A, B, C" } shouldBe true
        metadataLines(title).none { "N/A" in it || "null" in it } shouldBe true
    }
    "rapid focus changes wait for dwell and only load the final title" {
        runTest {
            val loader = LatestFanart<String>(this)
            val loaded = mutableListOf<String>()
            val shown = mutableListOf<String?>()
            repeat(10) { i ->
                loader.submit({ "$i".also(loaded::add) }, shown::add)
                advanceTimeBy(30)
            }
            loaded shouldBe emptyList()
            advanceTimeBy(FANART_DWELL_MS)
            runCurrent()
            loaded shouldBe listOf("9")
            shown shouldBe listOf("9")
        }
    }
})
