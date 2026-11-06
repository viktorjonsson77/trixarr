package org.jellyfin.playback.core.engine

/** Evidence is deliberately separate from the permission to attempt playback. */
enum class Capability { SUPPORTED, UNSUPPORTED, UNKNOWN, NOT_VERIFIED }
enum class PlaybackPermission { ALLOWED, UNSUPPORTED, REQUIRES_VERIFICATION }
data class MediaCapabilityInput(
	val container: String? = null, val videoCodec: String? = null,
	val dvProfile: Int? = null, val dvLevel: Int? = null, val dvCompatibilityId: Int? = null,
	val hdr: String? = null, val bitDepth: Int? = null, val width: Int? = null, val height: Int? = null,
	val frameRate: Double? = null, val audioCodec: String? = null, val channels: Int? = null,
	val channelLayout: String? = null, val sampleRate: Int? = null,
	val converted: Boolean = false,
)
data class DecoderCapability(
	val codec: String, val profiles: Map<Int, Int> = emptyMap(),
	val format: Capability = Capability.UNKNOWN,
)
data class DeviceCapabilitySnapshot(
	val androidApi: Int?, val decoders: List<DecoderCapability>, val codecListComplete: Boolean,
	val displayHdr: Set<String>?, val audioDecoding: Capability = Capability.UNKNOWN,
)
data class CapabilityDecision(
	val permission: PlaybackPermission, val evidence: Map<String, Capability>, val reasons: List<String>,
	val hevcFallbackPermitted: Boolean = false,
) {
	val canAttempt get() = permission != PlaybackPermission.UNSUPPORTED
}

/** No server transcode request and no decoder mutation occurs here. Unknown stays visible. */
class PlayerCapabilityPolicy {
	fun evaluate(media: MediaCapabilityInput, device: DeviceCapabilitySnapshot, available: Boolean): CapabilityDecision {
		val facts = linkedMapOf<String, Capability>()
		val reasons = mutableListOf<String>()
		fun fact(key: String, evidence: Capability, reason: String) { facts[key] = evidence; if (evidence != Capability.SUPPORTED) reasons += reason }
		fact("engine", if (available) Capability.SUPPORTED else Capability.UNSUPPORTED, "Engine is not available")
		fact("android", when { device.androidApi == null -> Capability.UNKNOWN; device.androidApi < 21 -> Capability.UNSUPPORTED; else -> Capability.SUPPORTED }, "Android API unavailable or below minimum")
		val containers = setOf("mkv", "matroska", "mp4", "mov", "webm", "ts", "mpegts", "m3u8", "hls", "mpeg", "avi")
		fact("container", if (media.container?.lowercase() in containers) Capability.SUPPORTED else Capability.UNKNOWN, "Container requires extractor verification")
		val codec = media.videoCodec?.lowercase()
		val known = setOf("h264", "avc", "hevc", "h265", "av1", "vp9", "vp8", "mpeg2video", "mpeg4")
		val dv = media.hdr == "DV" || media.dvProfile != null || media.hdr?.uppercase()?.contains("DOVI") == true || media.hdr?.uppercase()?.contains("DOLBY") == true
		val matching = device.decoders.filter { it.codec == if (dv) "dolby-vision" else codec }
		val decoder = when {
			matching.isNotEmpty() -> Capability.SUPPORTED
			device.codecListComplete && (codec in known || dv) -> Capability.UNSUPPORTED
			else -> Capability.UNKNOWN
		}
		fact("videoDecoder", decoder, "No confirmed decoder for delivered video format")
		fact("resolutionFrameRateBitDepth", when {
			media.width == null || media.height == null || media.frameRate == null || media.bitDepth == null -> Capability.UNKNOWN
			matching.any { it.format == Capability.SUPPORTED } -> Capability.SUPPORTED
			matching.isNotEmpty() && matching.all { it.format == Capability.UNSUPPORTED } -> Capability.UNSUPPORTED
			else -> Capability.UNKNOWN
		}, "Resolution, frame rate or bit depth is not confirmed for this decoder")
		val hevc = device.decoders.any { it.codec == "hevc" }
		fact("hevcDecoder", if (hevc) Capability.SUPPORTED else if (device.codecListComplete) Capability.UNSUPPORTED else Capability.UNKNOWN, "HEVC base-layer decoder unavailable")
		var safeFallback = false
		if (dv) {
			fact("dolbyVisionDecoder", decoder, "DV decoding requires a DV decoder; MIME support is not HDMI evidence")
			fact("dolbyVisionProfileLevel", when {
				media.dvProfile == null || media.dvLevel == null -> Capability.UNKNOWN
				matching.any { (it.profiles[media.dvProfile] ?: -1) >= media.dvLevel } -> Capability.SUPPORTED
				else -> Capability.UNKNOWN // Under-declared vendor profiles are not a reason to transcode.
			}, "DV profile/level requires verification")
			val hdr10 = when {
				media.dvProfile == 5 -> Capability.UNSUPPORTED
				media.dvCompatibilityId == 1 && media.dvProfile in setOf(7, 8) -> Capability.SUPPORTED
				else -> Capability.UNKNOWN
			}
			facts["hdr10Compatibility"] = hdr10
			safeFallback = hdr10 == Capability.SUPPORTED && hevc
			if (media.dvProfile == 5) reasons += "P5: no generic HEVC/HDR10 fallback; colour conversion is not verified"
			if (media.dvProfile == 7) reasons += "P7: no P7-to-P8 conversion or FEL reconstruction in this engine"
		}
		val hdr = if (dv) "DV" else media.hdr?.uppercase()
		fact("displayHdr", when {
			hdr == "SDR" -> Capability.SUPPORTED
			hdr == null || device.displayHdr == null -> Capability.UNKNOWN
			hdr in device.displayHdr -> Capability.SUPPORTED
			else -> Capability.NOT_VERIFIED
		}, "Display HDR support/output needs verification; no automatic tone mapping assumed")
		fact("audio", device.audioDecoding, "Audio codec/channel configuration needs verification")
		fact("channelLayout", if (media.channelLayout != null && media.channels != null) Capability.SUPPORTED else Capability.UNKNOWN, "Channel layout is unknown")
		facts["hdmiOutput"] = Capability.NOT_VERIFIED
		// Source metadata cannot describe a server-converted stream. Probe delivered tracks instead.
		if (media.converted) {
			facts.keys.filter { it !in setOf("engine", "android", "hdmiOutput") }.forEach { facts[it] = Capability.UNKNOWN }
			reasons += "Server converted delivery: source profile is not delivered format; await player format"
			safeFallback = false
		}
		val blocked = facts["engine"] == Capability.UNSUPPORTED || facts["android"] == Capability.UNSUPPORTED ||
			(!media.converted && (decoder == Capability.UNSUPPORTED || facts["resolutionFrameRateBitDepth"] == Capability.UNSUPPORTED || facts["audio"] == Capability.UNSUPPORTED))
		val uncertain = facts.filterKeys { it !in setOf("hdmiOutput", "hevcDecoder", "hdr10Compatibility") }.values.any { it != Capability.SUPPORTED }
		return CapabilityDecision(when { blocked -> PlaybackPermission.UNSUPPORTED; uncertain -> PlaybackPermission.REQUIRES_VERIFICATION; else -> PlaybackPermission.ALLOWED }, facts, reasons.ifEmpty { listOf("Declared format support; HDMI output remains unverified") }, safeFallback)
	}
}
