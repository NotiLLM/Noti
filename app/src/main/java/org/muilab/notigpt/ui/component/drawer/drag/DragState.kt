package org.muilab.notigpt.ui.component.drawer.drag

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

/**
 * Ephemeral drag state shared by drawer drag handles and drop targets.
 *
 * This class should stay UI-only and in-memory. Persisted ordering/grouping decisions belong to the drawer
 * ViewModel or database actions after a drag gesture is committed.
 */
@Stable
class DragState {
    var draggingId by mutableStateOf<String?>(null)
    var dragStartPointerInRoot by mutableStateOf(Offset.Zero)
    var dragOffsetY by mutableFloatStateOf(0f)

    var hoverId by mutableStateOf<String?>(null)
    var hoverStartMs by mutableStateOf<Long?>(null)

    // bounds in ROOT coords of visible items
    val boundsById = mutableStateMapOf<String, Rect>()

    var boxTopLeftInRoot by mutableStateOf(Offset.Zero)
    var boxSize by mutableStateOf(IntSize.Zero)

    fun clear() {
        draggingId = null
        hoverId = null
        hoverStartMs = null
        dragOffsetY = 0f
    }
}
