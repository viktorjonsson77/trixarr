package org.jellyfin.androidtv.ui.startup

import java.util.concurrent.atomic.AtomicBoolean

class LaunchHistory {
    private val claimed = AtomicBoolean(false)
    fun claimColdStart(): Boolean = claimed.compareAndSet(false, true)
}
internal val processLaunchHistory = LaunchHistory()
object LaunchTimeline {
    const val DURATION_MS = 350
    const val TRANSITION_MS = 350
}
