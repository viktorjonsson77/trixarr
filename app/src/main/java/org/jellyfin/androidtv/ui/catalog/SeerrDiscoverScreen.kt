package org.jellyfin.androidtv.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.seerr.DiscoveryCategory
import org.jellyfin.androidtv.seerr.SeerrHttpException
import org.jellyfin.androidtv.ui.base.Text

internal fun discoveryError(error: Throwable): Int = when {
    error is java.net.SocketTimeoutException -> R.string.discover_timeout
    error is SeerrHttpException -> when (error.status) {
        401 -> R.string.discover_auth_error
        403 -> R.string.discover_permission_error
        409 -> R.string.discover_conflict
        in 500..599 -> R.string.discover_server_error
        else -> R.string.discover_network_error
    }
    else -> R.string.discover_network_error
}
internal fun discoveryRowLabel(key: String): Int? = when (key) {
    "new_movies" -> R.string.discover_new_movies; "new_tv" -> R.string.discover_new_tv
    "upcoming_movies" -> R.string.discover_upcoming_movies; "upcoming_tv" -> R.string.discover_upcoming_tv
    "popular_movies" -> R.string.discover_popular_movies; "popular_tv" -> R.string.discover_popular_tv
    "top_movies" -> R.string.discover_top_movies; "top_tv" -> R.string.discover_top_tv
    "my_requests" -> R.string.discover_my_requests; "request_movies" -> R.string.discover_request_movies
    "request_tv" -> R.string.discover_request_tv; else -> null
}
internal fun discoveryStatus(title: CatalogTitle): Int = when (title.status) {
    TitleStatus.Available -> R.string.discover_available
    TitleStatus.Requestable -> if (title.requestAllowed) R.string.discover_request else R.string.discover_permission_error
    TitleStatus.Requested -> R.string.discover_pending
    TitleStatus.Processing, TitleStatus.Sending -> R.string.discover_processing
    TitleStatus.Partial -> R.string.discover_partial
    else -> R.string.discover_unknown
}

/** Shares CatalogPage stack/focus, CatalogImage, detail dialogs and the existing playback flow. */
@Composable
internal fun SeerrDiscoverScreen(page: CatalogPage, hero: CatalogTitle?,
    onCategory: (DiscoveryCategory) -> Unit, onGenre: (String) -> Unit, onOpen: (CatalogTitle) -> Unit,
    onPrimary: (CatalogTitle) -> Unit, onMenu: () -> Unit, onRetry: () -> Unit) {
    val enabled = LocalContentFocusEnabled.current
    val requesters = remember(page, page.discoveryCategory) { mutableMapOf<String, FocusRequester>() }
    fun requester(token: String) = requesters.getOrPut(token) { FocusRequester() }
    fun focused(token: String, title: CatalogTitle? = null) {
        if (enabled && !page.restoring) {
            page.focusToken = token
            if (title != null) page.selected = title.key
        }
    }
    val tabToken = "tab:${page.discoveryCategory}"
    LaunchedEffect(page.restore, page.loading, enabled, page.discoveryCategory) {
        if (enabled) {
            withFrameNanos { }
            val token = page.focusToken.takeIf { it in requesters } ?: tabToken
            runCatching { requester(token).requestFocus() }
            page.restoring = false
        }
    }
    CompositionLocalProvider(LocalFocusColor provides Color(0xFF4EDCFF)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(R.string.discover_title), color = Color.White, fontSize = 22.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(DiscoveryCategory.entries, key = { it.name }) { category ->
                        val token = "tab:$category"
                        TvButton(stringResource(category.label),
                            Modifier.testTag("discover-tab-$category").onPreviewKeyEvent {
                                if (category == DiscoveryCategory.New && it.key == Key.DirectionLeft) {
                                    if (it.type == KeyEventType.KeyDown) onMenu(); true
                                } else false
                            }, compact = true, selected = category == page.discoveryCategory,
                            requester = requester(token), onFocus = { focused(token) },
                            onClick = { onCategory(category) })
                    }
                }
            }
            if (page.discoveryCategory == DiscoveryCategory.Genres && page.discoveryGenres.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(page.discoveryGenres, key = { it.key }) { genre ->
                        val token = "genre:${genre.key}"
                        TvButton(genre.name + if (genre.key.startsWith("tv:")) " · TV" else " · Film",
                            compact = true, selected = page.discoveryGenre == genre.key, requester = requester(token),
                            onFocus = { focused(token) }, onClick = { onGenre(genre.key) })
                    }
                }
            }
            if (page.loading) {
                Text(stringResource(R.string.discover_loading), color = Muted, modifier = Modifier.testTag("discover-loading"))
            } else if (page.rows.isEmpty() || page.error != null) {
                Text(page.error ?: stringResource(R.string.discover_empty), color = Color.White, fontSize = 19.sp,
                    modifier = Modifier.testTag("discover-empty-error").padding(vertical = 40.dp))
                TvButton(stringResource(R.string.discover_retry), requester = requester("retry"), onFocus = { focused("retry") }, onClick = onRetry)
            } else LazyColumn(state = page.list, verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 28.dp), modifier = Modifier.testTag("discover-content")) {
                item(key = "hero") {
                    Column(Modifier.fillMaxWidth().height(230.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (hero != null) {
                            Text(hero.name, color = Color.White, fontSize = 36.sp, lineHeight = 40.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(.65f).testTag("discover-hero-title"))
                            Text((listOfNotNull(hero.year?.toString()) + hero.genres.take(3)).joinToString("  •  "), color = Muted, fontSize = 13.sp)
                            Text(hero.overview, color = Color.White.copy(.85f), fontSize = 14.sp, lineHeight = 18.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(.58f))
                            Text(stringResource(discoveryStatus(hero)), color = Color(0xFF4EDCFF), fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (hero.primary != TitleAction.Information) TvButton(
                                    stringResource(when(hero.primary) {
                                        TitleAction.Play, TitleAction.Resume -> R.string.discover_play
                                        TitleAction.Request -> R.string.discover_request
                                        else -> R.string.discover_episodes
                                    }), requester = requester("discover-primary"), onFocus = { focused("discover-primary") }, onClick = { onPrimary(hero) })
                                TvButton(stringResource(R.string.discover_information), requester = requester("discover-info"),
                                    onFocus = { focused("discover-info") }, onClick = { onOpen(hero) })
                                hero.releaseDate?.takeIf { it > java.time.LocalDate.now().toString() }?.let {
                                    Text(stringResource(R.string.discover_releases, it), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 13.dp))
                                }
                            }
                        }
                    }
                }
                items(page.rows, key = { it.key }) { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(discoveryRowLabel(row.key)?.let { stringResource(it) } ?: row.name,
                            color = Color.White, fontSize = 19.sp)
                        val rowState = page.rowStates.getOrPut("${page.discoveryCategory}:${row.key}") { LazyListState() }
                        LazyRow(state = rowState, contentPadding = PaddingValues(7.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(row.titles, key = { _, title -> title.key }) { index, title ->
                                val token = "discover:${row.key}:${title.key}"
                                TitleCard(title, Modifier.width(180.dp).height(108.dp).testTag(token).onPreviewKeyEvent {
                                    if (index == 0 && it.key == Key.DirectionLeft) {
                                        if (it.type == KeyEventType.KeyDown) onMenu(); true
                                    } else false
                                }, landscape = true, requester = requester(token),
                                    onFocus = { focused(token, title) }, onClick = { onOpen(title) })
                            }
                        }
                    }
                }
            }
        }
    }
}
