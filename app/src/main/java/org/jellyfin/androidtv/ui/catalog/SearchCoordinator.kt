package org.jellyfin.androidtv.ui.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Generation check also protects against transports which finish after cancellation. */
internal class SearchCoordinator<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (String) -> T,
) {
    private var job: Job? = null
    private var generation = 0L

    fun submit(query: String, loading: () -> Unit, result: (Result<T>) -> Unit) {
        val expected = ++generation
        job?.cancel()
        if (query.isBlank()) return
        job =
            scope.launch {
                delay(350)
                loading()
                val outcome = runCatching { fetch(query.trim()) }
                ensureActive()
                if (generation == expected) result(outcome)
            }
    }

    fun cancel() {
        generation++
        job?.cancel()
    }
}
