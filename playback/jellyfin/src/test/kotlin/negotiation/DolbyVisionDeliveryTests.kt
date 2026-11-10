package org.jellyfin.playback.jellyfin.negotiation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.playback.core.engine.Capability
import org.jellyfin.sdk.model.api.*

class DolbyVisionDeliveryTests : FunSpec({
	val video = mockk<MediaStream> { every { type } returns MediaStreamType.VIDEO; every { index } returns 0
		every { codec } returns "hevc"; every { dvProfile } returns 5; every { videoRangeType } returns VideoRangeType.SDR }
	val audio = mockk<MediaStream> { every { type } returns MediaStreamType.AUDIO; every { index } returns 1; every { codec } returns "eac3" }
	val source = mockk<MediaSourceInfo> { every { id } returns "source-a"; every { container } returns "mkv"
		every { supportsDirectPlay } returns true; every { supportsDirectStream } returns true; every { supportsTranscoding } returns true
		every { mediaStreams } returns listOf(video, audio); every { defaultAudioStreamIndex } returns 1 }
	fun profile(codecs: String): DeviceProfile = mockk {
		every { directPlayProfiles } returns listOf(mockk<DirectPlayProfile> {
			every { type } returns DlnaProfileType.VIDEO; every { container } returns "mkv,mp4"; every { audioCodec } returns codecs
		})
	}
	test("EAC3 enabled declaration retains original video without changing the audio sink") {
		val result = assessDolbyVisionDelivery(source, profile("ac3,eac3"), PlayMethod.DIRECT_PLAY, "source-a", 1)
		result.allowed shouldBe true
		result.evidence.audioCompatible shouldBe Capability.SUPPORTED
	}
	test("disabled AC3 EAC3 declaration is respected even if server advertises direct play") {
		assessDolbyVisionDelivery(source, profile("aac,mp3"), PlayMethod.DIRECT_PLAY).allowed shouldBe false
	}
	test("direct stream and transcode do not prove video copy or DV signaling") {
		listOf(PlayMethod.DIRECT_STREAM, PlayMethod.TRANSCODE).forEach {
			assessDolbyVisionDelivery(source, profile("aac"), it).allowed shouldBe false
		}
	}
	test("explicit source and audio requests cannot silently change") {
		assessDolbyVisionDelivery(source, profile("eac3"), PlayMethod.DIRECT_PLAY, "other").allowed shouldBe false
		assessDolbyVisionDelivery(source, profile("eac3"), PlayMethod.DIRECT_PLAY, requestedAudio = 9).allowed shouldBe false
	}
	test("missing video metadata cannot authorize converted output") {
		assessDolbyVisionDelivery(mockk<MediaSourceInfo> { every { mediaStreams } returns listOf(audio); every { defaultAudioStreamIndex } returns 1; every { id } returns "source-a"; every { supportsTranscoding } returns true; every { container } returns "mkv" }, profile("aac"), PlayMethod.TRANSCODE).allowed shouldBe false
	}
})
