package org.jellyfin.androidtv.util.artwork

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.Size

val ArtworkKindKey = coil3.Extras.Key<ArtworkRequestPolicy.Kind?>(null)
val ArtworkSourceKey = coil3.Extras.Key<ArtworkRequestPolicy.Source?>(null)

/** Runs before Coil's engine/cache lookup; URL size plus cache extras prevent undersized reuse. */
class ArtworkInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val value = request.data as? String ?: return chain.proceed()
        val display = request.context.resources.displayMetrics
        val width = (chain.size.width as? Dimension.Pixels)?.px ?: display.widthPixels
        val height = (chain.size.height as? Dimension.Pixels)?.px ?: display.heightPixels
        val resolved = ArtworkUrls.resolve(value, width, height, request.extras[ArtworkSourceKey] ?: ArtworkSources.get(value), request.extras[ArtworkKindKey]) ?: return chain.proceed()
        val next = request.newBuilder().data(resolved.url).precision(coil3.size.Precision.INEXACT).memoryCacheKeyExtra("artwork", resolved.plan.cacheIdentity).build()
        return chain.withRequest(next).withSize(Size(resolved.plan.decoded.width, resolved.plan.decoded.height)).proceed()
    }
}
