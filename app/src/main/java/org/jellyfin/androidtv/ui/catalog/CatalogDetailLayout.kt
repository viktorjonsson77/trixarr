package org.jellyfin.androidtv.ui.catalog

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.Text

/** One title page: actions above focus-selected tabs, reading text never owns focus. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun CatalogDetailLayout(
    page: CatalogPage,
    title: CatalogTitle,
    focus: (String) -> Unit,
    actions: @Composable (Modifier) -> Unit,
    related: @Composable () -> Unit,
) {
    val enabled = LocalContentFocusEnabled.current
    val tabs = if (page.items.isNotEmpty()) listOf("content", "info") else listOf("info")
    val requesters = remember(page) { mapOf("content" to FocusRequester(), "info" to FocusRequester()) }
    val reading = rememberScrollState()
    val scope = rememberCoroutineScope()
    val selected = page.detailTab.takeIf { it in tabs } ?: tabs.first()
    fun top() {
        page.detailsExpanded = true
        page.focusToken = "primary"
        page.restoreFocus()
    }
    fun tab(name: String = selected) {
        page.detailsExpanded = false
        page.detailTab = name
        page.focusToken = "detail-tab:$name"
        requesters.getValue(name).requestFocus()
    }
    LaunchedEffect(page.restore, page.focusToken, enabled) {
        if (enabled && page.focusToken.startsWith("detail-tab:")) {
            withFrameNanos { }
            requesters.getValue(selected).requestFocus()
            page.restoring = false
        }
    }
    val header by animateDpAsState(if (page.detailsExpanded) 300.dp else 64.dp,
        tween(ROW_SCROLL_MS), label = "detail-header")
    Column(Modifier.fillMaxSize().padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Box(Modifier.height(header).fillMaxWidth().clipToBounds().testTag("detail-header")) {
            if (page.detailsExpanded) {
                Column(Modifier.fillMaxSize().padding(bottom = 60.dp).clipToBounds(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    TitleHero(title)
                    Text(statusLabel(title.status), color = if (title.status == TitleStatus.Available) Color(0xFF65CD79) else Muted, fontSize = 12.sp)
                }
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().focusProperties {
                    exit = { direction ->
                        if (direction == FocusDirection.Left || direction == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default
                    }
                }.focusGroup()) {
                actions(Modifier.onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionDown) {
                        if (event.type == KeyEventType.KeyDown) tab()
                        true
                    } else false
                })
                }
            } else Text(title.name, color = Color.White, fontSize = 29.sp, maxLines = 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tabs.forEach { name ->
                TvButton(if (name == "info") "Information" else if (title.kind == TitleKind.Series) "Avsnitt" else "Liknande titlar",
                    Modifier.testTag("detail-tab-$name").onPreviewKeyEvent { event ->
                        val index = tabs.indexOf(name)
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionRight -> {
                                if (event.type == KeyEventType.KeyDown) tab(tabs[(index + if (event.key == Key.DirectionRight) 1 else -1).coerceIn(tabs.indices)])
                                true
                            }
                            Key.DirectionUp -> {
                                if (event.type == KeyEventType.KeyDown) {
                                    if (name == "info" && reading.value > 0) scope.launch { reading.scrollTo((reading.value - 120).coerceAtLeast(0)) }
                                    else top()
                                }
                                true
                            }
                            Key.DirectionDown -> if (name == "info") {
                                if (event.type == KeyEventType.KeyDown && reading.canScrollForward) scope.launch { reading.scrollTo((reading.value + 120).coerceAtMost(reading.maxValue)) }
                                true
                            } else false
                            else -> false
                        }
                    }, requester = requesters.getValue(name), selected = selected == name,
                    onFocus = {
                        // Lazy actions attach after the eager tabs. Incidental initial focus
                        // must not collapse and remove the primary action before restoration.
                        if (enabled && !page.restoring) {
                            page.detailsExpanded = false
                            page.detailTab = name
                            focus("detail-tab:$name")
                        }
                    },
                    onClick = { tab(name) })
            }
        }
        if (selected == "info") Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(reading).testTag("detail-reading"),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title.overview.isNotBlank()) Text(title.overview, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
            metadataLines(title).forEach { Text(it, color = Muted, fontSize = 14.sp, lineHeight = 20.sp) }
        } else Column(Modifier.weight(1f).onPreviewKeyEvent { event ->
            if (event.key == Key.DirectionUp) {
                if (event.type == KeyEventType.KeyDown) tab("content")
                true
            } else false
        }) { related() }
    }
}
