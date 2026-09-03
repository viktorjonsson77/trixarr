package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItemSelectAction
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.seriesPrimaryImage
import org.jellyfin.androidtv.util.apiclient.seriesThumbImage
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.sdk.TrailerUtils
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
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
	var contextItem by remember { mutableStateOf<BaseItemDto?>(null) }

	LaunchedEffect(state.hero?.id) { state.hero?.let(backgroundService::setBackground) }

	Box(Modifier.fillMaxSize()) {
		LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			item { MainToolbar(MainToolbarActiveButton.Home, downFocusRequester = heroFocusRequester) }
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
					onItemLongClick = { contextItem = it },
					onItemClick = { item ->
						val action = if (row.kind == HomeRowKind.RESUME) BaseRowItemSelectAction.Play else BaseRowItemSelectAction.ShowDetails
						itemLauncher.launch(BaseItemDtoBaseRowItem(item, row.format == HomeCardFormat.LANDSCAPE, true, action), null, context)
					},
				)
			}
			item { Box(Modifier.height(32.dp)) }
		}
	}
	contextItem?.let { item ->
		CinematicItemContextMenu(item = item, onDismiss = { contextItem = null })
	}
}

@Composable
private fun CinematicMediaRow(row: CinematicHomeRow, onItemFocused: (BaseItemDto) -> Unit, onItemLongClick: (BaseItemDto) -> Unit, onItemClick: (BaseItemDto) -> Unit) {
	Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
		Text(
			text = stringResource(row.titleRes),
			fontSize = 16.sp,
			fontWeight = FontWeight.SemiBold,
			color = Color.White,
			modifier = Modifier.padding(horizontal = HomeHorizontalPadding),
		)
		LazyRow(contentPadding = PaddingValues(horizontal = HomeHorizontalPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			items(row.items, key = { it.id }) { item -> CinematicMediaCard(item, row.format, onItemFocused, onItemLongClick, onItemClick) }
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CinematicMediaCard(item: BaseItemDto, format: HomeCardFormat, onItemFocused: (BaseItemDto) -> Unit, onItemLongClick: (BaseItemDto) -> Unit, onItemClick: (BaseItemDto) -> Unit) {
	val api = koinInject<ApiClient>()
	var focused by remember { mutableStateOf(false) }
	var longPressHandled by remember { mutableStateOf(false) }
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
			.onPreviewKeyEvent { event ->
				if (event.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_DPAD_CENTER) false
				else if (event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.repeatCount > 0 || event.nativeKeyEvent.isLongPress)) {
					longPressHandled = true
					true
				} else if (event.type == KeyEventType.KeyUp && longPressHandled) {
					longPressHandled = false
					onItemLongClick(item)
					true
				} else false
			}
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

enum class CinematicContextAction { Resume, PlayFromBeginning, Details, Trailer, MarkPlayed, MarkUnplayed, AddToList, RemoveFromList }

fun contextActions(item: BaseItemDto, hasTrailer: Boolean): List<CinematicContextAction> = buildList {
	if ((item.userData?.playbackPositionTicks ?: 0L) > 0L) add(CinematicContextAction.Resume)
	add(CinematicContextAction.PlayFromBeginning)
	add(CinematicContextAction.Details)
	if (hasTrailer) add(CinematicContextAction.Trailer)
	add(if (item.userData?.played == true) CinematicContextAction.MarkUnplayed else CinematicContextAction.MarkPlayed)
	add(if (item.userData?.isFavorite == true) CinematicContextAction.RemoveFromList else CinematicContextAction.AddToList)
}

@Composable
fun CinematicItemContextMenu(item: BaseItemDto, onDismiss: () -> Unit) {
	val context = LocalContext.current
	val playbackHelper = koinInject<PlaybackHelper>()
	val navigationRepository = koinInject<org.jellyfin.androidtv.ui.navigation.NavigationRepository>()
	val mutationRepository = koinInject<ItemMutationRepository>()
	val api = koinInject<ApiClient>()
	val playbackLauncher = koinInject<PlaybackLauncher>()
	val scope = rememberCoroutineScope()
	var pendingListAction by remember { mutableStateOf<CinematicContextAction?>(null) }
	val hasTrailer = remember(item) { TrailerUtils.hasPlayableTrailers(context, item) }
	val menuColors = ButtonDefaults.colors(
		containerColor = Color(0xFF26272C),
		contentColor = Color.White,
		focusedContainerColor = Color(0xFF432453),
		focusedContentColor = Color.White,
	)
	if (pendingListAction != null) {
		Dialog(onDismissRequest = { pendingListAction = null }, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
			Column(Modifier.width(300.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF51A1B1F)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
				Text(item.name.orEmpty(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2)
				Text(stringResource(R.string.jellyfin_tv_my_list_favorites_explanation), color = Color.White.copy(.76f), fontSize = 13.sp)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Button(colors = menuColors, onClick = { pendingListAction = null }) { Text(stringResource(R.string.lbl_cancel)) }
					Button(colors = menuColors, onClick = {
						val add = pendingListAction == CinematicContextAction.AddToList
						scope.launch { mutationRepository.setFavorite(item.id, add) }
						pendingListAction = null
						onDismiss()
					}) { Text(stringResource(if (pendingListAction == CinematicContextAction.AddToList) R.string.jellyfin_tv_add_to_my_list else R.string.jellyfin_tv_remove_from_my_list)) }
				}
			}
		}
	} else Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
		Column(
			modifier = Modifier.width(280.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF51A1B1F)).padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Text(item.name.orEmpty(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
			contextActions(item, hasTrailer).forEach { action ->
				val label = when (action) {
					CinematicContextAction.Resume -> stringResource(R.string.jellyfin_tv_resume)
					CinematicContextAction.PlayFromBeginning -> stringResource(R.string.jellyfin_tv_play_from_beginning)
					CinematicContextAction.Details -> stringResource(R.string.jellyfin_tv_more_information)
					CinematicContextAction.Trailer -> stringResource(R.string.lbl_play_trailers)
					CinematicContextAction.MarkPlayed -> stringResource(R.string.lbl_mark_played)
					CinematicContextAction.MarkUnplayed -> stringResource(R.string.lbl_mark_unplayed)
					CinematicContextAction.AddToList -> stringResource(R.string.jellyfin_tv_add_to_my_list)
					CinematicContextAction.RemoveFromList -> stringResource(R.string.jellyfin_tv_remove_from_my_list)
				}
				Button(modifier = Modifier.fillMaxWidth().height(44.dp), colors = menuColors, onClick = {
					when (action) {
						CinematicContextAction.Resume -> playbackHelper.retrieveAndPlay(item.id, false, (item.userData?.playbackPositionTicks ?: 0L) / 10_000L, context)
						CinematicContextAction.PlayFromBeginning -> playbackHelper.retrieveAndPlay(item.id, false, 0L, context)
						CinematicContextAction.Details -> navigationRepository.navigate(org.jellyfin.androidtv.ui.navigation.Destinations.itemDetails(item.id))
						CinematicContextAction.Trailer -> scope.launch {
							if ((item.localTrailerCount ?: 0) > 0) {
								val trailers = api.userLibraryApi.getLocalTrailers(item.id).content
								playbackLauncher.launch(context, trailers)
							} else TrailerUtils.getExternalTrailerIntent(context, item)?.let(context::startActivity)
						}
						CinematicContextAction.MarkPlayed -> scope.launch { mutationRepository.setPlayed(item.id, true) }
						CinematicContextAction.MarkUnplayed -> scope.launch { mutationRepository.setPlayed(item.id, false) }
						CinematicContextAction.AddToList, CinematicContextAction.RemoveFromList -> pendingListAction = action
					}
					if (action != CinematicContextAction.AddToList && action != CinematicContextAction.RemoveFromList) onDismiss()
				}) { Text(label, fontSize = 13.sp, maxLines = 1) }
			}
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
