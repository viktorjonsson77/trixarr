package org.jellyfin.playback.media3.exoplayer

import android.content.Context
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/** Restrict the shared sink, leaving video renderers, decoder order, PCM processing and timing intact. */
class LocalAudioRenderersFactory(context: Context, private val passthroughEnabled: Boolean) : DefaultRenderersFactory(context) {
	override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? {
		val sink = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams) ?: return null
		return if (passthroughEnabled) sink else PcmOnlyAudioSink(sink)
	}
}

class PcmOnlyAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
	override fun supportsFormat(format: Format) = format.sampleMimeType == MimeTypes.AUDIO_RAW && super.supportsFormat(format)
	override fun getFormatSupport(format: Format) =
		if (format.sampleMimeType == MimeTypes.AUDIO_RAW) super.getFormatSupport(format) else AudioSink.SINK_FORMAT_UNSUPPORTED
	override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = AudioOffloadSupport.DEFAULT_UNSUPPORTED
	override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
		if (!supportsFormat(inputFormat)) throw AudioSink.ConfigurationException("Local decoding requires supported PCM output", inputFormat)
		super.configure(inputFormat, specifiedBufferSize, outputChannels)
	}
}
