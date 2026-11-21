package org.jellyfin.androidtv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text

/** Selecting seasons never submits; only the final explicit confirmation calls onSubmit. */
@Composable
internal fun SeerrRequestDialog(title: CatalogTitle, sending: Boolean, error: String?, onDismiss: () -> Unit, onSubmit: (Set<Int>) -> Unit) {
    val initial = remember { FocusRequester() }
    var seasons by remember(title.key) { mutableStateOf(emptySet<Int>()) }
    Dialog(onDismissRequest = { if (!sending) onDismiss() }) {
        androidx.activity.compose.BackHandler(enabled = !sending, onBack = onDismiss)
        LaunchedEffect(Unit) { withFrameNanos { }; initial.requestFocus() }
        Column(Modifier.onPreviewKeyEvent { event ->
            if (event.key == Key.Back) {
                if (event.type == KeyEventType.KeyUp && !sending) onDismiss()
                true
            } else false
        }.width(480.dp).heightIn(max = 440.dp).background(Ink).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.discover_request_title, title.name), color = Color.White, fontSize = 24.sp)
            Text(stringResource(if (title.kind == TitleKind.Series) R.string.discover_select_seasons else R.string.discover_confirm_movie), color = Muted, fontSize = 14.sp)
            if (title.kind == TitleKind.Series) LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(title.requestSeasons.ifEmpty { title.seasons }.filter { it.number > 0 }, key = { it.number }) { season ->
                    TvButton((if (season.number in seasons) "✓  " else "") + season.name +
                        if (!season.requestable) " · " + stringResource(when(season.status) {
                            TitleStatus.Available -> R.string.discover_available
                            TitleStatus.Processing -> R.string.discover_processing
                            TitleStatus.Partial -> R.string.discover_partial
                            else -> R.string.discover_pending
                        }) else "",
                        Modifier.fillMaxWidth().testTag("request-season-${season.number}"),
                        selected = season.number in seasons, onClick = {
                            if (!sending && season.requestable) seasons = if (season.number in seasons) seasons - season.number else seasons + season.number
                        })
                }
            }
            error?.let { Text(it, color = Color(0xFF4EDCFF), fontSize = 13.sp, modifier = Modifier.testTag("request-error")) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(stringResource(R.string.discover_cancel), Modifier.testTag("request-cancel"), requester = initial, onClick = { if (!sending) onDismiss() })
                if (title.kind == TitleKind.Movie || seasons.isNotEmpty()) TvButton(
                    stringResource(if (sending) R.string.discover_sending else R.string.discover_confirm), Modifier.testTag("request-confirm"),
                    onClick = { if (!sending) onSubmit(seasons) })
            }
        }
    }
}
