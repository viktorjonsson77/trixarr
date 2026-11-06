package org.jellyfin.androidtv.util.apiclient

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.playback.core.engine.OrderedPlaybackReporter
import org.jellyfin.playback.core.engine.ReportKind
import org.jellyfin.androidtv.data.compat.StreamInfo
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import timber.log.Timber
import java.time.Instant

class ReportingHelper(
	private val dataRefreshService: DataRefreshService,
	private val api: ApiClient,
	scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
	private val reporter = OrderedPlaybackReporter(scope)
	fun reportStart(
		lifecycleOwner: LifecycleOwner,
		playbackController: PlaybackController?,
		item: BaseItemDto,
		streamInfo: StreamInfo,
		position: Long,
		paused: Boolean
	) {
		val owner = playbackController?.sessionOwner ?: return
		val session = owner.snapshot ?: return
		if (session.mediaId != item.id.toString()) return

		val info = PlaybackStartInfo(
			itemId = item.id,
			positionTicks = position,
			canSeek = (streamInfo.runTimeTicks ?: 0) > 0,
			isPaused = paused,
			liveStreamId = streamInfo.mediaSource?.liveStreamId,
			playSessionId = streamInfo.playSessionId,
			playMethod = streamInfo.playMethod,
			audioStreamIndex = playbackController?.audioStreamIndex,
			subtitleStreamIndex = playbackController?.subtitleStreamIndex,
			isMuted = false,
			repeatMode = RepeatMode.REPEAT_NONE,
			playbackOrder = PlaybackOrder.DEFAULT,
			mediaSourceId = streamInfo.mediaSourceId,
		)

		reporter.enqueue(owner, session, ReportKind.START) { api.playStateApi.reportPlaybackStart(info) }
	}

	fun reportProgress(
		lifecycleOwner: LifecycleOwner,
		playbackController: PlaybackController?,
		item: BaseItemDto,
		streamInfo: StreamInfo,
		position: Long,
		paused: Boolean
	) {
		val owner = playbackController?.sessionOwner ?: return
		val session = owner.snapshot ?: return
		if (session.mediaId != item.id.toString()) return
		if (!owner.update(session.generation, position / 10000, paused)) return

		val info = PlaybackProgressInfo(
			itemId = item.id,
			positionTicks = position,
			canSeek = (streamInfo.runTimeTicks ?: 0) > 0,
			isPaused = paused,
			liveStreamId = streamInfo.mediaSource?.liveStreamId,
			playSessionId = streamInfo.playSessionId,
			playMethod = streamInfo.playMethod,
			audioStreamIndex = playbackController?.audioStreamIndex,
			subtitleStreamIndex = playbackController?.subtitleStreamIndex,
			isMuted = false,
			repeatMode = RepeatMode.REPEAT_NONE,
			playbackOrder = PlaybackOrder.DEFAULT,
			mediaSourceId = streamInfo.mediaSourceId,
		)

		reporter.enqueue(owner, session, ReportKind.PROGRESS) { api.playStateApi.reportPlaybackProgress(info) }
	}

	fun reportStopped(lifecycleOwner: LifecycleOwner, playbackController: PlaybackController?, item: BaseItemDto, streamInfo: StreamInfo, position: Long?) {
		val owner = playbackController?.sessionOwner ?: return
		val session = owner.snapshot ?: return
		if (session.mediaId != item.id.toString()) return

		val info = PlaybackStopInfo(
			itemId = item.id,
			positionTicks = position,
			mediaSourceId = streamInfo.mediaSourceId,
			liveStreamId = streamInfo.mediaSource?.liveStreamId,
			playSessionId = streamInfo.playSessionId,
			failed = session.state == org.jellyfin.playback.core.engine.SessionState.ERROR,
		)

		reporter.enqueue(owner, session, ReportKind.STOP) { api.playStateApi.reportPlaybackStopped(info) }

		// Update dataRefreshService
		dataRefreshService.lastPlayback = Instant.now()
		when (item.type) {
			BaseItemKind.MOVIE -> dataRefreshService.lastMoviePlayback = Instant.now()
			BaseItemKind.EPISODE -> dataRefreshService.lastTvPlayback = Instant.now()
			else -> Unit
		}
	}
}
