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

    @Test fun leanbackLauncherAndApprovedArtworkResourcesResolve() {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(context.packageName)
        val activities = pm.queryIntentActivities(intent, 0)
        assertEquals(1, activities.size)
        assertEquals("Trixarr", activities.single().loadLabel(pm).toString())
        assertNotNull(context.applicationInfo.loadIcon(pm))
        assertNotNull(context.applicationInfo.loadBanner(pm))
        assertNotNull(context.getDrawable(R.drawable.app_logo))
        // V3 uses the hash-verified approved raster master; no redesigned artwork.
    }

    @Test fun applicationAndSignerIdentityArePreserved() {
        assertEquals("se.viktorjonsson.jellyfintv.debug", context.packageName)
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val digest = MessageDigest.getInstance("SHA-256").digest(info.signingInfo!!.apkContentsSigners.single().toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("f3e4c2ca6d4f6462161eb268157cdcd5dfaa947d1179abce44bdcb055424b97c", digest)
    }

    @Test fun selectedResourcesAreTheApprovedTriangleAndTrixarrWordmark() {
        fun sha(id: Int) = context.resources.openRawResource(id).use {
            MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) }
        }
        assertEquals("49c6e84e369b5cc3d4cc84d73d5cb642d6b78242be89621a17fab01538611b6a", sha(R.drawable.app_logo))
        assertEquals("fecae7ca8ccd532e98635893d7b136188621642421dd866fc87a0181b510bcb7", sha(R.drawable.app_icon_foreground))
        val banners = setOf("3085315ae0b73236e45efacae705d4a1bedfccaf5da63b674d17f82ecae71c05", "350898cc49486a938b23ad0614b9dd67420624927333129f610940e8952a64a6", "6e074d9760ca8fd88f2916758081a95ceab75ce2582bde1b1d02dd2c690705f8", "814c928e1d9c06ef06530fd29803f6aae2e8148a4e0e6f8b3e027269c87575c6", "c77b2b52724943d8ded7eb85d676307ad03d8498bd6bec83b66c85c714d3a82e")
        assertTrue(banners.contains(sha(R.mipmap.app_banner)))
    }
}
