package org.jellyfin.playback.core.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.jellyfin.playback.core.backend.*
import org.jellyfin.playback.core.model.PlayState

class BackendOwnershipTests : FunSpec({
 test("replaced backend callbacks are isolated and release is once") {
  val service = BackendService()
  val first = mockk<PlayerBackend>(relaxed = true)
  val second = mockk<PlayerBackend>(relaxed = true)
  val callback = slot<PlayerBackendEventListener>()
  every { first.setListener(capture(callback)) } just Runs
  val listener = mockk<PlayerBackendEventListener>(relaxed = true)
  service.addListener(listener)
  service.switchBackend(first)
  val stale = callback.captured
  service.switchBackend(first)
  service.switchBackend(second)
  stale.onPlayStateChange(PlayState.PLAYING)
  verify(exactly = 0) { listener.onPlayStateChange(any()) }
  service.release(); service.release()
  verify(exactly = 1) { first.release() }
  verify(exactly = 1) { second.release() }
 }
 test("cleanup failure prevents a second session and never releases twice") {
  val owner = PlaybackSessionOwner()
  val generation = owner.begin("item", 5000)
  var releases = 0
  owner.bindEngine(generation, "engine", Runnable { releases++; error("cleanup") })
  owner.finish("STOPPED"); owner.finish("STOPPED")
  runCatching { owner.begin("second") }.isFailure shouldBe true
  releases shouldBe 1
  owner.snapshot!!.state shouldBe SessionState.ERROR
 }
})
