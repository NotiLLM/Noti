package org.muilab.notigpt.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * App shape scale, wired into [androidx.compose.material3.MaterialTheme.shapes].
 *
 * One scale for the whole app so corner radii stop drifting (previously 6/8/12/14/16/20 dp ad-hoc).
 * Reference via `MaterialTheme.shapes.medium` etc. — no literal `RoundedCornerShape` in `ui/` outside
 * this file.
 *
 * - extraSmall  6dp — badges, tiny inline chips
 * - small      10dp — chips, inline surfaces
 * - medium     14dp — list cards (SavedItemCard, category rows)
 * - large      18dp — hero cards, bottom sheets, NotiCard
 * - extraLarge 26dp — dialogs, review cards
 */
val NotiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)
