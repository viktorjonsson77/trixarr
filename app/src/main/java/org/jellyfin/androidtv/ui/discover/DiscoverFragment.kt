package org.jellyfin.androidtv.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.cinematic.CinematicScaffold
import org.jellyfin.androidtv.ui.cinematic.EmptyState
import org.jellyfin.androidtv.ui.cinematic.FocusableTvButton
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.browsing.PersonalMediaMode
import org.koin.compose.koinInject
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton

class DiscoverFragment : Fragment() {
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		JellyfinTheme {
			val navigation = koinInject<NavigationRepository>()
			val personalFocus = remember { FocusRequester() }
			LaunchedEffect(Unit) { personalFocus.requestFocus() }
			CinematicScaffold {
				Column(androidx.compose.ui.Modifier.fillMaxSize()) {
					MainToolbar(MainToolbarActiveButton.Discover, downFocusRequester = personalFocus)
					EmptyState(
						title = getString(R.string.jellyfin_tv_discover),
						description = getString(R.string.jellyfin_tv_seerr_not_connected),
					)
					Row(androidx.compose.ui.Modifier.padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
						FocusableTvButton(onClick = { navigation.navigate(Destinations.personalMedia(PersonalMediaMode.Favorites)) }, modifier = androidx.compose.ui.Modifier.focusRequester(personalFocus)) { Text(getString(R.string.jellyfin_tv_my_list)) }
						FocusableTvButton(onClick = { navigation.navigate(Destinations.personalMedia(PersonalMediaMode.History)) }) { Text(getString(R.string.jellyfin_tv_watch_history)) }
					}
				}
			}
		}
	}
}
