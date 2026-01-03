package org.muilab.notigpt.ui.component

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.muilab.notigpt.model.notifications.NotiGroupItem
import org.muilab.notigpt.model.notifications.NotiItem
import org.muilab.notigpt.ui.component.notification.card.groupcard.GroupCard
import org.muilab.notigpt.ui.component.notification.card.noticard.NotiCard
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.component.drawer.drag.DragState
import org.muilab.notigpt.ui.component.drawer.drag.findClosestHit
import org.muilab.notigpt.ui.component.drawer.drag.unionRect

private const val NEST_HOVER_MS = 450L

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NotiDrawer(context: Context, drawerViewModel: DrawerViewModel) {
    val items by drawerViewModel.groupedNotifications.collectAsState()
    val isSortingMode by drawerViewModel.isSortingMode.collectAsState()
    val category by drawerViewModel.category.collectAsState()
    val appCategory by drawerViewModel.appCategory.collectAsState()

    var viewportBounds by remember { mutableStateOf<Rect?>(null) }

    val dragState = remember { DragState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Reset list position on filter changes
    LaunchedEffect(category, appCategory) {
        // If the list has items, go back to the top immediately.
        // Use scrollToItem to avoid long animations during filter changes.
        listState.scrollToItem(0)
    }

    // Sync stale bounds cleanup
    val validIds = remember(items) { items.map { it.id }.toSet() }
    LaunchedEffect(validIds) {
        dragState.boundsById.keys.toList().forEach { id ->
            if (id !in validIds) dragState.boundsById.remove(id)
        }
    }

    // Ticker for hover timing
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(dragState.draggingId, dragState.hoverId) {
        while (dragState.draggingId != null && dragState.hoverId != null) {
            now = System.currentTimeMillis()
            delay(16)
        }
    }

    // Merge Preview Logic
    val draggingId = dragState.draggingId
    val hoverId = dragState.hoverId
    val hoverMs = dragState.hoverStartMs?.let { now - it } ?: 0L
    val showMergePreview = (draggingId != null && hoverId != null && hoverMs >= NEST_HOVER_MS && isSortingMode)

    val mergeRect: Rect? = remember(draggingId, hoverId, dragState.dragOffsetY, showMergePreview) {
        if (!showMergePreview) return@remember null
        val dId = draggingId ?: return@remember null
        val hId = hoverId ?: return@remember null

        val dragRect = dragState.boundsById[dId] ?: return@remember null
        val targetRect = dragState.boundsById[hId] ?: return@remember null

        val shiftedDrag = Rect(
            dragRect.left,
            dragRect.top + dragState.dragOffsetY,
            dragRect.right,
            dragRect.bottom + dragState.dragOffsetY
        )
        unionRect(shiftedDrag, targetRect).inflate(18f)
    }

    val density = LocalDensity.current
    val autoScrollPx = with(density) { 14.dp.toPx() }
    val edgePx = with(density) { 64.dp.toPx() }

    val highlightColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
    val strokeColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                dragState.boxTopLeftInRoot = coords.positionInRoot()
                dragState.boxSize = coords.size
            }
            .then(
                if (isSortingMode) {
                    Modifier.pointerInput(items) {
                        detectDragGestures(
                            onDragStart = { startInBox ->
                                val startInRoot = dragState.boxTopLeftInRoot + startInBox
                                val hit = findClosestHit(dragState.boundsById, startInRoot, null, validIds)
                                if (hit != null) {
                                    dragState.draggingId = hit
                                    dragState.dragStartPointerInRoot = startInRoot
                                    dragState.dragOffsetY = 0f
                                    dragState.hoverId = null
                                    dragState.hoverStartMs = null
                                }
                            },
                            onDrag = { change, dragAmount ->
                                val dId = dragState.draggingId ?: return@detectDragGestures
                                change.consume()
                                dragState.dragOffsetY += dragAmount.y

                                val pointerInBox = change.position
                                val pointerInRoot = dragState.boxTopLeftInRoot + pointerInBox

                                // Auto Scroll
                                if (pointerInBox.y < edgePx) scope.launch { listState.scrollBy(-autoScrollPx) }
                                else if (pointerInBox.y > dragState.boxSize.height - edgePx) scope.launch { listState.scrollBy(autoScrollPx) }

                                val hit = findClosestHit(dragState.boundsById, pointerInRoot, dId, validIds)
                                if (hit != dragState.hoverId) {
                                    dragState.hoverId = hit
                                    dragState.hoverStartMs = if (hit != null) System.currentTimeMillis() else null
                                }
                            },
                            onDragEnd = {
                                val dId = dragState.draggingId
                                val hId = dragState.hoverId
                                if (dId != null && hId != null && dId != hId) {
                                    val finalHoverMs = dragState.hoverStartMs?.let { System.currentTimeMillis() - it } ?: 0L
                                    if (finalHoverMs >= NEST_HOVER_MS) {
                                        // PERFORM MERGE
                                        drawerViewModel.onMerge(dId, hId)
                                    }
                                }
                                dragState.clear()
                            },
                            onDragCancel = { dragState.clear() }
                        )
                    }
                } else Modifier
            )
    ) {
        // Draw Merge Preview Bubble
        if (showMergePreview && mergeRect != null) {
            val origin = dragState.boxTopLeftInRoot
            val topLeft = Offset(mergeRect.left - origin.x, mergeRect.top - origin.y)
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(
                    color = highlightColor,
                    topLeft = topLeft,
                    size = Size(mergeRect.width, mergeRect.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(32f, 32f)
                )
                drawRoundRect(
                    color = strokeColor,
                    topLeft = topLeft,
                    size = Size(mergeRect.width, mergeRect.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(32f, 32f),
                    style = Stroke(width = 4f)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .onGloballyPositioned { layoutCoordinates ->
                    viewportBounds = layoutCoordinates.boundsInWindow()
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
        ) {
            items(items, key = { it.id }) { item ->

                val isDragging = dragState.draggingId == item.id
                val isHovering = dragState.hoverId == item.id
                val isMergeTarget = isHovering && hoverMs >= NEST_HOVER_MS && isSortingMode

                // Wrapper to capture bounds and handle drag visual offset
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            val tl = coords.positionInRoot()
                            val sz = coords.size
                            dragState.boundsById[item.id] = Rect(tl.x, tl.y, tl.x + sz.width, tl.y + sz.height)
                        }
                        .graphicsLayer {
                            translationY = if (isDragging) dragState.dragOffsetY else 0f
                            scaleX = if (isDragging) 1.02f else 1f
                            scaleY = if (isDragging) 1.02f else 1f
                            alpha = if (isDragging) 0.9f else 1f
                        }
                        .zIndex(if (isDragging) 10f else 0f)
                ) {
                    when (item) {
                        is NotiItem -> {
                            NotiCard(
                                context = context,
                                notiDisplayUnit = item.displayUnit,
                                isDragging = isDragging, // Visual only
                                drawerViewModel = drawerViewModel,
                                isCardVisible = true, // Simplified visibility logic
                                parentViewport = viewportBounds,
                                category = category,
                                appCategory = appCategory,
                                // Pass merging state for visual cues
                                isMergeTarget = isMergeTarget,
                                isInGroup = false
                            )
                        }
                        is NotiGroupItem -> {
                            GroupCard(
                                context = context,
                                groupItem = item,
                                drawerViewModel = drawerViewModel,
                                isMergeTarget = isMergeTarget,
                                isSortingMode = isSortingMode,
                                parentViewport = viewportBounds
                            )
                        }
                    }
                }
            }
        }
    }
}
