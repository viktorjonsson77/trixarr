package org.jellyfin.androidtv.util.artwork

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Bounded hints from DTO metadata; avoids a new ImageInfo network request for every card. */
object ArtworkSources {
    private val hints = object : LinkedHashMap<String, ArtworkRequestPolicy.Source>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtworkRequestPolicy.Source>) = size > 1024
    }
    @Synchronized fun remember(url: String, aspect: Float?) { if (aspect != null && aspect.isFinite() && aspect > 0) hints[url] = ArtworkRequestPolicy.Source(aspect.toDouble()) }
    @Synchronized fun get(url: String) = hints[url]
}

object ArtworkUrls {
    data class Request(val url: String, val plan: ArtworkRequestPolicy.Plan)
    fun resolve(value: String, width: Int, height: Int, source: ArtworkRequestPolicy.Source? = ArtworkSources.get(value), purpose: ArtworkRequestPolicy.Kind? = null): Request? {
        val url = value.toHttpUrlOrNull() ?: return null
        val path = url.pathSegments
        val proxy = path.indexOf("imageproxy")
        val tmdbSize = when {
            proxy >= 0 && path.getOrNull(proxy + 1) == "tmdb" && path.getOrNull(proxy + 2) == "t" && path.getOrNull(proxy + 3) == "p" -> proxy + 4
            url.host == "image.tmdb.org" && path.take(2) == listOf("t", "p") -> 2
            else -> -1
        }
        if (tmdbSize >= 0 && path.getOrNull(tmdbSize) in setOf("w500", "w1280", "original")) {
            val kind = purpose ?: if (path[tmdbSize] == "w500") ArtworkRequestPolicy.Kind.POSTER else ArtworkRequestPolicy.Kind.BACKGROUND
            val plan = ArtworkRequestPolicy.select(width, height, kind, source ?: ArtworkRequestPolicy.Source())
            val preset = path[tmdbSize]
            val ceiling = if (preset == "w500") 500 else 1280
            val selected = if (plan.requested.width > ceiling) "original" else preset
            return Request(url.newBuilder().setPathSegment(tmdbSize, selected).build().toString(), plan)
        }
        val images = path.indexOfFirst { it.equals("Images", true) }
        val detectedKind = when {
            images > 0 && path.take(images).any { it.equals("Users", true) } -> ArtworkRequestPolicy.Kind.AVATAR
            images > 0 && path.take(images).any { it.equals("Items", true) } -> when (path.getOrNull(images + 1)?.lowercase()) {
                "backdrop", "art" -> ArtworkRequestPolicy.Kind.BACKGROUND
                "thumb", "banner" -> ArtworkRequestPolicy.Kind.THUMBNAIL
                "logo" -> ArtworkRequestPolicy.Kind.LOGO
                "primary" -> if ((source?.aspect ?: (width.toDouble() / height.coerceAtLeast(1))) > 1.0) ArtworkRequestPolicy.Kind.THUMBNAIL else ArtworkRequestPolicy.Kind.POSTER
                else -> return null
            }
            else -> return null // Other providers keep their documented URLs and their existing caching.
        }
        val kind = purpose ?: detectedKind
        val hints = source ?: ArtworkRequestPolicy.Source(if (kind in setOf(ArtworkRequestPolicy.Kind.BACKGROUND, ArtworkRequestPolicy.Kind.THUMBNAIL)) 16.0 / 9 else null)
        val plan = ArtworkRequestPolicy.select(width, height, kind, hints)
        val builder = url.newBuilder()
        val sizing = setOf("width", "height", "fillwidth", "fillheight", "maxwidth", "maxheight", "quality")
        url.queryParameterNames.filter { it.lowercase() in sizing }.forEach { builder.removeAllQueryParameters(it) }
        builder.addQueryParameter("maxWidth", plan.requested.width.toString())
            .addQueryParameter("maxHeight", plan.requested.height.toString()).addQueryParameter("quality", "90")
        return Request(builder.build().toString(), plan)
    }
}
