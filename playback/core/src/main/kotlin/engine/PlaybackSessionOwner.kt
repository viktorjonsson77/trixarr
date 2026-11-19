package org.jellyfin.playback.core.engine

import java.util.UUID

enum class SessionState { PREPARING, READY, PLAYING, PAUSED, STOPPED, COMPLETED, ERROR, CANCELLED }
enum class ReportKind { START, PROGRESS, STOP }
data class PlaybackSessionSnapshot(
	val id: String, val generation: Long, val mediaId: String, val resumePositionMs: Long,
	val positionMs: Long, val engine: String?, val state: SessionState,
	val started: Boolean = false, val terminalReason: String? = null,
	val reportingState: String = "NOT_STARTED",
)

/** One owner per player context, used by legacy and core's Jellyfin reporting service. */
class PlaybackSessionOwner @JvmOverloads constructor(
    private val clock: MonotonicClock = MonotonicClock { System.nanoTime() / 1_000_000 },
    private val startupTimeouts: StartupTimeouts = StartupTimeouts(),
) {
    private var startup: PlaybackStartup? = null
    val startupSnapshot: StartupSnapshot? @Synchronized get() = startup?.snapshot()
    @Synchronized fun startupPhase(attempt: Long, phase: StartupPhase) { if (isCurrent(attempt)) startup?.advance(phase) }
    @Synchronized fun startupSource(attempt: Long, source: String?, delivery: String?) { if (isCurrent(attempt)) startup?.selected(source, delivery) }
    @JvmOverloads @Synchronized fun startupEvent(attempt: Long, event: StartupEvent, status: Int = -1) { if (isCurrent(attempt)) startup?.event(event, status) }
    @Synchronized fun startupBuffering(attempt: Long, value: Boolean) { if (isCurrent(attempt)) startup?.buffering(value) }
    @Synchronized fun startupSuspend(attempt: Long, value: Boolean) { if (isCurrent(attempt)) startup?.suspend(value) }
    @Synchronized fun startupTimeout(attempt: Long): StartupTimeout? = if (isCurrent(attempt)) startup?.timeout() else null

	private var generation = 0L
	private var cleanupFailed = false
	private var prepared = false
	private var startReport = false
	private var stopReport = false
	private var cleanup: Runnable? = null
	var snapshot: PlaybackSessionSnapshot? = null
		private set

	@JvmOverloads @Synchronized fun begin(mediaId: String = "unknown", resumeMs: Long = 0): Long {
		finish("REPLACED", SessionState.CANCELLED)
		check(!cleanupFailed) { "Previous session cleanup failed" }
		generation++
		prepared = false; startReport = false; stopReport = false
		snapshot = PlaybackSessionSnapshot(UUID.randomUUID().toString(), generation, mediaId, resumeMs.coerceAtLeast(0), resumeMs.coerceAtLeast(0), null, SessionState.PREPARING)
        startup = PlaybackStartup(snapshot!!.id, generation, clock, startupTimeouts)
		return generation
	}
	@Synchronized fun isCurrent(attempt: Long) = snapshot?.let { it.generation == attempt && it.terminalReason == null } == true
	@Synchronized fun claimPreparation(attempt: Long): Boolean {
		if (!isCurrent(attempt) || prepared) return false
		prepared = true
		snapshot = snapshot?.copy(state = SessionState.READY)
		return true
	}
	@Synchronized fun bindEngine(attempt: Long, engine: String, onEnd: Runnable) {
		check(isCurrent(attempt))
		check(cleanup == null) { "Session already owns an engine" }
		cleanup = onEnd
		snapshot = snapshot?.copy(engine = engine)
	}
	@Synchronized fun selectedEngine(attempt: Long, engine: String) {
		if (isCurrent(attempt)) snapshot = snapshot?.copy(engine = engine)
	}
	@Synchronized fun claimStart(attempt: Long): Boolean {
		if (!isCurrent(attempt) || !prepared || snapshot?.started == true) return false
		snapshot = snapshot?.copy(started = true, state = SessionState.PLAYING)
		return true
	}
	@Synchronized fun update(attempt: Long, positionMs: Long, paused: Boolean): Boolean {
		if (!isCurrent(attempt)) return false
		startup?.position(positionMs)
		snapshot = snapshot?.copy(positionMs = positionMs.coerceAtLeast(0), state = if (paused) SessionState.PAUSED else if (snapshot?.started == true) SessionState.PLAYING else SessionState.READY)
		return true
	}
	@Synchronized fun claimReport(id: String, kind: ReportKind): Boolean {
		val current = snapshot ?: return false
		if (current.id != id || !current.started) return false
		return when (kind) {
			ReportKind.START -> if (startReport || current.terminalReason != null) false else { startReport = true; snapshot = current.copy(reportingState = "START_QUEUED"); true }
			ReportKind.PROGRESS -> startReport && current.terminalReason == null
			ReportKind.STOP -> if (!startReport || stopReport || current.terminalReason == null) false else { stopReport = true; snapshot = current.copy(reportingState = "STOP_QUEUED"); true }
		}
	}
	@Synchronized fun reportingResult(id: String, result: String) { if (snapshot?.id == id) snapshot = snapshot?.copy(reportingState = result) }
	@JvmOverloads @Synchronized fun finish(reason: String, state: SessionState = SessionState.STOPPED): Boolean {
		val current = snapshot ?: return false
		if (current.terminalReason != null) return false
		startup?.finish(reason, state == SessionState.ERROR)
		snapshot = current.copy(state = state, terminalReason = reason)
		val release = cleanup; cleanup = null
		try { release?.run() } catch (error: Exception) { cleanupFailed = true; snapshot = snapshot?.copy(state = SessionState.ERROR, terminalReason = "CLEANUP_${error.javaClass.simpleName}") }
		return current.started
	}
	fun stop() = finish("STOPPED")
}
