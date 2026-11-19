package org.jellyfin.playback.core.engine

/** Monotonic, injectable time. Never uses wall time or server timestamps. */
fun interface MonotonicClock { fun nowMs(): Long }

enum class StartupPhase { NEGOTIATING, SOURCE_SELECTED, OPENING_STREAM, PREPARING_MEDIA, INITIALIZING_DECODER, WAITING_FIRST_FRAME, PLAYING, FAILED, CANCELLED }
enum class StartupEvent { REQUEST_STARTED, HTTP_OPENED, HTTP_ERROR, BYTES_RECEIVED, MANIFEST_LOADING, MANIFEST_READY, TRACKS_DISCOVERED, DECODER_INITIALIZING, DECODER_READY, FIRST_FRAME, AUDIO_READY }

data class StartupTimeouts(
    val negotiationMs: Long = 45_000,
    val streamOpeningMs: Long = 90_000,
    val preparationMs: Long = 90_000,
    val decoderMs: Long = 30_000,
    val firstFrameMs: Long = 30_000,
) {
    init { require(listOf(negotiationMs, streamOpeningMs, preparationMs, decoderMs, firstFrameMs).all { it > 0 }) }
    fun budget(phase: StartupPhase): Long? = when (phase) {
        StartupPhase.NEGOTIATING -> negotiationMs
        StartupPhase.SOURCE_SELECTED, StartupPhase.OPENING_STREAM -> streamOpeningMs
        StartupPhase.PREPARING_MEDIA -> preparationMs
        StartupPhase.INITIALIZING_DECODER -> decoderMs
        StartupPhase.WAITING_FIRST_FRAME -> firstFrameMs
        else -> null
    }
}
data class StartupPhaseTiming(val phase: StartupPhase, val startedAtMs: Long, val elapsedMs: Long)
data class StartupTimeout(val phase: StartupPhase, val elapsedMs: Long) {
    val reason: String get() = "STARTUP_TIMEOUT_${phase.name}"
}
data class StartupSnapshot(
    val sessionId: String, val generation: Long, val phase: StartupPhase,
    val startedAtMs: Long, val phaseStartedAtMs: Long, val phaseElapsedMs: Long, val totalElapsedMs: Long,
    val lastProgressAtMs: Long, val terminalReason: String?, val errorCategory: String?,
    val selectedSource: String?, val delivery: String?, val requestState: String, val httpStatus: Int?,
    val manifestState: String, val tracksState: String, val decoderState: String, val firstFrameState: String,
    val audioState: String, val bufferingReason: String, val suspended: Boolean,
    val timeoutPhase: StartupPhase?, val history: List<StartupPhaseTiming>,
)

