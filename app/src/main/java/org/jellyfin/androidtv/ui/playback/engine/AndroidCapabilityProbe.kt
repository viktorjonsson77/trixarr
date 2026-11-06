package org.jellyfin.androidtv.ui.playback.engine

import android.app.Activity
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import org.jellyfin.androidtv.data.compat.StreamInfo
import org.jellyfin.playback.core.engine.*
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod

/** Collects declarations only. Never promotes a declaration to HDMI verification. */
object AndroidCapabilityProbe {
	private fun codec(value: String?) = when (value?.lowercase()) { "h265" -> "hevc"; "avc" -> "h264"; else -> value?.lowercase() }
	private fun mime(codec: String?) = when (codec) {
		"hevc" -> "video/hevc"; "h264" -> "video/avc"; "av1" -> "video/av01"; "vp9" -> "video/x-vnd.on2.vp9"
		"vp8" -> "video/x-vnd.on2.vp8"; "mpeg2video" -> "video/mpeg2"; "mpeg4" -> "video/mp4v-es"
		"ac3" -> "audio/ac3"; "eac3" -> "audio/eac3"; "truehd" -> "audio/true-hd"; "dts" -> "audio/vnd.dts"
		"aac" -> "audio/mp4a-latm"; "flac" -> "audio/flac"; "opus" -> "audio/opus"; "mp3" -> "audio/mpeg"
		else -> null
	}
	@JvmStatic fun media(info: StreamInfo): MediaCapabilityInput {
		val streams = info.mediaSource?.mediaStreams.orEmpty()
		val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
		val audio = streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.index == info.mediaSource?.defaultAudioStreamIndex }
			?: streams.firstOrNull { it.type == MediaStreamType.AUDIO }
		val hdr = video?.videoRangeType?.toString()?.uppercase()
		return MediaCapabilityInput(info.container ?: info.mediaSource?.container, codec(video?.codec), video?.dvProfile, video?.dvLevel,
			video?.dvBlSignalCompatibilityId, when { video?.dvProfile != null || hdr?.contains("DOVI") == true -> "DV"; hdr?.contains("HDR10PLUS") == true -> "HDR10+"; hdr?.contains("HDR10") == true -> "HDR10"; hdr?.contains("HLG") == true -> "HLG"; hdr == "SDR" -> "SDR"; else -> hdr },
			video?.bitDepth, video?.width, video?.height, (video?.realFrameRate ?: video?.averageFrameRate)?.toDouble(),
			codec(audio?.codec), audio?.channels, audio?.channelLayout, audio?.sampleRate, info.playMethod == PlayMethod.TRANSCODE)
	}
	@JvmStatic fun device(activity: Activity, media: MediaCapabilityInput): DeviceCapabilitySnapshot {
		val codecs = runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder } }.getOrNull()
		val decoders = mutableListOf<DecoderCapability>()
		for (codec in codecs.orEmpty()) {
			for (type in codec.supportedTypes.filter { it.startsWith("video/") }) {
				val name = if (type == "video/dolby-vision") "dolby-vision" else listOf("hevc", "h264", "av1", "vp9", "vp8", "mpeg2video", "mpeg4").firstOrNull { mime(it) == type } ?: type
				val caps = runCatching { codec.getCapabilitiesForType(type) }.getOrNull()
				val profiles = mutableMapOf<Int, Int>()
				if (type == "video/dolby-vision") for (level in caps?.profileLevels.orEmpty()) {
					val profile = when (level.profile) { CodecProfileLevel.DolbyVisionProfileDvheStn -> 5; CodecProfileLevel.DolbyVisionProfileDvheDtb -> 7; CodecProfileLevel.DolbyVisionProfileDvheSt -> 8; else -> null }
					if (profile != null && level.level > 0) profiles[profile] = maxOf(profiles[profile] ?: 0, Integer.numberOfTrailingZeros(level.level) + 1)
				}
				val geometry = if (media.width != null && media.height != null && media.frameRate != null) runCatching {
					caps?.videoCapabilities?.areSizeAndRateSupported(media.width!!, media.height!!, media.frameRate!!)
				}.getOrNull() else null
				val depth = when { media.bitDepth == null -> null; media.bitDepth == 8 -> true; media.bitDepth == 10 && name == "hevc" -> caps?.profileLevels?.any { it.profile == CodecProfileLevel.HEVCProfileMain10 || it.profile == CodecProfileLevel.HEVCProfileMain10HDR10 || it.profile == CodecProfileLevel.HEVCProfileMain10HDR10Plus }; name == "dolby-vision" && media.bitDepth == 10 -> true; else -> null }
				decoders += DecoderCapability(name, profiles, when { geometry == false || depth == false -> Capability.UNSUPPORTED; geometry == true && depth == true -> Capability.SUPPORTED; else -> Capability.UNKNOWN })
			}
		}
		val audioMime = mime(media.audioCodec)
		val audioSupport = if (audioMime == null) Capability.UNKNOWN else {
			val native = codecs.orEmpty().any { codec -> audioMime in codec.supportedTypes && runCatching {
				val caps = codec.getCapabilitiesForType(audioMime).audioCapabilities
				(media.channels == null || caps.maxInputChannelCount >= media.channels!!) && (media.sampleRate == null || caps.isSampleRateSupported(media.sampleRate!!))
			}.getOrDefault(false) }
			val ffmpeg = runCatching { androidx.media3.decoder.ffmpeg.FfmpegLibrary.supportsFormat(audioMime) }.getOrDefault(false)
			if (native || ffmpeg) Capability.SUPPORTED else Capability.UNKNOWN // Passthrough may work without an audio decoder.
		}
		val hdr = if (Build.VERSION.SDK_INT >= 24) runCatching { activity.windowManager.defaultDisplay.hdrCapabilities.supportedHdrTypes.toList().mapNotNull { when (it) { 1 -> "DV"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> null } }.toSet() }.getOrNull() else null
		return DeviceCapabilitySnapshot(Build.VERSION.SDK_INT, decoders, codecs != null, hdr, audioSupport)
	}
}
