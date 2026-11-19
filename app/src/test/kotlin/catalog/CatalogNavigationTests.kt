package org.jellyfin.androidtv.ui.catalog

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CatalogNavigationTests :
    StringSpec({
        "row pivot is centered with a safe bound for narrow viewports" {
            CatalogNavigationPolicy.cardAnchor(800f, 168f, 6f) shouldBe 316f
            CatalogNavigationPolicy.cardAnchor(150f, 168f, 6f) shouldBe 6f
        }
        "each destination row restores its own column and clamps after removal" {
            CatalogNavigationPolicy.rememberedColumn(null, 12) shouldBe 0
            CatalogNavigationPolicy.rememberedColumn(8, 12) shouldBe 8
            CatalogNavigationPolicy.rememberedColumn(8, 3) shouldBe 2
            CatalogNavigationPolicy.rememberedColumn(-1, 3) shouldBe 0
        }

        "Back opens closed menu then requests explicit exit confirmation" {
            catalogBackAction(false) shouldBe CatalogBackAction.OpenMenu
            catalogBackAction(true) shouldBe CatalogBackAction.ConfirmExit
        }
        "dialog and filter dismissal precede menu and exit handling" {
            catalogBackAction(false, true) shouldBe CatalogBackAction.DismissOverlay
            catalogBackAction(true, true) shouldBe CatalogBackAction.DismissOverlay
        }

        "back from a section returns to the previous section with the same state" {
            data class Page(val section: String, val poster: String, val scroll: Int)
            val home = Page("Home", "resume:episode", 7)
            val discover = Page("Discover", "title:42", 18)
            val stack = mutableListOf(home)
            visitSection(stack, discover)
            visitSection(stack, Page("Movies", "movie:3", 4))
            popScreen(stack) shouldBe true
            (stack.last() === discover) shouldBe true
            stack.last().scroll shouldBe 18
            stack.last().poster shouldBe "title:42"
        }
        "revisiting an earlier section does not create duplicate screen histories" {
            val stack = mutableListOf("Home", "Discover", "Details", "Information")
            visitSection(stack, "Discover")
            stack shouldBe listOf("Home", "Discover")
            visitSection(stack, "Discover")
            stack shouldBe listOf("Home", "Discover")
        }
        "back through details information and episodes preserves underlying entry" {
            val stack =
                mutableListOf("Grid:row9:column4", "Details:information-button", "Information")
            popScreen(stack)
            stack.last() shouldBe "Details:information-button"
            stack += "Episodes:season2:episode3"
            popScreen(stack)
            popScreen(stack)
            stack.single() shouldBe "Grid:row9:column4"
            popScreen(stack) shouldBe false
        }
    })
