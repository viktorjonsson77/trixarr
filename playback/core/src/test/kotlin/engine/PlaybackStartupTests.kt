package org.jellyfin.playback.core.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PlaybackStartupTests : FunSpec({
    test("each startup phase has a bounded budget and releases once") {
        listOf(StartupPhase.NEGOTIATING, StartupPhase.SOURCE_SELECTED, StartupPhase.OPENING_STREAM,
            StartupPhase.PREPARING_MEDIA, StartupPhase.INITIALIZING_DECODER, StartupPhase.WAITING_FIRST_FRAME).forEach { phase ->
            var now = 0L; var releases = 0
            val owner = PlaybackSessionOwner(MonotonicClock { now })
            val generation = owner.begin("fixture", 1000)
            owner.bindEngine(generation, "fixture", Runnable { releases++ })
            owner.startupPhase(generation, phase)
            now = 200_000
            val timeout = owner.startupTimeout(generation)!!
            timeout.phase shouldBe phase
            owner.finish(timeout.reason, SessionState.ERROR)
            owner.finish(timeout.reason, SessionState.ERROR)
            releases shouldBe 1
            owner.startupSnapshot!!.errorCategory shouldBe "TIMEOUT"
        }
    }
    test("progress bytes cannot extend preparation forever") {
        var now = 0L
        val owner = PlaybackSessionOwner(MonotonicClock { now })
        val generation = owner.begin()
        owner.startupEvent(generation, StartupEvent.HTTP_OPENED, 200)
        repeat(100) { now += 1000; owner.startupEvent(generation, StartupEvent.BYTES_RECEIVED) }
        owner.startupTimeout(generation)!!.phase shouldBe StartupPhase.PREPARING_MEDIA
    }
    test("stale events cannot mutate a replacement session") {
        var now = 0L
        val owner = PlaybackSessionOwner(MonotonicClock { now })
        val old = owner.begin()
        val current = owner.begin()
        owner.startupEvent(old, StartupEvent.FIRST_FRAME)
        owner.startupSnapshot!!.phase shouldBe StartupPhase.NEGOTIATING
        owner.startupEvent(current, StartupEvent.FIRST_FRAME)
        now = 500_000
        owner.startupTimeout(current) shouldBe null
    }
})
