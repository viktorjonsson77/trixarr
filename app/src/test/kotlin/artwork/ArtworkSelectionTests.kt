package org.jellyfin.androidtv.ui.artwork

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import org.jellyfin.androidtv.ui.catalog.catalogBackdropImage
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

class ArtworkSelectionTests : FunSpec({
    test("Jellyfin backdrop list takes precedence over a baked thumbnail") {
        val item = BaseItemDto(type = org.jellyfin.sdk.model.api.BaseItemKind.MOVIE, id = UUID.randomUUID(), imageTags = mapOf(ImageType.PRIMARY to "poster", ImageType.THUMB to "thumb"), backdropImageTags = listOf("backdrop"))
        item.catalogBackdropImage()?.type shouldBe ImageType.BACKDROP
        item.catalogBackdropImage()?.tag shouldBe "backdrop"
    }
    test("episode can inherit the parent backdrop without using a small primary image") {
        val parent = UUID.randomUUID()
        val item = BaseItemDto(type = org.jellyfin.sdk.model.api.BaseItemKind.MOVIE, id = UUID.randomUUID(), parentBackdropItemId = parent, parentBackdropImageTags = listOf("parent"))
        item.catalogBackdropImage()?.item shouldBe parent
    }
    test("thumbnail remains a fallback when a backdrop is unavailable") {
        val item = BaseItemDto(type = org.jellyfin.sdk.model.api.BaseItemKind.MOVIE, id = UUID.randomUUID(), imageTags = mapOf(ImageType.THUMB to "thumb"))
        item.catalogBackdropImage()?.type shouldBe ImageType.THUMB
        BaseItemDto(type = org.jellyfin.sdk.model.api.BaseItemKind.MOVIE, id = UUID.randomUUID()).catalogBackdropImage() shouldBe null
    }
})
