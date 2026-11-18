package org.jellyfin.playback.core.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LocalAudioDecodePolicyTests : FunSpec({
	val audio = LocalAudioCapability("eac3", 6, 48000, true, false, true, true)
	fun dv(local: LocalAudioDecodeDecision, profile: Int = 5) = DolbyVisionSafeNegotiationPolicy().evaluate(
		DolbyVisionDeliveryEvidence(true, profile, VideoDelivery.ORIGINAL,
			if (local.canDirectPlay) Capability.SUPPORTED else Capability.UNSUPPORTED, serverAdvertised = true))
	test("A V encoded DD+ remains available when passthrough is on") {
		val result = LocalAudioDecodePolicy.evaluate(true, audio.copy(nativeDecoder = false))
		result.path shouldBe AudioOutputPath.ENCODED_OR_LOCAL
		dv(result).allowed shouldBe true
	}
	test("B C F G OFF retains original DV5 and source 5.1 for local PCM") {
		val result = LocalAudioDecodePolicy.evaluate(false, audio)
		result.path shouldBe AudioOutputPath.LOCAL_PCM
		result.capability.channels shouldBe 6
		result.capability.sampleRate shouldBe 48000
		dv(result).allowed shouldBe true
	}
	test("packaged FFmpeg is a local decoder independently of Android MediaCodec") {
		LocalAudioDecodePolicy.evaluate(false, audio.copy(nativeDecoder = false, softwareDecoder = true)).path shouldBe AudioOutputPath.LOCAL_PCM
	}
	test("D E missing local decoder cannot use encoded support with passthrough off") {
		val result = LocalAudioDecodePolicy.evaluate(false, audio.copy(nativeDecoder = false))
		result.canDirectPlay shouldBe false
		dv(result).allowed shouldBe false
	}
	test("E unknown codec with no local decoder is unsupported") {
		LocalAudioDecodePolicy.evaluate(false, audio.copy(codec = "unsupported", nativeDecoder = false, softwareDecoder = false)).canDirectPlay shouldBe false
	}
	test("PCM format and output compatibility are mandatory") {
		listOf(audio.copy(pcmOutput = false), audio.copy(channels = 0), audio.copy(channels = 9), audio.copy(sampleRate = -1)).forEach {
			LocalAudioDecodePolicy.evaluate(false, it).canDirectPlay shouldBe false
		}
	}
	test("I J local audio does not authorize unverified copy or full video transcode") {
		val original = dv(LocalAudioDecodePolicy.evaluate(false, audio)).evidence
		val policy = DolbyVisionSafeNegotiationPolicy()
		policy.evaluate(original.copy(delivery = VideoDelivery.VIDEO_TRANSCODE)).allowed shouldBe false
		val copy = original.copy(delivery = VideoDelivery.VIDEO_COPY_AUDIO_CONVERSION, videoCopyVerified = true,
			containerSignalingVerified = true, deliveredProfile = 5, convertedAudioCompatible = Capability.SUPPORTED)
		policy.evaluate(copy).allowed shouldBe true
		policy.evaluate(copy.copy(videoCopyVerified = false)).allowed shouldBe false
	}
	test("K L original DV7 and DV8 keep the same audio policy without certifying FEL or HDMI") {
		for (profile in listOf(7, 8)) {
			val result = dv(LocalAudioDecodePolicy.evaluate(false, audio), profile)
			result.allowed shouldBe true
			result.preservation shouldBe Capability.NOT_VERIFIED
		}
	}
	test("M N HDR10 and SDR retain existing non-DV delivery rules") {
		for (profile in listOf("HDR10", "SDR")) {
			val result = LocalAudioDecodePolicy.evaluate(false, audio)
			result.path shouldBe AudioOutputPath.LOCAL_PCM
			DolbyVisionSafeNegotiationPolicy().evaluate(DolbyVisionDeliveryEvidence(false, null, VideoDelivery.ORIGINAL)).allowed shouldBe true
		}
	}
})
