package org.jellyfin.androidtv.ui.catalog

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LatestFanartTests : StringSpec({
    "fanart uses calm timing and does not load passed titles during dwell" {
        runTest {
            FANART_DWELL_MS shouldBe 500L
            FANART_CROSSFADE_MS shouldBe 1600
            val loader = LatestFanart<String>(this)
            val loads = mutableListOf<String>()
            val published = mutableListOf<String?>()
            repeat(12) { index ->
                loader.submit({ loads.add("$index"); "$index" }, published::add)
                runCurrent()
                advanceTimeBy(30)
            }
            advanceTimeBy(469)
            runCurrent()
            loads shouldBe emptyList()
            advanceTimeBy(1)
            runCurrent()
            loads shouldBe listOf("11")
            published shouldBe listOf("11")
        }
    }
    "leaving cancels pending dwell without a load" {
        runTest {
            val loader = LatestFanart<String>(this)
            var loaded = false
            loader.submit({ loaded = true; "image" }, {})
            runCurrent()
            loader.cancel()
            advanceTimeBy(1000)
            runCurrent()
            loaded shouldBe false
        }
    }

    "late non cancellable image never replaces latest focused title" {
        runTest {
            val loader = LatestFanart<String>(this, dwellMs = 0)
            val old = CompletableDeferred<String>()
            val published = mutableListOf<String?>()
            loader.submit({ withContext(NonCancellable) { old.await() } }, published::add)
            runCurrent()
            loader.submit({ "latest" }, published::add)
            runCurrent()
            old.complete("old")
            runCurrent()
            published shouldBe listOf("latest")
        }
    }
    "missing image is reported without fabricated fallback" {
        runTest {
            val loader = LatestFanart<String>(this, dwellMs = 0)
            val published = mutableListOf<String?>()
            loader.submit({ "real" }, published::add)
            runCurrent()
            loader.submit({ null }, published::add)
            runCurrent()
            published shouldBe listOf("real", null)
        }
    }
})
