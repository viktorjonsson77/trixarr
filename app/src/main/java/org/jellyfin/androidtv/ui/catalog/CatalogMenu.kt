package org.jellyfin.androidtv.ui.catalog

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text

internal val menuSections = listOf(
    CatalogScreen.Search, CatalogScreen.Home, CatalogScreen.Discover,
    CatalogScreen.Movies, CatalogScreen.Series,
)
internal const val MENU_DURATION_MS = 240

/** Always the same overlay and icon positions; animation never participates in content measurement. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun CatalogMenu(
    expanded: Boolean,
    active: CatalogScreen?,
    onClose: () -> Unit,
    onClosed: () -> Unit,
    onSelect: (CatalogScreen?) -> Unit,
    focusEpoch: Int = 0,
    focusIndex: Int? = null,
    closeEpoch: Int = 0,
) {
    val progress = remember { Animatable(0f) }
    val requesters = remember { (menuSections + listOf(null)).associateWith { FocusRequester() } }
    var lastFocused by remember { mutableStateOf(active) }
    var previousEpoch by remember { mutableIntStateOf(focusEpoch) }
    val closed by rememberUpdatedState(onClosed)
    var wasOpen by remember { mutableStateOf(false) }
    var handledClose by remember { mutableIntStateOf(0) }
    LaunchedEffect(expanded, focusEpoch, focusIndex) {
        if (expanded) {
            withFrameNanos { }
            val destination = if (focusIndex != null) (menuSections + listOf(null))[focusIndex]
                else if (previousEpoch == focusEpoch) active else lastFocused
            requesters.getValue(destination).requestFocus()
            previousEpoch = focusEpoch
        }
    }
    LaunchedEffect(expanded, closeEpoch) {
        if (expanded) wasOpen = true
        progress.animateTo(if (expanded) 1f else 0f, tween(if (expanded) MENU_DURATION_MS else 120, easing = FastOutSlowInEasing))
        if (!expanded && (wasOpen || handledClose != closeEpoch)) {
            wasOpen = false
            handledClose = closeEpoch
            closed()
        }
    }
    Box(Modifier.fillMaxSize().testTag("navigation-overlay").drawBehind {
        val p = progress.value
        val edge = 72.dp.toPx() + (size.width * .40f - 72.dp.toPx()) * p
        drawRect(Brush.horizontalGradient(
            0f to Color(0xFF0B111B).copy(alpha = .92f * p),
            .48f to Color(0xFF172333).copy(alpha = .83f * p),
            .78f to Color(0xFF101925).copy(alpha = .45f * p),
            1f to Color.Transparent,
            endX = edge,
        ))
    }) {
        Column(
            Modifier.padding(start = 18.dp, top = 64.dp).width(220.dp)
                .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()
                .onPreviewKeyEvent {
                    if (it.key == Key.DirectionRight) {
                        if (it.type == KeyEventType.KeyDown) onClose()
                        true
                    } else false
                },
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            (menuSections + listOf(null)).forEach { section ->
                var focused by remember { mutableStateOf(false) }
                val selected = section == active
                val label = section?.label ?: "Inställningar"
                val tint = if (focused && progress.value > 0f) Ink else if (selected) Color.White else Muted
                Row(
                    Modifier.height(38.dp).width(202.dp)
                        .focusRequester(requesters.getValue(section))
                        .focusProperties { canFocus = expanded || progress.value > 0f }
                        .onFocusChanged {
                            focused = it.isFocused
                            if (focused) lastFocused = section
                        }
                        .drawBehind {
                            if (focused) drawRoundRect(Accent.copy(alpha = .93f * progress.value), cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()))
                        }
                        .then(if (expanded || progress.value > 0f) Modifier.clickable { if (expanded) onSelect(section) } else Modifier)
                        .testTag("menu-${section?.name ?: "Settings"}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(36.dp).height(32.dp)
                        .background(if (selected && !expanded) Color.White.copy(.12f) else Color.Transparent, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        Icon(ImageVector.vectorResource(when (section) {
                            CatalogScreen.Search -> R.drawable.ic_search
                            CatalogScreen.Home -> R.drawable.ic_catalog_home
                            CatalogScreen.Discover -> R.drawable.ic_catalog_discover
                            CatalogScreen.Movies -> R.drawable.ic_movie
                            CatalogScreen.Series -> R.drawable.ic_tv
                            null -> R.drawable.ic_settings
                            else -> R.drawable.ic_catalog_home
                        }), null, Modifier.size(20.dp), tint = tint)
                    }
                    if (expanded || progress.value > 0f) Text(label, color = tint, fontSize = 16.sp, maxLines = 1,
                        modifier = Modifier.padding(start = 12.dp).graphicsLayer {
                            alpha = progress.value
                            translationX = -12.dp.toPx() * (1f - progress.value)
                        })
                }
            }
        }
    }
}

@Composable
internal fun CatalogExitConfirmation(onStay: () -> Unit, onExit: () -> Unit) {
    val stay = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onStay) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            stay.requestFocus()
        }
        Column(Modifier.width(320.dp).background(Ink, RoundedCornerShape(12.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Vill du avsluta?", color = Color.White, fontSize = 22.sp)
            TvButton("Stanna kvar", requester = stay, primary = true, onClick = onStay)
            TvButton("Lämna", onClick = onExit)
        }
    }
}
