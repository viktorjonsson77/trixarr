package org.jellyfin.androidtv.catalog

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jellyfin.androidtv.ui.catalog.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SearchNavigationUiTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fieldNavigatesToResultsAndMenuWithoutChangingQuery() {
        var query by mutableStateOf("a")
        val field = FocusRequester()
        val result = FocusRequester()
        var menus = 0
        compose.setContent {
            Column {
                CatalogSearchField(query, field, {}, { query = it }, { menus++ }, { result.requestFocus() })
                TvButton("Resultat", requester = result, onClick = {})
            }
            LaunchedEffect(Unit) { field.requestFocus() }
        }
        compose.onNodeWithTag("catalog-search-field").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Resultat").assertIsFocused()
        compose.runOnUiThread { field.requestFocus() }
        compose.onNodeWithTag("catalog-search-field").performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals(1, menus)
        assertEquals("a", query)
    }

    @Test fun okEnablesTextInputAndSearchActionReturnsToResults() {
        var query by mutableStateOf("")
        val field = FocusRequester()
        val result = FocusRequester()
        compose.setContent {
            Column {
                CatalogSearchField(query, field, {}, { query = it }, {}, { result.requestFocus() })
                TvButton("Resultat", requester = result, onClick = {})
            }
            LaunchedEffect(Unit) { field.requestFocus() }
        }
        compose.onNodeWithTag("catalog-search-field").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("catalog-search-field").performTextInput("a")
        assertEquals("a", query)
        compose.onNodeWithTag("catalog-search-field").performImeAction()
        compose.onNodeWithText("Resultat").assertIsFocused()
        assertEquals("a", query)
    }

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Test fun externallyHiddenKeyboardDoesNotTrapDpadInEditingMode() {
        var query by mutableStateOf("")
        val field = FocusRequester()
        val result = FocusRequester()
        var keyboardVisible = false
        compose.setContent {
            val visible = WindowInsets.isImeVisible
            SideEffect { keyboardVisible = visible }
            Column {
                CatalogSearchField(query, field, {}, { query = it }, {}, { result.requestFocus() })
                TvButton("Resultat", requester = result, onClick = {})
            }
            LaunchedEffect(Unit) { field.requestFocus() }
        }
        compose.onNodeWithTag("catalog-search-field").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("catalog-search-field").performTextInput("a")
        compose.runOnUiThread {
            val input = compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            input.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitUntil { !keyboardVisible }
        compose.onNodeWithTag("catalog-search-field").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Resultat").assertIsFocused()
        assertEquals("a", query)
    }

    @Test fun preImeBackEndsEditingBeforeResultNavigation() {
        var query by mutableStateOf("")
        val field = FocusRequester()
        val result = FocusRequester()
        compose.setContent {
            Column {
                CatalogSearchField(query, field, {}, { query = it }, {}, { result.requestFocus() })
                TvButton("Resultat", requester = result, onClick = {})
            }
            LaunchedEffect(Unit) { field.requestFocus() }
        }
        compose.onNodeWithTag("catalog-search-field").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("catalog-search-field").performTextInput("a")
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            assertTrue(view.dispatchKeyEventPreIme(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK)))
            assertTrue(view.dispatchKeyEventPreIme(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK)))
        }
        compose.onNodeWithTag("catalog-search-field").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Resultat").assertIsFocused()
        assertEquals("a", query)
    }
}
