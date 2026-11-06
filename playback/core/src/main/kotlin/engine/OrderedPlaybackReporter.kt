package org.jellyfin.playback.core.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Accepted immutable start/stop intents stay ordered, even if their view is destroyed. No retries. */
class OrderedPlaybackReporter(scope: CoroutineScope) {
	private class Command(val owner: PlaybackSessionOwner, val session: PlaybackSessionSnapshot, val kind: ReportKind, val send: suspend () -> Unit)
	private val queue = Channel<Command>(Channel.UNLIMITED)
	init {
		scope.launch {
			for (command in queue) {
				if (command.kind == ReportKind.PROGRESS && !command.owner.isCurrent(command.session.generation)) continue
				try {
					command.send()
					command.owner.reportingResult(command.session.id, "${command.kind}_SENT")
				} catch (error: Exception) {
					command.owner.reportingResult(command.session.id, "${command.kind}_FAILED:${error.javaClass.simpleName}")
				}
			}
		}
	}
	fun enqueue(owner: PlaybackSessionOwner, session: PlaybackSessionSnapshot, kind: ReportKind, send: suspend () -> Unit): Boolean {
		if (!owner.claimReport(session.id, kind)) return false
		return queue.trySend(Command(owner, session, kind, send)).isSuccess
	}
	fun close() { queue.close() }
}
