package org.jellyfin.androidtv.ui.itemdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinematic.EmptyState
import org.jellyfin.androidtv.ui.cinematic.FocusableTvButton
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.CinematicItemContextMenu
import org.jellyfin.androidtv.ui.home.episodeLabel
import org.jellyfin.androidtv.ui.home.playbackProgress
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseItemPersonBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.androidtv.util.sdk.TrailerUtils
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import java.util.Locale
import java.util.UUID

enum class DetailAvailability { InLibrary, Requestable, Requested, Processing, PartiallyAvailable, Unavailable }

data class CinematicDetailState(
	val item: BaseItemDto? = null,
	val related: List<BaseItemDto> = emptyList(),
	val similar: List<BaseItemDto> = emptyList(),
	val availability: DetailAvailability = DetailAvailability.InLibrary,
	val loading: Boolean = true,
)

fun showVersionSelector(mediaSources: List<MediaSourceInfo>?): Boolean = mediaSources.orEmpty().size > 1

fun versionLabel(source: MediaSourceInfo): String = listOfNotNull(
	source.name?.takeIf(String::isNotBlank),
	source.container?.uppercase(Locale.ROOT)?.takeIf(String::isNotBlank),
).distinct().joinToString(" · ").ifBlank { "Version" }

fun favoriteStateAfterMutation(original: Boolean, requested: Boolean, succeeded: Boolean): Boolean = if (succeeded) requested else original

class CinematicDetailsFragment : Fragment() {
	private val api by inject<ApiClient>()
	private val background by inject<BackgroundService>()
	private val state = MutableStateFlow(CinematicDetailState())

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		val current by state.collectAsState()
		JellyfinTheme { CinematicDetailsScreen(current) }
	}

	override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val id = UUID.fromString(requireArguments().getString(ARG_ITEM_ID))
		lifecycleScope.launch {
			val loaded = runCatching { withContext(Dispatchers.IO) {
				val item = api.userLibraryApi.getItem(id).content
				val related = when (item.type) {
					BaseItemKind.SEASON, BaseItemKind.BOX_SET -> api.itemsApi.getItems(parentId = id, fields = ItemRepository.itemFields, imageTypeLimit = 1).content.items
					BaseItemKind.PERSON -> api.itemsApi.getItems(personIds = setOf(id), recursive = true, includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES), fields = ItemRepository.itemFields, imageTypeLimit = 1).content.items
					else -> emptyList()
				}
				val similar = if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE) {
					runCatching { api.libraryApi.getSimilarItems(GetSimilarItemsRequest(itemId = id, fields = ItemRepository.itemFields, limit = 18)).content.items }.getOrDefault(emptyList())
				} else emptyList()
				CinematicDetailState(item, related, similar, loading = false)
			} }.getOrElse { CinematicDetailState(loading = false) }
			state.value = loaded
			loaded.item?.let(background::setBackground)
		}
	}

	companion object { const val ARG_ITEM_ID = "ItemId" }
}

