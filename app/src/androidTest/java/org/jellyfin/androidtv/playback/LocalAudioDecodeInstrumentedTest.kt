package org.jellyfin.androidtv.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioSink
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.jellyfin.androidtv.util.profile.createDeviceProfile
import org.jellyfin.androidtv.util.profile.MediaCodecCapabilitiesTest
import org.jellyfin.playback.core.engine.AudioOutputPath
import org.jellyfin.playback.core.engine.LocalAudioDecodePolicy
import org.jellyfin.playback.media3.exoplayer.LocalAudioSupport
import org.jellyfin.playback.media3.exoplayer.PcmOnlyAudioSink
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAudioDecodeInstrumentedTest {
	@Test fun actualShieldEac3LocalPathAndDeviceProfile() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val capability = LocalAudioSupport.probe(context, "eac3", 6, 48000)
		assertTrue("Native or packaged software EAC3 decoder required", capability.canDecode)
		assertEquals(AudioOutputPath.LOCAL_PCM, LocalAudioDecodePolicy.evaluate(false, capability).path)
		val profile = createDeviceProfile(MediaCodecCapabilitiesTest(context), 200000000, false, false, false, false, true)
		assertTrue(profile.directPlayProfiles.filter { it.type == DlnaProfileType.VIDEO }.any { "eac3" in it.audioCodec.orEmpty().split(',') })
		assertTrue(profile.codecProfiles.any { it.codec == "eac3" })
	}
	@Test fun actualMedia3SinkRefusesEncodedBypassAndPreservesSixChannelPcm() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val sink = PcmOnlyAudioSink(DefaultAudioSink.Builder(context).build())
		try {
			val pcm = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_16BIT).setChannelCount(6).setSampleRate(48000).build()
			assertTrue(sink.supportsFormat(pcm))
			val encoded = pcm.buildUpon().setSampleMimeType(MimeTypes.AUDIO_E_AC3).build()
			assertFalse(sink.supportsFormat(encoded))
			assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, sink.getFormatSupport(encoded))
		} finally { sink.release() }
	}
}
