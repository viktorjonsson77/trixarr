@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.jellyfin.androidtv.ui.playback

import android.os.Handler
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.jellyfin.androidtv.data.compat.StreamInfo
import org.jellyfin.androidtv.data.compat.VideoOptions
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.RefreshRateSwitchingBehavior
import org.jellyfin.androidtv.ui.playback.engine.AndroidCapabilityProbe
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel
import org.jellyfin.androidtv.util.apiclient.ReportingHelper
import org.jellyfin.playback.core.engine.*
import org.jellyfin.sdk.api.client.*
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.UUID

private class RecordingApi : ApiClient() {
	override val baseUrl = "http://synthetic.invalid"
	override val accessToken: String? = null
	override val clientInfo = ClientInfo("fixture", "1")
	override val deviceInfo = DeviceInfo("fixture", "fixture")
	override val httpClientOptions = HttpClientOptions()
	override val webSocket: SocketApi get() = error("Not used")
	val bodies = mutableListOf<Any?>()
	var fail = false
	override fun update(baseUrl: String?, accessToken: String?, clientInfo: ClientInfo, deviceInfo: DeviceInfo) = Unit
	override suspend fun request(method: HttpMethod, path: String, queryParameters: Map<String, Any?>, requestHeaders: Map<String, Any?>, requestBody: Any?): RawResponse {
		bodies += requestBody
		if (fail) throw java.io.IOException("synthetic reporting failure")
		return RawResponse(ByteArray(0), 204, emptyMap())
	}
}
private class ChainEngine(override val id: String = "Media3 fixture", val failPrepare: Boolean = false) : PlayerEngine<Long> {
	override var state = "IDLE"
	override var positionMs = 0L
	var stops = 0; var releases = 0; var prepares = 0
	val tracks = mutableListOf<Triple<Int, Int, Int>>()
	override fun supports(request: Long) = true
	override fun prepare(request: Long) { prepares++; if (failPrepare) error("init"); positionMs = request; state = "READY" }
	override fun play() { state = "PLAYING" }
	override fun pause() { state = "PAUSED" }
	override fun stop() { stops++; state = "STOPPED" }
	override fun seek(positionMs: Long) { this.positionMs = positionMs }
	override fun selectTrack(type: Int, group: Int, track: Int): Boolean { tracks += Triple(type, group, track); return true }
	override fun release() { releases++; state = "RELEASED" }
}

