package org.jellyfin.androidtv.discover

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jellyfin.androidtv.ui.catalog.*
import org.jellyfin.androidtv.seerr.DiscoveryCategory
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeerrDiscoverUiTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun page() = CatalogPage(CatalogScreen.Discover).apply {
        loading=false; focusToken="tab:New"
        items=(1..15).map { CatalogTitle("synthetic:$it",TitleKind.Movie,"Synthetic $it",overview="Synthetic overview",year=2026) }
        rows=listOf(CatalogRow("new_movies","New",items),CatalogRow("new_tv","Series",items))
    }
    @OptIn(ExperimentalTestApi::class)
    @Test fun categoriesHeroRowsAndHardwareFocusRestoration() {
        val page=page(); var opened: CatalogTitle?=null; var visible by mutableStateOf(true);var selected:DiscoveryCategory?=null
        compose.setContent { if (visible) SeerrDiscoverScreen(page,page.items.first(),{selected=it},{},{opened=it},{},{},{}) }
        compose.onNodeWithTag("discover-tab-New").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight);pressKey(Key.DirectionCenter) }
        compose.runOnIdle {assertEquals(DiscoveryCategory.Upcoming,selected)}
        compose.onNodeWithTag("discover-hero-title").assertTextEquals("Synthetic 1")
        compose.onNodeWithTag("discover:new_movies:synthetic:1").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("discover:new_movies:synthetic:1").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {assertEquals("synthetic:1",opened?.key)}
        compose.onNodeWithTag("discover:new_movies:synthetic:1").performKeyInput {pressKey(Key.DirectionRight)}
        compose.onNodeWithTag("discover:new_movies:synthetic:2").assertIsFocused()
        compose.runOnIdle {visible=false}
        compose.runOnIdle {page.restoreFocus();visible=true}
        compose.onNodeWithTag("discover:new_movies:synthetic:2").assertIsFocused()
        compose.onNodeWithTag("discover:new_movies:synthetic:2").performKeyInput {repeat(8){pressKey(Key.DirectionRight)}}
        compose.onNodeWithTag("discover:new_movies:synthetic:10").assertIsFocused()
    }
    @Test fun emptyAndErrorStatesKeepCategoryAndRetryActions() {
        val page=page().apply {rows=emptyList();items=emptyList()};var retries=0
        compose.setContent {SeerrDiscoverScreen(page,null,{},{},{},{},{},{retries++})}
        compose.onNodeWithTag("discover-empty-error").assertExists()
        compose.onNodeWithText("Försök igen").performClick()
        compose.runOnIdle {assertEquals(1,retries);page.error="Syntetiskt nätverksfel"}
        compose.onNodeWithText("Syntetiskt nätverksfel").assertExists()
    }
    @OptIn(ExperimentalTestApi::class)
    @Test fun tvSeasonDialogRequiresSelectionAndExplicitConfirmationAndBackCancels() {
        var sent:Set<Int>?=null;var dismiss=0
        val title=CatalogTitle("series",TitleKind.Series,"Synthetic series",ids=TitleIds(local="synthetic-local-series"), seasons=listOf(TitleSeason("local",9,"Local season")),requestSeasons=listOf(
            TitleSeason("1",1,"Säsong 1",requestable=false,status=TitleStatus.Requested),
            TitleSeason("2",2,"Säsong 2",requestable=true)))
        compose.setContent {SeerrRequestDialog(title,false,null,{dismiss++},{sent=it})}
        compose.onNodeWithTag("request-cancel").assertIsFocused()
        compose.onNodeWithTag("request-confirm").assertDoesNotExist()
        compose.onNodeWithTag("request-season-1").performClick()
        compose.onNodeWithTag("request-confirm").assertDoesNotExist()
        compose.onNodeWithTag("request-season-2").performClick()
        compose.runOnIdle {assertNull(sent)}
        compose.onNodeWithTag("request-confirm").performClick()
        compose.runOnIdle {assertEquals(setOf(2),sent)}
        compose.onNodeWithTag("request-cancel").performKeyInput { pressKey(Key.Back) }
        compose.runOnIdle {assertEquals(1,dismiss)}
    }
    @Test fun movieDialogNeverSubmitsUntilConfirmAndShowsError() {
        var calls=0;var error by mutableStateOf<String?>(null)
        compose.setContent {SeerrRequestDialog(CatalogTitle("movie",TitleKind.Movie,"Synthetic"),false,error,{}, {calls++})}
        compose.runOnIdle {assertEquals(0,calls)}
        compose.onNodeWithTag("request-confirm").performClick()
        compose.runOnIdle {assertEquals(1,calls);error="Du saknar behörighet"}
        compose.onNodeWithTag("request-error").assertTextEquals("Du saknar behörighet")
    }
}
