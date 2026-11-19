package org.jellyfin.androidtv.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jellyfin.androidtv.ui.catalog.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DetailNavigationUiTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun key(key: Key) = compose.onNode(isFocused()).performKeyInput { pressKey(key) }

    @Test fun incidentalTabFocusDuringLoadingDoesNotRemovePrimaryAction() {
        val title = CatalogTitle("movie", TitleKind.Movie, "Film")
        val page = CatalogPage(CatalogScreen.Details, title)
        val primary = FocusRequester()
        compose.setContent {
            CatalogDetailLayout(page, title, { page.focusToken = it }, actions = { keys ->
                androidx.compose.foundation.lazy.LazyRow(modifier = keys) {
                    item { TvButton("Spela", requester = primary, onClick = {}) }
                }
            }, related = {})
            LaunchedEffect(page.loading) {
                if (!page.loading) { withFrameNanos { }; primary.requestFocus(); page.restoring = false }
            }
        }
        compose.onNodeWithTag("detail-tab-info").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        assertTrue(page.detailsExpanded)
        assertEquals("initial", page.focusToken)
        compose.runOnIdle { page.loading = false }
        compose.onNodeWithText("Spela").assertIsFocused().assertIsDisplayed()
    }

    @Test fun readingTabRetainsFocusAndBackRestoresTopBeforeLeaving() {
        val title = CatalogTitle("series", TitleKind.Series, "Serie", overview = "Lång beskrivning. ".repeat(400))
        val page = CatalogPage(CatalogScreen.Details, title).apply {
            loading = false; items = listOf(title); focusToken = "primary"; restoring = false
        }
        val primary = FocusRequester()
        var popped = 0
        compose.setContent {
            BackHandler { if (!returnToTitleTop(page)) popped++ }
            CatalogDetailLayout(page, title, { page.focusToken = it }, actions = { keys ->
                TvButton("Spela", keys, requester = primary, onClick = {})
            }, related = { TvButton("Avsnitt 1", onClick = {}) })
            LaunchedEffect(page.restore) {
                if (page.focusToken == "primary") { withFrameNanos { }; primary.requestFocus() }
            }
        }
        compose.onNodeWithText("Spela").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText("Spela").assertIsFocused()
        key(Key.DirectionDown)
        compose.onNodeWithTag("detail-tab-content").assertIsFocused()
        key(Key.DirectionRight)
        repeat(4) { key(Key.DirectionDown) }
        compose.onNodeWithTag("detail-tab-info").assertIsFocused()
        compose.onNodeWithTag("detail-reading").assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsProperties.Focused))
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Spela").assertIsFocused().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(350)
        compose.mainClock.autoAdvance = true
        assertTrue(page.detailsExpanded)
        assertEquals(0, popped)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, popped)
    }

    @Test fun newSectionEntryResetsRowsButRetainsRealContents() {
        val page = CatalogPage(CatalogScreen.Home).apply {
            loading = false
            rows = listOf(CatalogRow("row", "Titlar", (0..6).map { CatalogTitle("$it", TitleKind.Movie, "Titel $it") }))
        }
        compose.setContent { HomeRows(page, homeHero(page), {}, {}) }
        repeat(3) { key(Key.DirectionRight) }
        key(Key.DirectionDown)
        repeat(3) { key(Key.DirectionRight) }
        assertEquals(3, page.heroIndex)
        assertEquals(3, page.rowColumns["row"])
        compose.runOnIdle { page.resetNavigation() }
        compose.onNodeWithText("Mer information").assertIsFocused()
        assertEquals(0, page.heroIndex)
        assertEquals(0, page.list.firstVisibleItemScrollOffset)
        assertEquals(7, page.rows.single().titles.size)
        key(Key.DirectionDown)
        assertEquals("row:0", page.focusToken)
    }

    @Test fun searchMenuRestoreRetainsQueryButSectionReentryClearsIt() {
        val page = CatalogPage(CatalogScreen.Search).apply {
            query = "a"
            items = listOf(CatalogTitle("result", TitleKind.Movie, "Resultat"))
            focusToken = "card:result"
        }
        page.restoreFocus()
        assertEquals("a", page.query)
        assertEquals("card:result", page.focusToken)
        assertEquals(1, page.items.size)
        page.resetNavigation()
        assertEquals("", page.query)
        assertEquals("initial", page.focusToken)
        assertTrue(page.items.isEmpty())
        assertFalse(page.loading)
    }
}
