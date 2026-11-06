package org.jellyfin.androidtv.ui.playback.engine

object DiagnosticValues {
	const val UNKNOWN = "UNKNOWN"
	const val NOT_VERIFIED = "NOT VERIFIED"
	fun value(value: Any?): String = if (value == null || value == -1 || value == -1f) UNKNOWN else value.toString()
	/** Only an explicit RFC 6381 DV codec string supplies profile and level. Never infer from HEVC. */
	fun dolbyVision(codecs: String?): Pair<Int, Int>? {
		val match = Regex("(?:dvhe|dvh1|dvav|dva1)\\.(\\d{2})\\.(\\d{2})", RegexOption.IGNORE_CASE)
			.matchEntire(codecs.orEmpty().trim()) ?: return null
		return match.groupValues[1].toInt() to match.groupValues[2].toInt()
	}
	fun hdr(mime: String?, codecs: String?, transfer: Int?, staticInfo: Boolean): String = when {
		mime == "video/dolby-vision" || dolbyVision(codecs) != null -> "Dolby Vision"
		transfer == 7 -> "HLG"
		transfer == 6 && staticInfo -> "HDR10-compatible PQ + static metadata; HDR10+ UNKNOWN"
		transfer == 6 -> "PQ; HDR10/HDR10+ UNKNOWN"
		transfer == 3 -> "SDR"
		else -> UNKNOWN
	}
	fun encodingName(encoding: Int): String = when (encoding) {
		2 -> "PCM 16-bit"
		3 -> "PCM 8-bit"
		4 -> "PCM float"
		5 -> "AC3"
		6 -> "EAC3"
		7 -> "DTS"
		8 -> "DTS-HD (MA / DTS:X not verified)"
		13 -> "IEC61937"
		14 -> "TrueHD (Atmos not verified)"
		18 -> "EAC3 JOC (Atmos HDMI not verified)"
		else -> UNKNOWN
	}
	fun passthrough(encoding: Int?): String = when (encoding) {
		null -> UNKNOWN
		2, 3, 4, 21, 22, 0x20000000, 0x30000000 -> "PCM selected (no encoded passthrough)"
		5, 6, 7, 8, 13, 14, 18 -> "Encoded AudioTrack selected; HDMI $NOT_VERIFIED"
		else -> UNKNOWN
	}
}
