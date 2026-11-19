package org.jellyfin.androidtv.startup

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.AccountManagerMigration
import org.jellyfin.androidtv.auth.model.AuthenticationStoreServer
import org.jellyfin.androidtv.auth.model.AuthenticationStoreUser
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.seerr.KeystoreSeerrAuthStore
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.startup.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LaunchUiTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun cover(cold: Boolean, motion: Boolean): LaunchOverlay {
        compose.setContent { Text("Ready content") }
        compose.waitForIdle()
        lateinit var cover: LaunchOverlay
        compose.runOnUiThread {
            val root = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            cover = LaunchOverlay(compose.activity, root)
            cover.hold(LaunchSequence(motion, cold))
        }
        return cover
    }
    private fun snapshot(name: String): Bitmap {
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(context.getExternalFilesDir(null), "launch-evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return image
    }
    @Test fun coldStartFadesOneCompleteNativeImageAndFinishes() {
        val cover = cover(cold = true, motion = true)
        compose.waitUntil { cover.sequence.complete }
        compose.runOnUiThread {
            assertEquals(1, cover.view.childCount)
            assertTrue((cover.view.getChildAt(0) as android.widget.ImageView).drawable is BitmapDrawable)
            assertFalse(cover.view.isFocusable)
        }
        val bitmap = snapshot("cold-complete-native")
        assertTrue(bitmap.width > 0)
    }
    @Test fun contentIsDrawnBehindCoverBeforeRemoval() {
        val cover = cover(cold = true, motion = true)
        lateinit var destination: android.view.View
        compose.runOnUiThread {
            destination = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            assertEquals(1f, destination.alpha)
            cover.revealAfterDraw(destination)
        }
        compose.waitUntil { cover.view.parent == null }
        compose.runOnUiThread { assertTrue(cover.sequence.complete); assertEquals(1f, destination.alpha) }
        snapshot("first-content-after-native-fade")
    }
    @Test fun warmStartupUsesStaticWholeImage() {
        val history = LaunchHistory()
        assertTrue(history.claimColdStart())
        val cover = cover(cold = history.claimColdStart(), motion = true)
        compose.waitForIdle()
        compose.runOnUiThread {
            assertTrue(cover.sequence.complete)
            assertTrue((cover.view.getChildAt(0) as android.widget.ImageView).drawable is BitmapDrawable)
        }
        snapshot("warm-static-native")
    }
    @Test @SdkSuppress(minSdkVersion = 29)
    fun disabledMotionIsStaticAndSettingIsRestored() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        val previous = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            val cover = cover(cold = true, motion = launchMotionEnabled(context))
            compose.runOnUiThread { assertTrue(cover.sequence.complete) }
            snapshot("motion-disabled-native")
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, previous)
            automation.dropShellPermissionIdentity()
        }
    }

	@Test fun appLabelAndLauncherBrandingAreRetained() {
		assertEquals("ArrStreamer", context.getString(R.string.app_name_release))
		assertEquals("ArrStreamer Debug", context.applicationInfo.loadLabel(context.packageManager).toString())
		assertNotNull(context.packageManager.getApplicationIcon(context.applicationInfo))
		val banner = context.packageManager.getApplicationBanner(context.applicationInfo)!!
		assertEquals(banner.intrinsicWidth * 9, banner.intrinsicHeight * 16)
	}

	@Test @SdkSuppress(minSdkVersion = 23)
	fun storedJellyfinAndEncryptedSeerrProfilesSurviveColdAndWarmPresentation() {
		// A unique files/prefs namespace prevents touching real profiles on either device.
		val namespace = "launch-test-${UUID.randomUUID()}"
		val directory = File(context.cacheDir, namespace).apply { mkdirs() }
		val isolated = object : ContextWrapper(context) {
			override fun getFilesDir(): File = directory
			override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
				super.getSharedPreferences("$namespace-$name", mode)
		}
		val server = UUID.randomUUID()
		val user = UUID.randomUUID()
		val profile = "$server:$user"
		try {
			val store = AuthenticationStore(isolated, AccountManagerMigration(isolated))
			assertTrue(store.putServer(server, AuthenticationStoreServer("test", "http://127.0.0.1")))
			assertTrue(store.putUser(server, user, AuthenticationStoreUser("test", accessToken = "test-token")))
			KeystoreSeerrAuthStore(isolated).writeSession(profile, "connect.sid=test-cookie")
			val original = File(directory, "authentication_store.json").readBytes()
			val history = LaunchHistory()
			history.claimColdStart()
			history.claimColdStart()
			compose.setContent { Text("Ready content") }
			compose.waitForIdle()
			val restored = AuthenticationStore(isolated, AccountManagerMigration(isolated))
			assertEquals("test-token", restored.getUser(server, user)?.accessToken)
			assertEquals("connect.sid=test-cookie", KeystoreSeerrAuthStore(isolated).readSession(profile))
			assertNull(KeystoreSeerrAuthStore(isolated).readSession("another-profile"))
			assertArrayEquals(original, File(directory, "authentication_store.json").readBytes())
		} finally {
			isolated.getSharedPreferences("seerr_secure_sessions", Context.MODE_PRIVATE).edit().clear().commit()
			directory.deleteRecursively()
		}
	}
}
