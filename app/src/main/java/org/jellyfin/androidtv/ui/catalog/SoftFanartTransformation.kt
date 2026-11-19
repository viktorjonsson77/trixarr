package org.jellyfin.androidtv.ui.catalog

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/** Small cached software blur, also available on Shield / API 30 without RenderEffect. */
internal class SoftFanartTransformation : Transformation() {
    override val cacheKey = "catalog-soft-fanart-v2-960"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val target = (size.width as? coil3.size.Dimension.Pixels)?.px ?: 960
        val width = minOf(input.width, target, 960).coerceAtLeast(1)
        val height = (input.height.toLong() * width / input.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(input, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== input) scaled.recycle()
        val intermediate = IntArray(pixels.size)
        val output = IntArray(pixels.size)
        val radius = (width * 3 / 256).coerceAtLeast(1)
        // Running sums keep the previous apparent softness at O(pixels), not O(pixels * radius).
        fun pass(source: IntArray, target: IntArray, horizontal: Boolean) {
            val lines = if (horizontal) height else width
            val length = if (horizontal) width else height
            val divisor = radius * 2 + 1
            fun index(line: Int, at: Int) = if (horizontal) line * width + at.coerceIn(0, width - 1)
                else at.coerceIn(0, height - 1) * width + line
            for (line in 0 until lines) {
                var r = 0; var g = 0; var b = 0
                fun add(color: Int, sign: Int) {
                    r += sign * (color ushr 16 and 255)
                    g += sign * (color ushr 8 and 255)
                    b += sign * (color and 255)
                }
                for (at in -radius..radius) add(source[index(line, at)], 1)
                for (at in 0 until length) {
                    target[index(line, at)] = (255 shl 24) or (r / divisor shl 16) or (g / divisor shl 8) or (b / divisor)
                    add(source[index(line, at - radius)], -1)
                    add(source[index(line, at + radius + 1)], 1)
                }
            }
        }
        pass(pixels, intermediate, true)
        pass(intermediate, output, false)
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }
}
