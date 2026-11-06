package org.jellyfin.playback.core.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CapabilityPolicyTests : FunSpec({
	val policy = PlayerCapabilityPolicy()
	val base = MediaCapabilityInput("mkv", "hevc", hdr = "SDR", bitDepth = 10, width = 3840, height = 2160, frameRate = 23.976, audioCodec = "ac3", channels = 6, channelLayout = "5.1")
	val device = DeviceCapabilitySnapshot(28, listOf(DecoderCapability("hevc", format = Capability.SUPPORTED), DecoderCapability("dolby-vision", mapOf(5 to 12, 7 to 12, 8 to 12), Capability.SUPPORTED)), true, setOf("DV", "HDR10", "HLG"), Capability.SUPPORTED)
	test("SDR and HDR10 declarations permit playback but never verify HDMI") {
		listOf("SDR", "HDR10").forEach { hdr ->
			val result = policy.evaluate(base.copy(hdr = hdr), device, true)
			result.permission shouldBe PlaybackPermission.ALLOWED
			result.evidence["hdmiOutput"] shouldBe Capability.NOT_VERIFIED
		}
	}
	test("P5 requires DV decoder and never authorizes HEVC fallback") {
		val media = base.copy(dvProfile = 5, dvLevel = 6, hdr = "DV")
		policy.evaluate(media, device, true).hevcFallbackPermitted shouldBe false
		policy.evaluate(media, device.copy(decoders = device.decoders.take(1)), true).permission shouldBe PlaybackPermission.UNSUPPORTED
	}
	test("P7 and P8 compatibility is explicit and level does not imply output") {
		listOf(7, 8).forEach { profile ->
			val media = base.copy(dvProfile = profile, dvLevel = 6, hdr = "DV")
			policy.evaluate(media, device, true).hevcFallbackPermitted shouldBe false
			policy.evaluate(media.copy(dvCompatibilityId = 1), device, true).hevcFallbackPermitted shouldBe true
			policy.evaluate(media.copy(dvCompatibilityId = 4), device, true).hevcFallbackPermitted shouldBe false
			policy.evaluate(media.copy(dvLevel = 99), device, true).permission shouldBe PlaybackPermission.REQUIRES_VERIFICATION
		}
	}
	test("unknown and underdeclared support never silently become supported or trigger transcoding") {
		policy.evaluate(base.copy(videoCodec = "new-codec"), device, true).permission shouldBe PlaybackPermission.REQUIRES_VERIFICATION
		policy.evaluate(base.copy(dvProfile = 7, dvLevel = null), device, true).permission shouldBe PlaybackPermission.REQUIRES_VERIFICATION
		policy.evaluate(base, device.copy(codecListComplete = false, decoders = emptyList()), true).permission shouldBe PlaybackPermission.REQUIRES_VERIFICATION
	}
	test("known unsupported geometry codec API and unavailable engine are rejected") {
		policy.evaluate(base.copy(videoCodec = "av1"), device, true).canAttempt shouldBe false
		policy.evaluate(base, device.copy(decoders = listOf(DecoderCapability("hevc", format = Capability.UNSUPPORTED))), true).canAttempt shouldBe false
		policy.evaluate(base, device.copy(androidApi = 19), true).canAttempt shouldBe false
		policy.evaluate(base, device, false).canAttempt shouldBe false
	}
	test("source DV metadata cannot reject an unknown server transcoded delivery") {
		policy.evaluate(base.copy(dvProfile = 5, converted = true), device.copy(decoders = emptyList()), true).permission shouldBe PlaybackPermission.REQUIRES_VERIFICATION
	}
})
