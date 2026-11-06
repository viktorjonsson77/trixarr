package org.jellyfin.playback.jellyfin.playsession

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.playback.core.engine.OrderedPlaybackReporter
import org.jellyfin.playback.core.engine.ReportKind
import org.jellyfin.playback.core.engine.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.playback.core.mediastream.MediaConversionMethod
import org.jellyfin.playback.core.mediastream.mediaStream
import org.jellyfin.playback.core.model.PlayState
import org.jellyfin.playback.core.model.RepeatMode
import org.jellyfin.playback.core.plugin.PlayerService
import org.jellyfin.playback.core.queue.queue
import org.jellyfin.playback.jellyfin.queue.baseItem
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.QueueItem
import org.jellyfin.sdk.model.extensions.inWholeTicks
import timber.log.Timber
import kotlin.math.roundToInt
import org.jellyfin.sdk.model.api.RepeatMode as SdkRepeatMode

class PlaySessionService(
	private val api: ApiClient,
) : PlayerService() {
	private val reporter = OrderedPlaybackReporter(CoroutineScope(SupervisorJob() + Dispatchers.IO))
	private var streamId: String? = null
	private var stopInfo: PlaybackStopInfo? = null
	override fun onRelease() {
		val owner = manager.sessionOwner
		owner.finish("RELEASED")
		val session = owner.snapshot
		val info = stopInfo
		if (session != null && info != null) reporter.enqueue(owner, session, ReportKind.STOP) { api.playStateApi.reportPlaybackStopped(info.copy(positionTicks = session.positionMs * 10000)) }
		reporter.close()
	}

	override suspend fun onInitialize() {
		state.playState.onEach { playState ->
			when (playState) {
				PlayState.PLAYING -> sendStreamStart()
				PlayState.STOPPED -> sendStreamStop()
				PlayState.PAUSED -> sendStreamUpdate()
				PlayState.ERROR -> sendStreamStop()
			}
		}.launchIn(coroutineScope)
	}

	private val MediaConversionMethod.playMethod
		get() = when (this) {
			MediaConversionMethod.None -> PlayMethod.DIRECT_PLAY
			MediaConversionMethod.Remux -> PlayMethod.DIRECT_STREAM
			MediaConversionMethod.Transcode -> PlayMethod.TRANSCODE
		}

	private val RepeatMode.remoteRepeatMode
		get() = when (this) {
			RepeatMode.NONE -> SdkRepeatMode.REPEAT_NONE
			RepeatMode.REPEAT_ENTRY_ONCE -> SdkRepeatMode.REPEAT_ONE
			RepeatMode.REPEAT_ENTRY_INFINITE -> SdkRepeatMode.REPEAT_ALL
		}

	suspend fun sendUpdateIfActive() {
		coroutineScope.launch { sendStreamUpdate() }
	}

	private suspend fun getQueue(): List<QueueItem> {
		// The queues are lazy loaded so we only load a small amount of items to set as queue on the
		// backend.
		return manager.queue
			.peekNext(15)
			.mapNotNull { it.baseItem }
			.map { QueueItem(id = it.id, playlistItemId = it.playlistItemId) }
	}

	private suspend fun sendStreamStart() {
		val entry = manager.queue.entry.value ?: return
		val stream = entry.mediaStream ?: return
		val item = entry.baseItem ?: return

		val owner = manager.sessionOwner
		if (owner.snapshot?.terminalReason == null && owner.snapshot?.started == true && streamId == stream.identifier) { sendStreamUpdate(); return }
		if (owner.snapshot?.terminalReason == null) sendStreamStop()
		val attempt = owner.begin(item.id.toString(), withContext(Dispatchers.Main) { state.positionInfo.active.inWholeMilliseconds })
		streamId = stream.identifier
		owner.bindEngine(attempt, manager.backend.id, Runnable { manager.backend.stop() })
		owner.claimPreparation(attempt)
		owner.claimStart(attempt)
		stopInfo = PlaybackStopInfo(itemId = item.id, playSessionId = stream.identifier, playlistItemId = item.playlistItemId, positionTicks = owner.snapshot!!.positionMs * 10000, failed = false)

		val session = owner.snapshot ?: return
		val info =
				PlaybackStartInfo(
					itemId = item.id,
					playSessionId = stream.identifier,
					playlistItemId = item.playlistItemId,
					canSeek = true,
					isMuted = state.volume.muted,
					volumeLevel = (state.volume.volume * 100).roundToInt(),
					isPaused = state.playState.value != PlayState.PLAYING,
					aspectRatio = state.videoSize.value.aspectRatio.toString(),
					positionTicks = withContext(Dispatchers.Main) { state.positionInfo.active.inWholeTicks },
					playMethod = stream.conversionMethod.playMethod,
					repeatMode = state.repeatMode.value.remoteRepeatMode,
					nowPlayingQueue = getQueue(),
					playbackOrder = when (state.playbackOrder.value) {
						org.jellyfin.playback.core.model.PlaybackOrder.DEFAULT -> PlaybackOrder.DEFAULT
						org.jellyfin.playback.core.model.PlaybackOrder.RANDOM -> PlaybackOrder.SHUFFLE
						org.jellyfin.playback.core.model.PlaybackOrder.SHUFFLE -> PlaybackOrder.SHUFFLE
					}
				)
		reporter.enqueue(owner, session, ReportKind.START) { api.playStateApi.reportPlaybackStart(info) }
	}

	private suspend fun sendStreamUpdate() {
		val entry = manager.queue.entry.value ?: return
		val stream = entry.mediaStream ?: return
		val item = entry.baseItem ?: return

		val owner = manager.sessionOwner
		val active = owner.snapshot ?: return
		if (streamId != stream.identifier || !owner.update(active.generation, withContext(Dispatchers.Main) { state.positionInfo.active.inWholeMilliseconds }, state.playState.value == PlayState.PAUSED)) return

		val session = owner.snapshot ?: return
		val info =
				PlaybackProgressInfo(
					itemId = item.id,
					playSessionId = stream.identifier,
					playlistItemId = item.playlistItemId,
					canSeek = true,
					isMuted = state.volume.muted,
					volumeLevel = (state.volume.volume * 100).roundToInt(),
					isPaused = state.playState.value != PlayState.PLAYING,
					aspectRatio = state.videoSize.value.aspectRatio.toString(),
					positionTicks = withContext(Dispatchers.Main) { state.positionInfo.active.inWholeTicks },
					playMethod = stream.conversionMethod.playMethod,
					repeatMode = state.repeatMode.value.remoteRepeatMode,
					nowPlayingQueue = getQueue(),
					playbackOrder = when (state.playbackOrder.value) {
						org.jellyfin.playback.core.model.PlaybackOrder.DEFAULT -> PlaybackOrder.DEFAULT
						org.jellyfin.playback.core.model.PlaybackOrder.RANDOM -> PlaybackOrder.SHUFFLE
						org.jellyfin.playback.core.model.PlaybackOrder.SHUFFLE -> PlaybackOrder.SHUFFLE
					}
				)
		reporter.enqueue(owner, session, ReportKind.PROGRESS) { api.playStateApi.reportPlaybackProgress(info) }
	}

	private suspend fun sendStreamStop() {
		val owner = manager.sessionOwner
		val active = owner.snapshot ?: return
		val position = withContext(Dispatchers.Main) { state.positionInfo.active.inWholeMilliseconds }
		if (!owner.isCurrent(active.generation)) return
		if (position > 0) owner.update(active.generation, position, false)
		withContext(Dispatchers.Main) { owner.finish(if (state.playState.value == PlayState.ERROR) "PLAYBACK_ERROR" else "STOPPED", if (state.playState.value == PlayState.ERROR) SessionState.ERROR else SessionState.STOPPED) }
		val session = owner.snapshot ?: return
		val info = stopInfo?.copy(positionTicks = session.positionMs * 10000, failed = session.state == SessionState.ERROR) ?: return
		reporter.enqueue(owner, session, ReportKind.STOP) { api.playStateApi.reportPlaybackStopped(info) }
	}
}
