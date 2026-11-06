package org.jellyfin.playback.core.engine

/** Commands run on the playback thread. Sessions/reporting remain owned by PlaybackController. */
interface PlayerEngine<T> {
	val id: String
	val state: String
	val error: String? get() = null
	val positionMs: Long
	fun supports(request: T): Boolean
	/** Must not start playback or report a server session. Partial failure must be releasable. */
	fun prepare(request: T)
	fun play()
	fun pause()
	fun stop()
	fun seek(positionMs: Long)
	fun selectTrack(type: Int, group: Int, track: Int): Boolean
	fun release()
}

/** One owner, no active-session fallback. Candidates may only fail over before prepare succeeds. */
class PlayerEngineManager<T>(private val candidates: List<() -> PlayerEngine<T>>) {
	var active: PlayerEngine<T>? = null
		private set
	var fallbackReason: String? = null
		private set
	var selectionReason: String = "Default registered engine"
		private set
	var capabilityDecision: CapabilityDecision? = null
		private set
	fun evaluate(media: MediaCapabilityInput, device: DeviceCapabilitySnapshot): CapabilityDecision {
		val decision = PlayerCapabilityPolicy().evaluate(media, device, active != null || nextCandidate < candidates.size)
		capabilityDecision = decision
		selectionReason = "${active?.id ?: "registered engine"}: ${decision.permission}; ${decision.reasons.joinToString()}"
		return decision
	}
	private var released = false
	private var prepared = false
	private var nextCandidate = 0
	private var failure: Exception? = null
	private val failedEngines = mutableSetOf<String>()

	/** Allows the legacy owner to attach its Surface before the first request arrives. */
	fun initialize(): PlayerEngine<T> {
		check(!released)
		active?.let { return it }
		while (nextCandidate < candidates.size) {
			try {
				val candidate = candidates[nextCandidate++]()
				if (candidate.id in failedEngines) {
					try { candidate.release() } catch (error: Exception) { released = true; throw error }
					continue
				}
				active = candidate
				return candidate
			} catch (error: Exception) {
				if (released) throw error
				failure = error
				fallbackReason = error.javaClass.simpleName
			}
		}
		throw IllegalStateException("No available playback engine", failure)
	}

	private fun discard() {
		val previous = active
		previous?.let { failedEngines += it.id }
		active = null
		try { previous?.release() } catch (error: Exception) { released = true; throw error }
	}

	fun prepare(request: T) {
		check(!released)
		check(capabilityDecision?.canAttempt != false) { "Capability policy rejected playback" }
		if (prepared) { requireNotNull(active).prepare(request); return }
		while (true) {
			val candidate = initialize()
			try {
				if (!candidate.supports(request)) {
					fallbackReason = "Unsupported request"
					discard()
					continue
				}
				candidate.prepare(request)
				prepared = true
				return
			} catch (error: Exception) {
				if (released) throw error
				failure = error
				fallbackReason = error.javaClass.simpleName
				// Cleanup failure forbids fallback: a decoder/audio sink may still be alive.
				discard()
			}
		}
	}

	fun play() = requireNotNull(active).play()
	fun pause() = requireNotNull(active).pause()
	fun stop() { active?.stop() }
	fun seek(positionMs: Long) { require(positionMs >= 0); requireNotNull(active).seek(positionMs) }
	fun release() {
		if (released) return
		released = true
		val previous = active
		active = null
		previous?.release()
	}
}