@Composable
private fun CinematicDetailsScreen(state: CinematicDetailState) {
	val item = state.item
	if (state.loading) { EmptyState(stringResource(R.string.loading), ""); return }
	if (item == null) { EmptyState(stringResource(R.string.jellyfin_tv_search_error), stringResource(R.string.jellyfin_tv_search_error_description)); return }
	val context = LocalContext.current
	val playback = koinInject<PlaybackHelper>()
	val playbackLauncher = koinInject<PlaybackLauncher>()
	val api = koinInject<ApiClient>()
	val mutation = koinInject<ItemMutationRepository>()
	val launcher = koinInject<ItemLauncher>()
	val scope = rememberCoroutineScope()
	var contextItem by remember { mutableStateOf<BaseItemDto?>(null) }
	var showVersions by remember { mutableStateOf(false) }
	var isFavorite by remember(item.id) { mutableStateOf(item.userData?.isFavorite == true) }
	val hasTrailer = remember(item) { TrailerUtils.hasPlayableTrailers(context, item) }
	LazyColumn(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(.94f), Color.Black.copy(.45f), Color.Transparent))), verticalArrangement = Arrangement.spacedBy(14.dp)) {
		item {
			Column(Modifier.padding(start = 48.dp, top = 58.dp, end = 48.dp).width(880.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
				if (item.type == BaseItemKind.PERSON) {
					val portrait = item.itemImages[ImageType.PRIMARY]
					AsyncImage(
						url = portrait?.getUrl(api),
						blurHash = portrait?.blurHash,
						aspectRatio = 2f / 3f,
						scaleType = ImageView.ScaleType.CENTER_CROP,
						modifier = Modifier.width(150.dp).height(225.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF191A1F)),
					)
				}
				Text(item.name.orEmpty(), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 2)
				val facts = detailFacts(item)
				if (facts.isNotEmpty()) Text(facts, color = Color.White.copy(.82f), fontSize = 14.sp)
				item.overview?.takeIf(String::isNotBlank)?.let { Text(it, color = Color.White.copy(.88f), fontSize = 15.sp, maxLines = 4, overflow = TextOverflow.Ellipsis) }
				Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					if (item.type in setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE)) {
						val resume = (item.userData?.playbackPositionTicks ?: 0L) > 0
						FocusableTvButton(onClick = { playback.retrieveAndPlay(item.id, false, if (resume) (item.userData?.playbackPositionTicks ?: 0L) / 10_000L else 0L, context) }) { Text(stringResource(if (resume) R.string.jellyfin_tv_resume else R.string.lbl_play)) }
						if (resume) FocusableTvButton(onClick = { playback.retrieveAndPlay(item.id, false, 0L, context) }) { Text(stringResource(R.string.jellyfin_tv_play_from_beginning)) }
					}
					if (hasTrailer) FocusableTvButton(onClick = {
						scope.launch {
							if ((item.localTrailerCount ?: 0) > 0) {
								playbackLauncher.launch(context, api.userLibraryApi.getLocalTrailers(item.id).content)
							} else TrailerUtils.getExternalTrailerIntent(context, item)?.let(context::startActivity)
						}
					}) { Text(stringResource(R.string.lbl_play_trailers)) }
					if (item.type == BaseItemKind.MOVIE && showVersionSelector(item.mediaSources)) {
						FocusableTvButton(onClick = { showVersions = true }) { Text(stringResource(R.string.select_version)) }
					}
					FocusableTvButton(onClick = {
						val original = isFavorite
						val requested = !original
						isFavorite = requested
						scope.launch {
							val succeeded = runCatching { mutation.setFavorite(item.id, requested) }.isSuccess
							isFavorite = favoriteStateAfterMutation(original, requested, succeeded)
						}
					}) { Text(stringResource(if (isFavorite) R.string.jellyfin_tv_in_my_list else R.string.jellyfin_tv_add_to_my_list)) }
					if (item.type !in setOf(BaseItemKind.PERSON, BaseItemKind.BOX_SET)) FocusableTvButton(onClick = { scope.launch { mutation.setPlayed(item.id, item.userData?.played != true) } }) { Text(stringResource(if (item.userData?.played == true) R.string.lbl_mark_unplayed else R.string.lbl_mark_played)) }
				}
			}
		}
		item.people?.take(10)?.takeIf { it.isNotEmpty() }?.let { people ->
			item {
				Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
					Text(stringResource(R.string.lbl_cast_crew), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 48.dp))
					LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						items(people, key = { it.id }) { person ->
							PersonCard(person.name.orEmpty(), person.role, person.primaryImage?.getUrl(api)) {
								launcher.launch(BaseItemPersonBaseRowItem(person), null, context)
							}
						}
					}
				}
			}
		}
		if (state.related.isNotEmpty()) item { DetailMediaRow(if (item.type == BaseItemKind.SEASON) stringResource(R.string.lbl_episodes) else stringResource(R.string.lbl_items), state.related, launcher, { contextItem = it }) }
		if (state.similar.isNotEmpty()) item { DetailMediaRow(stringResource(R.string.jellyfin_tv_similar), state.similar, launcher, { contextItem = it }) }
		item { Spacer(Modifier.height(30.dp)) }
	}
	contextItem?.let { CinematicItemContextMenu(it) { contextItem = null } }
	if (showVersions) VersionSelectorDialog(
		item = item,
		onDismiss = { showVersions = false },
		onSelected = { source ->
			showVersions = false
			playbackLauncher.launch(context, listOf(item), mediaSourceId = source.id)
		},
	)
}

