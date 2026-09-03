package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VideoQueueManagerTests : StringSpec({
	"selected media source reaches and is cleared with the playback queue" {
		val manager = VideoQueueManager()
		manager.setSelectedMediaSourceId("source-two")
		manager.getSelectedMediaSourceId() shouldBe "source-two"
		manager.clearVideoQueue()
		manager.getSelectedMediaSourceId() shouldBe null
	}
})
