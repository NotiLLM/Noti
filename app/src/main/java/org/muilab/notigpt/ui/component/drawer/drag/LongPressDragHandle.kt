package org.muilab.notigpt.ui.component.drawer.drag

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A tiny, self-contained long-press drag handle.
 *
 * Design goals (matching the old Reorderable lib behavior):
 * - Drag starts ONLY after a long-press on this handle.
 * - While dragging, we consume pointer changes so other gestures (swipe, scroll, click)
 *   do not also trigger.
 * - Outside this handle, the rest of the item remains fully interactive.
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

