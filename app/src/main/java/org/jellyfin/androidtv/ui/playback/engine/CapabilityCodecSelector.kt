package org.jellyfin.androidtv.ui.playback.engine

import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import org.jellyfin.playback.core.engine.CapabilityDecision
import org.jellyfin.playback.core.engine.MediaCapabilityInput

/** Only restricts unsafe DV base-layer fallback; codec order and other MIME queries are unchanged. */
class CapabilityCodecSelector(private val delegate: MediaCodecSelector = MediaCodecSelector.DEFAULT) : MediaCodecSelector {
	@Volatile private var blockHevc = false
	fun configure(media: MediaCapabilityInput, decision: CapabilityDecision) {
		blockHevc = !media.converted && (media.dvProfile != null || media.hdr == "DV") && !decision.hevcFallbackPermitted
	}
	override fun getDecoderInfos(mimeType: String, requiresSecureDecoder: Boolean, requiresTunnelingDecoder: Boolean) =
		if (blockHevc && mimeType == "video/hevc") emptyList()
		else delegate.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
}
