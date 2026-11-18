package org.jellyfin.androidtv.ui.playback.audio

import android.util.Size
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.jellyfin.androidtv.util.profile.*
import org.jellyfin.playback.media3.exoplayer.*
import org.jellyfin.playback.jellyfin.negotiation.assessDolbyVisionDelivery
import org.jellyfin.sdk.model.api.*

class LocalAudioIntegrationTests : FunSpec({
	fun profile(on: Boolean, decoders: List<LocalAudioSupport.Profile>, downmix: Boolean = false): DeviceProfile {
		val media = mockk<MediaCodecCapabilitiesTest>(relaxed = true)
		val size = mockk<Size> { every { width } returns 3840; every { height } returns 2160 }
		every { media.getMaxResolution(any()) } returns size
		return createDeviceProfile(media, 200000000, on, downmix, false, false, true, decoders)
	}
	val decoded = listOf(LocalAudioSupport.Profile("eac3", 6, listOf(48000)))
	fun source(): MediaSourceInfo = mockk(relaxed = true) {
		every { id } returns "same-source"
		every { container } returns "mkv"
		every { supportsDirectPlay } returns true
		every { defaultAudioStreamIndex } returns 1
		every { mediaStreams } returns listOf(
			mockk<MediaStream>(relaxed = true) { every { type } returns MediaStreamType.VIDEO; every { index } returns 0; every { dvProfile } returns 5; every { dvLevel } returns 6 },
			mockk<MediaStream>(relaxed = true) { every { type } returns MediaStreamType.AUDIO; every { index } returns 1; every { codec } returns "eac3"; every { channels } returns 6; every { sampleRate } returns 48000 })
	}

	test("actual DeviceProfile OFF retains decodable EAC3 plus channel and rate constraints") {
		val p = profile(false, decoded)
		p.directPlayProfiles.filter { it.type == DlnaProfileType.VIDEO }.any { "eac3" in it.audioCodec.orEmpty().split(',') } shouldBe true
		p.codecProfiles.any { it.codec == "eac3" && it.conditions.size == 2 } shouldBe true
		assessDolbyVisionDelivery(source(), p, PlayMethod.DIRECT_PLAY, "same-source", 1).allowed shouldBe true
	}
	test("actual DeviceProfile excludes EAC3 when local decoding is absent; DV safety still rejects") {
		val p = profile(false, emptyList())
		p.directPlayProfiles.any { "eac3" in it.audioCodec.orEmpty().split(',') } shouldBe false
		assessDolbyVisionDelivery(source(), p, PlayMethod.DIRECT_PLAY).allowed shouldBe false
	}
	test("ON keeps legacy DD+ advertisement and explicit stereo preference remains authoritative") {
		profile(true, emptyList()).directPlayProfiles.any { "eac3" in it.audioCodec.orEmpty().split(',') } shouldBe true
		profile(false, decoded, true).directPlayProfiles.any { "eac3" in it.audioCodec.orEmpty().split(',') } shouldBe false
	}
	test("PCM-only sink denies encoded bypass and offload but delegates multichannel PCM unchanged") {
		val delegate = mockk<AudioSink>(relaxed = true)
		val pcm = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_16BIT).setChannelCount(6).setSampleRate(48000).build()
		val eac3 = pcm.buildUpon().setSampleMimeType(MimeTypes.AUDIO_E_AC3).build()
		every { delegate.supportsFormat(pcm) } returns true
		every { delegate.getFormatSupport(pcm) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
		val sink = PcmOnlyAudioSink(delegate)
		sink.supportsFormat(eac3) shouldBe false
		sink.getFormatSupport(eac3) shouldBe AudioSink.SINK_FORMAT_UNSUPPORTED
		sink.getFormatOffloadSupport(eac3) shouldBe AudioOffloadSupport.DEFAULT_UNSUPPORTED
		sink.supportsFormat(pcm) shouldBe true
		sink.getFormatSupport(pcm) shouldBe AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
		sink.configure(pcm, 123, null)
		verify(exactly = 1) { delegate.configure(pcm, 123, null) }
		(runCatching { sink.configure(eac3, 0, null) }.exceptionOrNull() is AudioSink.ConfigurationException) shouldBe true
		verify(exactly = 0) { delegate.configure(eac3, any(), any()) }
		sink.release()
		verify(exactly = 1) { delegate.release() }
	}
})
