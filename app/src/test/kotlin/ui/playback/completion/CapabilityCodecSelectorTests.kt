package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.mockk.*
import org.jellyfin.androidtv.ui.playback.engine.CapabilityCodecSelector
import org.jellyfin.playback.core.engine.*
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

class CapabilityCodecSelectorTests : FunSpec({
 test("DV only permits HEVC fallback with explicit compatible base layer and resets for SDR") {
  val delegate = mockk<MediaCodecSelector>(relaxed = true)
  val selector = CapabilityCodecSelector(delegate)
  val unknown = CapabilityDecision(PlaybackPermission.REQUIRES_VERIFICATION, emptyMap(), emptyList(), false)
  for (profile in listOf(5, 7, 8)) {
   selector.configure(MediaCapabilityInput(dvProfile = profile), unknown)
   selector.getDecoderInfos("video/hevc", false, false)
  }
  verify(exactly = 0) { delegate.getDecoderInfos("video/hevc", any(), any()) }
  selector.getDecoderInfos("video/dolby-vision", true, false)
  verify { delegate.getDecoderInfos("video/dolby-vision", true, false) }
  selector.configure(MediaCapabilityInput(dvProfile = 8, dvCompatibilityId = 1), unknown.copy(hevcFallbackPermitted = true))
  selector.getDecoderInfos("video/hevc", false, false)
  selector.configure(MediaCapabilityInput(hdr = "SDR"), unknown)
  selector.getDecoderInfos("video/hevc", false, false)
  verify(exactly = 2) { delegate.getDecoderInfos("video/hevc", false, false) }
 }
})
