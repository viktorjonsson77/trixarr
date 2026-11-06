package org.jellyfin.androidtv.ui.playback.engine

import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.androidtv.data.compat.StreamInfo
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

class PlaybackDiagnosticsTests : FunSpec({
	fun diagnostics(): PlaybackDiagnostics {
		val player = mockk<ExoPlayer>(relaxed = true)
		every { player.videoFormat } returns null
		every { player.audioFormat } returns null
		every { player.playerError } returns null
		every { player.currentTracks } returns Tracks.EMPTY
		return PlaybackDiagnostics(player, Media3PlayerEngine(player))
	}
	test("source DV metadata remains separate and URLs never enter view") {
		val diagnostics = diagnostics()
		diagnostics.source(StreamInfo().apply {
			mediaUrl = "https://private.example/movie?api_key=SECRET"
			mediaSource = mockk<MediaSourceInfo> {
				every { container } returns "mkv"
				every { mediaStreams } returns listOf(mockk<MediaStream>(relaxed = true) {
					every { type } returns MediaStreamType.VIDEO
					every { index } returns 0
					every { codec } returns "hevc"
					every { dvProfile } returns 7
					every { dvLevel } returns 6
				})
			}
		})
		val view = diagnostics.snapshot()
		view shouldContain "DV profile: 7"
		view shouldContain "Selected stream DV profile / level: UNKNOWN"
		view shouldContain "NOT VERIFIED"
		view shouldNotContain "SECRET"
		view shouldNotContain "private.example"
	}
	test("audio initialization observation is not HDMI verification") {
		val diagnostics = diagnostics()
		diagnostics.onAudioTrackInitialized(mockk(), AudioSink.AudioTrackConfig(14, 48000, 252, false, false, 4096))
		val view = diagnostics.snapshot()
		view shouldContain "Output encoding (AudioTrack): 14"
		view shouldContain "HDMI NOT VERIFIED"
	}
	test("decoder failure contains category without private exception message") {
		val diagnostics = diagnostics()
		val time = mockk<AnalyticsListener.EventTime>()
		diagnostics.onVideoCodecError(time, IllegalStateException("SECRET"))
		diagnostics.onAudioSinkError(time, IllegalArgumentException("SECRET"))
		val error = mockk<PlaybackException>()
		every { error.errorCodeName } returns "ERROR_CODE_DECODER_INIT_FAILED"
		diagnostics.onPlayerError(time, error)
		val view = diagnostics.snapshot()
		view shouldContain "ERROR_CODE_DECODER_INIT_FAILED"
		view shouldContain "Audio sink: IllegalArgumentException"
		view shouldNotContain "SECRET"
	}
})
