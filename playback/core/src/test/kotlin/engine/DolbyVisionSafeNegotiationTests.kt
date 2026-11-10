package org.jellyfin.playback.core.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DolbyVisionSafeNegotiationTests : FunSpec({
	val policy = DolbyVisionSafeNegotiationPolicy()
	val direct = DolbyVisionDeliveryEvidence(true, 5, VideoDelivery.ORIGINAL, Capability.SUPPORTED, serverAdvertised = true)
	test("supported original DV5 retains audio and does not claim HDMI") {
		policy.evaluate(direct).allowed shouldBe true
		policy.evaluate(direct).preservation shouldBe Capability.NOT_VERIFIED
	}
	test("disabled bitstream and unsupported audio cannot be overridden") {
		policy.evaluate(direct.copy(audioCompatible = Capability.UNSUPPORTED)).allowed shouldBe false
	}
	test("server rejection and unproven copy never become direct play") {
		policy.evaluate(direct.copy(serverAdvertised = false)).allowed shouldBe false
		policy.evaluate(direct.copy(delivery = VideoDelivery.VIDEO_COPY_AUDIO_CONVERSION)).allowed shouldBe false
	}
	test("full video conversion is unsafe even if HEVC is retained") {
		policy.evaluate(direct.copy(delivery = VideoDelivery.VIDEO_TRANSCODE)).allowed shouldBe false
	}
	test("copy requires server, container, copy and matching profile evidence independently") {
		val copy = direct.copy(delivery = VideoDelivery.VIDEO_COPY_AUDIO_CONVERSION, videoCopyVerified = true,
			containerSignalingVerified = true, deliveredProfile = 5, convertedAudioCompatible = Capability.SUPPORTED)
		policy.evaluate(copy).allowed shouldBe true
		listOf(copy.copy(serverAdvertised = false), copy.copy(videoCopyVerified = false),
			copy.copy(containerSignalingVerified = false), copy.copy(deliveredProfile = 8), copy.copy(convertedAudioCompatible = Capability.UNKNOWN)).forEach {
			policy.evaluate(it).allowed shouldBe false
		}
	}
	test("P7 classification and P8 HDR10 fallback are not inferred") {
		listOf(7, 8, 99).forEach { profile ->
			policy.evaluate(direct.copy(profile = profile)).preservation shouldBe Capability.NOT_VERIFIED
			policy.evaluate(direct.copy(profile = profile, delivery = VideoDelivery.VIDEO_TRANSCODE)).allowed shouldBe false
		}
	}
	test("source and audio changes must match the request") {
		policy.evaluate(direct.copy(sourceMatches = false)).allowed shouldBe false
		policy.evaluate(direct.copy(audioTrackMatches = false)).allowed shouldBe false
	}
	test("unknown profile original is separate from missing metadata conversion") {
		policy.evaluate(direct.copy(profile = null)).allowed shouldBe true
		policy.evaluate(direct.copy(dolbyVision = null, delivery = VideoDelivery.UNKNOWN)).allowed shouldBe false
	}
	test("SDR and HDR10 retain existing policy") {
		policy.evaluate(direct.copy(dolbyVision = false, delivery = VideoDelivery.VIDEO_TRANSCODE)).allowed shouldBe true
	}
})
