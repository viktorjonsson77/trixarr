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
        // User approved reuse of the existing triangle; the wordmark is now Trixarr.
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
        assertEquals("9c1e59e60e3abd5960255b960622747f5376549ea36ec8856ededdeaca4bdd2d", sha(R.drawable.app_logo))
        assertEquals("2b80ad6386b92803f83cf1e45116f4bed0a8e36c3d40b2da316bb6a656b3cea3", sha(R.drawable.app_icon_foreground))
        val banners = setOf("0dee77eb4d8db7d020fc00c9d6c9b0d5d192a47ff35e1b8f60eb88f6f33671b3", "52627967ac01d25801f0c6052d7332c2e09127f604b64d4c47fdc25799c9bd9c", "6c134efbcad441963ad9930d49aede7e9e126f400038839507c0e4d1c0b8243a", "728419e312630dd20975f5b804fbeeb41e5bd2b5fcfb1a00acbf1a514cf02cb7", "7df08dffa519ad1a2b4614fe79d1b29acfca76fa6dbce1549b48edb0b3f7d262", "802ab3206d8d4da9ee38df48e01aa322725060e2a93a911da88310aa7aa89fd3", "8c832ded4825f03ea51d9d4b1c77a58ea02e44fda7b31b8bf937ddf1bd52118f", "c0d4f39ab52b50d7111d1648fb371e35e906994cc4c4897b416aedde2203dea3", "f12135552bc241fe6eefcb4faab34d1fbfedd623b08e5638094e8d6c36893008", "fee2886acd7bb90cc929403f2076c410cc1f1db8140d10c02abacebde6e6fc59")
        assertTrue(banners.contains(sha(R.mipmap.app_banner)))
    }
}
