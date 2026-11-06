package org.jellyfin.androidtv.ui.playback.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
	try { block() } catch (error: Throwable) { check(error is T); return error }
	error("Expected exception")
}

private class FakeEngine(private val fail: Boolean = false, private val supported: Boolean = true) : PlayerEngine<Long> {
	override val id = "fake"
	override var state = "IDLE"
	override var positionMs = 0L
	var releases = 0
	var prepares = 0
	var cleanupFails = false
	override fun supports(request: Long) = supported
	override fun prepare(request: Long) { prepares++; if (fail) error("init"); positionMs = request; state = "READY" }
	override fun play() { state = "PLAYING" }
	override fun pause() { state = "PAUSED" }
	override fun stop() { state = "STOPPED" }
	override fun seek(positionMs: Long) { this.positionMs = positionMs }
	override fun selectTrack(type: Int, group: Int, track: Int) = true
	override fun release() { releases++; if (cleanupFails) error("cleanup"); state = "RELEASED" }
}

class PlayerEngineTests : FunSpec({
	test("lifecycle and resume preserve milliseconds and paused seek") {
		val engine = FakeEngine()
		val manager = PlayerEngineManager(listOf({ engine }))
		manager.prepare(123456L)
		engine.positionMs shouldBe 123456L
		manager.play(); engine.state shouldBe "PLAYING"
		manager.pause(); manager.seek(234567)
		engine.state shouldBe "PAUSED"
		engine.positionMs shouldBe 234567L
		manager.stop(); engine.state shouldBe "STOPPED"
		manager.release(); manager.release(); engine.releases shouldBe 1
		expectFailure<IllegalStateException> { manager.prepare(0) }
	}
	test("initialized engine is released even before first prepare") {
		val engine = FakeEngine()
		val manager = PlayerEngineManager(listOf({ engine }))
		manager.initialize() shouldBe engine
		manager.release()
		engine.releases shouldBe 1
	}
	test("default selection creates exactly one engine and reuses it") {
		var creations = 0
		val first = FakeEngine()
		val manager = PlayerEngineManager(listOf({ creations++; first }, { error("must not instantiate") }))
		manager.prepare(1); manager.prepare(2)
		creations shouldBe 1
		first.prepares shouldBe 2
		manager.active shouldBe first
	}
	test("unsupported candidate is released without preparation") {
		val first = FakeEngine(supported = false)
		val second = FakeEngine()
		val manager = PlayerEngineManager(listOf({ first }, { second }))
		manager.prepare(7000)
		first.releases shouldBe 1
		first.prepares shouldBe 0
		manager.active shouldBe second
	}
	test("initialization failure releases before bounded fallback with same resume request") {
		val first = FakeEngine(fail = true)
		val second = FakeEngine()
		val manager = PlayerEngineManager(listOf({ first }, { first.releases shouldBe 1; second }))
		manager.prepare(9000)
		second.positionMs shouldBe 9000L
		manager.fallbackReason shouldBe "IllegalStateException"
	}
	test("cleanup failure forbids another engine") {
		val first = FakeEngine(fail = true).apply { cleanupFails = true }
		val manager = PlayerEngineManager(listOf({ first }, { error("unsafe fallback") }))
		expectFailure<IllegalStateException> { manager.prepare(1) }.message shouldBe "cleanup"
		manager.active shouldBe null
	}
	test("failed candidates exhausted once") {
		val first = FakeEngine(fail = true)
		val manager = PlayerEngineManager(listOf({ first }))
		expectFailure<IllegalStateException> { manager.prepare(1) }
		first.prepares shouldBe 1
		first.releases shouldBe 1
	}
	test("factory failure can fall back without a session or resource") {
		val second = FakeEngine()
		val manager = PlayerEngineManager(listOf({ error("construction") }, { second }))
		manager.prepare(1)
		manager.active shouldBe second
	}
	test("active session failure never rotates engines or repeats a start") {
		var builds = 0
		val engine = FakeEngine()
		val manager = PlayerEngineManager(listOf({ builds++; engine }, { error("must not rotate") }))
		manager.prepare(9000)
		manager.play()
		manager.pause()
		manager.seek(12000)
		builds shouldBe 1
		engine.prepares shouldBe 1
		engine.positionMs shouldBe 12000L
	}
	test("no candidates means unavailable, never an imaginary native engine") {
		expectFailure<IllegalStateException> { PlayerEngineManager<Long>(emptyList()).prepare(1) }
	}
	test("negative seeks rejected before reaching engine") {
		val engine = FakeEngine()
		val manager = PlayerEngineManager(listOf({ engine }))
		manager.prepare(42)
		expectFailure<IllegalArgumentException> { manager.seek(-1) }
		engine.positionMs shouldBe 42L
	}
	test("DV profile requires explicit codec metadata") {
		DiagnosticValues.dolbyVision("dvhe.07.06") shouldBe (7 to 6)
		DiagnosticValues.dolbyVision("dvh1.08.09") shouldBe (8 to 9)
		DiagnosticValues.dolbyVision("hev1.2.4.L153") shouldBe null
		DiagnosticValues.dolbyVision(null) shouldBe null
		DiagnosticValues.dolbyVision("dvhe.bad") shouldBe null
	}
	test("HDR classification never equates PQ alone with HDR10 plus") {
		DiagnosticValues.hdr(null, null, null, false) shouldBe "UNKNOWN"
		DiagnosticValues.hdr("video/dolby-vision", null, 6, true) shouldBe "Dolby Vision"
		DiagnosticValues.hdr(null, null, 7, false) shouldBe "HLG"
		DiagnosticValues.hdr(null, null, 6, false) shouldContain "UNKNOWN"
		DiagnosticValues.hdr(null, null, 6, true) shouldContain "HDR10-compatible"
	}
	test("missing values stay unknown") {
		DiagnosticValues.value(null) shouldBe "UNKNOWN"
		DiagnosticValues.value(-1) shouldBe "UNKNOWN"
		DiagnosticValues.value(0) shouldBe "0"
	}
	test("AudioTrack diagnostics cannot verify HDMI Atmos or DTS X") {
		DiagnosticValues.passthrough(null) shouldBe "UNKNOWN"
		DiagnosticValues.passthrough(2) shouldContain "PCM"
		DiagnosticValues.passthrough(14) shouldContain "NOT VERIFIED"
		DiagnosticValues.passthrough(8) shouldContain "NOT VERIFIED"
	}
})
