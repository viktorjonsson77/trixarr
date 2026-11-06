package org.jellyfin.androidtv.ui.playback.engine

/** UI-thread gate for asynchronous server/segment callbacks. Does not send reports itself. */
class PlaybackAttemptGate {
	private var generation = 0L
	private var prepared = false
	private var started = false
	fun begin(): Long { generation++; prepared = false; started = false; return generation }
	fun isCurrent(attempt: Long) = attempt == generation
	fun claimPreparation(attempt: Long): Boolean {
		if (!isCurrent(attempt) || prepared) return false
		prepared = true
		return true
	}
	fun claimStart(attempt: Long): Boolean {
		if (!isCurrent(attempt) || !prepared || started) return false
		started = true
		return true
	}
	/** Returns whether this attempt needs a stop report; invalidates delayed starts. */
	fun stop(): Boolean {
		val report = started
		begin()
		return report
	}
}
