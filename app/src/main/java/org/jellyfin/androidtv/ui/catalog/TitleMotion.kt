package org.jellyfin.androidtv.ui.catalog

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState

internal const val TITLE_FOCUS_MS = 200
internal const val TITLE_SCROLL_MS = 280
internal const val ROW_SCROLL_MS = 300
internal const val TITLE_FOCUS_SCALE = 1.05f

/** Animate measured destinations; the lazy list retains its own clamping at either edge. */
internal suspend fun LazyListState.animateTitleRow(index: Int, itemSizes: List<Int>) {
    while (true) {
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        val reference = item ?: info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.index - index) } ?: return
        val between = if (index >= reference.index)
            itemSizes.subList(reference.index, index).sum()
        else -itemSizes.subList(index, reference.index).sum()
        val distance = (reference.offset - info.viewportStartOffset - info.beforeContentPadding +
            between + (index - reference.index) * info.mainAxisItemSpacing).toFloat()
        var consumed = 0f
        scroll {
            var previous = 0f
            animate(0f, distance, animationSpec = tween(ROW_SCROLL_MS, easing = FastOutSlowInEasing)) { value, _ ->
                consumed += scrollBy(value - previous)
                previous = value
            }
        }
        // Once measured, clamping may legitimately prevent the last row aligning at the top.
        if (item != null || kotlin.math.abs(consumed) < .5f) return
    }
}

/** Retarget from the currently rendered offset, never queue competing row animations. */
internal suspend fun LazyListState.animateTitleCard(index: Int, pitch: Float, cardWidth: Float, safe: Float) {
    val reference = layoutInfo.visibleItemsInfo.firstOrNull() ?: return
    val left = reference.offset + (index - reference.index) * pitch
    val distance = left - CatalogNavigationPolicy.cardAnchor(layoutInfo.viewportEndOffset.toFloat(), cardWidth, safe)
    if (distance == 0f) return
    scroll {
        var previous = 0f
        animate(0f, distance, animationSpec = tween(TITLE_SCROLL_MS, easing = FastOutSlowInEasing)) { value, _ ->
            scrollBy(value - previous)
            previous = value
        }
    }
}

internal suspend fun androidx.compose.foundation.lazy.grid.LazyGridState.animateTitleGrid(index: Int, columns: Int, safe: Float) {
    val info = layoutInfo
    val reference = info.visibleItemsInfo.firstOrNull { it.index == index }
        ?: info.visibleItemsInfo.firstOrNull() ?: return
    val top = reference.offset.y + (index / columns - reference.index / columns) *
        (reference.size.height + info.mainAxisItemSpacing)
    val bottom = top + reference.size.height
    val distance = when {
        top < safe -> top - safe
        bottom > info.viewportEndOffset - safe -> bottom - info.viewportEndOffset + safe
        else -> 0f
    }
    if (distance == 0f) return
    scroll {
        var previous = 0f
        animate(0f, distance, animationSpec = tween(TITLE_SCROLL_MS, easing = FastOutSlowInEasing)) { value, _ ->
            scrollBy(value - previous)
            previous = value
        }
    }
}

/** Save the intended horizontal endpoint when a screen or menu interrupts its animation. */
internal fun LazyListState.finishTitleCard(index: Int, pitch: Float, cardWidth: Float, safe: Float) {
    val reference = layoutInfo.visibleItemsInfo.firstOrNull() ?: return
    val left = reference.offset + (index - reference.index) * pitch
    val distance = left - CatalogNavigationPolicy.cardAnchor(layoutInfo.viewportEndOffset.toFloat(), cardWidth, safe)
    val position = (firstVisibleItemIndex * pitch + firstVisibleItemScrollOffset + distance).coerceAtLeast(0f)
    val item = (position / pitch).toInt()
    requestScrollToItem(item, kotlin.math.round(position - item * pitch).toInt())
}

internal fun androidx.compose.foundation.lazy.grid.LazyGridState.finishTitleGrid(index: Int, columns: Int, safe: Float) {
    val info = layoutInfo
    val reference = info.visibleItemsInfo.firstOrNull() ?: return
    val pitch = reference.size.height + info.mainAxisItemSpacing
    val top = reference.offset.y + (index / columns - reference.index / columns) * pitch
    val distance = when {
        top < safe -> top - safe
        top + reference.size.height > info.viewportEndOffset - safe ->
            top + reference.size.height - info.viewportEndOffset + safe
        else -> 0f
    }
    val position = (firstVisibleItemIndex / columns * pitch + firstVisibleItemScrollOffset + distance).coerceAtLeast(0f)
    val row = (position / pitch).toInt()
    requestScrollToItem(row * columns, kotlin.math.round(position - row * pitch).toInt())
}
