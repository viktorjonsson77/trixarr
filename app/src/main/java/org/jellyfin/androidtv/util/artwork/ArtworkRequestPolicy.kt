package org.jellyfin.androidtv.util.artwork

import kotlin.math.ceil
import kotlin.math.min

/** All sizes are rendered pixels, never HDMI output pixels or unconverted dp. */
object ArtworkRequestPolicy {
    enum class Kind(val maxWidth: Int, val maxHeight: Int) {
        POSTER(1024, 1536), THUMBNAIL(1920, 1080), BACKGROUND(2560, 1440), LOGO(1600, 600), AVATAR(512, 512)
    }
    data class Source(val aspect: Double? = null, val width: Int? = null, val height: Int? = null)
    data class Size(val width: Int, val height: Int)
    data class Plan(val requested: Size, val decoded: Size, val kind: Kind) {
        val cacheIdentity get() = "artwork-v1:${kind.name}:${requested.width}x${requested.height}:${decoded.width}x${decoded.height}"
    }
    fun pixels(dp: Float, density: Float): Int = ceil(dp.coerceAtLeast(1f) * density.coerceAtLeast(.1f)).toInt().coerceAtLeast(1)
    fun select(viewWidth: Int, viewHeight: Int, kind: Kind, source: Source = Source()): Plan {
        val vw = viewWidth.coerceAtLeast(1); val vh = viewHeight.coerceAtLeast(1)
        val overscan = if (kind == Kind.POSTER || kind == Kind.THUMBNAIL) 1.05 else 1.0
        var w = vw * overscan; var h = vh * overscan
        val aspect = source.aspect?.takeIf { it.isFinite() && it > 0 }
            ?: if (source.width != null && source.height != null && source.width > 0 && source.height > 0) source.width.toDouble() / source.height else null
        // Cover the view at the SOURCE aspect; a fit-sized server image would be enlarged again by Crop.
        if (aspect != null) { if (w / h > aspect) h = w / aspect else w = h * aspect }
        val limitW = min(kind.maxWidth, source.width?.takeIf { it > 0 } ?: kind.maxWidth)
        val limitH = min(kind.maxHeight, source.height?.takeIf { it > 0 } ?: kind.maxHeight)
        val factor = min(1.0, min(limitW / w, limitH / h))
        fun bucket(n: Double, cap: Int) = (ceil(n / 32).toInt() * 32).coerceIn(1, cap)
        val requested = Size(bucket(w * factor, limitW), bucket(h * factor, limitH))
        val decodeFactor = min(1.0, min(limitW.toDouble() / (vw * overscan), limitH.toDouble() / (vh * overscan)))
        return Plan(requested, Size(ceil(vw * overscan * decodeFactor).toInt().coerceAtLeast(1), ceil(vh * overscan * decodeFactor).toInt().coerceAtLeast(1)), kind)
    }
}
