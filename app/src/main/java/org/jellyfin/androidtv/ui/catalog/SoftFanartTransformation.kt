package org.jellyfin.androidtv.ui.catalog

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/** Small cached software blur, also available on Shield / API 30 without RenderEffect. */
internal class SoftFanartTransformation : Transformation() {
    override val cacheKey = "catalog-soft-fanart-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = minOf(input.width, 256)
        val height = (input.height.toLong() * width / input.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(input, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== input) scaled.recycle()
        val intermediate = IntArray(pixels.size)
        val output = IntArray(pixels.size)
        fun pass(source: IntArray, target: IntArray, horizontal: Boolean) {
            for (y in 0 until height) for (x in 0 until width) {
                var red = 0
                var green = 0
                var blue = 0
                for (offset in -3..3) {
                    val px = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                    val py = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                    val color = source[py * width + px]
                    red += color ushr 16 and 255
                    green += color ushr 8 and 255
                    blue += color and 255
                }
                target[y * width + x] =
                    (255 shl 24) or (red / 7 shl 16) or (green / 7 shl 8) or (blue / 7)
            }
        }
        pass(pixels, intermediate, true)
        pass(intermediate, output, false)
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }
}