/** Real legacy controller + core owner/manager + real Jellyfin DTO reporting, fake only at UI/codec/HTTP boundaries. */
class ControllerChainTests : FunSpec({
	afterTest { unmockkAll(); stopKoin() }
	fun fixture(method: PlayMethod = PlayMethod.DIRECT_PLAY, failedInit: Boolean = false): ChainFixture = ChainFixture(method, failedInit)
	test("startup stuck before decoder cannot buffer indefinitely") {
        val f = fixture()
        f.stallStartup = true
        f.start(0)
        f.advanceHandlerBy(200_000)
        f.controller.sessionOwner.snapshot!!.state shouldBe SessionState.ERROR
        f.engine.prepares shouldBe 1
    }
    test("background startup does not consume its timeout budget") {
        val f = fixture(); f.stallStartup = true; f.controller.setBackgrounded(true); f.start(0)
        f.advanceHandlerBy(200_000)
        f.controller.sessionOwner.snapshot!!.terminalReason shouldBe null
        f.controller.setBackgrounded(false); f.advanceHandlerBy(91_000)
        f.controller.sessionOwner.snapshot!!.terminalReason shouldBe "STARTUP_TIMEOUT_SOURCE_SELECTED"
        f.engine.stops shouldBe 1
    }
    test("timeout stops once and a new session ignores the stale watchdog") {
        val f = fixture(); f.stallStartup = true; f.start(0); f.scope.runCurrent()
        f.advanceHandlerBy(91_000); f.scope.runCurrent()
        f.engine.stops shouldBe 1
        f.api.bodies.filterIsInstance<PlaybackStopInfo>().size shouldBe 1
        f.stallStartup = false; f.start(0); f.scope.runCurrent(); f.advanceHandlerBy(200_000)
        f.controller.sessionOwner.snapshot!!.terminalReason shouldBe null
        f.engine.prepares shouldBe 2
        f.controller.stop()
    }
	listOf(PlayMethod.DIRECT_PLAY, PlayMethod.DIRECT_STREAM, PlayMethod.TRANSCODE).forEach { method ->
		test("$method controller start pause seek resume stop uses one identity and ordered reports") {
			val f = fixture(method)
			f.start(48000)
			f.scope.runCurrent()
			f.api.bodies.filterIsInstance<PlaybackStartInfo>().single().positionTicks shouldBe 480000000L
			f.controller.pause()
			f.scope.runCurrent()
			f.controller.seek(60000)
			f.controller.isPaused shouldBe true
			f.scope.runCurrent()
			f.controller.play(60000)
			f.scope.runCurrent()
			f.controller.stop(); f.controller.stop()
			f.scope.runCurrent()
			f.api.bodies.filterIsInstance<PlaybackStartInfo>().size shouldBe 1
			f.api.bodies.filterIsInstance<PlaybackStopInfo>().single().positionTicks shouldBe 600000000L
			f.engine.stops shouldBe 1
			f.controller.sessionOwner.snapshot!!.terminalReason shouldBe "STOPPED"
		}
	}
	test("background preparation defers audio until foreground") {
        val f = fixture(); f.controller.setBackgrounded(true); f.start(1000)
        f.engine.state shouldBe "READY"
        f.controller.setBackgrounded(false)
        f.engine.state shouldBe "PLAYING"
        f.controller.stop(); f.scope.runCurrent()
    }
    test("active decoder error is terminal with one failed stop and no retry") {
        val f = fixture(); f.start(1000); f.scope.runCurrent()
        f.controller.playerErrorEncountered(); f.scope.runCurrent()
        f.controller.sessionOwner.snapshot!!.state shouldBe SessionState.ERROR
        f.api.bodies.filterIsInstance<PlaybackStopInfo>().single().failed shouldBe true
        f.engine.prepares shouldBe 1
    }
	test("initialization failure sends no logical start and cleans the session") {
		val f = fixture(failedInit = true)
		f.start(125000)
		f.scope.runCurrent()
		f.api.bodies.size shouldBe 0
		f.controller.sessionOwner.snapshot!!.terminalReason shouldBe "INITIALIZATION_FAILED"
		f.engine.releases shouldBe 1
	}
	test("report failure does not create a new playback session or retry start") {
		val f = fixture(); f.api.fail = true; f.start(70000); f.scope.runCurrent()
		val id = f.controller.sessionOwner.snapshot!!.id
		f.controller.pause(); f.scope.runCurrent(); f.controller.stop(); f.scope.runCurrent()
		f.engine.prepares shouldBe 1
		f.controller.sessionOwner.snapshot!!.id shouldBe id
		f.api.bodies.filterIsInstance<PlaybackStartInfo>().size shouldBe 1
		f.controller.sessionOwner.snapshot!!.reportingState.startsWith("STOP_FAILED") shouldBe true
	}
	listOf(5, 7, 8).forEach { profile ->
		test("DV P$profile goes through controller policy engine reporting cleanup without inferred HDMI") {
			val f = ChainFixture(PlayMethod.DIRECT_PLAY, false, mediaFixture().copy(dvProfile = profile, dvLevel = 6, hdr = "DV"))
			f.start(40000); f.scope.runCurrent(); f.controller.stop(); f.scope.runCurrent()
			f.manager.capabilityDecision!!.evidence["hdmiOutput"] shouldBe Capability.NOT_VERIFIED
			f.api.bodies.filterIsInstance<PlaybackStartInfo>().size shouldBe 1
			f.api.bodies.filterIsInstance<PlaybackStopInfo>().size shouldBe 1
		}
	}
	test("HDR10 and unknown codecs run with their policy evidence intact") {
		val f = ChainFixture(PlayMethod.DIRECT_PLAY, false, mediaFixture().copy(hdr = "HDR10", videoCodec = "future-codec"))
		f.start(1000); f.scope.runCurrent()
		f.manager.capabilityDecision!!.permission shouldBe PlaybackPermission.REQUIRES_VERIFICATION
		f.controller.stop(); f.scope.runCurrent()
		f.api.bodies.filterIsInstance<PlaybackStartInfo>().size shouldBe 1
	}
	test("unsupported codec and unsafe P5 fallback never start reporting or audio") {
		val f = ChainFixture(PlayMethod.DIRECT_PLAY, false, mediaFixture().copy(dvProfile = 5, dvLevel = 6), deviceFixture().copy(decoders = listOf(DecoderCapability("hevc", format = Capability.SUPPORTED))))
		f.start(1000); f.scope.runCurrent()
		f.manager.capabilityDecision!!.canAttempt shouldBe false
		f.engine.prepares shouldBe 0
		f.api.bodies.size shouldBe 0
	}
	test("initial fallback preserves requested resume and creates one report pair") {
		val f = ChainFixture(PlayMethod.DIRECT_PLAY, true, fallback = true)
		f.start(77000); f.scope.runCurrent()
		f.fallbackEngine.positionMs shouldBe 77000L
		f.engine.releases shouldBe 1
		f.controller.stop(); f.scope.runCurrent()
		f.api.bodies.filterIsInstance<PlaybackStartInfo>().size shouldBe 1
		f.api.bodies.filterIsInstance<PlaybackStopInfo>().size shouldBe 1
	}
	test("audio and subtitle switches stay in the existing session") {
		val f = fixture(); f.start(1000); f.scope.runCurrent()
		val id = f.controller.sessionOwner.snapshot!!.id
		f.controller.switchAudioStream(1)
		f.controller.setSubtitleIndex(2)
		f.controller.setSubtitleIndex(-1)
		f.engine.tracks.any { it.first == 1 } shouldBe true
		f.engine.tracks.any { it.first == 3 && it.second == -1 } shouldBe true
		f.controller.sessionOwner.snapshot!!.id shouldBe id
		f.controller.stop(); f.scope.runCurrent()
		f.api.bodies.filterIsInstance<PlaybackStartInfo>().size shouldBe 1
	}
	test("completion is terminal and release is once despite late controller notifications") {
		val f = fixture(); f.start(1000); f.scope.runCurrent()
		f.controller.onCompletion(); f.scope.runCurrent()
		f.controller.onCompletion(); f.controller.onPrepared(); f.controller.onProgress()
		f.controller.sessionOwner.snapshot!!.state shouldBe SessionState.COMPLETED
		f.engine.releases shouldBe 1
		f.api.bodies.filterIsInstance<PlaybackStopInfo>().size shouldBe 1
	}
	test("cancelled and duplicate segment callbacks cannot start a stopped generation") {
		val f = fixture(); f.deferSegments = true; f.start(90000)
		f.controller.stop()
		f.segmentCallback!!.invoke(); f.segmentCallback!!.invoke(); f.scope.runCurrent()
		f.api.bodies.size shouldBe 0
		f.engine.stops shouldBe 1
	}
	test("interruption checkpoint resumes in a new context without stale reports") {
		val f = fixture(); f.start(90000); f.scope.runCurrent(); f.controller.pause(); f.scope.runCurrent()
		val checkpoint = f.controller.sessionOwner.snapshot!!
		f.controller.endPlayback(false); f.scope.runCurrent()
		val oldGeneration = checkpoint.generation
		f.controller.sessionOwner.isCurrent(oldGeneration) shouldBe false
		f.engine.releases shouldBe 1
		checkpoint.positionMs shouldBe 90000L
		val resumed = ChainFixture(PlayMethod.DIRECT_PLAY, false, itemId = f.itemId)
		resumed.start(checkpoint.positionMs); resumed.scope.runCurrent()
		resumed.controller.sessionOwner.snapshot!!.id.equals(checkpoint.id) shouldBe false
		resumed.api.bodies.filterIsInstance<PlaybackStartInfo>().single().itemId shouldBe f.itemId
		resumed.api.bodies.filterIsInstance<PlaybackStartInfo>().single().positionTicks shouldBe 900000000L
		resumed.controller.stop(); resumed.scope.runCurrent()
	}
})

