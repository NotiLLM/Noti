package org.muilab.notigpt.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale for the app, on a 4-pt grid.
 *
 * The refinement direction favours whitespace-driven hierarchy over container tinting, so these tokens
 * intentionally lean generous (section gaps, card inner padding). Use them instead of literal `.dp`
 * paddings in `ui/` so screens share one rhythm. Keep values here; reference as `Dimens.screenH` etc.
 */
object Dimens {
    /** Horizontal gutter for all list/screen content. */
    val screenH = 16.dp

    /** Vertical gap between stacked cards in a list. */
    val cardOuterV = 6.dp

    /** Inner padding inside a card body. */
    val cardInner = 16.dp

    /** Gap above a section (header + content block). */
    val sectionTop = 28.dp

    /** Gap between a section header and its first item. */
    val sectionHeaderBottom = 8.dp

    /** Small inline gap (icon↔text, chip↔chip). */
    val inline = 8.dp

    /** Medium gap between related elements within a card. */
    val element = 12.dp
}
