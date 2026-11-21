package org.jellyfin.androidtv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import org.jellyfin.androidtv.seerr.SeerrClientProvider
import org.jellyfin.androidtv.status.*
import org.jellyfin.androidtv.ui.base.Text

/** Separate polling lifetime. Status updates never reload catalog rows or their focus/scroll state. */
@Composable
internal fun RequestDownloadHost(clients: SeerrClientProvider, profile: String, token: () -> String?, active: Boolean,
    requestIds: List<Int>, onOpen: (CatalogTitle) -> Unit, onPlay: (CatalogTitle) -> Unit,
    content: @Composable (Map<Int, RequestDownload>, (CatalogTitle) -> Unit, @Composable () -> Unit) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("status_bridge_configuration", 0) }
    var endpoint by remember(profile) { mutableStateOf(preferences.getString(profile, null)?.let(::secureBridgeUrl)) }
    var configure by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CatalogTitle?>(null) }
    val statuses = remember(profile, endpoint) { mutableStateMapOf<Int, RequestDownload>() }
    var error by remember(endpoint) { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val client = remember(profile, endpoint) { endpoint?.let { clients.statusBridge(it, profile, token) } }
    val ids = remember(requestIds) { requestIds.distinct().sorted() }
    LaunchedEffect(client, active, resumed, ids, selected?.key) {
        if (client != null && active && resumed) {
            pollStatuses(if (selected != null) 12_000 else 45_000, fetch = {
                val selectedIds = selected?.requestIds?.takeIf { it.isNotEmpty() }?.toList() ?: ids
                val chunks = if (selectedIds.isEmpty()) listOf(emptyList()) else selectedIds.chunked(25)
                StatusBatch(chunks.flatMap { client.statuses(it).requests })
            }, publish = { value -> value.requests.forEach { statuses[it.requestId] = it }; error = null },
                failure = { failure -> error = when {
                    failure is BridgeFailure && failure.status == 401 -> "Seerr-sessionen har gått ut. Logga in igen i Seerr-inställningarna."
                    failure is BridgeFailure && failure.status == 404 -> "Begäran kunde inte hittas eller är inte tillgänglig för användaren."
                    failure is BridgeFailure && failure.status == 503 -> "Statusbryggan eller en nödvändig tjänst svarar inte."
                    else -> "Status kunde inte uppdateras. Kontrollera den säkra anslutningen."
                } })
        }
    }
    content(statuses, { selected = it }, {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Statusanslutning", compact = true, onClick = { configure = true })
            Text(error ?: if (endpoint == null) "Anslut en säker statusbrygga för nedladdningsstatus" else "Säker nedladdningsstatus", color = Muted, fontSize = 12.sp, maxLines = 1)
        }
    })
    if (configure) BridgeEndpointDialog(endpoint.orEmpty(), onDismiss = { configure = false }, onSave = { url ->
        if (url == null) preferences.edit().remove(profile).apply() else preferences.edit().putString(profile, url).apply()
        endpoint = url; configure = false
    })
    selected?.let { title ->
        val related = statuses.values.filter { it.requestId in title.requestIds || (title.requestIds.isEmpty() && it.tmdbId == title.ids.tmdb && it.mediaType == if (title.kind == TitleKind.Movie) "movie" else "tv") }.sortedBy { it.requestId }
        DownloadStatusDialog(title, related, error, onDismiss = { selected = null }, onOpen = { selected = null; onOpen(title) }, onPlay = { selected = null; onPlay(title) })
    }
}

@Composable
internal fun BridgeEndpointDialog(initial: String, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    val cancel = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        androidx.activity.compose.BackHandler(onBack = onDismiss)
        LaunchedEffect(Unit) { withFrameNanos { }; cancel.requestFocus() }
        Column(Modifier.onPreviewKeyEvent { e -> if (e.key == Key.Back) { if (e.type == KeyEventType.KeyUp) onDismiss(); true } else false }.width(520.dp).background(Ink).padding(24.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Text("Säker statusanslutning", color = Color.White, fontSize = 23.sp)
            Text("Ange statusbryggans privata HTTPS-adress. Inga tjänstelösenord eller API-nycklar ska anges här.", color = Muted, fontSize = 14.sp)
            BasicTextField(value, { value = it }, textStyle = TextStyle(color = Color.White, fontSize = 16.sp), singleLine = true,
                modifier = Modifier.fillMaxWidth().border(1.dp, Color.Cyan).padding(12.dp).testTag("bridge-url"))
            if (error) Text("En giltig HTTPS-adress krävs. Certifikatet måste vara betrott av enheten.", color = Color.Cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("Avbryt", requester = cancel, onClick = onDismiss)
                TvButton("Spara", onClick = { val url = secureBridgeUrl(value); if (url != null) onSave(url) else error = true })
                if (initial.isNotEmpty()) TvButton("Koppla från", onClick = { onSave(null) })
            }
        }
    }
}

