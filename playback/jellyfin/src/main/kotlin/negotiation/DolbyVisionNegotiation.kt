package org.jellyfin.playback.jellyfin.negotiation

import org.jellyfin.playback.core.engine.*
import org.jellyfin.sdk.model.api.*

/** PlaybackInfo has no affirmative delivered-DV-signaling proof for converted streams. */
fun assessDolbyVisionDelivery(
	source: MediaSourceInfo,
	profile: DeviceProfile?,
	method: PlayMethod,
	requestedSource: String? = null,
	requestedAudio: Int? = null,
	originalSource: MediaSourceInfo? = null,
): DolbyVisionNegotiationDecision {
	val video = source.mediaStreams.orEmpty().firstOrNull { it.type == MediaStreamType.VIDEO }
	val audioIndex = requestedAudio?.takeIf { it >= 0 } ?: source.defaultAudioStreamIndex
	val audio = source.mediaStreams.orEmpty().firstOrNull { it.type == MediaStreamType.AUDIO && it.index == audioIndex }
		?: source.mediaStreams.orEmpty().firstOrNull { it.type == MediaStreamType.AUDIO }
	val originalVideo = originalSource?.mediaStreams.orEmpty().firstOrNull { it.type == MediaStreamType.VIDEO }
	val originalDv = originalVideo?.dvProfile != null || originalVideo?.videoRangeType?.toString()?.uppercase()?.let {
		it.contains("DOVI") || it.contains("DOLBY")
	} == true
	val range = video?.videoRangeType?.toString()?.uppercase()
	val dv = when {
		originalDv || video?.dvProfile != null || range?.contains("DOVI") == true || range?.contains("DOLBY") == true -> true
		range == "SDR" || range?.startsWith("HDR10") == true || range == "HLG" -> false
		else -> null
	}
	val declared = profile?.directPlayProfiles?.filter { it.type == DlnaProfileType.VIDEO }
	val audioCompatibility = when {
		audio == null -> Capability.UNKNOWN
		declared.isNullOrEmpty() -> Capability.UNKNOWN
		declared.any { entry ->
			(entry.container.isNullOrBlank() || entry.container!!.split(',').any { it.equals(source.container, true) }) &&
			(entry.audioCodec.isNullOrBlank() || entry.audioCodec!!.split(',').any { it.equals(audio.codec, true) })
		} -> Capability.SUPPORTED
		else -> Capability.UNSUPPORTED
	}
	return DolbyVisionSafeNegotiationPolicy().evaluate(DolbyVisionDeliveryEvidence(
		dolbyVision = dv, profile = video?.dvProfile,
		delivery = if (method == PlayMethod.DIRECT_PLAY) VideoDelivery.ORIGINAL else VideoDelivery.UNKNOWN,
		audioCompatible = audioCompatibility,
		sourceMatches = (requestedSource == null || source.id == requestedSource) &&
			(!originalDv || method != PlayMethod.DIRECT_PLAY || originalVideo?.dvProfile == video?.dvProfile),
		audioTrackMatches = requestedAudio == null || requestedAudio < 0 ||
			(source.mediaStreams.orEmpty().any { it.type == MediaStreamType.AUDIO && it.index == requestedAudio } &&
				(source.defaultAudioStreamIndex == null || source.defaultAudioStreamIndex == requestedAudio)),
		serverAdvertised = when (method) {
			PlayMethod.DIRECT_PLAY -> source.supportsDirectPlay
			PlayMethod.DIRECT_STREAM -> source.supportsDirectStream
			else -> source.supportsTranscoding
		},
	))
}
