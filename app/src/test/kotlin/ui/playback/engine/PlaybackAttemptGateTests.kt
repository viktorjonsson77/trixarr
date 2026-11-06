package org.jellyfin.androidtv.ui.playback.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PlaybackAttemptGateTests : FunSpec({
	test("duplicate server and segment callbacks cannot prepare or report start twice") {
		val gate = org.jellyfin.playback.core.engine.PlaybackSessionOwner()
		val attempt = gate.begin()
		gate.claimPreparation(attempt) shouldBe true
		gate.claimPreparation(attempt) shouldBe false
		gate.claimStart(attempt) shouldBe true
		gate.claimStart(attempt) shouldBe false
		gate.stop() shouldBe true
		gate.stop() shouldBe false
	}
	test("stop while initializing invalidates callbacks and sends no stop for unstarted session") {
		val gate = org.jellyfin.playback.core.engine.PlaybackSessionOwner()
		val attempt = gate.begin()
		gate.claimPreparation(attempt) shouldBe true
		gate.stop() shouldBe false
		gate.claimStart(attempt) shouldBe false
		gate.isCurrent(attempt) shouldBe false
	}
	test("retry ignores old response and delayed start while new attempt reports once") {
		val gate = org.jellyfin.playback.core.engine.PlaybackSessionOwner()
		val old = gate.begin()
		gate.claimPreparation(old)
		gate.claimStart(old)
		gate.stop() shouldBe true
		val next = gate.begin()
		gate.claimPreparation(old) shouldBe false
		gate.claimStart(old) shouldBe false
		gate.claimStart(next) shouldBe false
		gate.claimPreparation(next) shouldBe true
		gate.claimStart(next) shouldBe true
		gate.stop() shouldBe true
	}
})
