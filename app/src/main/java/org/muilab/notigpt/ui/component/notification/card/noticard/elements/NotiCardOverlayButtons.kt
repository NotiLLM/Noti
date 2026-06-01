package org.muilab.notigpt.ui.component.notification.card.noticard.elements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.component.drawer.drag.longPressDragHandle
import org.muilab.notigpt.ui.component.notification.action.NotiActionIconButton
import org.muilab.notigpt.ui.utils.NotiExpandState
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.domain.action.NotiActionType
import sh.calvin.reorderable.ReorderableCollectionItemScope

/**
 * Overlay action buttons shown on top of a notification card.
 *
 * This layer bridges visible card affordances to ViewModel callbacks. Avoid adding direct repository or service
 * calls here so overlay behavior stays testable through the card's action surface.
 */
@Composable
fun NotiCardOverlayButtons(
    modifier: Modifier = Modifier,
    translationX: Float,
    requiresExpansion: Boolean,
    progress: Float,
    isSortingMode: Boolean,
    isPinned: Boolean,
    anchored: AnchoredDraggableState<NotiExpandState>,
    anchoredFlingBehavior: FlingBehavior,
    onUpdateMeasuredAnchors: suspend () -> Unit,
    notiKey: String,
    drawerViewModel: DrawerViewModel,
    onOverlayBoundsChange: (Rect?) -> Unit,
    reorderEnabled: Boolean = false,
    // If provided by a parent ReorderableItem, this enables library-native drag/auto-scroll.
    reorderScope: ReorderableCollectionItemScope? = null,
    // Fallback callbacks (used when reorderScope == null).
    onStartReorderDrag: (Offset) -> Unit = {},
    onReorderDrag: (Offset) -> Unit = {},
    onStopReorderDrag: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .padding(end = 12.dp, top = 8.dp)
            .fillMaxHeight()
            .graphicsLayer { this.translationX = translationX }
            .zIndex(2f)
            .onGloballyPositioned { coords ->
                onOverlayBoundsChange(coords.boundsInWindow())
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (requiresExpansion) {
            val expandPainter = if (progress < 0.5f) {
                painterResource(R.drawable.expand_circle_down)
            } else {
                painterResource(R.drawable.expand_circle_up)
            }
            Icon(
                painter = expandPainter,
                contentDescription = "Expand",
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(30.dp)
                    .anchoredDraggable(anchored, Orientation.Vertical, enabled = true, flingBehavior = anchoredFlingBehavior)
                    .clickable {
                        coroutineScope.launch {
                            onUpdateMeasuredAnchors()
                            if (anchored.offset < 20f) anchored.animateTo(NotiExpandState.Opened)
                            else anchored.animateTo(NotiExpandState.Collapsed)
                        }
                    },
            )
        }

        if (isSortingMode) {
            val handleModifier = if (reorderEnabled && reorderScope != null) {
                with(reorderScope) { Modifier.minimumInteractiveComponentSize().longPressDraggableHandle() }
            } else {
                Modifier
                    .minimumInteractiveComponentSize()
                    .longPressDragHandle(
                        enabled = reorderEnabled,
                        onDragStartInParent = { start -> onStartReorderDrag(start) },
                        onDragDelta = { delta -> onReorderDrag(delta) },
                        onDragEnd = { onStopReorderDrag() },
                        onDragCancel = { onStopReorderDrag() },
                    )
            }

            Icon(
                painter = painterResource(R.drawable.drag_handle),
                contentDescription = "Drag to reorder",
                modifier = handleModifier,
            )
        } else {
            NotiActionIconButton(
                if (isPinned) R.drawable.pin_yes else R.drawable.pin_no,
                "Pin",
                {
                    if (isPinned) drawerViewModel.actOnNoti(notiKey, NotiActionType.Unpin)
                    else drawerViewModel.actOnNoti(notiKey, NotiActionType.Pin)
                },
                if (isPinned) Color(76, 139, 245) else Color.Unspecified,
            )
        }
    }
}
