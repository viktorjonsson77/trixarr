package org.jellyfin.androidtv.ui.playback

import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.androidtv.ui.playback.engine.Media3PlayerEngine
import org.jellyfin.androidtv.ui.playback.engine.PlaybackDiagnostics
import org.jellyfin.playback.core.engine.PlaybackSessionOwner

/** Real Media3 exception/diagnostic path; codec and AudioTrack hardware are not simulated here. */
class AudioSinkDiagnosticTests : FunSpec({
    listOf(false, true).forEach { recoverable ->
        test("DEAD_OBJECT recoverable=$recoverable is recorded without turning a sink callback into a terminal player error") {
            val player = mockk<ExoPlayer>(relaxed = true)
            every { player.videoFormat } returns null
            every { player.audioFormat } returns null
            every { player.playerError } returns null
            every { player.currentTracks } returns Tracks.EMPTY
            every { player.currentPosition } returns if (recoverable) 342L else 0L
            every { player.playbackState } returns if (recoverable) Player.STATE_READY else Player.STATE_BUFFERING
            val engine = Media3PlayerEngine(player)
            val owner = PlaybackSessionOwner()
            owner.begin("resident-evil-fixture", 0)
            val diagnostics = PlaybackDiagnostics(player, engine)
            diagnostics.session(owner)
            val format = Format.Builder().setSampleMimeType("audio/eac3").setChannelCount(6).setSampleRate(48000).build()
            diagnostics.onAudioSinkError(mockk<AnalyticsListener.EventTime>(), AudioSink.WriteException(-6, format, recoverable))
            val snapshot = diagnostics.snapshot()
            snapshot.contains("ERRORS / Audio sink: WriteException") shouldBe true
            snapshot.contains("ERRORS / Audio sink code: -6") shouldBe true
            snapshot.contains("ERRORS / Audio sink recoverable (Media3): $recoverable") shouldBe true
            snapshot.contains("ERRORS / Playback: UNKNOWN") shouldBe true
            owner.snapshot!!.terminalReason shouldBe null
            verify(exactly = 0) { player.stop(); player.release() }
        }
    }
})