private fun mediaFixture() = MediaCapabilityInput("mkv", "hevc", hdr = "SDR", bitDepth = 10, width = 3840, height = 2160, frameRate = 23.976, audioCodec = "ac3", channels = 6, channelLayout = "5.1", sampleRate = 48000)
private fun deviceFixture() = DeviceCapabilitySnapshot(28, listOf(DecoderCapability("hevc", format = Capability.SUPPORTED), DecoderCapability("dolby-vision", mapOf(5 to 12, 7 to 12, 8 to 12), Capability.SUPPORTED)), true, setOf("DV", "HDR10", "HLG"), Capability.SUPPORTED)
private class ChainFixture(method: PlayMethod, failedInit: Boolean, val media: MediaCapabilityInput = mediaFixture(), val device: DeviceCapabilitySnapshot = deviceFixture(), val itemId: UUID = UUID.randomUUID(), fallback: Boolean = false) {
	val api = RecordingApi()
	val scope = TestScope()
    var clockMs = 0L
    var stallStartup = false
    val scheduled = mutableListOf<Pair<Long, Runnable>>()
    val handler = mockk<Handler>(relaxed = true)
    fun advanceHandlerBy(ms: Long) {
        val end = clockMs + ms
        var count = 0
        while (true) {
            val next = scheduled.minByOrNull { it.first } ?: break
            if (next.first > end) break
            check(count++ < 1000)
            scheduled.remove(next); clockMs = next.first; next.second.run()
        }
        clockMs = end
    }
	val engine = ChainEngine(failPrepare = failedInit)
	val fallbackEngine = ChainEngine("fallback fixture")
	val manager = PlayerEngineManager(if (fallback) listOf({ engine }, { fallbackEngine }) else listOf({ engine }))
	val video = mockk<VideoManager>(relaxed = true)
	val fragment = mockk<CustomPlaybackOverlayFragment>(relaxed = true)
	val item = mockk<BaseItemDto>(relaxed = true) { every { id } returns itemId; every { mediaStreams } answers { stream.mediaSource.mediaStreams }; every { type } returns BaseItemKind.MOVIE }
	val stream = StreamInfo().apply {
		itemId = this@ChainFixture.itemId; playSessionId = "server-fixture"; playMethod = method; mediaUrl = "https://synthetic.invalid/video"; container = "mkv"; runTimeTicks = 10000000000L
		mediaSource = mockk(relaxed = true) {
			every { id } returns "source"; every { mediaStreams } returns listOf(
				mockk<MediaStream>(relaxed = true) { every { type } returns MediaStreamType.VIDEO; every { index } returns 0; every { codec } returns media.videoCodec
					every { dvProfile } returns media.dvProfile; every { dvLevel } returns media.dvLevel; every { dvBlSignalCompatibilityId } returns media.dvCompatibilityId
					every { width } returns media.width; every { height } returns media.height; every { realFrameRate } returns media.frameRate?.toFloat(); every { bitDepth } returns media.bitDepth
					every { videoRangeType } returns if (media.hdr == "HDR10") VideoRangeType.HDR10 else VideoRangeType.SDR
				},
				mockk<MediaStream>(relaxed = true) { every { type } returns MediaStreamType.AUDIO; every { index } returns 1; every { codec } returns "ac3"; every { channels } returns 6; every { channelLayout } returns "5.1"; every { sampleRate } returns 48000; every { language } returns "eng" },
				mockk<MediaStream>(relaxed = true) { every { type } returns MediaStreamType.SUBTITLE; every { index } returns 2; every { deliveryMethod } returns SubtitleDeliveryMethod.EMBED }
			); every { defaultAudioStreamIndex } returns 1; every { defaultSubtitleStreamIndex } returns -1
		}
	}
	val controller: PlaybackController
	var deferSegments = false
	var segmentCallback: (() -> Unit)? = null
	init {
		mockkStatic(android.widget.Toast::class)
        every { android.widget.Toast.makeText(any(), any<CharSequence>(), any()) } returns mockk(relaxed = true)
        mockkStatic(android.text.TextUtils::class)
		every { android.text.TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
		val prefs = mockk<UserPreferences>(relaxed = true)
		every { prefs[UserPreferences.videoStartDelay] } returns 0L
		val refresh = mockk<DataRefreshService>(relaxed = true)
		val reporting = ReportingHelper(refresh, api, scope)
		stopKoin()
		startKoin { modules(module {
			single { prefs }; single { refresh }; single { reporting }; single<ApiClient> { api }; single { VideoQueueManager() }
		}) }
		every { handler.postDelayed(any(), any()) } answers { scheduled += (clockMs + secondArg<Long>()) to firstArg<Runnable>(); true }
        every { handler.removeCallbacks(any()) } answers { val r = firstArg<Runnable>(); scheduled.removeAll { it.second === r }; Unit }
        controller = PlaybackController(listOf(item), fragment, 0, handler, mockk<InteractionTrackerViewModel>(relaxed = true), RefreshRateSwitchingBehavior.DISABLED, PlaybackSessionOwner(MonotonicClock { clockMs }))
		controller.mVideoManager = video
		controller.mCurrentOptions = VideoOptions()
		every { video.isInitialized } returns true
		every { video.isSeekable } returns true
		every { video.duration } returns 1000000
		every { video.isPlaying } answers { manager.active?.state == "PLAYING" }
		every { video.currentPosition } answers { manager.active?.positionMs ?: 0L }
		every { video.bindSession(any(), any()) } answers { firstArg<PlaybackSessionOwner>().bindEngine(secondArg(), engine.id, Runnable { manager.stop() }) }
		every { video.setMediaStreamInfo(any(), any()) } answers { runCatching {
			manager.evaluate(AndroidCapabilityProbe.media(secondArg()), device)
			manager.prepare(controller.sessionOwner.snapshot!!.resumePositionMs)
		}.isSuccess }
		every { video.start() } answers { manager.play(); engine.state = if (stallStartup) "BUFFERING" else "PLAYING"; setState(if (stallStartup) "BUFFERING" else "PLAYING"); if (!stallStartup) controller.sessionOwner.startupEvent(controller.sessionOwner.snapshot!!.generation, StartupEvent.FIRST_FRAME) }
		every { video.play() } answers { manager.play() }
		every { video.pause() } answers { manager.pause() }
		every { video.seekTo(any()) } answers { manager.seek(firstArg()); firstArg<Long>() }
		every { video.destroy() } answers { manager.release() }
		every { video.setExoPlayerTrack(any(), any(), any()) } answers { manager.active!!.selectTrack(1, firstArg(), 0) }
		every { video.selectEngineTrack(any(), any(), any()) } answers { manager.active!!.selectTrack(firstArg(), secondArg(), thirdArg()) }
		val textGroup = androidx.media3.common.Tracks.Group(
			androidx.media3.common.TrackGroup("sub", androidx.media3.common.Format.Builder().setId("subtitle-2").setSampleMimeType("application/x-subrip").build()),
			false, intArrayOf(4), booleanArrayOf(true))
		video.mExoPlayer = mockk(relaxed = true) { every { currentTracks } returns androidx.media3.common.Tracks(listOf(textGroup)) }

		mockkStatic("org.jellyfin.androidtv.ui.playback.PlaybackControllerHelperKt")
		every { controller.applyMediaSegments(any(), any()) } answers {
			segmentCallback = thirdArg(); if (!deferSegments) segmentCallback!!.invoke()
		}
	}
	private fun setState(value: String) {
		val field = PlaybackController::class.java.getDeclaredField("mPlaybackState").apply { isAccessible = true }
		field.set(controller, java.lang.Enum.valueOf(PlaybackController.PlaybackState::class.java, value))
	}
	fun start(position: Long) {
		val attempt = controller.sessionOwner.begin(itemId.toString(), position)
		controller.sessionOwner.claimPreparation(attempt)
		controller.mCurrentPosition = position
		setState("BUFFERING")
		PlaybackController::class.java.getDeclaredField("finishedInitialSeek").apply { isAccessible = true; setBoolean(controller, true) }
		val method = PlaybackController::class.java.getDeclaredMethod("startItem", BaseItemDto::class.java, java.lang.Long.TYPE, StreamInfo::class.java, java.lang.Long.TYPE).apply { isAccessible = true }
		method.invoke(controller, item, position, stream, attempt)
	}
}
