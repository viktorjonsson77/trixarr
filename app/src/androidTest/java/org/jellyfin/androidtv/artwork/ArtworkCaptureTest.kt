package org.jellyfin.androidtv.artwork

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File

/** Captures only artwork dimensions/technical parameters; never URLs or authentication. */
@RunWith(AndroidJUnit4::class)
class ArtworkCaptureTest {
    @Test fun captureLibrary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val stage = InstrumentationRegistry.getArguments().getString("stage") ?: "after"
        require(stage in listOf("before", "after"))
        instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close()
        SystemClock.sleep(1000)
        context.startActivity(Intent().setClassName(context, "org.jellyfin.androidtv.ui.startup.StartupActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.uptimeMillis() + 25000
        while (SingletonImageLoader.get(context).memoryCache?.keys.isNullOrEmpty() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(250)
        check(!SingletonImageLoader.get(context).memoryCache?.keys.isNullOrEmpty()) { "No artwork loaded; capture is invalid" }
        SystemClock.sleep(2000)
        instrumentation.uiAutomation.injectInputEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN), true)
        instrumentation.uiAutomation.injectInputEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN), true)
        SystemClock.sleep(2500)
        val shared = GlobalContext.get().get<ImageLoader>()
        val singleton = SingletonImageLoader.get(context)
        val records = JSONArray()
        for ((label, loader) in listOf("shared" to shared, "singleton" to singleton)) {
            for (key in loader.memoryCache?.keys.orEmpty()) {
                val value = loader.memoryCache?.get(key) ?: continue
                val url = key.key.toHttpUrlOrNull() ?: continue
                val allowed = setOf("maxwidth", "maxheight", "fillwidth", "fillheight", "quality", "width", "height")
                val params = JSONObject()
                url.queryParameterNames.filter { it.lowercase() in allowed }.forEach { params.put(it, url.queryParameter(it)) }
                val path = url.pathSegments
                val item = path.indexOfFirst { it.equals("Items", true) }
                val row = JSONObject().put("loader", label).put("decoded_width", value.image.width).put("decoded_height", value.image.height).put("parameters", params)
                if (item >= 0 && path.size > item + 3) row.put("item", path[item + 1]).put("image_type", path[item + 3])
                else row.put("remote", true)
                records.put(row)
            }
        }
        val metrics = context.resources.displayMetrics
        val data = JSONObject().put("render_width", metrics.widthPixels).put("render_height", metrics.heightPixels).put("density", metrics.density).put("density_dpi", metrics.densityDpi).put("shared_cache_bytes", shared.memoryCache?.size).put("singleton_cache_bytes", singleton.memoryCache?.size).put("same_loader", shared === singleton).put("cache_records", records)
        val dir = File(context.getExternalFilesDir(null), "image-quality-evidence").apply { mkdirs() }
        File(dir, "$stage.json").writeText(data.toString(2))
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(dir, "$stage.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        println("Artwork capture stage=$stage count=${records.length()} render=${metrics.widthPixels}x${metrics.heightPixels}")
    }
}
