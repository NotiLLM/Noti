package org.muilab.notigpt.ui.component.notification.card.groupcard.elements

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.max

/**
 * Pointer-input helper for delegating horizontal swipes between group cards and child cards.
 *
 * Keep nested gesture arbitration here. Card components should consume this as a modifier instead of embedding
 * low-level pointer-state rules in their layout code.
 */
internal fun Modifier.groupCardSwipeDelegation(
    enabled: Boolean,
    touchSlopPx: Float,
    childrenBoundsInParent: Rect?,
    onDelegate: (SwipeDelegationState) -> Unit,
): Modifier {
    if (!enabled) return this

    return pointerInput(touchSlopPx) {
        val horizontalBiasFactor = 0.45f
        val minHorizontalPx = touchSlopPx

        awaitEachGesture {
            onDelegate(SwipeDelegationState.Group)
            val down = awaitFirstDown(requireUnconsumed = false)

            // Decide based on DOWN location.
            val downInChildren = childrenBoundsInParent?.contains(down.position) == true

            val slop = awaitTouchSlopOrCancellation(down.id) { _, over ->
                val absX = abs(over.x)
                val absY = abs(over.y)
                if (absX > max(minHorizontalPx, absY * horizontalBiasFactor)) {
                    onDelegate(if (downInChildren) SwipeDelegationState.Child else SwipeDelegationState.Group)
                }
            }

            if (slop == null) {
                onDelegate(SwipeDelegationState.Group)
            }
        }
    }
}
