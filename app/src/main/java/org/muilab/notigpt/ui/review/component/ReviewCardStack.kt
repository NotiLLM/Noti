package org.muilab.notigpt.ui.review.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.muilab.notigpt.model.features.SavedItem

private const val MAX_VISIBLE = 3
private const val MAX_ROTATION_DEG = 10f

/**
 * Tinder-style swipe stack. The top card drags horizontally; a commit past the threshold flies it
 * off-screen and fires [onApprove] (right) or [onReject] (left). Behind cards are scaled/offset to
 * suggest depth. Tapping the top card calls [onExpand].
 */
@Composable
fun ReviewCardStack(
    items: List<SavedItem>,
    onApprove: (SavedItem) -> Unit,
    onReject: (SavedItem) -> Unit,
    onExpand: (SavedItem) -> Unit,
    modifier: Modifier = Modifier,
    minimalCard: @Composable (item: SavedItem, approveProgress: Float, rejectProgress: Float) -> Unit,
) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val commitThreshold = screenWidthPx * 0.30f

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val visible = items.take(MAX_VISIBLE)
        // Draw deepest first so the top card renders last (in front).
        visible.reversed().forEachIndexed { indexFromBack, item ->
            val depth = visible.lastIndex - indexFromBack // 0 = top
            if (depth == 0) {
                key(item.savedItemId) {
                    TopCard(
                        item = item,
                        commitThreshold = commitThreshold,
                        screenWidthPx = screenWidthPx,
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
    item: SavedItem,
    commitThreshold: Float,
    screenWidthPx: Float,
    onApprove: (SavedItem) -> Unit,
    onReject: (SavedItem) -> Unit,
    onExpand: (SavedItem) -> Unit,
    minimalCard: @Composable (item: SavedItem, approveProgress: Float, rejectProgress: Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    val approveProgress = (offsetX.value / commitThreshold).coerceIn(0f, 1f)
    val rejectProgress = (-offsetX.value / commitThreshold).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = (offsetX.value / screenWidthPx) * MAX_ROTATION_DEG
            }
            .pointerInput(item.savedItemId) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                    },
                    onDragEnd = {
                        when {
                            offsetX.value > commitThreshold -> scope.launch {
                                offsetX.animateTo(screenWidthPx * 1.5f, tween(250))
                                onApprove(item)
                            }
                            offsetX.value < -commitThreshold -> scope.launch {
                                offsetX.animateTo(-screenWidthPx * 1.5f, tween(250))
                                onReject(item)
                            }
                            else -> scope.launch { offsetX.animateTo(0f, tween(200)) }
                        }
                    },
                )
            }
            .pointerInput(item.savedItemId) {
                detectTapGestures(onTap = { onExpand(item) })
            },
    ) {
        minimalCard(item, approveProgress, rejectProgress)
    }
}
