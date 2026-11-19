package org.jellyfin.androidtv.artwork

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import org.jellyfin.androidtv.ui.AsyncImageView
import org.jellyfin.androidtv.ui.catalog.SoftFanartTransformation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration

@RunWith(AndroidJUnit4::class)
class ArtworkRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun fixture(): File {
        compose.activity.setTheme(org.jellyfin.androidtv.R.style.Theme_Jellyfin)
        val file = File(compose.activity.cacheDir, "artwork-fixture-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.CYAN)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
    @Test fun resizingAnExistingViewReloadsAndRetainsSufficientResolution() {
        val file = fixture()
        var bound = false
        val expanded = mutableStateOf(false)
        lateinit var view: AsyncImageView
        try {
            compose.setContent {
                AndroidView(modifier = Modifier.size(if (expanded.value) 200.dp else 50.dp, if (expanded.value) 300.dp else 75.dp), factory = { context ->
                    AsyncImageView(context).also {
                        view = it; it.crossFadeDuration = Duration.ZERO; it.scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                }, update = { if (!bound) { bound = true; it.load(android.net.Uri.fromFile(file).toString()) } })
            }
            compose.waitUntil(10000) { view.drawable != null && view.drawable.intrinsicWidth > 0 }
            val operation = AsyncImageView::class.java.getDeclaredField("loadJob").apply { isAccessible = true }
            val firstOperation = operation.get(view)
            compose.runOnIdle { expanded.value = true }
            compose.waitUntil(10000) {
                val current = operation.get(view) as? kotlinx.coroutines.Job
                current !== firstOperation && current?.isCompleted == true
            }
            compose.runOnIdle {
                assertEquals((200 * compose.activity.resources.displayMetrics.density).toInt(), view.width)
                assertTrue("A larger view must not reuse an undersized bitmap", view.drawable.intrinsicWidth >= view.width)
            }
        } finally { file.delete() }
    }
    @Test fun failedLoadShowsTheFallbackInsteadOfKeepingOldArtwork() {
        val file = fixture()
        var bound = false
        lateinit var view: AsyncImageView
        try {
            compose.setContent {
                AndroidView(modifier = Modifier.size(50.dp, 75.dp), factory = { context ->
                    AsyncImageView(context).also { view = it; it.crossFadeDuration = Duration.ZERO }
                }, update = { if (!bound) { bound = true; it.load(android.net.Uri.fromFile(file).toString()) } })
            }
            compose.waitUntil(10000) { view.drawable != null && view.drawable.intrinsicWidth > 0 }
            compose.runOnIdle { view.load("file:///nonexistent-artwork-fixture.png", placeholder = ColorDrawable(Color.MAGENTA)) }
            val operation = AsyncImageView::class.java.getDeclaredField("loadJob").apply { isAccessible = true }
            compose.waitUntil(10000) { (operation.get(view) as? kotlinx.coroutines.Job)?.isCompleted == true }
            compose.runOnIdle { assertEquals(Color.MAGENTA, (view.drawable as? ColorDrawable)?.color) }
        } finally { file.delete() }
    }
    @Test fun lowResolutionSourceIsNotUpscaledDuringDecode() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "artwork-small-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val loader = org.koin.core.context.GlobalContext.get().get<coil3.ImageLoader>()
            val result = loader.execute(coil3.request.ImageRequest.Builder(context).data(file).size(800, 600).precision(coil3.size.Precision.INEXACT).build())
            assertEquals(120, result.image!!.width); assertEquals(80, result.image!!.height)
        } finally { file.delete() }
    }
    @Test fun softFanartPreservesMoreDetailWithBoundedMemoryAndDoesNotEnlargeASmallSource() = runBlocking {
        val transform = SoftFanartTransformation()
        assertNotEquals("catalog-soft-fanart-v1", transform.cacheKey)
        val large = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val started = System.nanoTime()
        val output = transform.transform(large, Size(1920, 1080))
        assertEquals(960, output.width); assertEquals(540, output.height)
        val ms = (System.nanoTime() - started) / 1_000_000
        println("Artwork blur 1920x1080 ->960x540 elapsedMs=$ms bytes=${output.allocationByteCount}")
        assertTrue("Bounded transform should finish without freezing UI", ms < 2000)
        val small = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        val unchangedSize = transform.transform(small, Size(1920, 1080))
        assertEquals(120, unchangedSize.width); assertEquals(60, unchangedSize.height)
        large.recycle(); output.recycle(); small.recycle(); unchangedSize.recycle()
    }
}
