package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.coroutines.flow.map
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.android.ext.android.inject

class LibraryChooserFragment : Fragment() {
	private val userViewsRepository by inject<UserViewsRepository>()
	private val itemLauncher by inject<ItemLauncher>()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		val type = CollectionType.valueOf(requireArguments().getString(ARG_COLLECTION_TYPE)!!)
		val librariesFlow = remember(type) {
			userViewsRepository.views.map { views -> views.filter { it.collectionType == type } }
		}
		val libraries by librariesFlow.collectAsState(emptyList())
		JellyfinTheme {
			Column(Modifier.fillMaxSize()) {
				MainToolbar(if (type == CollectionType.MOVIES) MainToolbarActiveButton.Movies else MainToolbarActiveButton.Series)
				Column(
					modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
				) {
					libraries.forEach { library ->
						Button(onClick = { itemLauncher.launchUserView(library) }) { Text(library.name.orEmpty()) }
					}
				}
			}
		}
	}

	companion object { const val ARG_COLLECTION_TYPE = "collectionType" }
}
