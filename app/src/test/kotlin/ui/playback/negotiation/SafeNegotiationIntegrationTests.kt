package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.jellyfin.androidtv.data.compat.VideoOptions
import org.jellyfin.playback.core.engine.*
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.*
import java.util.UUID

class SafeNegotiationIntegrationTests : FunSpec({
	fun source(direct: Boolean): MediaSourceInfo = mockk(relaxed = true) {
		every { id } returns "source-a"
		every { container } returns "mkv"
		every { defaultAudioStreamIndex } returns 1
		every { supportsDirectPlay } returns direct
		every { supportsDirectStream } returns false
		every { supportsTranscoding } returns true
		every { mediaStreams } returns listOf(
			mockk<MediaStream>(relaxed = true) { every { type } returns MediaStreamType.VIDEO; every { dvProfile } returns 5 },
			mockk<MediaStream>(relaxed = true) { every { type } returns MediaStreamType.AUDIO; every { index } returns 1; every { codec } returns "eac3" }
		)
	}
	test("real PlaybackInfo mapping rejects converted DV before any delivery URL is created") {
		val api = mockk<ApiClient>()
		val options = VideoOptions().apply { itemId = UUID.randomUUID(); mediaSourceId = "source-a"; audioStreamIndex = 1 }
		val response = mockk<PlaybackInfoResponse> { every { mediaSources } returns listOf(source(false)); every { playSessionId } returns "server-session" }
		val result = createStreamInfo(api, options, response)
		result.mediaUrl shouldBe null
		result.negotiationDecision.allowed shouldBe false
		result.mediaSourceId shouldBe "source-a"
		verify { api wasNot Called }
	}
	test("requested source cannot fall back to another library version") {
		val options = VideoOptions().apply { mediaSourceId = "other" }
		val response = mockk<PlaybackInfoResponse> { every { mediaSources } returns listOf(source(true)) }
		(runCatching { createStreamInfo(mockk(), options, response) }.exceptionOrNull() is IllegalStateException) shouldBe true
	}
	test("manager rejection prevents prepare and creates no fallback engine") {
		val adapter = mockk<PlayerEngine<String>>(relaxed = true)
		val manager = PlayerEngineManager(listOf({ adapter }))
		val rejection = DolbyVisionSafeNegotiationPolicy().evaluate(DolbyVisionDeliveryEvidence(true, 5, VideoDelivery.VIDEO_TRANSCODE))
		manager.evaluate(MediaCapabilityInput(dvProfile = 5), DeviceCapabilitySnapshot(30, emptyList(), false, null), rejection).canAttempt shouldBe false
		(runCatching { manager.prepare("must not open") }.exceptionOrNull() is IllegalStateException) shouldBe true
		verify { adapter wasNot Called }
		manager.release()
	}
	test("rejected session reports no start or stop and ignores stale callback after resume") {
		val owner = PlaybackSessionOwner()
		val old = owner.begin("resident", 42000)
		owner.claimPreparation(old) shouldBe true
		owner.finish("UNSAFE_DV_DELIVERY", SessionState.ERROR)
		owner.claimStart(old) shouldBe false
		owner.claimReport(owner.snapshot!!.id, ReportKind.STOP) shouldBe false
		val next = owner.begin("resident", 42000)
		owner.isCurrent(old) shouldBe false
		owner.snapshot!!.resumePositionMs shouldBe 42000
		owner.stop()
	}
})
