package org.jellyfin.androidtv.ui.catalog

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.Text

/** Reconcile responses after composition, including image and metadata completing in one frame. */
@Composable
internal fun SyncHeroMetadata(page: CatalogPage, details: CatalogTitle?) {
    LaunchedEffect(details, page.presentedHero?.key) {
        if (details != null && details.key == page.presentedHero?.key) page.presentedHero = details
    }
}

internal fun metadataLines(title: CatalogTitle): List<String> = listOfNotNull(
    when (title.kind) { TitleKind.Movie -> "Film"; TitleKind.Series -> "Serie"; TitleKind.Episode -> "Avsnitt" },
    (title.releaseDate ?: title.year?.toString())?.let { "Premiär: $it" },
    title.runtimeTicks?.takeIf { it > 0 }?.div(600_000_000L)?.let { "Längd: $it min" },
    title.certification?.takeIf { it.isNotBlank() }?.let { "Åldersgräns: $it" },
    title.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
    title.language?.takeIf { it.isNotBlank() }?.let { "Originalspråk: $it" },
    title.seasons.takeIf { it.isNotEmpty() }?.let { "${it.size} säsonger" },
    title.studios.takeIf { it.isNotEmpty() }?.joinToString(", "),
    title.people.takeIf { it.isNotEmpty() }?.joinToString(", ") { p -> p.name + (p.role ?: p.job)?.let { " ($it)" }.orEmpty() },
)

/** The action retains focus while D-pad scrolls reading content only when it overflows. */
@Composable
internal fun InformationContent(title: CatalogTitle, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; back.requestFocus() }
    ReadingSurface(title, Modifier.fillMaxSize(), onLeft = onBack) { keyModifier ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Tillbaka", keyModifier.testTag("information-back"), requester = back, primary = true, onClick = onBack)
            Text(title.name, color = Color.White, fontSize = 29.sp, maxLines = 2)
        }
    }
}

@Composable
internal fun ReadingMetadata(title: CatalogTitle, modifier: Modifier = Modifier) {
    val read = remember { FocusRequester() }
    ReadingSurface(title, modifier) { keys ->
        TvButton("Läs beskrivning", keys, requester = read, onClick = { read.requestFocus() })
    }
}

@Composable
internal fun ReadingSurface(title: CatalogTitle, modifier: Modifier, onLeft: (() -> Unit)? = null,
    action: @Composable (Modifier) -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val keys = Modifier.onPreviewKeyEvent { e ->
        when {
            e.key == Key.DirectionLeft && onLeft != null -> { if (e.type == KeyEventType.KeyDown) onLeft(); true }
            e.key == Key.DirectionUp && scroll.value == 0 && onLeft == null -> false
            e.key == Key.DirectionDown || e.key == Key.DirectionUp -> {
                if (e.type == KeyEventType.KeyDown && scroll.maxValue > 0) scope.launch {
                    scroll.scrollTo((scroll.value + if (e.key == Key.DirectionDown) 120 else -120).coerceIn(0, scroll.maxValue))
                }
                true
            }
            else -> false
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        action(keys)
        Column(Modifier.weight(1f).fillMaxWidth(.9f).verticalScroll(scroll).testTag("reading-content"),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title.overview.isNotBlank()) Text(title.overview, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
            metadataLines(title).forEach { Text(it, color = Muted, fontSize = 14.sp, lineHeight = 20.sp) }
        }
        Text(if (scroll.canScrollForward) "↓ Mer text" else "", color = Muted, fontSize = 12.sp, modifier = Modifier.height(18.dp))
    }
}

@Composable
internal fun RequestableTitleContent(title: CatalogTitle, requester: FocusRequester?, onFocus: () -> Unit,
    onRequest: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TitleHero(title)
        Text(statusLabel(title.status), color = Muted, fontSize = 12.sp)
        TvButton(if (title.requestAllowed) "Begär" else "Tillbaka", requester = requester, primary = true,
            onFocus = onFocus, onClick = if (title.requestAllowed) onRequest else onBack)
        ReadingMetadata(title, Modifier.weight(1f))
    }
}
