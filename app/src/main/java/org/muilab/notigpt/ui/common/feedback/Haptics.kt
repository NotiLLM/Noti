package org.muilab.notigpt.ui.common.feedback

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Semantic haptic wrappers over [HapticFeedback].
 *
 * Callers express intent ("a toggle just flipped", "a swipe committed") rather than picking a raw
 * [HapticFeedbackType], so the feel stays consistent app-wide and can be retuned in one place. Obtain
 * a [HapticFeedback] via `LocalHapticFeedback.current` and call these.
 */
object Haptics {
    /** A binary control flipped (checkbox, star, archive, pin). */
    fun toggle(h: HapticFeedback, on: Boolean) =
        h.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** A drag crossed a commit threshold (review swipe, expand snap point) — light tick. */
    fun thresholdCross(h: HapticFeedback) =
        h.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

    /** A gesture committed to a destructive/irreversible action (archive/dismiss swipe). */
    fun commit(h: HapticFeedback) =
        h.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Long-press recognised (card options, reorder pickup). */
    fun longPress(h: HapticFeedback) =
        h.performHapticFeedback(HapticFeedbackType.LongPress)
}
