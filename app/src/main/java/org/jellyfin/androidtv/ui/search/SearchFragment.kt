package org.jellyfin.androidtv.ui.search

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinematic.EmptyState
import org.jellyfin.androidtv.ui.cinematic.ErrorState
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.home.CinematicItemContextMenu
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.jellyfin.androidtv.ui.search.composable.SearchVoiceInput
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.speech.rememberSpeechRecognizerAvailability
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

class SearchFragment : Fragment() {
	companion object { const val EXTRA_QUERY = "query" }

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		JellyfinTheme {
			val viewModel = koinViewModel<SearchViewModel>()
			val state by viewModel.searchStateFlow.collectAsState()
			var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
			val inputFocus = remember { FocusRequester() }
			val speechAvailable = rememberSpeechRecognizerAvailability()
			LaunchedEffect(Unit) {
				withFrameNanos { }
				val initial = arguments?.getString(EXTRA_QUERY).orEmpty()
				if (initial.isBlank()) inputFocus.requestFocus() else {
					query = query.copy(text = initial)
					viewModel.searchImmediately(initial)
				}
			}
			CinematicSearchScreen(state, query.text, inputFocus, speechAvailable,
				onQueryChanged = { query = query.copy(text = it); viewModel.searchDebounced(it) },
				onSubmit = { viewModel.searchImmediately(query.text) })
		}
	}
}

@Composable
private fun CinematicSearchScreen(state: CinematicSearchState, query: String, inputFocus: FocusRequester, speechAvailable: Boolean, onQueryChanged: (String) -> Unit, onSubmit: () -> Unit) {
	val launcher = koinInject<ItemLauncher>()
	val background = koinInject<BackgroundService>()
	val context = LocalContext.current
	var focusedItem by remember { mutableStateOf<BaseItemDto?>(null) }
	var contextItem by remember { mutableStateOf<BaseItemDto?>(null) }
	LaunchedEffect(focusedItem?.id) { focusedItem?.let(background::setBackground) }
	LazyColumn(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(.92f), Color.Transparent))), verticalArrangement = Arrangement.spacedBy(12.dp)) {
		item { MainToolbar(MainToolbarActiveButton.Search, downFocusRequester = inputFocus) }
		item {
			Column(Modifier.padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(stringResource(R.string.lbl_search), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
				Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					if (speechAvailable) SearchVoiceInput(onQueryChange = onQueryChanged, onQuerySubmit = onSubmit)
					SearchTextInput(query, onQueryChanged, onSubmit, Modifier.weight(1f).focusRequester(inputFocus))
				}
			}
		}
		when (state) {
			CinematicSearchState.EmptyQuery -> item { EmptyState(stringResource(R.string.jellyfin_tv_search_start), stringResource(R.string.jellyfin_tv_search_start_description)) }
			is CinematicSearchState.Loading -> item { EmptyState(stringResource(R.string.loading), state.query) }
			is CinematicSearchState.NoResults -> item { EmptyState(stringResource(R.string.jellyfin_tv_search_empty), stringResource(R.string.jellyfin_tv_search_empty_description, state.query)) }
			is CinematicSearchState.Error -> item { ErrorState(stringResource(R.string.jellyfin_tv_search_error), stringResource(R.string.jellyfin_tv_search_error_description), onSubmit, stringResource(R.string.jellyfin_tv_retry)) }
			is CinematicSearchState.Results -> items(state.groups, key = { it.labelRes }) { group ->
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					Text(stringResource(group.labelRes), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 40.dp))
					LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						items(group.items.toList(), key = { it.id }) { item -> SearchResultCard(item, { focusedItem = item }, { launcher.launch(BaseItemDtoBaseRowItem(item), null, context) }, { contextItem = item }) }
					}
				}
			}
		}
	}
	contextItem?.let { CinematicItemContextMenu(it) { contextItem = null } }
}

@Composable
private fun SearchResultCard(item: BaseItemDto, onFocus: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
	val api = koinInject<ApiClient>()
	var focused by remember { mutableStateOf(false) }
	var longPress by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "SearchCardFocus")
	val shape = RoundedCornerShape(11.dp)
	Column(Modifier.width(118.dp).scale(scale).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
		.onPreviewKeyEvent { event ->
			if (event.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_DPAD_CENTER) false
			else if (event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.repeatCount > 0 || event.nativeKeyEvent.isLongPress)) { longPress = true; true }
			else if (event.type == KeyEventType.KeyUp && longPress) { longPress = false; onLongClick(); true }
			else false
		}.clickable(onClick = onClick).focusable(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
		Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(Color(0xFF1B1C21)).border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFF9B4DFF) else Color.White.copy(.12f), shape)) {
			val image = item.itemImages[ImageType.PRIMARY]
			AsyncImage(url = image?.getUrl(api), blurHash = image?.blurHash, aspectRatio = 2f / 3f, scaleType = ImageView.ScaleType.CENTER_CROP, modifier = Modifier.fillMaxSize())
		}
		Text(item.name.orEmpty(), color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
	}
}
