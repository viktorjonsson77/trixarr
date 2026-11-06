package org.jellyfin.androidtv.ui.playback.engine

import org.jellyfin.playback.core.engine.PlayerEngine
import org.jellyfin.playback.core.engine.PlayerEngineManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer

/** Adapts the existing configured player without changing renderers, surfaces or server policy. */
class Media3PlayerEngine(val player: ExoPlayer) : PlayerEngine<MediaItem> {
	override val id = "Jellyfin Media3"
	private var released = false
	override val error get() = player.playerError?.errorCodeName
	override val positionMs get() = player.currentPosition
	override val state get() = when {
		released -> "RELEASED"
		player.playerError != null -> "ERROR"
		player.playbackState == Player.STATE_BUFFERING -> "BUFFERING"
		player.playbackState == Player.STATE_ENDED -> "ENDED"
		player.isPlaying -> "PLAYING"
		player.playbackState == Player.STATE_READY -> "READY / PAUSED"
		else -> "IDLE"
	}
	override fun supports(request: MediaItem) = request.localConfiguration != null
	override fun prepare(request: MediaItem) { check(!released); player.setMediaItem(request); player.prepare() }
	override fun play() = player.play()
	override fun pause() = player.pause()
	override fun stop() = player.stop()
	override fun seek(positionMs: Long) = player.seekTo(positionMs)
	override fun selectTrack(type: Int, group: Int, track: Int): Boolean {
		if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) return false
		val parameters = player.trackSelectionParameters.buildUpon()
		if (type == C.TRACK_TYPE_TEXT && group == -1) {
			player.trackSelectionParameters = parameters.clearOverridesOfType(type).setTrackTypeDisabled(type, true).build()
			return true
		}
		val selected = player.currentTracks.groups.getOrNull(group) ?: return false
		if (selected.type != type || track !in 0 until selected.length || !selected.isTrackSupported(track)) return false
		player.trackSelectionParameters = parameters.setTrackTypeDisabled(type, false)
			.setOverrideForType(TrackSelectionOverride(selected.mediaTrackGroup, track)).build()
		return true
	}
	override fun release() { if (!released) { released = true; player.release() } }
}
