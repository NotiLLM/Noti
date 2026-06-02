package org.muilab.notigpt.ui.notification.component.card.groupcard.elements

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Swipe gesture wrapper for group cards.
 *
 * Keep group-card gesture thresholds and callback dispatch here. Shared nested-swipe coordination lives in the
 * swipe-delegation helpers so child cards and parent cards do not fight for the same drag.
 */
internal fun Modifier.groupCardSwipe(
    enabled: Boolean,
    cardWidthPx: Float,
    endActionsWidthPx: Float,
    swipeDeleteLeft: Boolean,
    horizontalOffsetX: Animatable<Float, *>,
    scope: CoroutineScope,
    touchSlopPx: Float,
    onDismiss: () -> Unit,
    childrenBoundsInParent: androidx.compose.ui.geometry.Rect? = null,
): Modifier {
    if (!enabled) return this

    return this.pointerInput(cardWidthPx, endActionsWidthPx, touchSlopPx, swipeDeleteLeft) {
        val horizontalBiasFactor = 0.45f
        val minHorizontalPx = touchSlopPx * 0.45f

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            // If the gesture starts on the child container, never steal it.
            val childBounds = childrenBoundsInParent
            if (childBounds != null && childBounds.contains(down.position)) {
                return@awaitEachGesture
            }

            var isHorizontal = false
            val slopResult = awaitTouchSlopOrCancellation(down.id) { change, over ->
                val absX = abs(over.x)
                val absY = abs(over.y)
                if (absX > max(minHorizontalPx, absY * horizontalBiasFactor)) {
                    isHorizontal = true
                    change.consume()
                }
            }

            if (slopResult != null && isHorizontal) {
                val velocityTracker = VelocityTracker()
                try {
                    drag(down.id) { change ->
                        val delta = change.positionChange()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (delta.x != 0f) {
                            change.consume()
                            // Use current value as base so dragging works even when already partially revealed.
                            val base = horizontalOffsetX.value
                            val newOffset = base + delta.x
                            scope.launch {
                                horizontalOffsetX.snapTo(newOffset.coerceIn(-cardWidthPx, endActionsWidthPx))
                            }
                        }
                    }
                } finally {
                    scope.launch {
                        val swipeThresholdPx = cardWidthPx * 0.20f

                        if (abs(horizontalOffsetX.value) > swipeThresholdPx) {
                            // When revealing actions (non-dismiss direction), always snap to FULL endActionsWidth.
                            if (swipeDeleteLeft) {
                                when {
                                    horizontalOffsetX.value < -swipeThresholdPx -> {
                                        horizontalOffsetX.animateTo(-cardWidthPx, tween(300))
                                        onDismiss()
                                        horizontalOffsetX.snapTo(0f)
                                    }
                                    horizontalOffsetX.value > swipeThresholdPx -> horizontalOffsetX.animateTo(endActionsWidthPx)
                                    else -> horizontalOffsetX.animateTo(0f)
                                }
                            } else {
                                when {
                                    horizontalOffsetX.value > swipeThresholdPx -> {
                                        horizontalOffsetX.animateTo(cardWidthPx, tween(300))
                                        onDismiss()
                                        horizontalOffsetX.snapTo(0f)
                                    }
                                    horizontalOffsetX.value < -swipeThresholdPx -> horizontalOffsetX.animateTo(-endActionsWidthPx)
                                    else -> horizontalOffsetX.animateTo(0f)
                                }
                            }
                        } else {
                            horizontalOffsetX.animateTo(0f)
                        }
                    }
                }
            }
        }
    }
}
