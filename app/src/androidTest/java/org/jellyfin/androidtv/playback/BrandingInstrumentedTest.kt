package org.jellyfin.androidtv.playback

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.jellyfin.androidtv.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class BrandingInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun applicationAndPlayerDisplayLabels() {
        assertEquals("Trixarr", context.getString(R.string.app_name))
        assertEquals("Trixarr", context.applicationInfo.loadLabel(context.packageManager).toString())
        assertEquals("Trixarr Player Engine", context.getString(R.string.player_product_name))
        assertTrue(context.getString(R.string.player_diagnostics_title).startsWith("Trixarr Player Engine"))
        assertEquals("Your media. Your way.", context.getString(R.string.product_tagline))
    }

    @Test fun leanbackLauncherAndLegacyArtworkResourcesResolve() {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(context.packageName)
        val activities = pm.queryIntentActivities(intent, 0)
        assertEquals(1, activities.size)
        assertEquals("Trixarr", activities.single().loadLabel(pm).toString())
        assertNotNull(context.applicationInfo.loadIcon(pm))
        assertNotNull(context.applicationInfo.loadBanner(pm))
        assertNotNull(context.getDrawable(R.drawable.app_logo))
        // Resource resolution is not approval of the retained legacy artwork.
    }

    @Test fun applicationAndSignerIdentityArePreserved() {
        assertEquals("se.viktorjonsson.jellyfintv.debug", context.packageName)
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val digest = MessageDigest.getInstance("SHA-256").digest(info.signingInfo!!.apkContentsSigners.single().toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c", digest)
    }
}
