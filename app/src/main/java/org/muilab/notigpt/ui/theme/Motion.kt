package org.muilab.notigpt.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Shared motion vocabulary.
 *
 * Anything a finger touches settles on a spring (natural, interruptible); pure fades use short tweens.
 * Replaces scattered `tween(200)/tween(250)` across cards, the review stack, and the top bar so motion
 * reads as one system. Springs are hand-rolled here rather than pulled from `MotionScheme` to stay off
 * the material3 alpha's expressive-theme surface; swap to `MotionScheme` tokens later if adopted.
 */
object MotionSpecs {
    /** Offset / expansion settle — card drag release, anchored-drawer snap. */
    fun <T> settle(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Stiffer settle for small controls (button press, chip select). */
    fun <T> snappy(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Review-card fly-off after a committed swipe. */
    fun <T> flyOff(): AnimationSpec<T> = tween(durationMillis = 260)

    /** Alpha-only cross-fades (search bar, title swaps). */
    fun <T> quick(): AnimationSpec<T> = tween(durationMillis = 180)

    /** Screen push/pop + menu fade-through duration, in ms. */
    const val ScreenTransitionMs = 280
}
