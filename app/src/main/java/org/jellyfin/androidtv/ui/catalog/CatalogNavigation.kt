package org.jellyfin.androidtv.ui.catalog

/** Section visits reuse the same state object and retain the previous screen. */
internal fun <T> visitSection(stack: MutableList<T>, page: T) {
    val previous = stack.indexOf(page)
    if (previous < 0) stack.add(page)
    else while (stack.lastIndex > previous) stack.removeAt(stack.lastIndex)
}

internal fun <T> popScreen(stack: MutableList<T>): Boolean {
    if (stack.size <= 1) return false
    stack.removeAt(stack.lastIndex)
    return true
}

internal enum class CatalogBackAction { DismissOverlay, PreviousScreen, OpenMenu, ConfirmExit }

/** Modal surfaces take precedence; Back alone never authorizes leaving the activity. */
internal fun catalogBackAction(menuOpen: Boolean, modalOpen: Boolean = false, nested: Boolean = false) = when {
    modalOpen -> CatalogBackAction.DismissOverlay
    menuOpen -> CatalogBackAction.ConfirmExit
    nested -> CatalogBackAction.PreviousScreen
    else -> CatalogBackAction.OpenMenu
}

/** Shared TV row geometry, observed on Shield; values are in the caller's coordinate space. */
internal object CatalogNavigationPolicy {
    const val featuredTitleCount = 5
    const val heroRotationMs = 5000L
    const val expandedHeroHeight = 408
    const val rowHeroHeight = 260
    const val rowHeroTop = 40

    fun cardAnchor(viewport: Float, cardWidth: Float, safe: Float): Float =
        ((viewport - cardWidth) / 2f).coerceAtLeast(safe)

    fun rememberedColumn(previous: Int?, size: Int): Int =
        (previous ?: 0).coerceIn(0, (size - 1).coerceAtLeast(0))
}

/** Back first restores the title's full top before popping the catalogue stack. */
internal fun returnToTitleTop(page: CatalogPage): Boolean {
    if (page.screen != CatalogScreen.Details || page.detailsExpanded) return false
    page.detailsExpanded = true
    page.focusToken = "primary"
    page.restoreFocus()
    return true
}
