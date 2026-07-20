package org.muilab.notigpt.ui.review.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.review.viewmodel.ReviewViewModel
import org.muilab.notigpt.ui.common.feedback.Haptics
import org.muilab.notigpt.ui.theme.MotionSpecs

private const val MAX_VISIBLE = 3
private const val MAX_ROTATION_DEG = 10f

/**
 * Tinder-style swipe stack. The top card drags horizontally; a commit past the threshold flies it
 * off-screen and fires [onApprove] (right) or [onReject] (left). Behind cards are scaled/offset to
 * suggest depth. Tapping the top card calls [onExpand]. Width comes from the layout constraints
 * (not a deprecated screen-metrics read).
 */
@Composable
fun ReviewCardStack(
    items: List<ReviewViewModel.ReviewEntry>,
    onApprove: (ReviewViewModel.ReviewEntry) -> Unit,
    onReject: (ReviewViewModel.ReviewEntry) -> Unit,
    onExpand: (ReviewViewModel.ReviewEntry) -> Unit,
    modifier: Modifier = Modifier,
    minimalCard: @Composable (item: ReviewViewModel.ReviewEntry, approveProgress: Float, rejectProgress: Float) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val stackWidthPx = with(density) { maxWidth.toPx() }
        val commitThreshold = stackWidthPx * 0.30f

        val visible = items.take(MAX_VISIBLE)
        // Draw deepest first so the top card renders last (in front).
        visible.reversed().forEachIndexed { indexFromBack, item ->
            val depth = visible.lastIndex - indexFromBack // 0 = top
            if (depth == 0) {
                key(item.key) {
                    TopCard(
                        item = item,
                        commitThreshold = commitThreshold,
                        screenWidthPx = stackWidthPx,
                        onApprove = onApprove,
                        onReject = onReject,
                        onExpand = onExpand,
                        minimalCard = minimalCard,
                    )
                }
            } else {
                val scale = 1f - depth * 0.04f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationY = with(density) { (depth * 10).dp.toPx() }
                        },
                ) {
                    minimalCard(item, 0f, 0f)
                }
            }
        }
    }
}

@Composable
private fun TopCard(
    item: ReviewViewModel.ReviewEntry,
    commitThreshold: Float,
    screenWidthPx: Float,
    onApprove: (ReviewViewModel.ReviewEntry) -> Unit,
    onReject: (ReviewViewModel.ReviewEntry) -> Unit,
    onExpand: (ReviewViewModel.ReviewEntry) -> Unit,
    minimalCard: @Composable (item: ReviewViewModel.ReviewEntry, approveProgress: Float, rejectProgress: Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }

    val approveProgress = (offsetX.value / commitThreshold).coerceIn(0f, 1f)
    val rejectProgress = (-offsetX.value / commitThreshold).coerceIn(0f, 1f)
    val approveLabel = stringResource(R.string.review_approve)
    val rejectLabel = stringResource(R.string.review_reject)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = (offsetX.value / screenWidthPx) * MAX_ROTATION_DEG
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(approveLabel) {
                        onApprove(item)
                        true
                    },
                    CustomAccessibilityAction(rejectLabel) {
                        onReject(item)
                        true
                    },
                )
            }
            .pointerInput(item.key) {
                // `acc` is the source of truth for both threshold ticks and the commit decision;
                // offsetX mirrors it for rendering. `wasPast` ticks the haptic once per crossing.
                var acc = 0f
                var wasPast = false
                detectDragGestures(
                    onDragStart = {
                        acc = offsetX.value
                        wasPast = kotlin.math.abs(acc) > commitThreshold
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        acc += dragAmount.x
                        scope.launch { offsetX.snapTo(acc) }
                        val nowPast = kotlin.math.abs(acc) > commitThreshold
                        if (nowPast && !wasPast) Haptics.thresholdCross(haptic)
                        wasPast = nowPast
                    },
                    onDragEnd = {
                        when {
                            acc > commitThreshold -> scope.launch {
                                Haptics.commit(haptic)
                                offsetX.animateTo(screenWidthPx * 1.5f, MotionSpecs.flyOff())
                                onApprove(item)
                            }
                            acc < -commitThreshold -> scope.launch {
                                Haptics.commit(haptic)
                                offsetX.animateTo(-screenWidthPx * 1.5f, MotionSpecs.flyOff())
                                onReject(item)
                            }
                            else -> scope.launch { offsetX.animateTo(0f, MotionSpecs.settle()) }
                        }
                    },
                )
            }
            .pointerInput(item.key) {
                detectTapGestures(onTap = { onExpand(item) })
            },
    ) {
        minimalCard(item, approveProgress, rejectProgress)
    }
}
