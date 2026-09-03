package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.filterNotNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.NowPlayingComposable
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.base.button.IconButtonDefaults
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.compose.koinInject

enum class MainToolbarActiveButton {
	User,
	Home,
	Discover,
	Search,
	Movies,
	Series,

	None,
}

enum class MainToolbarDestination { Home, Discover, Movies, Series, Search }

fun mainToolbarDestinations(): List<MainToolbarDestination> = listOf(
	MainToolbarDestination.Home,
	MainToolbarDestination.Discover,
	MainToolbarDestination.Movies,
	MainToolbarDestination.Series,
	MainToolbarDestination.Search,
)

@Composable
fun MainToolbar(
	activeButton: MainToolbarActiveButton = MainToolbarActiveButton.None,
	downFocusRequester: FocusRequester? = null,
) {
	val userRepository = koinInject<UserRepository>()
	val api = koinInject<ApiClient>()

	// Prevent user image to disappear when signing out by skipping null values
	val currentUser by remember { userRepository.currentUser.filterNotNull() }.collectAsState(null)
	val userImage = remember(currentUser) { currentUser?.primaryImage?.getUrl(api) }

	MainToolbar(
		userImage = userImage,
		activeButton = activeButton,
		downFocusRequester = downFocusRequester,
	)
}

@Composable
private fun MainToolbar(
	userImage: String? = null,
	activeButton: MainToolbarActiveButton,
	downFocusRequester: FocusRequester?,
) {
	val focusRequester = remember { FocusRequester() }
	val navigationRepository = koinInject<NavigationRepository>()
	val mediaManager = koinInject<MediaManager>()
	val sessionRepository = koinInject<SessionRepository>()
	val userViewsRepository = koinInject<UserViewsRepository>()
	val userViews by remember { userViewsRepository.views }.collectAsState(emptyList())
	val activity = LocalActivity.current
	val activeButtonColors = ButtonDefaults.colors(
		containerColor = androidx.compose.ui.graphics.Color.Transparent,
		contentColor = androidx.compose.ui.graphics.Color.White,
		focusedContainerColor = androidx.compose.ui.graphics.Color(0xCC201923),
		focusedContentColor = androidx.compose.ui.graphics.Color.White,
	)
	val inactiveButtonColors = ButtonDefaults.colors(
		containerColor = androidx.compose.ui.graphics.Color.Transparent,
		contentColor = androidx.compose.ui.graphics.Color(0xD9FFFFFF),
		focusedContainerColor = androidx.compose.ui.graphics.Color(0xCC201923),
		focusedContentColor = androidx.compose.ui.graphics.Color.White,
	)
	fun openLibrary(collectionType: CollectionType) {
		val matching = matchingLibraries(userViews, collectionType)
		when (matching.size) {
			0 -> Unit
			1 -> navigationRepository.navigate(Destinations.cinematicLibrary(matching.single(), collectionType))
			else -> navigationRepository.navigate(Destinations.libraryChooser(collectionType))
		}
	}

	Toolbar(
		modifier = Modifier
			.onPreviewKeyEvent { event ->
				if (downFocusRequester != null && event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
					downFocusRequester.requestFocus()
					true
				} else false
			}
			.focusRestorer(focusRequester)
			.focusGroup()
			.then(if (downFocusRequester != null) Modifier.focusProperties { down = downFocusRequester } else Modifier),
		start = {
			ToolbarButtons(modifier = Modifier.then(if (downFocusRequester != null) Modifier.focusProperties { down = downFocusRequester } else Modifier)) {
				Logo()
				NowPlayingComposable(
					onFocusableChange = {},
				)
			}
		},
		center = {
			ToolbarButtons(
				modifier = Modifier
					.focusRequester(focusRequester)
					.then(if (downFocusRequester != null) Modifier.focusProperties { down = downFocusRequester } else Modifier)
			) {
				ProvideTextStyle(JellyfinTheme.typography.default.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
					ToolbarDestinationButton(
						active = activeButton == MainToolbarActiveButton.Home,
						onClick = {
							if (activeButton != MainToolbarActiveButton.Home) {
								navigationRepository.navigate(
									Destinations.home,
									replace = true,
								)
							}
						},
						colors = if (activeButton == MainToolbarActiveButton.Home) activeButtonColors else inactiveButtonColors,
						content = { Text(stringResource(R.string.lbl_home)) }
					)
					ToolbarDestinationButton(
						active = activeButton == MainToolbarActiveButton.Discover,
						onClick = {
							if (activeButton != MainToolbarActiveButton.Discover) {
								navigationRepository.navigate(Destinations.discover)
							}
						},
						colors = if (activeButton == MainToolbarActiveButton.Discover) activeButtonColors else inactiveButtonColors,
						content = { Text(stringResource(R.string.jellyfin_tv_discover)) }
					)
					ToolbarDestinationButton(
						active = activeButton == MainToolbarActiveButton.Movies,
						onClick = { openLibrary(CollectionType.MOVIES) },
						colors = if (activeButton == MainToolbarActiveButton.Movies) activeButtonColors else inactiveButtonColors,
						content = { Text(stringResource(R.string.lbl_movies)) },
					)
					ToolbarDestinationButton(
						active = activeButton == MainToolbarActiveButton.Series,
						onClick = { openLibrary(CollectionType.TVSHOWS) },
						colors = if (activeButton == MainToolbarActiveButton.Series) activeButtonColors else inactiveButtonColors,
						content = { Text(stringResource(R.string.lbl_series)) },
					)
					ToolbarDestinationButton(
						active = activeButton == MainToolbarActiveButton.Search,
						onClick = {
							if (activeButton != MainToolbarActiveButton.Search) navigationRepository.navigate(Destinations.search())
						},
						colors = if (activeButton == MainToolbarActiveButton.Search) activeButtonColors else inactiveButtonColors,
						content = { Text(stringResource(R.string.lbl_search)) },
					)
				}
			}
		},
		end = {
			ToolbarButtons(modifier = Modifier.then(if (downFocusRequester != null) Modifier.focusProperties { down = downFocusRequester } else Modifier)) {
				val userImagePainter = rememberAsyncImagePainter(userImage)
				val userImageState by userImagePainter.state.collectAsState()
				val userImageVisible = userImageState is AsyncImagePainter.State.Success

				IconButton(
					onClick = {
						if (activeButton != MainToolbarActiveButton.User) {
							mediaManager.clearAudioQueue()
							sessionRepository.destroyCurrentSession()

							// Open login activity
							activity?.startActivity(ActivityDestinations.startup(activity))
							activity?.finishAfterTransition()
						}
					},
					colors = if (activeButton == MainToolbarActiveButton.User) activeButtonColors else ButtonDefaults.colors(),
					contentPadding = if (userImageVisible) PaddingValues(3.dp) else IconButtonDefaults.ContentPadding,
				) {
					if (!userImageVisible) {
						Icon(
							imageVector = ImageVector.vectorResource(R.drawable.ic_user),
							contentDescription = stringResource(R.string.lbl_switch_user),
						)
					} else {
						Image(
							painter = userImagePainter,
							contentDescription = stringResource(R.string.lbl_switch_user),
							contentScale = ContentScale.Crop,
							modifier = Modifier
								.aspectRatio(1f)
								.clip(IconButtonDefaults.Shape)
						)
					}
				}
				IconButton(
					onClick = {
						activity?.startActivity(ActivityDestinations.userPreferences(activity))
					},
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_settings),
						contentDescription = stringResource(R.string.lbl_settings),
					)
				}

				ToolbarClock()
			}
		}
	)
}

fun matchingLibraries(views: Collection<BaseItemDto>, collectionType: CollectionType): List<BaseItemDto> =
	views.filter { it.collectionType == collectionType }

@Composable
private fun ToolbarDestinationButton(
	active: Boolean,
	onClick: () -> Unit,
	colors: org.jellyfin.androidtv.ui.base.button.ButtonColors,
	content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
	Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
		Button(onClick = onClick, colors = colors, content = content)
		Box(
			Modifier
				.width(22.dp)
				.height(2.dp)
				.background(if (active) androidx.compose.ui.graphics.Color(0xFF8B5CF6) else androidx.compose.ui.graphics.Color.Transparent)
		)
	}
}
