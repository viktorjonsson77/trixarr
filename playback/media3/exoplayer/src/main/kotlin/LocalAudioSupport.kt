package org.jellyfin.playback.media3.exoplayer

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import org.jellyfin.playback.core.engine.LocalAudioCapability
import org.jellyfin.playback.core.engine.LocalAudioDecodePolicy

/** Same Media3 decoder registry, packaged extension and PCM sink used for playback. Fail closed on query errors. */
object LocalAudioSupport {
	@JvmStatic fun mime(codec: String?): String? = when (codec?.lowercase()) {
		"ac3" -> MimeTypes.AUDIO_AC3
		"eac3" -> MimeTypes.AUDIO_E_AC3
		"aac" -> MimeTypes.AUDIO_AAC
		"aac_latm" -> "audio/mp4a-latm"
		"alac" -> MimeTypes.AUDIO_ALAC
		"dca", "dts" -> MimeTypes.AUDIO_DTS
		"flac" -> MimeTypes.AUDIO_FLAC
		"mlp", "truehd" -> MimeTypes.AUDIO_TRUEHD
		"mp2" -> MimeTypes.AUDIO_MPEG_L2
		"mp3" -> MimeTypes.AUDIO_MPEG
		"opus" -> MimeTypes.AUDIO_OPUS
		"pcm_alaw" -> MimeTypes.AUDIO_ALAW
		"pcm_mulaw" -> MimeTypes.AUDIO_MLAW
		"pcm_s16le", "pcm_s24le" -> MimeTypes.AUDIO_RAW
		"vorbis" -> MimeTypes.AUDIO_VORBIS
		else -> null
	}

	@JvmStatic fun probe(context: Context, codec: String, channels: Int, sampleRate: Int): LocalAudioCapability {
		val sink = DefaultAudioSink.Builder(context).build()
		return try { probe(sink, codec, channels, sampleRate) } finally { sink.release() }
	}

	private fun probe(sink: DefaultAudioSink, codec: String, channels: Int, sampleRate: Int): LocalAudioCapability {
		val mime = mime(codec)
		val format = Format.Builder().setSampleMimeType(mime).setChannelCount(channels).setSampleRate(sampleRate)
			.setPcmEncoding(if (codec == "pcm_s24le") C.ENCODING_PCM_24BIT else C.ENCODING_PCM_16BIT).build()
		val native = if (mime == MimeTypes.AUDIO_RAW) sink.supportsFormat(format) else mime != null && runCatching {
			MediaCodecUtil.getDecoderInfos(mime, false, false).any { it.isFormatSupported(format) }
		}.getOrDefault(false)
		val software = mime != null && runCatching { FfmpegLibrary.supportsFormat(mime) }.getOrDefault(false)
		run {
			val pcm = format.buildUpon().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
			return LocalAudioCapability(codec, channels, sampleRate, native, software,
				runCatching { sink.supportsFormat(pcm) && android.media.AudioTrack.getMinBufferSize(sampleRate,
					androidx.media3.common.util.Util.getAudioTrackChannelConfig(channels), C.ENCODING_PCM_16BIT) > 0 }.getOrDefault(false),
				mime != null && mime != MimeTypes.AUDIO_RAW && runCatching { sink.supportsFormat(format) }.getOrDefault(false))
		}
	}

	/** Rectangular profile limits: every advertised rate works at every channel count up to maxChannels. */
	data class Profile(val codec: String, val maxChannels: Int, val sampleRates: List<Int>)
	@JvmStatic fun profiles(context: Context, codecs: List<String>): List<Profile> {
		val sink = DefaultAudioSink.Builder(context).build()
		return try {
			codecs.mapNotNull { codec ->
				val maxChannels = (1..if (codec == "ac3") 6 else 8).takeWhile { channels ->
					LocalAudioDecodePolicy.evaluate(false, probe(sink, codec, channels, 48000)).canDirectPlay
				}.lastOrNull() ?: return@mapNotNull null
				val rates = (if (codec in listOf("ac3", "eac3")) listOf(32000, 44100, 48000)
					else listOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 88200, 96000, 176400, 192000))
					.filter { rate -> (1..maxChannels).all { LocalAudioDecodePolicy.evaluate(false, probe(sink, codec, it, rate)).canDirectPlay } }
				Profile(codec, maxChannels, rates)
			}
		} finally { sink.release() }
	}
}
