package org.jellyfin.androidtv.ui.catalog

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.toBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import org.jellyfin.androidtv.ui.base.Text

internal val FanartLayerCountKey = SemanticsPropertyKey<Int>("FanartLayerCount")

internal val Accent = Color(0xFFF0F3F5)
internal val LocalContentFocusEnabled = staticCompositionLocalOf { true }
internal val LocalFocusColor = staticCompositionLocalOf { Accent }
internal val Muted = Color(0xFFB9BBC2)
internal val Ink = Color(0xFF080C10)
internal val CardShape = RoundedCornerShape(5.dp)

/** One focus target and one key-up activation, including held OK. */
@Composable
internal fun TvTarget(
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onClick: (() -> Unit)?,
    filled: Boolean = false,
    selected: Boolean = false,
    quiet: Boolean = false,
    animateCard: Boolean = false,
    logicalFocus: Boolean? = null,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    val enabled = LocalContentFocusEnabled.current
    var nativeFocused by remember { mutableStateOf(false) }
    val focused = logicalFocus ?: nativeFocused
    val focusProgress by animateFloatAsState(if (focused && animateCard) 1f else 0f,
        tween(TITLE_FOCUS_MS, easing = FastOutSlowInEasing), label = "title-focus")
    val focusColor = LocalFocusColor.current
    val shape = if (quiet) RoundedCornerShape(20.dp) else CardShape
    Box(
        modifier
            .zIndex(if (focused && animateCard) 1f else 0f)
            .graphicsLayer {
                if (animateCard) {
                    scaleX = 1f + (TITLE_FOCUS_SCALE - 1f) * focusProgress
                    scaleY = scaleX
                }
            }
            .focusProperties { canFocus = enabled }
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged {
                nativeFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clip(shape)
            .background(
                when {
                    focused && filled -> LocalFocusColor.current
                    selected -> Accent.copy(.18f)
                    quiet -> Color.Transparent
                    else -> Color(0xA012171D)
                }
            )
            .border(
                if (animateCard) (.6f + 1.4f * focusProgress).dp else if (focused) 2.dp else .6.dp,
                when {
                    animateCard -> lerp(Color(0x554B535E), focusColor, focusProgress)
                    focused -> LocalFocusColor.current
                    selected -> Accent.copy(.6f)
                    quiet -> Color.Transparent
                    else -> Color(0x554B535E)
                },
                shape,
            )
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier.focusable()
            ),
        content = {
            content(focused)
            if (animateCard) Box(Modifier.matchParentSize().graphicsLayer { alpha = .04f * focusProgress }
                .background(Color.White))
        },
    )
}

@Composable
internal fun TvButton(
    label: String,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    primary: Boolean = false,
    compact: Boolean = false,
    selected: Boolean = false,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    TvTarget(
        modifier.height(if (compact) 26.dp else 42.dp).widthIn(min = if (compact) 50.dp else 96.dp),
        requester,
        onFocus,
        onClick,
        filled = true,
        selected = selected,
        quiet = compact,
    ) { focused ->
        Text(
            label,
            color = if (focused) Ink else Color.White,
            fontSize = if (compact) 12.sp else 14.sp,
            maxLines = 1,
            modifier =
                Modifier.align(Alignment.Center)
                    .padding(
                        horizontal = if (label.length == 1) 2.dp else if (compact) 12.dp else 18.dp
                    ),
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun Fanart(url: String?, subdued: Boolean = false, soft: Boolean = false, onReady: (String?) -> Unit = {}, identity: String? = url) {
    val notifyReady by androidx.compose.runtime.rememberUpdatedState(onReady)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = remember(scope) { LatestFanart<ImageBitmap>(scope) }
    var pending by remember { mutableStateOf<Pair<ImageBitmap?, String?>?>(null) }
    var current by remember { mutableStateOf<ImageBitmap?>(null) }
    var incoming by remember { mutableStateOf<ImageBitmap?>(null) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { pending }.collect { value ->
            if (value != null) {
                incoming = value.first
                if (current == null) {
                    current = incoming
                    incoming = null
                    notifyReady(value.second)
                } else if (incoming != null) {
                    notifyReady(value.second)
                    alpha.snapTo(0f)
                    alpha.animateTo(1f, tween(FANART_CROSSFADE_MS))
                    current = incoming
                    incoming = null
                } else notifyReady(value.second)
            }
        }
    }
    DisposableEffect(loader) { onDispose { loader.cancel() } }
    LaunchedEffect(url, soft, identity) {
        // Drop a queued old result; an already visible fade finishes without an alpha reset.
        pending = null
        loader.submit(load = {
            if (url == null) null else SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context).data(url).apply {
                    if (soft) {
                        allowHardware(false)
                        transformations(SoftFanartTransformation())
                    }
                }.build()
            ).image?.toBitmap()?.asImageBitmap()
        }, publish = { pending = it to identity }, stabilize = current != null)
    }
    Box(Modifier.fillMaxSize().background(Ink).testTag("catalog-fanart").semantics {
        set(FanartLayerCountKey, (if (current != null) 1 else 0) + (if (incoming != null) 1 else 0))
    }) {
        current?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        incoming?.let { Image(it, null, Modifier.fillMaxSize().then(Modifier.graphicsLayer { this.alpha = alpha.value }), contentScale = ContentScale.Crop) }
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(.78f), Color.Black.copy(.28f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(.12f),
                        .38f to Color.Transparent,
                        .80f to Ink.copy(.48f),
                        1f to Ink,
                    )
                )
        )
        if (subdued) Box(Modifier.fillMaxSize().background(Ink.copy(.58f)))
    }
}

