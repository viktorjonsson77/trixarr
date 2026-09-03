package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItemSelectAction
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.seriesPrimaryImage
import org.jellyfin.androidtv.util.apiclient.seriesThumbImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

private val HomeHorizontalPadding = 28.dp
private val CardShape = RoundedCornerShape(11.dp)

@Composable
fun CinematicHomeScreen(state: CinematicHomeState, onItemFocused: (BaseItemDto) -> Unit) {
	val backgroundService = koinInject<BackgroundService>()
	val itemLauncher = koinInject<ItemLauncher>()
	val context = LocalContext.current
	val heroFocusRequester = remember { FocusRequester() }
	val listState = rememberLazyListState()
	val showToolbarScrim by remember {
		derivedStateOf {
			listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24
		}
	}

	LaunchedEffect(state.hero?.id) { state.hero?.let(backgroundService::setBackground) }

	Box(Modifier.fillMaxSize()) {
		LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			item { Spacer(Modifier.height(95.dp)) }
			item {
				CinematicHomeHero(
					item = state.hero,
					modifier = Modifier.padding(horizontal = HomeHorizontalPadding),
					focusRequester = heroFocusRequester,
					onPlay = { item ->
						itemLauncher.launch(BaseItemDtoBaseRowItem(item, false, false, BaseRowItemSelectAction.Play), null, context)
					},
				)
			}
			items(state.rows, key = { it.kind }) { row ->
				CinematicMediaRow(
					row = row,
					onItemFocused = onItemFocused,
					onItemClick = { item ->
						val action = if (row.kind == HomeRowKind.RESUME) BaseRowItemSelectAction.Play else BaseRowItemSelectAction.ShowDetails
						itemLauncher.launch(BaseItemDtoBaseRowItem(item, row.format == HomeCardFormat.LANDSCAPE, true, action), null, context)
					},
				)
			}
			item { Spacer(Modifier.height(32.dp)) }
		}
		Box(Modifier.fillMaxWidth().height(48.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent))))
		if (showToolbarScrim) {
			Box(Modifier.fillMaxWidth().height(95.dp).background(Color.Black.copy(alpha = .82f)))
		}
		MainToolbar(MainToolbarActiveButton.Home, downFocusRequester = heroFocusRequester)
	}
}

@Composable
private fun CinematicMediaRow(row: CinematicHomeRow, onItemFocused: (BaseItemDto) -> Unit, onItemClick: (BaseItemDto) -> Unit) {
	Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
		Text(
			text = stringResource(row.titleRes),
			fontSize = 16.sp,
			fontWeight = FontWeight.SemiBold,
			color = Color.White,
			modifier = Modifier.padding(horizontal = HomeHorizontalPadding),
		)
		LazyRow(contentPadding = PaddingValues(horizontal = HomeHorizontalPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			items(row.items, key = { it.id }) { item -> CinematicMediaCard(item, row.format, onItemFocused, onItemClick) }
		}
	}
}

@Composable
private fun CinematicMediaCard(item: BaseItemDto, format: HomeCardFormat, onItemFocused: (BaseItemDto) -> Unit, onItemClick: (BaseItemDto) -> Unit) {
	val api = koinInject<ApiClient>()
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "HomeCardFocus")
	val image = remember(item, format) {
		when (format) {
			HomeCardFormat.LANDSCAPE -> item.itemImages[ImageType.THUMB] ?: item.seriesThumbImage ?: item.parentImages[ImageType.THUMB] ?: item.itemImages[ImageType.PRIMARY]
			HomeCardFormat.POSTER -> item.seriesPrimaryImage ?: item.itemImages[ImageType.PRIMARY]
		}
	}
	val width = if (format == HomeCardFormat.LANDSCAPE) 142.dp else 110.dp

	Box(
		modifier = Modifier
			.width(width)
			.then(if (format == HomeCardFormat.LANDSCAPE) Modifier.aspectRatio(16f / 9f) else Modifier.aspectRatio(2f / 3f))
			.scale(scale)
			.clip(CardShape)
			.background(Color(0xFF202124))
			.border(
				if (focused) 2.dp else 1.dp,
				if (focused) Brush.linearGradient(listOf(Color(0xFFAA5CC3), Color(0xFF00A4DC))) else Brush.linearGradient(listOf(Color.White.copy(alpha = .12f), Color.White.copy(alpha = .12f))),
				CardShape,
			)
			.onFocusChanged { state -> focused = state.isFocused; if (state.isFocused) onItemFocused(item) }
			.clickable { onItemClick(item) }
			.focusable(),
	) {
		AsyncImage(
			url = image?.getUrl(api),
			blurHash = image?.blurHash,
			aspectRatio = if (format == HomeCardFormat.LANDSCAPE) 16f / 9f else 2f / 3f,
			scaleType = ImageView.ScaleType.CENTER_CROP,
			modifier = Modifier.fillMaxSize(),
		)
		Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .88f)))))
		Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp, vertical = 5.dp)) {
			Text(
				text = if (item.type == BaseItemKind.EPISODE) item.seriesName ?: item.name.orEmpty() else item.name.orEmpty(),
				fontSize = if (format == HomeCardFormat.LANDSCAPE) 11.sp else 10.sp,
				fontWeight = FontWeight.SemiBold,
				color = Color.White,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			item.episodeLabel()?.let { Text(it, fontSize = 9.sp, color = Color.White.copy(alpha = .82f), maxLines = 1) }
		}
		val progress = item.playbackProgress()
		if (progress > 0f) {
			Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .25f)))
			Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(3.dp).background(Brush.horizontalGradient(listOf(Color(0xFFAA5CC3), Color(0xFF00A4DC)))))
		}
	}
}

fun BaseItemDto.episodeLabel(): String? {
	if (type != BaseItemKind.EPISODE) return null
	val code = listOfNotNull(parentIndexNumber?.let { "S${it.toString().padStart(2, '0')}" }, indexNumber?.let { "E${it.toString().padStart(2, '0')}" }).joinToString("")
	return listOf(code.takeIf(String::isNotBlank), name?.takeIf { it != seriesName }).filterNotNull().joinToString(" · ").takeIf(String::isNotBlank)
}

fun BaseItemDto.playbackProgress(): Float {
	val position = userData?.playbackPositionTicks ?: return 0f
	val runtime = runTimeTicks?.takeIf { it > 0 } ?: return 0f
	return (position.toDouble() / runtime.toDouble()).toFloat().coerceIn(0f, 1f)
}
