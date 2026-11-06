package org.jellyfin.androidtv.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.exoplayer.ExoPlayer
import org.jellyfin.androidtv.ui.playback.engine.Media3PlayerEngine
import org.jellyfin.androidtv.ui.playback.engine.PlaybackDiagnostics
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerEngineInstrumentedTest {
	@Test fun packagedFfmpegExtensionLoads() {
		assertTrue(androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable())
		assertNotNull(androidx.media3.decoder.ffmpeg.FfmpegLibrary.getVersion())
	}

	@Test fun realMedia3LifecycleAndDiagnosticSnapshotWithoutServer() {
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		instrumentation.runOnMainSync {
			val player = ExoPlayer.Builder(instrumentation.targetContext).build()
			val engine = Media3PlayerEngine(player)
			val diagnostics = PlaybackDiagnostics(player, engine)
			try {
				assertEquals("IDLE", engine.state)
				engine.pause()
				engine.seek(12345)
				assertEquals(12345L, engine.positionMs)
				assertFalse(engine.selectTrack(1, 99, 0))
				assertTrue(engine.selectTrack(3, -1, 0))
				val snapshot = diagnostics.snapshot()
				assertTrue(snapshot.contains("NOT VERIFIED"))
				assertTrue(snapshot.contains("UNKNOWN"))
				assertTrue(snapshot.contains("Jellyfin Media3"))
				engine.stop()
			} finally {
				diagnostics.release()
				engine.release()
				engine.release()
			}
			assertEquals("RELEASED", engine.state)
		}
	}
}
