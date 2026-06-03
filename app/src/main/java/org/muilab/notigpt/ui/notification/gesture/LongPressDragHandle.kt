package org.muilab.notigpt.ui.notification.gesture

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Compose modifier that turns long-press movement into drawer drag state updates.
 *
 * Keep gesture recognition here and sorting decisions outside it. The modifier should report drag
 * identity and position, not decide how the drawer data model changes.
 */
@Composable
fun Modifier.longPressDragHandle(
    enabled: Boolean,
    onDragStartInParent: (startInParent: Offset) -> Unit,
    onDragDelta: (dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit = onDragEnd,
): Modifier {
    if (!enabled) return this

    return pointerInput(enabled) {
        detectDragGesturesAfterLongPress(
            onDragStart = { startInParent ->
                onDragStartInParent(startInParent)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                onDragDelta(dragAmount)
            },
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
        )
    }
}