@Composable
internal fun CompactDownload(status: RequestDownload) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(status) { while (true) { now = Instant.now(); delay(15_000) } }
    Column(Modifier.width(180.dp).testTag("download-card-${status.requestId}")) {
        Text(compactStatus(status.status, now), color = Color(0xFF4EDCFF), fontSize = 11.sp, maxLines = 1)
        status.status.percent()?.takeIf { !status.status.isOld(now) }?.let { percent ->
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(.15f))) {
                Box(Modifier.fillMaxWidth(percent / 100f).height(3.dp).background(Color(0xFF4EDCFF)))
            }
        }
    }
}

@Composable
internal fun DownloadStatusDialog(title: CatalogTitle, values: List<RequestDownload>, error: String?, onDismiss: () -> Unit, onOpen: () -> Unit, onPlay: (() -> Unit)? = null) {
    val initial = remember { FocusRequester() }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(10_000) } }
    Dialog(onDismissRequest = onDismiss) {
        androidx.activity.compose.BackHandler(onBack = onDismiss)
        LaunchedEffect(Unit) { withFrameNanos { }; initial.requestFocus() }
        Column(Modifier.onPreviewKeyEvent { e -> if (e.key == Key.Back) { if (e.type == KeyEventType.KeyUp) onDismiss(); true } else false }.width(680.dp).heightIn(max = 480.dp).background(Ink).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.name, color = Color.White, fontSize = 25.sp, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("Tillbaka", requester = initial, onClick = onDismiss)
                TvButton("Mer information", onClick = onOpen)
                TvButton("Läs status", Modifier.testTag("download-reading").onPreviewKeyEvent { e ->
                    if (e.key == Key.DirectionDown || e.key == Key.DirectionUp && state.canScrollBackward) {
                        if (e.type == KeyEventType.KeyDown) scope.launch { state.scrollBy(if (e.key == Key.DirectionDown) 120f else -120f) }
                        true
                    } else false
                }, onClick = {})
                if (values.any { it.status.state == "AVAILABLE" && it.status.jellyfinVerified && !it.status.isOld(now) } && title.playbackId != null && onPlay != null) TvButton("Tillgänglig – Spela", onClick = onPlay)
            }
            if (error != null) Text(error, color = Color.Cyan, fontSize = 13.sp)
            if (values.isEmpty()) Text("Status saknas. Anslut statusbryggan eller försök igen.", color = Muted)
            LazyColumn(state = state, modifier = Modifier.weight(1f, fill = false).testTag("download-detail-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                values.forEach { request ->
                    item(key = "request:${request.requestId}") {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Begäran ${request.requestId}", color = Muted, fontSize = 12.sp)
                            DownloadReading(request.status, now)
                            if (request.issues.isNotEmpty()) Text("Vissa tjänster svarar inte eller tillgängligheten är inte verifierad.", color = Muted, fontSize = 12.sp)
                        }
                    }
                    items(request.seasons, key = { "${request.requestId}:season:${it.seasonNumber}" }) { season ->
                        Text("Säsong ${season.seasonNumber}: ${season.availableEpisodes}/${season.totalEpisodes} avsnitt tillgängliga", color = Color.White, fontSize = 16.sp)
                    }
                    items(request.episodes, key = { "${request.requestId}:${it.seasonNumber}:${it.episodeNumber}" }) { episode ->
                        var expanded by remember { mutableStateOf(false) }
                        Column {
                            TvButton("S${episode.seasonNumber} · Avsnitt ${episode.episodeNumber} · ${compactStatus(episode.status, now)}",
                                Modifier.fillMaxWidth().testTag("download-episode-${episode.seasonNumber}-${episode.episodeNumber}"), onClick = { expanded = !expanded })
                            if (expanded) DownloadReading(episode.status, now)
                        }
                    }
                    items(request.status.transfers.indices.toList(), key = { "${request.requestId}:transfer:$it" }) { index ->
                        Column { Text("Nedladdning ${index + 1}", color = Muted); DownloadReading(request.status.transfers[index], now) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadReading(status: DownloadStatus, now: Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(compactStatus(status, now), color = Color.White, fontSize = 17.sp)
        val zone = ZoneId.systemDefault()
        if (!status.isOld(now)) {
            byteLabel(status.bytesPerSecond)?.let { Text("$it/s", color = Muted, fontSize = 13.sp) }
            byteLabel(status.remainingBytes)?.let { Text("$it kvar", color = Muted, fontSize = 13.sp) }
        }
        Text("Beräknat nedladdad: ${etaLabel(status.downloadEta, status, now, zone)}", color = Muted, fontSize = 13.sp)
        Text("Beräknat tillgänglig: ${etaLabel(status.availabilityEta, status, now, zone)}", color = Color(0xFF4EDCFF), fontSize = 13.sp)
        Text("Källa: " + (if (status.downloadEta.source == "QBITTORRENT") "qBittorrent" else "Underlag saknas") + " · " + when(status.downloadEta.confidence) { "HIGH" -> "Hög säkerhet"; "MEDIUM" -> "Måttlig säkerhet"; "LOW" -> "Låg säkerhet"; else -> "Okänd säkerhet" }, color = Muted, fontSize = 11.sp)
    }
}
