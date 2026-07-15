package org.muilab.notigpt.ui.notification.component.card.noticard.elements

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private const val ACTIONS_REVEAL_EXTRA_DP = 8

/**
 * Swipe gesture handler for one notification card.
 *
 * Keep gesture thresholds and translation math here, while action execution remains callback-driven. If group
 * and notification swipe rules converge, extract shared gesture policy rather than duplicating thresholds.
 */
fun Modifier.notiCardSwipeHandler(
    enabled: Boolean,
    endActionsWidth: Float,
    cardWidth: Float,
    swipeDeleteLeft: Boolean,
    excludedBounds: List<Rect>,
    horizontalOffsetX: Animatable<Float, *>,
    onDismiss: () -> Unit,
    scope: CoroutineScope,
    onSwipeActiveChanged: (Boolean) -> Unit = {},
    // Both directions dismiss instead of one dismissing and the other revealing the background
    // action row. Kept switchable rather than deleting the single-direction path (see NotiCard.kt).
    bidirectionalDismiss: Boolean = false,
): Modifier {
    if (!enabled) return this

    return this.pointerInput(endActionsWidth, cardWidth, swipeDeleteLeft, bidirectionalDismiss) {
        val density = this
        val extraPx = with(density) { ACTIONS_REVEAL_EXTRA_DP.dp.toPx() }
        val maxActionsOffset = endActionsWidth + extraPx

        // Direction decides horizontal intent, not speed: at slop crossing the post-slop
        // overshoot is tiny for slow drags, so any magnitude floor here makes slow
        // horizontal swipes impossible. Dismiss/reveal distances below stay unchanged.
        val horizontalBiasFactor = 1.05f

        awaitEachGesture {
            onSwipeActiveChanged(false)
            val down = awaitFirstDown(requireUnconsumed = false)

            // Read the latest excluded button rects (expand + pin) without re-keying pointerInput.
            // Only those two controls swallow the gesture; the rest of the card is swipeable.
            if (excludedBounds.any { it.contains(down.position) }) {
                return@awaitEachGesture
            }

            var isHorizontal = false
            val slopResult = awaitTouchSlopOrCancellation(down.id) { change, over ->
                val absX = abs(over.x)
                val absY = abs(over.y)
                if (absX > absY * horizontalBiasFactor) {
                    isHorizontal = true
                    onSwipeActiveChanged(true)
                    change.consume()
                }
            }

            if (slopResult == null) {
                onSwipeActiveChanged(false)
                return@awaitEachGesture
            }
            if (!isHorizontal) {
                onSwipeActiveChanged(false)
                return@awaitEachGesture
            }

            val velocityTracker = VelocityTracker()
            var dragCompletedNormally = false

            // Avoid suspending calls in restricted pointerInput scope.
            // We'll coalesce deltas (CONFLATED behavior) and apply them on the provided scope.
            var pendingDx = 0f
            var snapJob: Job? = null

            var shouldSettle = true
            try {
                drag(down.id) { change ->
                    val delta = change.positionChange()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)

                    if (delta.x != 0f) {
                        change.consume()
                        pendingDx += delta.x

                        // Launch at most one in-flight snap job; it will consume the latest pendingDx.
                        if (snapJob?.isActive != true) {
                            snapJob = scope.launch {
                                while (true) {
                                    val dx = pendingDx
                                    pendingDx = 0f
                                    if (dx == 0f) break

                                    val base = horizontalOffsetX.value
                                    val newOffset = if (bidirectionalDismiss) {
                                        (base + dx).coerceIn(-cardWidth, cardWidth)
                                    } else {
                                        (base + dx).coerceIn(-cardWidth, maxActionsOffset)
                                    }
                                    horizontalOffsetX.snapTo(newOffset)
                                }
                            }
                        }
                    }
                }
                dragCompletedNormally = true
            } catch (_: Throwable) {
                dragCompletedNormally = false
            } finally {
                val endedByUp = dragCompletedNormally
                onSwipeActiveChanged(false)

                if (!endedByUp) {
                    shouldSettle = false
                }

                if (shouldSettle) {
                    scope.launch {
                        // Ensure the last pending delta gets applied before we decide.
                        snapJob?.join()

                        val vel = try {
                            velocityTracker.calculateVelocity()
                        } catch (_: Throwable) {
                            Velocity.Zero
                        }
                        val flingVelocityX = vel.x
                        val flingThreshold = 800f
                        val swipeThresholdPx = cardWidth * 0.20f

                        // If no fling, decide based on how far the user dragged when they lifted.
                        // We use a smaller actions threshold to feel responsive.
                        val dismissThresholdPx = cardWidth * 0.35f
                        val actionsThresholdPx = max(48f, endActionsWidth * 0.18f)

                        val currentOffsetVal = horizontalOffsetX.value

                        if (bidirectionalDismiss) {
                            val minOffsetForFling = swipeThresholdPx * 0.5f
                            val flungPastMin = abs(flingVelocityX) > flingThreshold && abs(currentOffsetVal) > minOffsetForFling
                            if (flungPastMin || abs(currentOffsetVal) >= dismissThresholdPx) {
                                val dir = if (currentOffsetVal < 0f) -1 else 1
                                horizontalOffsetX.animateTo(dir * cardWidth, tween(250))
                                onDismiss()
                                horizontalOffsetX.snapTo(0f)
                            } else {
                                horizontalOffsetX.animateTo(0f, tween(200))
                            }
                            return@launch
                        }

                        // If fling: keep the existing fling behavior.
                        if (abs(flingVelocityX) > flingThreshold) {
                            val flingDir = if (flingVelocityX < 0f) -1 else 1
                            val offsetDir = when {
                                currentOffsetVal < 0f -> -1
                                currentOffsetVal > 0f -> 1
                                else -> flingDir
                            }
                            val minOffsetForFling = swipeThresholdPx * 0.5f

                            if (flingDir == offsetDir && abs(currentOffsetVal) > minOffsetForFling) {
                                if (flingDir < 0) {
                                    if (swipeDeleteLeft) {
                                        horizontalOffsetX.animateTo(-cardWidth, tween(300))
                                        onDismiss()
                                        horizontalOffsetX.snapTo(0f)
                                    } else {
                                        horizontalOffsetX.animateTo(-maxActionsOffset)
                                    }
                                } else {
                                    if (swipeDeleteLeft) {
                                        horizontalOffsetX.animateTo(maxActionsOffset)
                                    } else {
                                        horizontalOffsetX.animateTo(cardWidth, tween(300))
                                        onDismiss()
                                        horizontalOffsetX.snapTo(0f)
                                    }
                                }
                                return@launch
                            }
                        }

                        // Non-fling release settle.
                        val dismissDir = if (swipeDeleteLeft) -1 else 1
                        val actionsDir = -dismissDir
                        fun sign(x: Float) = when {
                            x > 0f -> 1
                            x < 0f -> -1
                            else -> 0
                        }
                        val offsetDir = sign(currentOffsetVal)

                        if (offsetDir == dismissDir) {
                            if (abs(currentOffsetVal) >= dismissThresholdPx) {
                                horizontalOffsetX.animateTo(dismissDir * cardWidth, tween(250))
                                onDismiss()
                                horizontalOffsetX.snapTo(0f)
                            } else {
                                horizontalOffsetX.animateTo(0f, tween(200))
                            }
                            return@launch
                        }

                        if (offsetDir == actionsDir) {
                            if (abs(currentOffsetVal) >= actionsThresholdPx) {
                                // Reveal full actions + a touch of breathing room.
                                val target = actionsDir * maxActionsOffset
                                horizontalOffsetX.animateTo(target, tween(200))
                            } else {
                                horizontalOffsetX.animateTo(0f, tween(200))
                            }
                            return@launch
                        }

                        // No meaningful offset
                        horizontalOffsetX.animateTo(0f, tween(200))
                    }
                }
            }
        }
    }
}