/** Startup observations belong to PlaybackSessionOwner; this never owns a second session or reports playback. */
internal class PlaybackStartup(
    private val id: String, private val generation: Long, private val clock: MonotonicClock,
    private val timeouts: StartupTimeouts,
) {
    private val started = clock.nowMs()
    private var phaseStarted = started
    private var phase = StartupPhase.NEGOTIATING
    private var endedAt: Long? = null
    private var phasePaused = 0L
    private var totalPaused = 0L
    private var pausedAt: Long? = null
    private var lastProgress = started
    private var lastPosition = -1L
    private var terminalReason: String? = null
    private var category: String? = null
    private var timeoutPhase: StartupPhase? = null
    private var source: String? = null
    private var delivery: String? = null
    private var request = "NOT_REQUESTED"
    private var httpStatus: Int? = null
    private var manifest = "NOT_OBSERVED"
    private var tracks = "NOT_DISCOVERED"
    private var decoder = "NOT_INITIALIZED"
    private var firstFrame = "NOT_RENDERED"
    private var audio = "NOT_INITIALIZED"
    private var buffering = "STARTUP"
    private val history = mutableListOf<StartupPhaseTiming>()
    private fun now() = endedAt ?: clock.nowMs()
    private fun phaseElapsed() = (now() - phaseStarted - phasePaused - (pausedAt?.let { now() - it } ?: 0)).coerceAtLeast(0)
    private fun settlePause() { pausedAt?.let { val n = now(); phasePaused += n - it; totalPaused += n - it; pausedAt = n } }
    fun advance(next: StartupPhase) {
        if (terminalReason != null || phase == StartupPhase.PLAYING || next.ordinal <= phase.ordinal) return
        val elapsed = phaseElapsed()
        settlePause()
        history += StartupPhaseTiming(phase, phaseStarted, elapsed)
        phase = next; phaseStarted = now(); phasePaused = 0; lastProgress = now()
    }
    fun selected(sourceId: String?, method: String?) {
        // Opaque source IDs only; never retain names, paths or URLs.
        source = sourceId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }
        delivery = method?.takeIf { it in setOf("DirectPlay", "DirectStream", "Transcode", "DIRECT_PLAY", "DIRECT_STREAM", "TRANSCODE") }
        advance(StartupPhase.SOURCE_SELECTED)
    }
    fun event(event: StartupEvent, status: Int) {
        if (terminalReason != null) return
        if (status in 100..599) httpStatus = status
        lastProgress = now()
        when (event) {
            StartupEvent.REQUEST_STARTED -> { request = "OPENING"; advance(StartupPhase.OPENING_STREAM) }
            StartupEvent.HTTP_OPENED -> { request = "OPEN"; advance(StartupPhase.PREPARING_MEDIA) }
            StartupEvent.HTTP_ERROR -> request = "HTTP_ERROR"
            StartupEvent.BYTES_RECEIVED -> { request = "RECEIVING"; advance(StartupPhase.PREPARING_MEDIA) }
            StartupEvent.MANIFEST_LOADING -> manifest = "LOADING"
            StartupEvent.MANIFEST_READY -> { manifest = "LOADED"; advance(StartupPhase.PREPARING_MEDIA) }
            StartupEvent.TRACKS_DISCOVERED -> { tracks = "DISCOVERED"; advance(StartupPhase.INITIALIZING_DECODER) }
            StartupEvent.DECODER_INITIALIZING -> { decoder = "INITIALIZING"; advance(StartupPhase.INITIALIZING_DECODER) }
            StartupEvent.DECODER_READY -> { decoder = "INITIALIZED"; advance(StartupPhase.WAITING_FIRST_FRAME) }
            StartupEvent.FIRST_FRAME -> { firstFrame = "RENDERED"; buffering = "NONE"; advance(StartupPhase.PLAYING) }
            StartupEvent.AUDIO_READY -> audio = "INITIALIZED"
        }
    }
    fun buffering(value: Boolean) { buffering = if (!value) "NONE" else if (firstFrame == "RENDERED") "REBUFFERING" else "STARTUP" }
    fun position(value: Long) { if (value > lastPosition) { lastPosition = value; lastProgress = now() } }
    fun suspend(value: Boolean) {
        if (value && pausedAt == null) pausedAt = now()
        if (!value && pausedAt != null) { settlePause(); pausedAt = null }
    }
    fun timeout(): StartupTimeout? {
        if (terminalReason != null || pausedAt != null) return null
        val budget = timeouts.budget(phase) ?: return null
        return if (phaseElapsed() >= budget) StartupTimeout(phase, phaseElapsed()) else null
    }
    fun finish(reason: String, failed: Boolean) {
        if (terminalReason != null) return
        if (reason.startsWith("STARTUP_TIMEOUT_")) timeoutPhase = phase
        category = if (timeoutPhase != null) "TIMEOUT" else if (failed) "PLAYBACK_ERROR" else "CANCELLED"
        if (phase != StartupPhase.PLAYING) {
            history += StartupPhaseTiming(phase, phaseStarted, phaseElapsed())
            settlePause(); phase = if (failed) StartupPhase.FAILED else StartupPhase.CANCELLED
            phaseStarted = now(); phasePaused = 0
        }
        terminalReason = reason; endedAt = clock.nowMs()
    }
    fun snapshot(): StartupSnapshot = StartupSnapshot(id, generation, phase, started, phaseStarted, phaseElapsed(),
        (now() - started - totalPaused - (pausedAt?.let { now() - it } ?: 0)).coerceAtLeast(0),
        lastProgress, terminalReason, category, source, delivery, request, httpStatus, manifest, tracks, decoder,
        firstFrame, audio, buffering, pausedAt != null, timeoutPhase, history.toList())
}
