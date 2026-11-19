package org.jellyfin.androidtv.ui.catalog

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalView

/** Keeps transition input ordered until the overlay has relinquished focus. */
@Composable
internal fun CatalogInputBoundary(
    blocked: () -> Boolean,
    ready: () -> Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val pending = remember { mutableStateListOf<AndroidKeyEvent?>() }
    val view = LocalView.current
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var replaying by remember { mutableStateOf(false) }
    var draining by remember { mutableStateOf(false) }
    val back by rememberUpdatedState(onBack)
    fun handleBack() {
        if (blocked() || pending.isNotEmpty()) {
            if (blocked()) draining = false
            pending.add(null)
        } else back()
    }
    BackHandler { handleBack() }
    LaunchedEffect(blocked(), ready(), pending.size) {
        if (blocked()) draining = false
        if (!blocked() && pending.isNotEmpty() && (draining || ready() || pending.first() == null)) {
            // Restoration runs on the composition frame; dispatch only once it owns focus.
            withFrameNanos { }
            if (blocked()) return@LaunchedEffect
            val event = pending.removeAt(0)
            draining = true
            replaying = true
            try {
                if (event == null) back() else view.dispatchKeyEvent(event)
            } finally {
                replaying = false
                // A screen-changing action needs a new focus handoff. Ordinary row moves
                // must remain retargetable while their visual animation is running.
                val activation = event?.action == AndroidKeyEvent.ACTION_UP && event.keyCode in
                    setOf(AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER)
                if (pending.isEmpty() || event == null || activation) draining = false
            }
        }
    }
    Box(Modifier.onPreviewKeyEvent { event ->
        if (event.key == Key.Back) {
            if (event.type == KeyEventType.KeyUp) {
                if (dispatcher != null) dispatcher.onBackPressed() else handleBack()
            }
            return@onPreviewKeyEvent true
        }
        val navigation = event.key in setOf(Key.DirectionLeft, Key.DirectionRight,
            Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
        if (!replaying && navigation && (blocked() || pending.isNotEmpty())) {
            if (blocked()) draining = false
            pending.add(AndroidKeyEvent(event.nativeKeyEvent))
            true
        } else false
    }) { content() }
}
