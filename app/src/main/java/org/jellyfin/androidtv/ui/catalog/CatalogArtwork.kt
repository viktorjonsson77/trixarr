package org.jellyfin.androidtv.ui.catalog

import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

internal fun BaseItemDto.catalogBackdropImage(): JellyfinImage? =
    itemBackdropImages.firstOrNull() ?: parentBackdropImages.firstOrNull() ?: itemImages[ImageType.THUMB] ?: parentImages[ImageType.THUMB]