@Composable
private fun PersonCard(name: String, role: String?, imageUrl: String?, onClick: () -> Unit) {
	var focused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(11.dp)
	Column(
		Modifier.width(96.dp).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable(),
		verticalArrangement = Arrangement.spacedBy(5.dp),
	) {
		AsyncImage(
			url = imageUrl,
			aspectRatio = 2f / 3f,
			scaleType = ImageView.ScaleType.CENTER_CROP,
			modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(Color(0xFF191A1F))
				.border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFF19C7F3) else Color.White.copy(.12f), shape),
		)
		Text(name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
		role?.takeIf(String::isNotBlank)?.let { Text(it, color = Color.White.copy(.62f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
	}
}

@Composable
private fun VersionSelectorDialog(item: BaseItemDto, onDismiss: () -> Unit, onSelected: (MediaSourceInfo) -> Unit) {
	Dialog(onDismissRequest = onDismiss) {
		Column(
			Modifier.width(420.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF17181D)).padding(22.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(stringResource(R.string.select_version), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
			item.mediaSources.orEmpty().forEach { source ->
				FocusableTvButton(onClick = { onSelected(source) }, modifier = Modifier.fillMaxWidth()) {
					Text(versionLabel(source), color = Color.White)
				}
			}
			FocusableTvButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
		}
	}
}

fun detailFacts(item: BaseItemDto): String = listOfNotNull(
	item.episodeLabel(), item.productionYear?.toString(), item.officialRating,
	item.runTimeTicks?.div(600_000_000L)?.let { "$it min" },
	item.communityRating?.let { String.format(Locale.ROOT, "★ %.1f", it) },
	item.genres?.take(3)?.joinToString(" · ")?.takeIf(String::isNotBlank),
).joinToString("   ")

@Composable
private fun DetailTextSection(title: String, body: String) = Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
	Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
	Text(body, color = Color.White.copy(.78f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun DetailMediaRow(title: String, entries: List<BaseItemDto>, launcher: ItemLauncher, onLongClick: (BaseItemDto) -> Unit) {
	val context = LocalContext.current
	Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
		Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 48.dp))
		LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			items(entries, key = { it.id }) { entry -> DetailCard(entry, { launcher.launch(BaseItemDtoBaseRowItem(entry), null, context) }, { onLongClick(entry) }) }
		}
	}
}

@Composable
private fun DetailCard(item: BaseItemDto, onClick: () -> Unit, onLongClick: () -> Unit) {
	val api = koinInject<ApiClient>()
	var focused by remember { mutableStateOf(false) }
	var held by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "DetailCardFocus")
	val shape = RoundedCornerShape(11.dp)
	Column(Modifier.width(if (item.type == BaseItemKind.EPISODE) 190.dp else 116.dp).scale(scale).onFocusChanged { focused = it.isFocused }
		.onPreviewKeyEvent { event ->
			if (event.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_DPAD_CENTER) false
			else if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && (event.nativeKeyEvent.repeatCount > 0 || event.nativeKeyEvent.isLongPress)) { held = true; true }
			else if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyUp && held) { held = false; onLongClick(); true } else false
		}.clickable(onClick = onClick).focusable(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
		val landscape = item.type == BaseItemKind.EPISODE
		Box(Modifier.fillMaxWidth().aspectRatio(if (landscape) 16f / 9f else 2f / 3f).clip(shape).background(Color(0xFF191A1F)).border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFF9B4DFF) else Color.White.copy(.12f), shape)) {
			val image = item.itemImages[ImageType.PRIMARY]
			AsyncImage(modifier = Modifier.fillMaxSize(), url = image?.getUrl(api), blurHash = image?.blurHash, aspectRatio = if (landscape) 16f / 9f else 2f / 3f, scaleType = ImageView.ScaleType.CENTER_CROP)
			val progress = item.playbackProgress()
			if (progress > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(3.dp).background(Color(0xFF19C7F3)))
		}
		Text(item.episodeLabel() ?: item.name.orEmpty(), color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
	}
}
