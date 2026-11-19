package org.jellyfin.androidtv.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jellyfin.androidtv.ui.catalog.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeMenuUiTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test fun hardwareBackRunsPolicyOnceWithoutClearingContentFocus() {
        var backs = 0
        val focus = FocusRequester()
        compose.setContent {
            CatalogInputBoundary({ false }, { true }, { backs++ }) {
                TvButton("Content", requester = focus, onClick = {})
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        compose.onNodeWithText("Content").performKeyInput { pressKey(Key.Back) }
        assertEquals(1, backs)
        compose.onNodeWithText("Content").assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun closingMenuBuffersDirectionsAndOkInOrder() {
        var expanded by mutableStateOf(true)
        var visible by mutableStateOf(true)
        val content = FocusRequester()
        var column by mutableIntStateOf(0)
        var activated = -1
        compose.setContent {
            CatalogInputBoundary({ visible && !expanded }, { !visible && column == 0 }, {}) {
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    if (!visible && event.key == Key.DirectionRight) {
                        if (event.type == KeyEventType.KeyDown) column++
                        true
                    } else false
                }) {
                    TvButton("Content", requester = content, onClick = { activated = column })
                    CatalogMenu(expanded, CatalogScreen.Home, { expanded = false }, {
                        visible = false
                        content.requestFocus()
                    }, {})
                }
            }
        }
        compose.onNodeWithTag("menu-Home").assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("menu-Home").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
        }
        assertEquals(-1, activated)
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {
            assertEquals(2, column)
            assertEquals(2, activated)
        }
        compose.onNodeWithText("Content").assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun openingCannotOverwriteAnAlreadyRequestedMenuDestination() {
        val open = mutableStateOf(false)
        val index = mutableIntStateOf(2)
        var selected: CatalogScreen? = null
        compose.setContent {
            CatalogMenu(open.value, CatalogScreen.Discover, {}, {}, { selected = it }, focusIndex = index.intValue)
        }
        compose.runOnIdle { open.value = true; index.intValue = 3 }
        compose.onNodeWithTag("menu-Movies").assertIsFocused().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(CatalogScreen.Movies, selected) }
        compose.runOnIdle { repeat(20) { index.intValue = if (it % 2 == 0) 5 else 0 }; index.intValue = 4 }
        compose.onNodeWithTag("menu-Series").assertIsFocused()
    }

    @Test fun selectionBeforeFirstOpeningFrameStillCompletesClose() {
        val open = mutableStateOf(false)
        val close = mutableIntStateOf(0)
        var completed = 0
        compose.setContent {
            CatalogMenu(open.value, CatalogScreen.Home, {}, { completed++ }, {}, closeEpoch = close.intValue)
        }
        compose.runOnIdle { open.value = true; open.value = false; close.intValue++ }
        compose.runOnIdle { assertEquals(1, completed) }
    }

    @Test fun closedRailHasNoLabelsOrFocusableActions() {
        compose.setContent {
            CatalogMenu(false, CatalogScreen.Home, {}, {}, {})
        }
        compose.onNodeWithText("Hem").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.onAllNodes(hasTestTag("navigation-overlay")).assertCountEquals(1)
    }

    @Test fun openingFocusesActiveSectionAndKeepsContentBounds() {
        val open = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.padding(start = 80.dp, top = 340.dp).size(168.dp, 98.dp).testTag("card"))
                CatalogMenu(open.value, CatalogScreen.Series, {}, {}, {})
            }
        }
        val before = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { open.value = true }
        compose.onNodeWithTag("menu-Series").assertIsFocused()
        assertEquals(before, compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot)
        val entries = listOf("Search", "Home", "Discover", "Movies", "Series", "Settings")
        val positions = entries.map { compose.onNodeWithTag("menu-$it").fetchSemanticsNode().boundsInRoot.top }
        assertEquals(positions.sorted(), positions)
        compose.onAllNodes(hasClickAction()).assertCountEquals(6)
    }

    @Test fun interruptedClosingDoesNotRestoreFocusOrDuplicateOverlay() {
        val open = mutableStateOf(true)
        var closed = 0
        compose.setContent { CatalogMenu(open.value, CatalogScreen.Home, {}, { closed++ }, {}) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { open.value = false }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { open.value = true }
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithTag("menu-Home").assertIsFocused()
        assertEquals(0, closed)
        compose.runOnIdle { open.value = false }
        compose.mainClock.advanceTimeBy(320)
        compose.runOnIdle { assertEquals(1, closed) }
        compose.onNodeWithText("Hem").assertDoesNotExist()
        compose.onAllNodes(hasTestTag("navigation-overlay")).assertCountEquals(1)
    }

    @Test fun closeRestoresExactContentTargetAfterAnimation() {
        val open = mutableStateOf(false)
        val card = FocusRequester()
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                TvButton("Content", requester = card, onClick = {})
                CatalogMenu(open.value, CatalogScreen.Home, {}, { card.requestFocus() }, {})
            }
        }
        compose.runOnIdle { card.requestFocus(); open.value = true }
        compose.onNodeWithTag("menu-Home").assertIsFocused()
        compose.runOnIdle { open.value = false }
        compose.onNodeWithText("Content").assertIsFocused()
    }

    @Test fun longHeroTextDoesNotMoveFollowingRow() {
        val title = mutableStateOf(CatalogTitle("test", TitleKind.Movie, "Kort"))
        compose.setContent {
            Column {
                HomeHero(title.value) {}
                Box(Modifier.size(168.dp, 98.dp).testTag("row"))
            }
        }
        val before = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { title.value = title.value.copy(name = "Lång titel ".repeat(30), overview = "Beskrivning ".repeat(100)) }
        assertEquals(before, compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot)
    }
    @Test fun exitDialogStartsOnStayAndBackNeverExits() {
        val show = mutableStateOf(true)
        var exits = 0
        compose.setContent {
            if (show.value) CatalogExitConfirmation({ show.value = false }, { exits++ })
        }
        compose.onNodeWithText("Stanna kvar").assertIsFocused()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("Vill du avsluta?").assertDoesNotExist()
        assertEquals(0, exits)
        compose.runOnIdle { show.value = true }
        compose.onNodeWithText("Lämna").performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun menuDoesNotResetScrolledContent() {
        val open = mutableStateOf(false)
        val state = androidx.compose.foundation.lazy.LazyListState(8, 17)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                androidx.compose.foundation.lazy.LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                    items(40) { Box(Modifier.height(80.dp).fillMaxWidth()) }
                }
                CatalogMenu(open.value, CatalogScreen.Home, {}, {}, {})
            }
        }
        compose.runOnIdle { open.value = true }
        compose.onNodeWithTag("menu-Home").assertIsFocused()
        compose.runOnIdle { open.value = false }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(8, state.firstVisibleItemIndex)
            assertEquals(17, state.firstVisibleItemScrollOffset)
        }
    }

    @Test fun dismissingExitDialogCannotStealTheRememberedCardFocus() {
        val expanded = mutableStateOf(false)
        val visible = mutableStateOf(false)
        val dialog = mutableStateOf(false)
        val epoch = mutableIntStateOf(0)
        val card = FocusRequester()
        var remembered = "card"
        compose.setContent {
            LaunchedEffect(visible.value) {
                if (!visible.value) { withFrameNanos {}; card.requestFocus() }
            }
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalContentFocusEnabled provides !visible.value) {
                    Column {
                        TvButton("Hero", onFocus = { remembered = "hero" }, onClick = {})
                        TvButton("Retained card", requester = card, onFocus = { remembered = "card" }, onClick = {})
                    }
                }
                CatalogMenu(expanded.value, CatalogScreen.Home,
                    onClose = { expanded.value = false },
                    onClosed = { visible.value = false },
                    onSelect = {}, focusEpoch = epoch.intValue)
                if (dialog.value) CatalogExitConfirmation(
                    { dialog.value = false; epoch.intValue++ }, {})
            }
        }
        compose.onNodeWithText("Retained card").assertIsFocused()
        compose.runOnIdle { visible.value = true; expanded.value = true }
        compose.onNodeWithTag("menu-Home").assertIsFocused()
        compose.runOnIdle { dialog.value = true }
        compose.onNodeWithText("Stanna kvar").assertIsFocused()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("menu-Home").assertIsFocused()
        compose.runOnIdle { assertEquals("card", remembered); expanded.value = false }
        compose.onNodeWithText("Retained card").assertIsFocused()
    }

}
