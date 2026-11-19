package org.jellyfin.androidtv.ui.preference

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.BackHandler
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import org.jellyfin.androidtv.ui.catalog.CatalogMenu
import org.jellyfin.androidtv.ui.catalog.CatalogExitConfirmation
import org.jellyfin.androidtv.ui.catalog.CatalogScreen
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.screen.UserPreferencesScreen

class PreferencesActivity : FragmentActivity(R.layout.fragment_content_view) {
    private var catalogMenu by mutableStateOf(false)
    private var menuRestore by mutableIntStateOf(0)
    private var confirmExit by mutableStateOf(false)
    private var contentFocus: View? = null
    private var destination: CatalogScreen? = null


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val screen = intent.extras?.getString(EXTRA_SCREEN) ?: UserPreferencesScreen::class.qualifiedName
		val screenArgs = intent.extras?.getBundle(EXTRA_SCREEN_ARGS) ?: bundleOf()

		supportFragmentManager
			.beginTransaction()
			.replace(R.id.content_view, PreferencesFragment().apply {
				// Set screen
				arguments = bundleOf(
					PreferencesFragment.EXTRA_SCREEN to screen,
					PreferencesFragment.EXTRA_SCREEN_ARGS to screenArgs
				)
			}, FRAGMENT_TAG)
			.commit()
        attachCatalogMenu()
	}

    private fun attachCatalogMenu() {
        if (!intent.getBooleanExtra("catalog-menu", false)) return
        addContentView(ComposeView(this).apply {
            setContent {
                JellyfinTheme {
                    BackHandler(enabled = !confirmExit) {
                        val preferences = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)
                        val children = preferences?.childFragmentManager
                        val modal = children?.fragments?.any {
                            it.isVisible && it.javaClass.simpleName.contains("Dialog")
                        } == true
                        if (modal) children?.popBackStackImmediate()
                        else if (catalogMenu) confirmExit = true
                        else {
                            contentFocus = currentFocus
                            destination = null
                            catalogMenu = true
                        }
                    }
                    CatalogMenu(catalogMenu, null,
                        focusEpoch = menuRestore,
                        onClose = { catalogMenu = false },
                        onClosed = {
                            val section = destination
                            destination = null
                            if (section == null) contentFocus?.requestFocus()
                            else {
                                setResult(RESULT_OK, Intent().putExtra("catalog-section", section.name))
                                finish()
                            }
                        },
                        onSelect = { destination = it; catalogMenu = false },
                    )
                    if (confirmExit) CatalogExitConfirmation(
                        onStay = { confirmExit = false; menuRestore++ },
                        onExit = { finishAffinity() },
                    )
                }
            }
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

	companion object {
		const val EXTRA_SCREEN = "screen"
		const val EXTRA_SCREEN_ARGS = "screen_args"
		const val FRAGMENT_TAG = "PreferencesActivity"
	}
}

