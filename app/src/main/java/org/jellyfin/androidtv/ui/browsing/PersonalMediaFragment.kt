package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinematic.EmptyState
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.CinematicItemContextMenu
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.*
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.LocalDateTime

enum class PersonalMediaMode { Favorites, History }
enum class HistoryDateGroup { Today, Yesterday, ThisWeek, Older }

fun historyDateGroup(playedAt: LocalDateTime, today: LocalDate): HistoryDateGroup {
	val date = playedAt.toLocalDate()
	return when {
		date == today -> HistoryDateGroup.Today
		date == today.minusDays(1) -> HistoryDateGroup.Yesterday
		!date.isBefore(today.minusDays(6)) -> HistoryDateGroup.ThisWeek
		else -> HistoryDateGroup.Older
	}
}

fun groupedHistory(items: List<BaseItemDto>, today: LocalDate = LocalDate.now()): Map<HistoryDateGroup, List<BaseItemDto>> =
	items.mapNotNull { item -> item.userData?.lastPlayedDate?.let { historyDateGroup(it, today) to item } }
		.groupBy({ it.first }, { it.second })

class PersonalMediaFragment : Fragment() {
	private val api by inject<ApiClient>()
	private var entries by mutableStateOf<List<BaseItemDto>>(emptyList())
	private var loaded by mutableStateOf(false)

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		val mode = PersonalMediaMode.valueOf(requireArguments().getString(ARG_MODE)!!)
		JellyfinTheme { PersonalMediaScreen(mode, entries, loaded) }
	}

	override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val mode = PersonalMediaMode.valueOf(requireArguments().getString(ARG_MODE)!!)
		lifecycleScope.launch {
			entries = runCatching { withContext(Dispatchers.IO) {
				api.itemsApi.getItems(
					recursive = true,
					includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
					fields = ItemRepository.itemFields,
					filters = setOf(if (mode == PersonalMediaMode.Favorites) ItemFilter.IS_FAVORITE else ItemFilter.IS_PLAYED),
					sortBy = setOf(if (mode == PersonalMediaMode.Favorites) ItemSortBy.SORT_NAME else ItemSortBy.DATE_PLAYED),
					sortOrder = setOf(if (mode == PersonalMediaMode.Favorites) SortOrder.ASCENDING else SortOrder.DESCENDING),
					imageTypeLimit = 1,
					limit = 250,
				).content.items.filter { mode == PersonalMediaMode.Favorites || it.userData?.lastPlayedDate != null }
			} }.getOrDefault(emptyList())
			loaded = true
		}
	}

	companion object { const val ARG_MODE = "mode" }
}

@Composable
private fun PersonalMediaScreen(mode: PersonalMediaMode, entries: List<BaseItemDto>, loaded: Boolean) {
	val api = koinInject<ApiClient>()
	val launcher = koinInject<ItemLauncher>()
	val context = LocalContext.current
	var menuItem by remember { mutableStateOf<BaseItemDto?>(null) }
	Column(Modifier.fillMaxSize().background(Color(0xFF08090C))) {
		MainToolbar()
		Text(stringResource(if (mode == PersonalMediaMode.Favorites) R.string.jellyfin_tv_my_list else R.string.jellyfin_tv_watch_history), color = Color.White, fontSize = 30.sp, modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp))
		if (!loaded) EmptyState(stringResource(R.string.loading), "")
		else if (entries.isEmpty()) EmptyState(stringResource(if (mode == PersonalMediaMode.Favorites) R.string.jellyfin_tv_my_list else R.string.jellyfin_tv_watch_history), stringResource(R.string.jellyfin_tv_personal_empty))
		val sections = if (mode == PersonalMediaMode.History) groupedHistory(entries).entries.map { it.key to it.value } else listOf(null to entries)
		LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
			items(sections, key = { it.first?.name ?: "favorites" }) { section ->
				Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
					section.first?.let { group -> Text(historyGroupLabel(group), color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 40.dp)) }
					LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						items(section.second, key = { it.id }) { item -> PersonalMediaCard(item, api, { launcher.launch(BaseItemDtoBaseRowItem(item), null, context) }, { menuItem = item }) }
					}
				}
			}
		}
	}
	menuItem?.let { CinematicItemContextMenu(it) { menuItem = null } }
}

@Composable
private fun historyGroupLabel(group: HistoryDateGroup) = stringResource(when (group) {
	HistoryDateGroup.Today -> R.string.jellyfin_tv_history_today
	HistoryDateGroup.Yesterday -> R.string.jellyfin_tv_history_yesterday
	HistoryDateGroup.ThisWeek -> R.string.jellyfin_tv_history_week
	HistoryDateGroup.Older -> R.string.jellyfin_tv_history_older
})

@Composable
private fun PersonalMediaCard(item: BaseItemDto, api: ApiClient, onClick: () -> Unit, onLongClick: () -> Unit) {
	var held by remember { mutableStateOf(false) }
	Column(Modifier.width(118.dp).onPreviewKeyEvent { event ->
		if (event.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_DPAD_CENTER) false
		else if (event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.repeatCount > 0 || event.nativeKeyEvent.isLongPress)) { held = true; true }
		else if (event.type == KeyEventType.KeyUp && held) { held = false; onLongClick(); true } else false
	}.clickable(onClick = onClick).focusable()) {
					val shape = RoundedCornerShape(11.dp)
					val image = item.itemImages[ImageType.PRIMARY]
					Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(Color(0xFF1A1B20)).border(1.dp, Color.White.copy(.14f), shape)) {
						AsyncImage(modifier = Modifier.fillMaxSize(), url = image?.getUrl(api), blurHash = image?.blurHash, aspectRatio = 2f / 3f, scaleType = ImageView.ScaleType.CENTER_CROP)
					}
					Text(item.name.orEmpty(), color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
	}
}
