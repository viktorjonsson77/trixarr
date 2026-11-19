package org.jellyfin.androidtv.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.Text

internal fun homeFeaturedTitles(page: CatalogPage): List<CatalogTitle> =
    page.rows.flatMap { it.titles }.distinctBy { it.key }.take(CatalogNavigationPolicy.featuredTitleCount)

/** Home's featured title is independent of each row's remembered focus. */
internal fun homeHero(page: CatalogPage): CatalogTitle? {
    val titles = page.rows.flatMap { it.titles }
    return if (page.focusToken == "initial" || page.focusToken.startsWith("hero"))
        homeFeaturedTitles(page).let { it.getOrNull(page.heroIndex.coerceIn(0, (it.size - 1).coerceAtLeast(0))) }
    else titles.firstOrNull { it.key == page.selected } ?: titles.firstOrNull()
}

@Composable
internal fun HomeRows(page: CatalogPage, hero: CatalogTitle?, onMenu: () -> Unit,
    onOpen: (CatalogTitle) -> Unit) =
    SectionRows(page, hero, true, onMenu, onOpen, {})

@Composable
internal fun CatalogRows(page: CatalogPage, hero: CatalogTitle?, onOpen: (CatalogTitle) -> Unit,
    onCategory: (CatalogCategory) -> Unit, onMenu: () -> Unit) =
    SectionRows(page, hero, false, onMenu, onOpen, onCategory)

