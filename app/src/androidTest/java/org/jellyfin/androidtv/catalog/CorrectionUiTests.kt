package org.jellyfin.androidtv.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.draw.drawWithContent
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jellyfin.androidtv.ui.catalog.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CorrectionUiTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val recorded = AtomicReference<GraphicsLayer?>()
    private fun content(body: @Composable () -> Unit) {
        compose.setContent {
            val layer = rememberGraphicsLayer()
            Box(Modifier.fillMaxSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                recorded.set(layer)
            }) { body() }
        }
    }
    private fun snapshot(name: String): Bitmap {
        compose.waitForIdle()
        compose.waitUntil { recorded.get() != null }
        val bitmap = runBlocking { recorded.get()!!.toImageBitmap() }.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        val dir = File(compose.activity.getExternalFilesDir(null), "correction-evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
    private fun fixture(home: Boolean): CatalogPage = CatalogPage(if (home) CatalogScreen.Home else CatalogScreen.Discover).apply {
        loading = false
        rows = (0..6).map { r -> CatalogRow("row$r", "Rad $r", (0..12).map { c -> CatalogTitle("$r-$c", TitleKind.Movie, "Titel $r $c") }) }
        items = rows.flatMap { it.titles }
    }
    private fun key(key: Key) { compose.onNode(isFocused()).performKeyInput { pressKey(key) }; compose.waitForIdle() }

    @Test fun menuFocusHandoffPreservesRememberedCardAndHeroIdentity() {
        val page = fixture(false)
        var enabled by mutableStateOf(true)
        val menu = androidx.compose.ui.focus.FocusRequester()
        content {
            Box {
                CompositionLocalProvider(LocalContentFocusEnabled provides enabled) {
                    CatalogRows(page, page.items.firstOrNull { it.key == page.selected }, {}, {}, {})
                }
                if (!enabled) TvButton("Meny", requester = menu, onClick = {})
            }
            LaunchedEffect(enabled) { if (!enabled) menu.requestFocus() }
        }
        repeat(3) { key(Key.DirectionRight) }
        repeat(5) {
            compose.runOnIdle { enabled = false }
            compose.onNodeWithText("Meny").assertIsFocused()
            compose.runOnIdle { page.restoreFocus(); enabled = true }
            compose.onNodeWithTag("target-row0:0-3").assertIsFocused().assertIsDisplayed()
            compose.runOnIdle {
                assertEquals("row0:0-3", page.focusToken)
                assertEquals("0-3", page.selected)
                assertEquals(3, page.rowColumns["row0"])
            }
        }
    }

    @Test fun heroRotationKeepsFocusAndStopsInRowsOrBehindMenu() {
        val page = fixture(true)
        var enabled by mutableStateOf(true)
        content {
            CompositionLocalProvider(LocalContentFocusEnabled provides enabled) {
                HomeRows(page, homeHero(page), {}, {})
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(5100)
        compose.runOnIdle { assertEquals(1, page.heroIndex); assertEquals("hero", page.focusToken) }
        compose.onNodeWithText("Mer information").assertIsFocused()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(10100)
        compose.runOnIdle { assertEquals(1, page.heroIndex); assertEquals("row0:0-0", page.focusToken) }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(10100)
        compose.runOnIdle { assertEquals(1, page.heroIndex) }
    }

    @Test fun heroCarouselClampsAndPreservesIndependentRowFocus() {
        val page = fixture(true)
        var menus = 0
        var opened: String? = null
        content { HomeRows(page, homeHero(page), { menus++ }, { opened = it.key }) }
        repeat(7) { key(Key.DirectionRight) }
        assertEquals(4, page.heroIndex)
        assertEquals("0-4", homeHero(page)?.key)
        key(Key.DirectionCenter)
        assertEquals("0-4", opened)
        key(Key.DirectionDown)
        assertEquals("row0:0-0", page.focusToken)
        key(Key.DirectionRight)
        key(Key.DirectionUp)
        assertEquals("0-4", homeHero(page)?.key)
        assertEquals(1, page.rowColumns["row0"])
        repeat(4) { key(Key.DirectionLeft) }
        assertEquals(0, menus)
        key(Key.DirectionLeft)
        assertEquals(1, menus)
    }

    @Test fun homeHeroOpensDetailsEvenForPlayableTitles() {
        val page = fixture(true)
        val playable = page.rows.first().titles.first().copy(playbackId = "playable-id")
        page.rows = listOf(page.rows.first().copy(titles = listOf(playable))) + page.rows.drop(1)
        var opened: String? = null
        content { HomeRows(page, homeHero(page), {}, { opened = it.key }) }
        compose.onNodeWithText("Mer information").assertIsFocused()
        key(Key.DirectionCenter)
        assertEquals(playable.key, opened)
    }

    @Test fun rowsRememberColumnsAndKeepHeroWhileReturningToFullTop() {
        val page = fixture(true)
        content {
            val hero = homeHero(page)
            HomeRows(page, hero, {}, {})
        }
        compose.waitForIdle()
        val top = compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot
        key(Key.DirectionDown)
        repeat(4) { key(Key.DirectionRight) }
        val compact = compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot
        val rowTop = compose.onNodeWithTag("section-row0").fetchSemanticsNode().boundsInRoot.top
        key(Key.DirectionDown)
        assertEquals("row1:1-0", page.focusToken)
        assertEquals(compact, compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot)
        assertEquals(rowTop, compose.onNodeWithTag("section-row1").fetchSemanticsNode().boundsInRoot.top, 1f)
        repeat(2) { key(Key.DirectionRight) }
        key(Key.DirectionUp)
        assertEquals("row0:0-4", page.focusToken)
        key(Key.DirectionDown)
        assertEquals("row1:1-2", page.focusToken)
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        assertEquals("hero", page.focusToken)
        assertEquals("0-0", homeHero(page)?.key)
        assertEquals(4, page.rowColumns["row0"])
        assertEquals(top, compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot)
        assertEquals(0, page.list.firstVisibleItemIndex)
        assertEquals(0, page.list.firstVisibleItemScrollOffset)
    }

    @Test fun rowEdgesStayVisibleAndLeftOpensMenuOnlyAtFirstCard() {
        val page = fixture(false)
        var menus = 0
        content { CatalogRows(page, page.items.first(), {}, {}, { menus++ }) }
        compose.waitForIdle()
        key(Key.DirectionLeft)
        assertEquals(1, menus)
        compose.mainClock.autoAdvance = false
        compose.onNode(isFocused()).performKeyInput { repeat(20) { pressKey(Key.DirectionRight) } }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("row0:0-12", page.focusToken)
        compose.onNodeWithTag("target-row0:0-12").assertIsDisplayed().assertIsFocused()
        key(Key.DirectionRight)
        assertEquals("row0:0-12", page.focusToken)
        key(Key.DirectionLeft)
        assertEquals("row0:0-11", page.focusToken)
        assertEquals(1, menus)
    }

    @Test fun titleFocusScalesWithoutRemeasuringAndOkUsesImmediateFocus() {
        val first = androidx.compose.ui.focus.FocusRequester()
        val second = androidx.compose.ui.focus.FocusRequester()
        val measured = mutableListOf<androidx.compose.ui.unit.IntSize>()
        var opened = ""
        content {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TitleCard(CatalogTitle("a", TitleKind.Movie, "A"),
                    Modifier.size(168.dp, 96.dp).testTag("animated-a")
                        .then(Modifier.onSizeChanged { measured.add(it) }),
                    requester = first, onFocus = {}, onClick = { opened = "a" })
                TitleCard(CatalogTitle("b", TitleKind.Movie, "B"),
                    Modifier.size(168.dp, 96.dp).testTag("animated-b"),
                    requester = second, onFocus = {}, onClick = { opened = "b" })
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        compose.waitForIdle()
        val focusedWidth = compose.onNodeWithTag("animated-a").fetchSemanticsNode().boundsInRoot.width
        val normalWidth = compose.onNodeWithTag("animated-b").fetchSemanticsNode().boundsInRoot.width
        snapshot("title-focus-first")
        val measurements = measured.toList()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { second.requestFocus() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("animated-b").assertIsFocused()
        compose.onNodeWithTag("animated-b").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals("b", opened)
        compose.mainClock.advanceTimeBy(100)
        snapshot("title-focus-mid")
        compose.mainClock.advanceTimeBy(140)
        assertEquals(normalWidth, compose.onNodeWithTag("animated-a").fetchSemanticsNode().boundsInRoot.width, .5f)
        assertEquals(focusedWidth, compose.onNodeWithTag("animated-b").fetchSemanticsNode().boundsInRoot.width, .5f)
        assertEquals(normalWidth * TITLE_FOCUS_SCALE, focusedWidth, .5f)
        assertEquals(measurements, measured)
        snapshot("title-focus-second")
        compose.mainClock.autoAdvance = true
    }

    @Test fun okDuringVerticalMotionOpensLatestLogicalTitle() {
        val page = fixture(true)
        var opened = ""
        content { HomeRows(page, page.rows.first().titles.first(), {}, { opened = it.key }) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNode(isFocused()).performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }
        assertEquals("1-0", opened)
        compose.mainClock.autoAdvance = true
    }

    @Test fun leavingDuringVerticalMotionSettlesAtLogicalRow() {
        val page = fixture(true)
        val enabled = mutableStateOf(true)
        content { CompositionLocalProvider(LocalContentFocusEnabled provides enabled.value) {
            HomeRows(page, page.rows.first().titles.first(), {}, {})
        } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNode(isFocused()).performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
        }
        compose.runOnUiThread { enabled.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(1, page.list.firstVisibleItemIndex)
            assertEquals(0, page.list.firstVisibleItemScrollOffset)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun rapidHorizontalReversalsSettleOnLatestCardAndHero() {
        val page = fixture(false)
        content {
            val hero = page.items.firstOrNull { it.key == page.selected } ?: page.items.first()
            CatalogRows(page, hero, {}, {}, {})
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNode(isFocused()).performKeyInput {
            repeat(10) { pressKey(Key.DirectionRight) }
            repeat(3) { pressKey(Key.DirectionLeft) }
            repeat(2) { pressKey(Key.DirectionRight) }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("0-9", page.selected); assertEquals("row0:0-9", page.focusToken) }
        val card = compose.onNodeWithTag("target-row0:0-9").assertIsFocused().fetchSemanticsNode().boundsInRoot
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(card.left >= viewport.left)
        assertTrue(card.right <= viewport.right)
        compose.onNode(hasText("Titel 0 9") and hasAnyAncestor(hasTestTag("catalog-hero"))).assertExists()
        snapshot("title-rapid-final")
    }

    @Test fun slowFanartFadeKeepsOpaqueBackgroundAndOnlyTwoLayers() {
        val folder = File(compose.activity.cacheDir, "fanart-animation-fixture").apply { mkdirs() }
        fun image(name: String, color: Int): String {
            val file = File(folder, "$name.png")
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            return file.toURI().toString()
        }
        val first = image("first", android.graphics.Color.YELLOW)
        val next = image("next", android.graphics.Color.GREEN)
        val final = image("final", android.graphics.Color.CYAN)
        val url = mutableStateOf(first)
        val ready = AtomicReference<String?>()
        content { Fanart(url.value, onReady = { ready.set(it) }) }
        compose.waitUntil(5000) { ready.get() == first }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { url.value = next }
        compose.waitUntil(5000) {
            compose.mainClock.advanceTimeByFrame()
            ready.get() == next
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(2, compose.onNodeWithTag("catalog-fanart").fetchSemanticsNode().config[FanartLayerCountKey])
        repeat(100) { frame ->
            if (frame == 30) compose.runOnUiThread { url.value = final }
            compose.mainClock.advanceTimeBy(16)
            val bitmap = snapshot("fanart-frame-$frame")
            val pixel = bitmap.getPixel(bitmap.width * 9 / 10, bitmap.height * 4 / 10)
            assertTrue("Visible fanart frame $frame", android.graphics.Color.green(pixel) > 100)
            val layers = compose.onNodeWithTag("catalog-fanart").fetchSemanticsNode().config[FanartLayerCountKey]
            assertTrue(layers in 1..2)
        }
        compose.waitUntil(5000) {
            compose.mainClock.advanceTimeByFrame()
            ready.get() == final
        }
        compose.mainClock.advanceTimeBy(1632)
        val finalBitmap = snapshot("fanart-final-retarget")
        val finalPixel = finalBitmap.getPixel(finalBitmap.width * 9 / 10, finalBitmap.height * 4 / 10)
        assertTrue(android.graphics.Color.blue(finalPixel) > 100)
        assertEquals(1, compose.onNodeWithTag("catalog-fanart").fetchSemanticsNode().config[FanartLayerCountKey])
        compose.mainClock.autoAdvance = true
        folder.deleteRecursively()
    }

    @Test fun rapidVerticalMovementAcrossCategoryRowDoesNotOvershoot() {
        val page = fixture(false)
        page.rows = page.rows.mapIndexed { index, row ->
            if (index == 3) row.copy(titles = emptyList(), categories = listOf(CatalogCategory("Genre", page.items.take(2)))) else row
        }
        val positions = mutableListOf<Pair<Int, Int>>()
        content {
            CatalogRows(page, page.items.first(), {}, {}, {})
            LaunchedEffect(page) {
                snapshotFlow { page.list.firstVisibleItemIndex to page.list.firstVisibleItemScrollOffset }
                    .collect { positions.add(it) }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNode(isFocused()).performKeyInput { repeat(4) { pressKey(Key.DirectionDown) } }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("row4:4-0", page.focusToken)
            assertEquals(4, page.list.firstVisibleItemIndex)
            assertEquals(0, page.list.firstVisibleItemScrollOffset)
            assertTrue(positions.zipWithNext().all { (a, b) -> b.first > a.first || b.first == a.first && b.second >= a.second })
        }
    }

    @Test fun okReleaseAfterVerticalAnimationStillOpensCapturedTitle() {
        val page = fixture(true)
        var opened = ""
        content { HomeRows(page, page.items.first(), {}, { opened = it.key }) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            keyDown(Key.DirectionCenter)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("", opened)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        assertEquals("1-0", opened)
    }

    @Test fun focusedCardDrawsOverFollowingNeighborWithoutMovingIt() {
        val first = androidx.compose.ui.focus.FocusRequester()
        content {
            Row(Modifier.padding(12.dp)) {
                TitleCard(CatalogTitle("z", TitleKind.Movie, "Z"), Modifier.size(168.dp, 96.dp).testTag("z-card"),
                    requester = first, onFocus = {}, onClick = {})
                Box(Modifier.size(168.dp, 96.dp).testTag("z-neighbor").background(Color.Green))
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        compose.waitForIdle()
        val neighbor = compose.onNodeWithTag("z-neighbor").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("z-card").fetchSemanticsNode().boundsInRoot
        assertTrue(card.right > neighbor.left)
        val bitmap = snapshot("title-z-index")
        val edge = bitmap.getPixel((card.right - 2).toInt(), card.center.y.toInt())
        assertTrue(android.graphics.Color.red(edge) > 200 && android.graphics.Color.green(edge) > 200 && android.graphics.Color.blue(edge) > 200)
        assertEquals(neighbor, compose.onNodeWithTag("z-neighbor").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun sameFrameImageAndMetadataKeepDetailsAndRejectOldIdentity() {
        val page = CatalogPage(CatalogScreen.Discover)
        val first = CatalogTitle("first", TitleKind.Movie, "First")
        val next = CatalogTitle("next", TitleKind.Movie, "Next")
        val full = next.copy(genres = listOf("Drama"), runtimeTicks = 600_000_000L)
        val details = mutableStateOf<CatalogTitle?>(null)
        page.presentedHero = first
        content { SyncHeroMetadata(page, details.value) }
        compose.runOnIdle { details.value = full }
        compose.runOnIdle { assertEquals(first, page.presentedHero) }
        compose.runOnIdle { details.value = null }
        compose.runOnIdle { page.presentedHero = next; details.value = full }
        compose.runOnIdle { assertEquals(full, page.presentedHero) }
        compose.runOnIdle { details.value = first.copy(genres = listOf("Old")) }
        compose.runOnIdle { assertEquals(full, page.presentedHero) }
    }

    @Test fun tenHomeCyclesRestoreExactTopAndHeroBounds() {
        val page = fixture(true)
        content { Box(Modifier.fillMaxSize().padding(start = 80.dp, top = 26.dp, end = 36.dp, bottom = 26.dp)) {
            HomeRows(page, page.rows.first().titles.first(), {}, {})
        } }
        compose.onNodeWithText("Mer information").assertIsFocused()
        val hero = compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("section-row0").fetchSemanticsNode().boundsInRoot
        val initial = snapshot("home-top-initial")
        compose.onNode(isFocused()).performKeyInput {
            repeat(4) { pressKey(Key.DirectionDown) }
            repeat(4) { pressKey(Key.DirectionUp) }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, page.list.firstVisibleItemScrollOffset) }
        repeat(10) { cycle ->
            repeat(4) { key(Key.DirectionDown) }
            repeat(4) { key(Key.DirectionUp) }
            compose.runOnIdle { assertEquals(0, page.list.firstVisibleItemIndex); assertEquals(0, page.list.firstVisibleItemScrollOffset) }
            assertEquals(hero, compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot)
            assertEquals(first, compose.onNodeWithTag("section-row0").fetchSemanticsNode().boundsInRoot)
            assertTrue("Top pixels after cycle $cycle", initial.sameAs(snapshot("home-top-$cycle")))
        }
    }

    @Test fun informationKeepsBackFocusedAndReadingTextNeverGetsFocus() {
        var backs = 0
        val title = CatalogTitle("info", TitleKind.Movie, "Titel", overview = "En kort handling.", genres = listOf("Drama", "Mystik", "Komedi"))
        content { InformationContent(title) { backs++ } }
        compose.onNodeWithText("Tillbaka").assertIsFocused()
        repeat(3) { key(Key.DirectionDown) }
        compose.onNodeWithText("Tillbaka").assertIsFocused()
        compose.onAllNodes(isFocusable()).assertCountEquals(1)
        key(Key.DirectionLeft)
        compose.runOnIdle { assertEquals(1, backs) }
        key(Key.Enter)
        compose.runOnIdle { assertEquals(2, backs) }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, backs) }
    }

    @Test fun catalogHeroAndBackgroundRemainFixedThroughFiveRowsAndReturn() {
        val page = fixture(false)
        content { Box(Modifier.fillMaxSize().background(Color(0xFF203040)).padding(start = 80.dp, top = 26.dp, end = 36.dp, bottom = 26.dp)) {
            CatalogRows(page, page.items.first(), {}, {}, {})
        } }
        val hero = compose.onNodeWithTag("catalog-hero").fetchSemanticsNode().boundsInRoot
        repeat(3) {
            repeat(5) { row ->
                key(Key.DirectionDown)
                if (row == 2 || row == 4) {
                    val bitmap = snapshot("catalog-row-$row")
                    val bounds = compose.onNodeWithTag("section-list").fetchSemanticsNode().boundsInRoot
                    // A clipped old card bottom spans most of the width with near-black pixels.
                    for (y in bounds.top.toInt() until bounds.top.toInt() + 8) {
                        val xs = bounds.left.toInt() until bounds.right.toInt()
                        val black = xs.count { x -> val c = bitmap.getPixel(x, y); android.graphics.Color.red(c) < 12 && android.graphics.Color.green(c) < 12 && android.graphics.Color.blue(c) < 12 }
                        assertTrue("Black strip at row $row y=$y", black < xs.count() / 2)
                    }
                }
            }
            assertEquals(hero, compose.onNodeWithTag("catalog-hero").fetchSemanticsNode().boundsInRoot)
            repeat(5) { key(Key.DirectionUp) }
            compose.runOnIdle { assertEquals(0, page.list.firstVisibleItemIndex); assertEquals(0, page.list.firstVisibleItemScrollOffset) }
        }
    }

    @Test fun returningFromTitleRestoresSameRowCardAndOffsets() {
        val page = fixture(true)
        val details = mutableStateOf<CatalogTitle?>(null)
        content {
            if (details.value != null) InformationContent(details.value!!) { details.value = null; page.restoreFocus() }
            else HomeRows(page, page.rows.first().titles.first(), {}, { details.value = it })
        }
        repeat(3) { key(Key.DirectionDown) }
        repeat(8) { key(Key.DirectionRight) }
        val token = page.focusToken
        val vertical = page.list.firstVisibleItemIndex to page.list.firstVisibleItemScrollOffset
        val horizontal = page.rowStates.mapValues { it.value.firstVisibleItemIndex to it.value.firstVisibleItemScrollOffset }
        key(Key.Enter)
        compose.onNodeWithText("Tillbaka").assertIsFocused()
        key(Key.Enter)
        compose.runOnIdle {
            assertEquals(token, page.focusToken)
            assertEquals(vertical, page.list.firstVisibleItemIndex to page.list.firstVisibleItemScrollOffset)
            assertEquals(horizontal, page.rowStates.mapValues { it.value.firstVisibleItemIndex to it.value.firstVisibleItemScrollOffset })
        }
        compose.onNodeWithTag("target-$token").assertIsFocused()
    }
    @Test fun requestableTitleShowsMetadataWithoutOpeningAnInformationSubpageOrSending() {
        var requests = 0
        val focus = androidx.compose.ui.focus.FocusRequester()
        val title = CatalogTitle("request", TitleKind.Movie, "Begärbar film", overview = "Fullständig handling syns direkt.",
            year = 2026, genres = listOf("Drama", "Mystik", "Thriller"), requestAllowed = true, status = TitleStatus.Requestable)
        content {
            LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
            RequestableTitleContent(title, focus, {}, { requests++ }, {})
        }
        compose.onNodeWithText("Begär").assertIsFocused()
        assertTrue(compose.onAllNodesWithText("Fullständig handling syns direkt.", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Drama, Mystik, Thriller").assertExists()
        compose.onNodeWithText("Mer information").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test fun genreAndStudioActionsOpenTheirActualMetadataMembers() {
        val page = fixture(false)
        val member = page.items.first().copy(genres = listOf("Drama"), studios = listOf("Verklig metadata i isolerat test"))
        page.rows = buildCatalogSections(null, listOf(member), emptyList(), emptyList(), emptyList(), emptyList())
        var opened: CatalogCategory? = null
        content { CatalogRows(page, member, {}, { opened = it }, {}) }
        key(Key.DirectionDown)
        key(Key.Enter)
        compose.runOnIdle { assertEquals("Drama", opened?.name); assertEquals(listOf(member), opened?.titles) }
        key(Key.DirectionDown)
        key(Key.Enter)
        compose.runOnIdle { assertEquals(member.studios.single(), opened?.name); assertEquals(listOf(member), opened?.titles) }
    }

}
