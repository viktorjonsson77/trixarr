package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinematic.EmptyState
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.CinematicItemContextMenu
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import java.util.UUID
import java.util.Locale

class CinematicLibraryFragment : Fragment() {
	private val api by inject<ApiClient>()
	private var items by mutableStateOf<List<BaseItemDto>>(emptyList())
	private var loaded by mutableStateOf(false)

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		val type = CollectionType.valueOf(requireArguments().getString(ARG_COLLECTION_TYPE)!!)
		JellyfinTheme { CinematicLibraryScreen(type, items, loaded) }
	}

	override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val parentId = UUID.fromString(requireArguments().getString(ARG_LIBRARY_ID))
		val type = CollectionType.valueOf(requireArguments().getString(ARG_COLLECTION_TYPE)!!)
		lifecycleScope.launch {
			items = runCatching { withContext(Dispatchers.IO) {
				api.itemsApi.getItems(
					parentId = parentId,
					recursive = true,
					includeItemTypes = setOf(if (type == CollectionType.MOVIES) BaseItemKind.MOVIE else BaseItemKind.SERIES),
					fields = ItemRepository.browseFields,
					sortBy = setOf(ItemSortBy.SORT_NAME),
					sortOrder = setOf(SortOrder.ASCENDING),
					imageTypeLimit = 1,
					limit = 300,
				).content.items
			} }.getOrDefault(emptyList())
			loaded = true
		}
	}

	companion object {
		const val ARG_LIBRARY_ID = "libraryId"
		const val ARG_COLLECTION_TYPE = "collectionType"
	}
}

@Composable
private fun CinematicLibraryScreen(type: CollectionType, items: List<BaseItemDto>, loaded: Boolean) {
	val backgroundService = koinInject<BackgroundService>()
	val launcher = koinInject<ItemLauncher>()
	val context = LocalContext.current
	var selected by remember(items) { mutableStateOf(items.firstOrNull()) }
	var contextItem by remember { mutableStateOf<BaseItemDto?>(null) }
	LaunchedEffect(selected?.id) { selected?.let(backgroundService::setBackground) }
	Column(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(.90f), Color.Transparent)))) {
		MainToolbar(if (type == CollectionType.MOVIES) MainToolbarActiveButton.Movies else MainToolbarActiveButton.Series)
		selected?.let { item ->
			Column(Modifier.padding(horizontal = 32.dp).height(118.dp).width(620.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text(item.name.orEmpty(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
				val facts = listOfNotNull(item.productionYear?.toString(), item.officialRating, item.communityRating?.let { String.format(Locale.ROOT, "★ %.1f", it) }).joinToString("  ·  ")
				if (facts.isNotEmpty()) Text(facts, color = Color.White.copy(.78f), fontSize = 13.sp)
				item.overview?.let { Text(it, color = Color.White.copy(.82f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
			}
		}
		if (!loaded) EmptyState(title = stringResource(R.string.loading), description = stringResource(R.string.jellyfin_tv_library_loading))
		else if (items.isEmpty()) EmptyState(
			title = stringResource(if (type == CollectionType.MOVIES) R.string.lbl_movies else R.string.lbl_series),
			description = stringResource(R.string.jellyfin_tv_library_empty),
		)
		LazyVerticalGrid(
			columns = GridCells.Adaptive(112.dp),
			modifier = Modifier.fillMaxSize(),
			contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 32.dp, end = 32.dp, top = 10.dp, bottom = 32.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			items(items, key = { it.id }) { item -> LibraryPoster(item, item.id == items.firstOrNull()?.id, { selected = item }, { launcher.launch(BaseItemDtoBaseRowItem(item), null, context) }, { contextItem = item }) }
		}
	}
	contextItem?.let { CinematicItemContextMenu(it) { contextItem = null } }
}

@Composable
private fun LibraryPoster(item: BaseItemDto, requestInitialFocus: Boolean, onFocus: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
	val api = koinInject<ApiClient>()
	val focusRequester = remember { FocusRequester() }
	var focused by remember { mutableStateOf(false) }
	var longPress by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "LibraryPosterFocus")
	val shape = RoundedCornerShape(12.dp)
	LaunchedEffect(requestInitialFocus) { if (requestInitialFocus) focusRequester.requestFocus() }
	Column(
		Modifier.width(112.dp).scale(scale).focusRequester(focusRequester).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
			.onPreviewKeyEvent { event ->
				if (event.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_DPAD_CENTER) false
				else if (event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.repeatCount > 0 || event.nativeKeyEvent.isLongPress)) { longPress = true; true }
				else if (event.type == KeyEventType.KeyUp && longPress) { longPress = false; onLongClick(); true }
				else false
			}
			.clickable(onClick = onClick).focusable(),
		verticalArrangement = Arrangement.spacedBy(5.dp),
	) {
		Box(Modifier.fillMaxWidth().height(168.dp).clip(shape).background(Color(0xFF17181D)).border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFF9B4DFF) else Color.White.copy(.12f), shape)) {
			val image = item.itemImages[ImageType.PRIMARY]
			AsyncImage(
				url = image?.getUrl(api),
				blurHash = image?.blurHash,
				aspectRatio = 2f / 3f,
				scaleType = ImageView.ScaleType.CENTER_CROP,
				modifier = Modifier.fillMaxSize(),
			)
		}
		Text(item.name.orEmpty(), color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
	}
}