internal fun facts(title: CatalogTitle): String =
    listOfNotNull(
            when (title.kind) { TitleKind.Movie -> "Film"; TitleKind.Series -> "Serie"; TitleKind.Episode -> "Avsnitt" },
            title.releaseDate ?: title.year?.toString(),
            if (title.kind == TitleKind.Series)
                title.seasons.size
                    .takeIf { it > 0 }
                    ?.let { if (it == 1) "1 säsong" else "$it säsonger" }
            else title.runtimeTicks?.div(600_000_000L)?.let { "$it min" },
            title.seasonNumber?.let { season -> "S$season" + (title.episodeNumber?.let { " · E$it" } ?: "") },
            title.certification,
            title.genres.take(2).joinToString(" • ").ifBlank { null },
        )
        .joinToString("    ")

internal fun statusLabel(status: TitleStatus) =
    when (status) {
        TitleStatus.Available -> "Tillgänglig"
        TitleStatus.Requestable -> "Inte tillgänglig ännu"
        TitleStatus.Sending -> "Begäran skickas …"
        TitleStatus.Requested -> "Begärd"
        TitleStatus.Processing -> "Bearbetas"
        TitleStatus.Partial -> "Delvis tillgänglig"
        TitleStatus.Declined -> "Begäran nekad"
        TitleStatus.Error -> "Begäran kunde inte slutföras"
        TitleStatus.Unknown -> ""
    }

/** Fixed home geometry is shared by every title, including missing metadata. */
@Composable
internal fun HomeHero(title: CatalogTitle?, actions: @Composable () -> Unit) {
    Column {
        Box(Modifier.height(240.dp))
        TitleHero(title, compact = true)
        Box(Modifier.height(42.dp)) { if (title != null) actions() }
    }
}

@Composable
internal fun TitleHero(title: CatalogTitle?, compact: Boolean = false) {
    Column(
        Modifier.fillMaxWidth(if (compact) .52f else .58f).height(if (compact) 126.dp else 159.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.height(if (compact) 57.dp else 77.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                title?.seriesName ?: title?.name.orEmpty(),
                color = Color.White,
                fontSize = if (compact) 32.sp else 40.sp,
                fontWeight = FontWeight.Light,
                maxLines = 2,
                lineHeight = if (compact) 29.sp else 36.sp,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            title?.let(::facts).orEmpty(),
            color = Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            title?.overview.orEmpty(),
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TitleCard(
    title: CatalogTitle,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
    search: Boolean = false,
    requester: FocusRequester? = null,
    logicalFocus: Boolean? = null,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    TvTarget(modifier, requester, onFocus, onClick, animateCard = true, logicalFocus = logicalFocus) {
        val image = if (landscape) title.backdrop ?: title.poster else title.poster
        if (image != null)
            AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Color.Black.copy(.97f))
                    )
                )
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (landscape) title.seriesName ?: title.name else title.name,
                color = Color.White,
                fontSize = if (landscape) 12.sp else 11.sp,
                maxLines = 2,
                lineHeight = 13.sp,
                overflow = TextOverflow.Ellipsis,
            )
            if (search)
                Text(
                    listOfNotNull(
                            when (title.kind) { TitleKind.Movie -> "Film"; TitleKind.Series -> "Serie"; TitleKind.Episode -> "Avsnitt" },
            title.releaseDate ?: title.year?.toString(),
                            title.seasonNumber?.let { season -> "S$season" + (title.episodeNumber?.let { " · E$it" } ?: "") },
            title.certification,
                            if (title.kind == TitleKind.Movie) "Film" else "Serie",
                        )
                        .joinToString("  "),
                    color = Muted,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            if (landscape && title.progress > 0) {
                Progress(title)
                title.minutesRemaining?.let {
                    Text("$it min kvar", color = Color.White, fontSize = 9.sp)
                }
            } else
                Text(
                    if (title.playbackId != null || title.ids.local != null)
                        if (search) "▶  Spela" else "▶"
                    else if (title.primary == TitleAction.Request) "+  Begär"
                    else statusLabel(title.status),
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
        }
    }
}

@Composable
internal fun Progress(title: CatalogTitle) {
    Box(
        Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF414853), RoundedCornerShape(2.dp))
    ) {
        if (title.progress > 0)
            Box(
                Modifier.fillMaxWidth(title.progress)
                    .fillMaxHeight()
                    .background(LocalFocusColor.current, RoundedCornerShape(2.dp))
            )
    }
}
