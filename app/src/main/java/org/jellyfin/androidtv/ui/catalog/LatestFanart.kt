package org.jellyfin.androidtv.ui.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

internal const val FANART_DWELL_MS = 500L
internal const val FANART_CROSSFADE_MS = 1600

/** A completed old request cannot publish, even if its transport ignores cancellation. */
internal class LatestFanart<T>(private val scope: CoroutineScope, private val dwellMs: Long = FANART_DWELL_MS) {
    private var generation = 0L
    private var job: Job? = null

    fun submit(load: suspend () -> T?, publish: (T?) -> Unit, stabilize: Boolean = true) {
        val expected = ++generation
        job?.cancel()
        job = scope.launch {
            if (stabilize) delay(dwellMs)
            val image = load()
            ensureActive()
            if (expected == generation) publish(image)
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
    }
}
