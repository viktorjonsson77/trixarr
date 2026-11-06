package org.jellyfin.playback.core.backend

import org.jellyfin.playback.core.mediastream.MediaStream
import org.jellyfin.playback.core.model.PositionInfo
import org.jellyfin.playback.core.queue.QueueEntry
import org.jellyfin.playback.core.support.PlaySupportReport
import org.jellyfin.playback.core.ui.PlayerSubtitleView
import org.jellyfin.playback.core.ui.PlayerSurfaceView
import org.jellyfin.playback.core.mediastream.mediaStream
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration

/**
 * Implementation for a media player backend. A backend is unaware of queues and can only play or
 * preload items.
 */
interface PlayerBackend : org.jellyfin.playback.core.engine.PlayerEngine<QueueEntry> {
	override val positionMs get() = getPositionInfo().active.inWholeMilliseconds
	override fun prepare(request: QueueEntry) = prepareItem(request)
	override fun supports(request: QueueEntry): Boolean = request.mediaStream?.let { supportsStream(it).canPlay } == true
	override fun seek(positionMs: Long) = seekTo(positionMs.milliseconds)

	// Testing
	fun supportsStream(stream: MediaStream): PlaySupportReport

	// UI
	fun setSurfaceView(surfaceView: PlayerSurfaceView?)
	fun setSubtitleView(surfaceView: PlayerSubtitleView?)

	// Data retrieval

	fun setListener(eventListener: PlayerBackendEventListener?)
	fun getPositionInfo(): PositionInfo

	// Mutation

	fun prepareItem(item: QueueEntry)
	fun playItem(item: QueueEntry)

	override fun play()
	override fun pause()
	override fun stop()

	fun seekTo(position: Duration)
	fun setScrubbing(scrubbing: Boolean)

	fun setSpeed(speed: Float)
}

