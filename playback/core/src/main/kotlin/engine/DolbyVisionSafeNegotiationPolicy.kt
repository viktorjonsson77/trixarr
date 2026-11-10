package org.jellyfin.playback.core.engine

/** Delivery evidence, not a codec selector or an HDMI-output claim. */
enum class VideoDelivery { ORIGINAL, VIDEO_COPY_AUDIO_CONVERSION, VIDEO_TRANSCODE, UNKNOWN }

data class DolbyVisionDeliveryEvidence(
	val dolbyVision: Boolean?,
	val profile: Int?,
	val delivery: VideoDelivery,
	val audioCompatible: Capability = Capability.UNKNOWN,
	val sourceMatches: Boolean = true,
	val audioTrackMatches: Boolean = true,
	val serverAdvertised: Boolean = false,
	val videoCopyVerified: Boolean = false,
	val containerSignalingVerified: Boolean = false,
	val deliveredProfile: Int? = null,
	val convertedAudioCompatible: Capability = Capability.UNKNOWN,
)

data class DolbyVisionNegotiationDecision(
	val allowed: Boolean,
	val preservation: Capability,
	val reason: String,
	val evidence: DolbyVisionDeliveryEvidence,
)

/** Fail closed for converted DV: HEVC, AllowVideoStreamCopy and DirectStream are not preservation evidence. */
class DolbyVisionSafeNegotiationPolicy {
	fun evaluate(input: DolbyVisionDeliveryEvidence): DolbyVisionNegotiationDecision {
		fun result(allowed: Boolean, preservation: Capability, reason: String) =
			DolbyVisionNegotiationDecision(allowed, preservation, reason, input)
		if (!input.sourceMatches || !input.audioTrackMatches) return result(false, Capability.UNKNOWN, "Requested source or audio track changed")
		if (input.dolbyVision == false) return result(true, Capability.NOT_VERIFIED, "Non-DV source; existing capability policy applies")
		if (input.delivery == VideoDelivery.ORIGINAL && input.serverAdvertised && input.audioCompatible != Capability.UNSUPPORTED) {
			return result(true, Capability.NOT_VERIFIED, "Original video retained; decoder capability and physical output require separate verification")
		}
		if (input.dolbyVision == null) return result(false, Capability.UNKNOWN, "Source HDR metadata missing; converted video safety cannot be established")
		if (input.delivery == VideoDelivery.VIDEO_COPY_AUDIO_CONVERSION && input.serverAdvertised &&
			input.convertedAudioCompatible == Capability.SUPPORTED && input.videoCopyVerified && input.containerSignalingVerified && input.profile != null && input.deliveredProfile == input.profile) {
			return result(true, Capability.NOT_VERIFIED, "Video copy and matching DV signaling verified; HDMI and FEL processing remain unverified")
		}
		return result(false, Capability.UNKNOWN, "No verified Dolby Vision-preserving delivery. Video conversion is blocked; choose supported audio or another source")
	}
}
