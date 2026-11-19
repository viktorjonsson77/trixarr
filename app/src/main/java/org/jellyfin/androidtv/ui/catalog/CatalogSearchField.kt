package org.jellyfin.androidtv.ui.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text

/** The system owns keyboard D-pad traversal; catalogue focus is restored after IME dismissal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogSearchField(query: String, requester: FocusRequester?, onFocus: () -> Unit,
    onQuery: (String) -> Unit, onMenu: () -> Unit, onResults: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var sawKeyboard by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    val enabled = LocalContentFocusEnabled.current
    LaunchedEffect(imeVisible) {
        if (imeVisible) sawKeyboard = true
        else if (sawKeyboard) { editing = false; sawKeyboard = false }
    }
    BackHandler(editing && imeVisible) { editing = false; keyboard?.hide() }
    LaunchedEffect(editing) { if (editing) keyboard?.show() }
    BasicTextField(query, onValueChange = onQuery, singleLine = true, readOnly = !editing,
        textStyle = TextStyle(color = if (focused) Ink else Color.White, fontSize = 18.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { editing = false; keyboard?.hide(); onResults() }),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("catalog-search-field")
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .focusProperties { canFocus = enabled }
            .onFocusChanged { focused = it.isFocused; if (focused) onFocus() }
            .onPreInterceptKeyBeforeSoftKeyboard { event ->
                if (editing && event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp) {
                        editing = false
                        sawKeyboard = false
                        keyboard?.hide()
                    }
                    true
                } else false
            }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        if (event.type == KeyEventType.KeyUp) {
                            editing = true
                            keyboard?.show()
                        }
                        true
                    }
                    Key.DirectionLeft -> if (!editing || !imeVisible) {
                        if (event.type == KeyEventType.KeyDown) { editing = false; onMenu() }
                        true
                    } else false
                    Key.DirectionDown -> if (!editing || !imeVisible) {
                        if (event.type == KeyEventType.KeyDown) { editing = false; onResults() }
                        true
                    } else false
                    else -> false
                }
            }.background(if (focused) Accent else Color.White.copy(alpha = .12f)).padding(12.dp),
        decorationBox = { inner ->
            if (query.isEmpty()) Text("Sök filmer och serier", color = if (focused) Ink else Muted, fontSize = 18.sp)
            inner()
        })
}
