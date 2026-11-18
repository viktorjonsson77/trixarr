package org.jellyfin.playback.core.engine

/** Audio transport and decoding are independent. None of these facts verifies an AVR. */
data class LocalAudioCapability(
	val codec: String,
	val channels: Int,
	val sampleRate: Int,
	val nativeDecoder: Boolean,
	val softwareDecoder: Boolean,
	val pcmOutput: Boolean,
	val passthrough: Boolean,
) {
	val canDecode get() = nativeDecoder || softwareDecoder
}

enum class AudioOutputPath { ENCODED_OR_LOCAL, LOCAL_PCM, UNSUPPORTED }

data class LocalAudioDecodeDecision(val path: AudioOutputPath, val capability: LocalAudioCapability) {
	val canDirectPlay get() = path != AudioOutputPath.UNSUPPORTED
}

/** Supplies audio compatibility to the existing profile/DV policy; never authorizes video conversion. */
object LocalAudioDecodePolicy {
	fun evaluate(passthroughEnabled: Boolean, capability: LocalAudioCapability): LocalAudioDecodeDecision {
		val validFormat = capability.channels in 1..8 && capability.sampleRate > 0
		val local = validFormat && capability.canDecode && capability.pcmOutput
		val path = when {
			validFormat && passthroughEnabled && capability.passthrough -> AudioOutputPath.ENCODED_OR_LOCAL
			local -> AudioOutputPath.LOCAL_PCM
			else -> AudioOutputPath.UNSUPPORTED
		}
		return LocalAudioDecodeDecision(path, capability)
	}
}
