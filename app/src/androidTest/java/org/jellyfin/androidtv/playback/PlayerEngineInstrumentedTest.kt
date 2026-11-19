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
    @Test fun delayedOldAudioTrackReleasePreservesReplacementDiagnostics() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val player = ExoPlayer.Builder(instrumentation.targetContext).build()
            val engine = Media3PlayerEngine(player)
            val diagnostics = PlaybackDiagnostics(player, engine)
            val timeline = androidx.media3.common.Timeline.EMPTY
            val time = androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime(0, timeline, 0, null, 0, timeline, 0, null, 0, 0)
            val config = androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig(6, 48000, 252, false, false, 192000)
            try {
                diagnostics.onAudioTrackInitialized(time, config)
                diagnostics.onAudioTrackInitialized(time, config)
                diagnostics.onAudioTrackReleased(time, config)
                assertTrue(diagnostics.snapshot().contains("ACTUAL AUDIO OUTPUT / Channel count: 6"))
                diagnostics.onAudioTrackReleased(time, config)
                assertTrue(diagnostics.snapshot().contains("ACTUAL AUDIO OUTPUT / Channel count: UNKNOWN"))
            } finally { diagnostics.release(); engine.release() }
        }
    }

    @Test fun pinnedMedia3DoesNotTreatP5OrP7AsGenericHevc() {
        for (profile in listOf("05", "07")) {
            val format = androidx.media3.common.Format.Builder().setSampleMimeType("video/dolby-vision")
                .setCodecs("dvhe.$profile.06").build()
            assertNull(androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getAlternativeCodecMimeType(format))
        }
    }

    @Test fun realAdapterSessionCleanupAndDiagnostics() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val engine = Media3PlayerEngine(ExoPlayer.Builder(instrumentation.targetContext).build())
            val owner = org.jellyfin.playback.core.engine.PlaybackSessionOwner()
            val generation = owner.begin("synthetic", 12345)
            owner.bindEngine(generation, engine.id, Runnable { engine.release() })
            val diagnostics = PlaybackDiagnostics(engine.player, engine)
            diagnostics.session(owner)
            try {
                engine.seek(12345)
                assertEquals(12345L, engine.positionMs)
                assertTrue(diagnostics.snapshot().contains(owner.snapshot!!.id))
                owner.finish("RECREATED")
                assertFalse(owner.isCurrent(generation))
                assertEquals("RELEASED", engine.state)
                owner.finish("DUPLICATE")
            } finally { diagnostics.release(); engine.release() }
        }
    }

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
