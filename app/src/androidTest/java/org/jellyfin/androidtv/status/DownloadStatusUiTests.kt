package org.jellyfin.androidtv.status

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jellyfin.androidtv.ui.catalog.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DownloadStatusUiTests {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun status(progress:Double=.68)=DownloadStatus(state="DOWNLOADING",progress=progress,bytesPerSecond=32_400_000.0,remainingBytes=200_000_000.0,observedAt=Instant.now().toString())
 @OptIn(ExperimentalTestApi::class)
 @Test fun updatingEpisodeProgressPreservesFocusAndBack() {
  val title=CatalogTitle("synthetic",TitleKind.Series,"Synthetic show",requestIds=setOf(1))
  var value by mutableStateOf(RequestDownload(1,"tv",tmdbId=10,status=status().copy(state="PARTIALLY_AVAILABLE",progress=null,bytesPerSecond=null,remainingBytes=null),
    seasons=listOf(SeasonDownload(2,6,8,"PARTIALLY_AVAILABLE")),episodes=listOf(EpisodeDownload(2,7,status()),EpisodeDownload(2,8,status().copy(state="WAITING_FOR_RELEASE",progress=null)))))
  var back=0
  compose.setContent {DownloadStatusDialog(title,listOf(value),null,{back++},{})}
  compose.onNodeWithText("Säsong 2: 6/8 avsnitt tillgängliga").assertExists()
  compose.onNodeWithTag("download-episode-2-7").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus){it()}
  compose.onNodeWithTag("download-episode-2-7").assertIsFocused()
  compose.runOnIdle {value=value.copy(episodes=listOf(EpisodeDownload(2,7,status(.74)),value.episodes[1]))}
  compose.onNodeWithTag("download-episode-2-7").assertIsFocused().performKeyInput {pressKey(Key.DirectionCenter)}
  compose.onNodeWithText("32,4 MB/s").assertExists()
  compose.onNodeWithTag("download-episode-2-7").performKeyInput {pressKey(Key.Back)}
  compose.runOnIdle {assertEquals(1,back)}
 }
 @Test fun insecureUrlCannotBeSaved() {
  var saved:String?=null
  compose.setContent {BridgeEndpointDialog("http://private.invalid",{}, {saved=it})}
  compose.onNodeWithText("Spara").performClick()
  compose.onNodeWithText("En giltig HTTPS-adress krävs. Certifikatet måste vara betrott av enheten.").assertExists()
  compose.runOnIdle {assertNull(saved)}
  compose.onNodeWithTag("bridge-url").performTextReplacement("https://private.invalid")
  compose.onNodeWithText("Spara").performClick()
  compose.runOnIdle {assertEquals("https://private.invalid",saved)}
 }
 @Test fun completedDownloadDoesNotExposePlayUntilJellyfinVerified() {
  val title=CatalogTitle("synthetic",TitleKind.Movie,"Synthetic movie",playbackId="synthetic")
  var value by mutableStateOf(RequestDownload(1,"movie",status=status(1.0).copy(state="DOWNLOAD_COMPLETE")))
  compose.setContent {DownloadStatusDialog(title,listOf(value),null,{}, {}, {})}
  compose.onNodeWithText("Tillgänglig – Spela").assertDoesNotExist()
  compose.runOnIdle {value=value.copy(status=status(1.0).copy(state="AVAILABLE",jellyfinVerified=true))}
  compose.onNodeWithText("Tillgänglig – Spela").assertExists()
 }
}