/** One vertical list, transparent rows and explicit row-to-row D-pad destinations. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionRows(page: CatalogPage, hero: CatalogTitle?, home: Boolean, onMenu: () -> Unit,
    onOpen: (CatalogTitle) -> Unit, onCategory: (CatalogCategory) -> Unit) {
    val rows = visibleRows(page.rows)
    val scope = rememberCoroutineScope()
    val requesters = remember(page) { mutableMapOf<String, FocusRequester>() }
    val motionFocus = remember { FocusRequester() }
    val columns = page.rowColumns
    var activationToken by remember(page) { mutableStateOf<String?>(null) }
    var activationHero by remember(page) { mutableStateOf<CatalogTitle?>(null) }
    var heroInputEpoch by remember(page) { mutableIntStateOf(0) }
    var verticalMotion by remember(page) { mutableStateOf(false) }
    var moving by remember(page) { mutableStateOf<Job?>(null) }
    val enabled = LocalContentFocusEnabled.current
    val density = LocalDensity.current
    val pitch = with(density) { 176.dp.toPx() }
    val cardWidth = with(density) { 168.dp.toPx() }
    val safe = with(density) { 6.dp.toPx() }
    fun token(row: CatalogRow, index: Int) = row.key + ":" + (row.titles.getOrNull(index)?.key ?: "category:$index")
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    fun focused(key: String, title: CatalogTitle? = null) {
        if (enabled && !page.restoring) {
            page.focusToken = key
            if (title != null) page.selected = title.key
        }
    }
    fun move(rowIndex: Int) {
        if (rowIndex !in rows.indices && !(home && rowIndex == -1)) return
        val row = rows.getOrNull(rowIndex)
        val index = row?.let { CatalogNavigationPolicy.rememberedColumn(columns[it.key], maxOf(it.titles.size, it.categories.size)) } ?: 0
        val key = if (row == null) "hero" else token(row, index)
        page.focusToken = key
        row?.titles?.getOrNull(index)?.let { page.selected = it.key }
        page.restoring = true
        motionFocus.requestFocus()
        verticalMotion = true
        moving?.cancel()
        fun bodyHeight(index: Int) = with(density) {
            (if (rows[index].titles.isEmpty()) 42.dp else if (home && index == 0) 98.dp else 96.dp).roundToPx() +
                2 * 4.dp.roundToPx()
        }
        val measured = page.list.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index in rows.indices
        }
        val headerHeight = measured?.let { it.size - bodyHeight(it.index) }
            ?: with(density) { 27.dp.roundToPx() }
        val itemSizes = buildList {
            rows.indices.forEach { add(headerHeight + bodyHeight(it)) }
        }
        moving = scope.launch {
            // Reset the list completely when returning to the expanded Home header.
            page.list.animateTitleRow(rowIndex.coerceAtLeast(0), itemSizes)
            if (row != null) page.rowStates.getOrPut(row.key) { LazyListState() }.animateTitleCard(index, pitch, cardWidth, safe)
            withFrameNanos { }
            requester(key).requestFocus()
            if (row != null) row.titles.getOrNull(index)?.let { page.selected = it.key }
            page.restoring = false
        }
    }
    fun moveCard(rowIndex: Int, index: Int) {
        val row = rows[rowIndex]
        val key = token(row, index)
        columns[row.key] = index
        page.focusToken = key
        row.titles.getOrNull(index)?.let { page.selected = it.key }
        page.restoring = true
        verticalMotion = false
        moving?.cancel()
        val state = page.rowStates.getOrPut(row.key) { LazyListState() }
        if (state.layoutInfo.visibleItemsInfo.any { it.index == index }) requesters[key]?.requestFocus()
        moving = scope.launch {
            state.animateTitleCard(index, pitch, cardWidth, safe)
            withFrameNanos { }
            requester(key).requestFocus()
            page.restoring = false
        }
    }
    DisposableEffect(page, enabled) {
        onDispose {
            moving?.cancel()
            activationToken = null
            if (page.restoring) {
                val destination = if (home && page.focusToken.startsWith("hero")) 0
                    else rows.indexOfFirst { page.focusToken.startsWith(it.key + ":") }
                        .takeIf { it >= 0 }
                if (verticalMotion) destination?.let { page.list.requestScrollToItem(it) }
                val row = rows.firstOrNull { page.focusToken.startsWith(it.key + ":") }
                if (row != null) page.rowStates[row.key]?.finishTitleCard(columns[row.key] ?: 0, pitch, cardWidth, safe)
            }
        }
    }
    fun vertical(row: Int, event: KeyEvent): Boolean {
        if (event.key != Key.DirectionUp && event.key != Key.DirectionDown) return false
        if (event.type == KeyEventType.KeyDown) {
            val currentRow = if (page.focusToken.startsWith("hero")) -1 else rows.indexOfFirst { page.focusToken.startsWith(it.key + ":") }.takeIf { it >= 0 } ?: row
            move(currentRow + if (event.key == Key.DirectionDown) 1 else -1)
        }
        return true
    }
    LaunchedEffect(page, page.loading, page.restore, enabled) {
        if (enabled && !page.loading && rows.isNotEmpty()) {
            val key = page.focusToken.takeUnless { it == "initial" || it.startsWith("card:") || it.startsWith("filter:") }
                ?: if (home) "hero" else token(rows.first(), 0)
            page.focusToken = key
            withFrameNanos { }
            requesters[key]?.requestFocus()
            page.restoring = false
        }
    }
    Column(Modifier.fillMaxSize().focusRequester(motionFocus).onPreviewKeyEvent { event ->
        if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
            if (event.type == KeyEventType.KeyDown && activationToken == null) {
                activationToken = page.focusToken
                activationHero = homeHero(page)
            }
            if (event.type == KeyEventType.KeyUp) {
                val token = activationToken ?: page.focusToken
                activationToken = null
                if (token.startsWith("hero")) activationHero?.let(onOpen)
                else {
                    val row = rows.firstOrNull { token.startsWith(it.key + ":") }
                    val title = row?.titles?.firstOrNull { token == row.key + ":" + it.key }
                    if (title != null) onOpen(title)
                    else row?.categories?.getOrNull(columns[row.key] ?: 0)?.let(onCategory)
                }
            }
            true
        } else if (vertical(0, event)) true
        else if (home && page.focusToken.startsWith("hero") && event.key in setOf(Key.DirectionLeft, Key.DirectionRight)) {
            if (event.type == KeyEventType.KeyDown) {
                heroInputEpoch++
                if (event.key == Key.DirectionLeft && page.heroIndex == 0) onMenu()
                else page.heroIndex = (page.heroIndex + if (event.key == Key.DirectionRight) 1 else -1)
                    .coerceIn(0, (homeFeaturedTitles(page).size - 1).coerceAtLeast(0))
            }
            true
        }
        else if (!page.focusToken.startsWith("hero") &&
            (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)) {
            if (event.type == KeyEventType.KeyDown) {
                val rowIndex = rows.indexOfFirst { page.focusToken.startsWith(it.key + ":") }
                val row = rows.getOrNull(rowIndex)
                if (row != null) {
                    val column = CatalogNavigationPolicy.rememberedColumn(columns[row.key], maxOf(row.titles.size, row.categories.size))
                    val next = column + if (event.key == Key.DirectionRight) 1 else -1
                    if (next in 0 until maxOf(row.titles.size, row.categories.size)) {
                        if (page.restoring && verticalMotion) {
                            columns[row.key] = next
                            move(rowIndex)
                        } else moveCard(rowIndex, next)
                    } else if (next < 0) onMenu()
                }
            }
            true
        } else false
    }.focusable(enabled)) {
        val expanded = home && (page.focusToken.startsWith("hero") || page.focusToken == "initial")
        val featured = homeFeaturedTitles(page)
        LaunchedEffect(home, expanded, enabled, page.heroIndex, heroInputEpoch, activationToken, featured.size) {
            if (home && expanded && enabled && activationToken == null && featured.size > 1) {
                delay(CatalogNavigationPolicy.heroRotationMs)
                page.heroIndex = (page.heroIndex + 1) % featured.size
            }
        }
        val headerHeight by animateDpAsState(
            (if (expanded) CatalogNavigationPolicy.expandedHeroHeight else CatalogNavigationPolicy.rowHeroHeight).dp,
            tween(ROW_SCROLL_MS, easing = FastOutSlowInEasing), label = "row-header")
        val heroTop by animateDpAsState(
            (if (expanded) 240 else CatalogNavigationPolicy.rowHeroTop).dp,
            tween(ROW_SCROLL_MS, easing = FastOutSlowInEasing), label = "hero-top")
        Box(Modifier.height(headerHeight).fillMaxWidth().testTag(if (home) "home-hero" else "catalog-hero")) {
            Column(Modifier.padding(top = heroTop)) {
                TitleHero(hero, compact = true)
                if (home) Box(Modifier.height(42.dp).graphicsLayer { alpha = if (expanded) 1f else 0f }) {
                    CompositionLocalProvider(LocalContentFocusEnabled provides (enabled && expanded)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvButton("Mer information",
                                Modifier.onPreviewKeyEvent { e ->
                                    if (vertical(-1, e)) true
                                    else if (e.key == Key.DirectionLeft) { if (e.type == KeyEventType.KeyDown) onMenu(); true } else false
                                }, requester = requester("hero"), primary = true,
                                onFocus = { focused("hero") }, onClick = { homeHero(page)?.let(onOpen) })
                        }
                    }
                }
            }
            if (expanded && featured.size > 1) Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                featured.indices.forEach { index ->
                    Box(Modifier.size(4.dp).background(if (index == page.heroIndex) Accent else Color.White.copy(alpha = .3f), CircleShape))
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
        val lastBody = if (rows.lastOrNull()?.titles?.isEmpty() == true) 42.dp else 104.dp
        val bottom = (maxHeight - lastBody - 27.dp).coerceAtLeast(25.dp)
        LazyColumn(state = page.list, modifier = Modifier.fillMaxSize().testTag("section-list"),
            verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = bottom)) {
            itemsIndexed(rows, key = { _, row -> row.key }) { rowIndex, row ->
                Column(Modifier.testTag("section-${row.key}"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(row.name, color = Color.White, fontSize = 17.sp)
                    val rowState = page.rowStates.getOrPut(row.key) { LazyListState() }
                    LaunchedEffect(page.focusToken, enabled, page.restoring) {
                        if (enabled && !page.restoring && page.focusToken.startsWith(row.key + ":")) {
                            withFrameNanos { }
                            rowState.animateTitleCard(columns[row.key] ?: 0, pitch, cardWidth, safe)
                        }
                    }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides remember {
                        object : BringIntoViewSpec {
                            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
                        }
                    }) {
                    LazyRow(state = rowState,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(maxOf(row.titles.size, row.categories.size), key = { token(row, it) }) { index ->
                            val key = token(row, index)
                            val modifier = Modifier.onPreviewKeyEvent { e ->
                                if (vertical(rowIndex, e)) true
                                else if (index == 0 && e.key == Key.DirectionLeft) { if (e.type == KeyEventType.KeyDown) onMenu(); true }
                                else false
                            }.testTag("target-$key")
                            val title = row.titles.getOrNull(index)
                            if (title != null) TitleCard(title, modifier.width(168.dp).height(if (home && rowIndex == 0) 98.dp else 96.dp),
                                landscape = true, requester = requester(key), logicalFocus = enabled && page.focusToken == key,
                                onFocus = { if (enabled && !page.restoring) { columns[row.key] = index; focused(key, title) } }, onClick = { onOpen(title) })
                            else row.categories.getOrNull(index)?.let { category ->
                                TvButton(category.name, modifier.width(168.dp), requester = requester(key),
                                    onFocus = { if (enabled && !page.restoring) { columns[row.key] = index; focused(key) } }, onClick = { onCategory(category) })
                            }
                        }
                    }
                    }
                }
            }
        }
        }
        if (rows.isEmpty() && !page.loading) TvButton("Inga titlar · Öppna menyn", onClick = onMenu)
    }
}
