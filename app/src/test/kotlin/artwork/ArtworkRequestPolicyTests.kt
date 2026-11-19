package org.jellyfin.androidtv.ui.artwork

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.util.artwork.ArtworkRequestPolicy as Policy
import org.jellyfin.androidtv.util.artwork.ArtworkUrls

class ArtworkRequestPolicyTests : FunSpec({
    test("dp is converted once to actual rendering pixels at several densities") {
        listOf(1f, 1.5f, 2f, 3f).forEach { density -> Policy.pixels(168f, density) shouldBe (168 * density).toInt() }
    }
    test("poster crop requests cover the measured view at the source aspect") {
        val p = Policy.select(336, 400, Policy.Kind.POSTER, Policy.Source(2.0 / 3))
        (p.requested.width >= 336 && p.requested.height >= 504) shouldBe true
        p.decoded shouldBe Policy.Size(353, 420)
    }
    test("landscape episode and background sizes follow different viewport dimensions") {
        val thumb = Policy.select(336, 189, Policy.Kind.THUMBNAIL, Policy.Source(16.0 / 9))
        val background = Policy.select(1920, 1080, Policy.Kind.BACKGROUND, Policy.Source(16.0 / 9))
        (thumb.requested.width < background.requested.width) shouldBe true
        background.decoded shouldBe Policy.Size(1920, 1080)
    }
    test("known low resolution sources are never enlarged by the request plan") {
        val p = Policy.select(1000, 1500, Policy.Kind.POSTER, Policy.Source(width = 200, height = 300))
        p.requested shouldBe Policy.Size(200, 300)
        p.decoded shouldBe Policy.Size(200, 300)
    }
    test("high resolution artwork is bounded without forcing all cards to one size") {
        val p = Policy.select(7680, 4320, Policy.Kind.BACKGROUND, Policy.Source(width = 7680, height = 4320))
        p.requested shouldBe Policy.Size(2560, 1440)
        (Policy.select(120, 180, Policy.Kind.POSTER).requested != Policy.select(336, 504, Policy.Kind.POSTER).requested) shouldBe true
    }
    test("crop cover handles wide source art without fit then upscale") {
        val p = Policy.select(1920, 1080, Policy.Kind.BACKGROUND, Policy.Source(2.0))
        (p.requested.width >= 2160 && p.requested.height >= 1080) shouldBe true
    }
    test("card decoding includes the existing five percent focus scale") {
        val p = Policy.select(336, 196, Policy.Kind.THUMBNAIL, Policy.Source(16.0 / 9))
        p.decoded shouldBe Policy.Size(353, 206)
        (p.requested.width >= 367 && p.requested.height >= 206) shouldBe true
    }
    test("Seerr and TMDB use supported original preset only when the current preset is too small") {
        val proxy = "https://example.invalid/imageproxy/tmdb/t/p/w1280/art.jpg"
        ArtworkUrls.resolve(proxy, 1920, 1080)!!.url.contains("/original/") shouldBe true
        ArtworkUrls.resolve(proxy, 336, 196)!!.url.contains("/w1280/") shouldBe true
        ArtworkUrls.resolve(proxy, 1920, 1080)!!.url.contains("maxWidth") shouldBe false
        ArtworkUrls.resolve("https://image.tmdb.org/t/p/w500/a.jpg", 300, 450)!!.url.contains("/w500/") shouldBe true
    }
    test("a backdrop used as a card uses the card purpose rather than full-screen decoding") {
        val p = ArtworkUrls.resolve("https://example.invalid/Items/i/Images/Backdrop", 336, 196, purpose = Policy.Kind.THUMBNAIL)!!.plan
        p.kind shouldBe Policy.Kind.THUMBNAIL
        p.decoded shouldBe Policy.Size(353, 206)
    }
    test("invalid metadata and dimensions remain bounded") {
        val p = Policy.select(0, -1, Policy.Kind.POSTER, Policy.Source(Double.NaN, -3, 0))
        (p.requested.width > 0 && p.requested.height > 0) shouldBe true
    }
    test("different sizes produce different network and memory identities") {
        val url = "https://example.invalid/Items/fixture/Images/Primary?tag=revision&fillWidth=100&fillHeight=150"
        val small = ArtworkUrls.resolve(url, 100, 150)!!
        val large = ArtworkUrls.resolve(url, 400, 600)!!
        (small.url != large.url && small.plan.cacheIdentity != large.plan.cacheIdentity) shouldBe true
        small.url.contains("fillWidth") shouldBe false
        small.url.contains("tag=revision") shouldBe true
        small.url.contains("quality=90") shouldBe true
    }
    test("unrelated providers and local files are not rewritten") {
        ArtworkUrls.resolve("https://example.invalid/other-provider/a.jpg", 300, 450) shouldBe null
        ArtworkUrls.resolve("file:///local/fixture.png", 300, 450) shouldBe null
    }
    test("image revision and format are preserved in cache identity URL") {
        val a = ArtworkUrls.resolve("https://example.invalid/Items/i/Images/Backdrop/0?tag=one&format=webp", 1920, 1080)!!
        val b = ArtworkUrls.resolve("https://example.invalid/Items/i/Images/Backdrop/0?tag=two&format=webp", 1920, 1080)!!
        (a.url != b.url) shouldBe true
        a.url.contains("format=webp") shouldBe true
    }
})
