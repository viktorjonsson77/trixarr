package startup

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.floats.shouldBeExactly
import org.jellyfin.androidtv.ui.startup.LaunchHistory
import org.jellyfin.androidtv.ui.startup.LaunchTimeline
import java.io.File

class LaunchTests : StringSpec({
	"only the first startup in each process claims full animation" {
		val process = LaunchHistory()
		process.claimColdStart() shouldBe true
		repeat(5) { process.claimColdStart() shouldBe false }
		LaunchHistory().claimColdStart() shouldBe true
	}
    "native launch animates only the complete bitmap alpha" {
        val source = File("src/main/java/org/jellyfin/androidtv/ui/startup/LaunchOverlay.kt").readText()
        source.contains("image.setImageResource(R.drawable.app_logo)") shouldBe true
        source.contains("image.animate().alpha(1f)") shouldBe true
        source.contains("LaunchTimeline.DURATION_MS.toLong()") shouldBe true
        listOf("AnimatedVectorDrawable", "scaleX", "scaleY", "rotation", "translationX", "ValueAnimator").forEach {
            source.contains(it) shouldBe false
        }
        LaunchTimeline.DURATION_MS shouldBe 350
        File("src/main/res/drawable/arrstreamer_launch_system.xml").exists() shouldBe false
    }

	"home has no permanent branding or logo header" {
		val source = File("src/main/java/org/jellyfin/androidtv/ui/catalog/CatalogFragment.kt").readText()
		val home = source.substringAfter("private fun Home(").substringBefore("private fun loadMore(")
		listOf("Logo(", "app_logo", "ArrStreamer").forEach { home.contains(it) shouldBe false }
		// The newer photographic reference intentionally places the hero lower.
        // Its fixed geometry is verified by HomeMenuUiTests, not a ban on spacing.
        val components = File("src/main/java/org/jellyfin/androidtv/ui/catalog/CatalogComponents.kt").readText()
        val hero = components.substringAfter("internal fun HomeHero(").substringBefore("internal fun TitleHero(")
        listOf("Logo(", "app_logo", "ArrStreamer").forEach { hero.contains(it) shouldBe false }
		val toolbar = File("src/main/java/org/jellyfin/androidtv/ui/shared/toolbar/Toolbar.kt").readText()
		toolbar.contains("Logo(") shouldBe false
		toolbar.contains("app_logo") shouldBe false
		File("src/main/java/org/jellyfin/androidtv/ui/seerr/SeerrDetailsFragment.kt").readText().contains("ArrStreamer") shouldBe false
	}
	"startup navigation does not await a presentation timer" {
		val source = File("src/main/java/org/jellyfin/androidtv/ui/startup/StartupActivity.kt").readText()
		val navigation = source.substringAfter("private suspend fun openNextActivity()").substringBefore("// Fragment switching")
		listOf("delay(", "awaitAnimation", "DURATION_MS", "elapsed").forEach { navigation.contains(it) shouldBe false }
		navigation.contains("startActivity(intent, options)") shouldBe true
	}
})
