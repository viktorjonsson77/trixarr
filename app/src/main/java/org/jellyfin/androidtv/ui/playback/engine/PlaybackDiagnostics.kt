package org.jellyfin.androidtv.ui.playback.engine

import android.app.Activity
import android.app.AlertDialog
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.Format
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import org.jellyfin.androidtv.data.compat.StreamInfo
import org.jellyfin.sdk.model.api.MediaStreamType

/** Event driven, bounded, in-memory diagnostics. URLs, tokens and exception messages are never retained. */
class PlaybackDiagnostics(private val player: ExoPlayer, private val engine: Media3PlayerEngine) : AnalyticsListener {
	private var sessionOwner: org.jellyfin.playback.core.engine.PlaybackSessionOwner? = null
	fun session(owner: org.jellyfin.playback.core.engine.PlaybackSessionOwner) { sessionOwner = owner }
	fun policy(decision: org.jellyfin.playback.core.engine.CapabilityDecision, reason: String, fallback: String?) {
		put("DEVICE CAPABILITY / Policy", decision.permission)
		decision.evidence.forEach { (key, value) -> put("DEVICE CAPABILITY / $key", value) }
		put("PLAYER / Engine selection reason", reason)
		put("ERRORS / Fallback", fallback ?: "NONE")
		put("SELECTED DECODER / Selection reason", "Media3 default ordering; policy blocks unverified DV HEVC fallback; see actual decoder events")
	}
	private val fields = linkedMapOf<String, String>()
	private var dropped = 0
	private var lastFailure: String? = null
	private var dialog: AlertDialog? = null
	private var text: TextView? = null
	private fun put(key: String, value: Any?) { fields[key] = DiagnosticValues.value(value) }
	fun initializationFailure(error: Exception) {
		lastFailure = error.javaClass.simpleName
		put("ERRORS / Engine initialization", lastFailure)
	}
	fun audioPolicy(context: android.content.Context, passthroughEnabled: Boolean, info: StreamInfo) {
		val audio = info.mediaSource?.mediaStreams.orEmpty().firstOrNull {
			it.type == MediaStreamType.AUDIO && it.index == info.mediaSource?.defaultAudioStreamIndex
		}
		put("SOURCE AUDIO / Codec", audio?.codec)
		put("DEVICE CAPABILITY / Passthrough enabled", passthroughEnabled)
		val capability = audio?.codec?.takeIf { org.jellyfin.playback.media3.exoplayer.LocalAudioSupport.mime(it) != null }?.let { org.jellyfin.playback.media3.exoplayer.LocalAudioSupport.probe(context, it, audio.channels ?: -1, audio.sampleRate ?: -1) }
		put("DEVICE CAPABILITY / Local native audio decoder", capability?.nativeDecoder)
		put("DEVICE CAPABILITY / Local FFmpeg audio decoder", capability?.softwareDecoder)
		put("DEVICE CAPABILITY / CAN_DECODE_AUDIO", capability?.canDecode)
		put("DEVICE CAPABILITY / CAN_PASSTHROUGH_AUDIO", capability?.passthrough)
		put("SELECTED AUDIO PATH / Requested mode", if (passthroughEnabled) "Encoded or local (Media3)" else "Local decode to PCM only")
		put("PHYSICAL AVR VERIFICATION / Output", "NOT VERIFIED")
	}
	fun source(info: StreamInfo) {
		fields.keys.removeAll { it.startsWith("SOURCE FORMAT /") || it.startsWith("VIDEO / MediaCodec") || it.startsWith("VIDEO / Decoder input") || it.startsWith("ERRORS /") }
		fields.keys.removeAll { it.startsWith("NEGOTIATION /") }
		info.negotiationDecision?.let { decision ->
			put("NEGOTIATION / Source DV profile", decision.evidence.profile)
			put("NEGOTIATION / Selected source", info.mediaSourceId)
			put("NEGOTIATION / Requested preference", "Original video; conversion requires verified DV signaling")
			put("NEGOTIATION / Server-selected method", info.playMethod)
			put("NEGOTIATION / Server DirectPlay", info.mediaSource?.supportsDirectPlay)
			put("NEGOTIATION / Server DirectStream", info.mediaSource?.supportsDirectStream)
			put("NEGOTIATION / Server Transcoding", info.mediaSource?.supportsTranscoding)
			put("NEGOTIATION / Source audio selection", info.mediaSource?.defaultAudioStreamIndex)
			put("NEGOTIATION / Container signaling verified", decision.evidence.containerSignalingVerified)
			put("NEGOTIATION / Converted audio compatibility", decision.evidence.convertedAudioCompatible)
			put("NEGOTIATION / Client audio declaration (not HDMI)", decision.evidence.audioCompatible)
			put("NEGOTIATION / Server advertised", decision.evidence.serverAdvertised)
			put("NEGOTIATION / Video copy verified", decision.evidence.videoCopyVerified)
			put("NEGOTIATION / DV preservation", decision.preservation)
			put("NEGOTIATION / Allowed", decision.allowed)
			put("NEGOTIATION / Reason", decision.reason)
		}
		dropped = 0
		put("ERRORS / Previous attempt", lastFailure)
		put("PLAYER / Delivery", info.playMethod)
		put("PLAYER / URL type", info.mediaUrl?.substringBefore(':')?.takeIf { it in listOf("http", "https", "file", "content") })
		put("SOURCE FORMAT / Container (Jellyfin)", info.mediaSource?.container)
		info.mediaSource?.mediaStreams?.filter { it.type == MediaStreamType.VIDEO || it.type == MediaStreamType.AUDIO }?.forEach { stream ->
			val prefix = "SOURCE FORMAT / ${stream.type} #${stream.index} (Jellyfin)"
			put("$prefix codec", stream.codec)
			put("$prefix profile", stream.profile)
			put("$prefix bitrate", stream.bitRate)
			if (stream.type == MediaStreamType.VIDEO) {
				put("$prefix resolution", "${DiagnosticValues.value(stream.width)} × ${DiagnosticValues.value(stream.height)}")
				put("$prefix fps", stream.realFrameRate)
				put("$prefix HDR", stream.videoRangeType)
				put("$prefix DV profile", stream.dvProfile)
				put("$prefix DV level", stream.dvLevel)
				put("$prefix primaries", stream.colorPrimaries)
				put("$prefix transfer", stream.colorTransfer)
				put("$prefix matrix", stream.colorSpace)
				put("$prefix bit depth", stream.bitDepth)
			} else {
				put("$prefix channels", stream.channels)
				put("$prefix layout", stream.channelLayout)
				put("$prefix sample rate", stream.sampleRate)
			}
		}
	}
	private fun format(prefix: String, format: Format?) {
		put("$prefix codec", format?.sampleMimeType)
		put("$prefix codecs", format?.codecs)
		put("$prefix bitrate", format?.bitrate)
		if (prefix.startsWith("VIDEO")) {
			put("$prefix resolution", format?.let { "${it.width} × ${it.height}" })
			put("$prefix fps", format?.frameRate)
			put("$prefix color (Media3)", format?.colorInfo)
			put("$prefix HDR", DiagnosticValues.hdr(format?.sampleMimeType, format?.codecs, format?.colorInfo?.colorTransfer, format?.colorInfo?.hdrStaticInfo != null))
			put("$prefix DV profile / level", DiagnosticValues.dolbyVision(format?.codecs))
		} else {
			put("$prefix track id", format?.id)
			put("$prefix channels", format?.channelCount)
			put("$prefix sample rate", format?.sampleRate)
		}
	}
	override fun onVideoDecoderInitialized(time: AnalyticsListener.EventTime, name: String, timestamp: Long, duration: Long) {
		put("VIDEO / Decoder", name)
		val codec = runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name == name } }.getOrNull()
		put("VIDEO / Hardware acceleration (Android)", if (Build.VERSION.SDK_INT >= 29) codec?.isHardwareAccelerated else null)
	}
	override fun onVideoDecoderReleased(time: AnalyticsListener.EventTime, name: String) {
		put("VIDEO / Decoder", null)
		put("VIDEO / Hardware acceleration (Android)", null)
	}
	override fun onAudioDecoderReleased(time: AnalyticsListener.EventTime, name: String) { put("AUDIO / Decoder", null) }
	override fun onAudioTrackReleased(time: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) {
		fields.keys.filter { it.startsWith("ACTUAL AUDIO OUTPUT /") || it.startsWith("AUDIO / AudioTrack") || it.startsWith("AUDIO / Output encoding") || it == "AUDIO / Passthrough selected" }.forEach { put(it, null) }
	}
	override fun onAudioDecoderInitialized(time: AnalyticsListener.EventTime, name: String, timestamp: Long, duration: Long) { put("AUDIO / Decoder", name) }
	override fun onVideoInputFormatChanged(time: AnalyticsListener.EventTime, format: Format, reuse: androidx.media3.exoplayer.DecoderReuseEvaluation?) {
		format("VIDEO / Decoder input", format)
		put("VIDEO / Decoder reuse result", reuse?.result)
		put("VIDEO / Decoder reuse discard reasons", reuse?.discardReasons)
	}
	override fun onDroppedVideoFrames(time: AnalyticsListener.EventTime, count: Int, elapsed: Long) { dropped += count }
	override fun onPlayerError(time: AnalyticsListener.EventTime, error: PlaybackException) { lastFailure = error.errorCodeName; put("ERRORS / Playback", error.errorCodeName) }
	override fun onVideoCodecError(time: AnalyticsListener.EventTime, error: Exception) { put("ERRORS / Video decoder", error.javaClass.simpleName) }
	override fun onAudioCodecError(time: AnalyticsListener.EventTime, error: Exception) { put("ERRORS / Audio decoder", error.javaClass.simpleName) }
	override fun onAudioSinkError(time: AnalyticsListener.EventTime, error: Exception) { put("ERRORS / Audio sink", error.javaClass.simpleName) }
	override fun onAudioTrackInitialized(time: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) {
		put("ACTUAL AUDIO OUTPUT / Mode", DiagnosticValues.passthrough(config.encoding))
		put("ACTUAL AUDIO OUTPUT / Channel count", Integer.bitCount(config.channelConfig))
		put("AUDIO / Output encoding (AudioTrack)", config.encoding)
		put("AUDIO / Output encoding name", DiagnosticValues.encodingName(config.encoding))
		put("AUDIO / AudioTrack sample rate", config.sampleRate)
		put("AUDIO / AudioTrack channel mask", config.channelConfig)
		put("AUDIO / AudioTrack offload", config.offload)
		put("AUDIO / AudioTrack buffer bytes", config.bufferSize)
		put("AUDIO / Passthrough selected", DiagnosticValues.passthrough(config.encoding))
	}
	override fun onEvents(player: androidx.media3.common.Player, events: AnalyticsListener.Events) { text?.text = snapshot() }
	fun mediaFormat(format: MediaFormat?) {
		put("VIDEO / MediaCodec HDR10+ metadata present", format?.containsKey("hdr10-plus-info"))
		put("VIDEO / MediaCodec HDR static metadata present", format?.containsKey("hdr-static-info"))
		listOf("mime", "width", "height", "color-standard", "color-transfer", "color-range", "profile", "level").forEach { key ->
			put("VIDEO / MediaCodec output $key", runCatching { if (format?.containsKey(key) == true) format.getString(key) else null }.getOrNull()
				?: runCatching { if (format?.containsKey(key) == true) format.getInteger(key) else null }.getOrNull())
		}
	}
	fun snapshot(): String {
		sessionOwner?.snapshot?.let { session ->
			put("SESSION / ID", session.id)
			put("SESSION / Generation", session.generation)
			put("SESSION / Reporting", session.reportingState)
			put("SESSION / Resume ms", session.resumePositionMs)
			put("SESSION / Position ms", session.positionMs)
			put("SESSION / Terminal reason", session.terminalReason)
			put("ACTUAL PLAYER STATE / Session", session.state)
		}

		format("VIDEO / Selected stream", player.videoFormat)
		format("AUDIO / Selected stream", player.audioFormat)
		put("PLAYER / Active engine", engine.id)
		put("PLAYER / Engine version", MediaLibraryInfo.VERSION)
		put("PLAYER / State", engine.state)
		put("PLAYER / Position ms (engine timeline)", engine.positionMs)
		put("PLAYER / Buffered ms", player.totalBufferedDuration)
		put("PLAYER / Dropped frames", dropped)
		put("PLAYER / Unsupported tracks (Media3)", player.currentTracks.groups.sumOf { group -> (0 until group.length).count { !group.isTrackSupported(it) } })
		put("VIDEO / TV activated HDR", DiagnosticValues.NOT_VERIFIED)
		put("AUDIO / HDMI output / Atmos / DTS:X active", DiagnosticValues.NOT_VERIFIED)
		listOf("VIDEO / Decoder", "VIDEO / Hardware acceleration (Android)", "AUDIO / Decoder", "ERRORS / Video decoder", "ERRORS / Audio decoder", "ERRORS / Audio sink", "ERRORS / Playback").forEach { fields.putIfAbsent(it, DiagnosticValues.UNKNOWN) }
		fields.putIfAbsent("AUDIO / Passthrough requested", "UNKNOWN (automatic Media3 policy)")
		fields.putIfAbsent("AUDIO / Passthrough selected", DiagnosticValues.UNKNOWN)
		fields.putIfAbsent("AUDIO / Output encoding (AudioTrack)", DiagnosticValues.UNKNOWN)
		fields.putIfAbsent("ERRORS / Fallback", "No engine fallback; existing server retry policy")
		return fields.entries.joinToString("\n\n") { "${it.key}: ${it.value}" }
	}
	fun show(activity: Activity) {
		if (dialog?.isShowing == true) return
		put("VIDEO / Android decoder MIME support", runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }.flatMap { it.supportedTypes.toList() }.distinct().joinToString() }.getOrNull())
		put("DEVICE / Android", "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
		put("DEVICE / Model", "${Build.MANUFACTURER} ${Build.MODEL}")
		if (Build.VERSION.SDK_INT >= 23) {
			val audio = activity.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
			put("AUDIO / Android advertised output encodings (not active route)", audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).joinToString { it.encodings.joinToString() })
		}
		val display = activity.windowManager.defaultDisplay
		put("DEVICE / Refresh rate", display.refreshRate)
		if (Build.VERSION.SDK_INT >= 23) put("DEVICE / Display mode", display.mode)
		if (Build.VERSION.SDK_INT >= 24) put("DEVICE / Supported HDR types (Android; 1=DV 2=HDR10 3=HLG 4=HDR10+)", display.hdrCapabilities.supportedHdrTypes.joinToString())
		text = TextView(activity).apply { textSize = 18f; setTextColor(android.graphics.Color.WHITE); setBackgroundColor(android.graphics.Color.rgb(25, 25, 25)); setPadding(32, 24, 32, 24); text = snapshot() }
		val scroll = ScrollView(activity).apply { addView(text); isFocusable = true }
		dialog = AlertDialog.Builder(activity).setTitle("Playback diagnostics · D-pad to scroll")
			.setView(scroll).setPositiveButton("Close", null).create().also {
				it.setOnDismissListener { text = null; dialog = null }
				it.show()
				scroll.requestFocus()
			}
	}
	fun release() { dialog?.dismiss(); player.removeAnalyticsListener(this) }
}
