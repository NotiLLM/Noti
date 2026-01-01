package org.muilab.notigpt.view.component.notification

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiGroupItem
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_SAVE
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.view.component.notification.action.NotiActionIconButton
import org.muilab.notigpt.viewModel.DrawerViewModel
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun GroupCard(
    context: Context,
    groupItem: NotiGroupItem,
    drawerViewModel: DrawerViewModel,
    isMergeTarget: Boolean,
    isSortingMode: Boolean,
    parentViewport: Rect?
) {
    val group = groupItem.group
    val children = groupItem.children
    val expanded = group.isExpanded

    // Determine Group Top Status: True if ANY child is set to top

    // Feature 1 State: Rename Dialog
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(group.title) }

    // Feature 2 State: Swipe Logic
    val swipeDeleteLeft = SharedPreferencesManager.swipeDeleteLeft
    val coroutineScope = rememberCoroutineScope()
    val horizontalOffsetX = remember { Animatable(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }
    var endActionsWidth by remember { mutableFloatStateOf(0f) }

    // Background color
    val backgroundColor = when {
        isMergeTarget -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceDim
    }

    // Determinate Category of the group (based on the first child, or mixed)
    // We use this to toggle action icons (e.g., Unarchive vs Archive)
    val representativeCategory = children.firstOrNull()?.category ?: ""

    // Swipe Gesture Handler (Adapted from NotiCard)
    val viewTouchSlop = LocalViewConfiguration.current.touchSlop
    val swipeHandler = Modifier.pointerInput(cardWidth, endActionsWidth) {
        val horizontalBiasFactor = 0.45f
        val minHorizontalPx = viewTouchSlop * 0.45f

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // If user is trying to tap/long-press title or expand button, we might need logic here
            // But usually pointerInput on parent works fine.

            var isHorizontal = false
            val slopResult = awaitTouchSlopOrCancellation(down.id) { change, over ->
                val absX = abs(over.x)
                val absY = abs(over.y)
                if (absX > max(minHorizontalPx, absY * horizontalBiasFactor)) {
                    isHorizontal = true
                    change.consume()
                }
            }

            if (slopResult != null && isHorizontal) {
                val velocityTracker = VelocityTracker()
                try {
                    drag(down.id) { change ->
                        val delta = change.positionChange()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (delta.x != 0f) {
                            change.consume()
                            val newOffset = horizontalOffsetX.targetValue + delta.x
                            // Limit swipe to reveal actions
                            coroutineScope.launch {
                                horizontalOffsetX.snapTo(newOffset.coerceIn(-cardWidth, endActionsWidth))
                            }
                        }
                    }
                } finally {
                    coroutineScope.launch {
                        val vel = try { velocityTracker.calculateVelocity() } catch (_: Throwable) { androidx.compose.ui.unit.Velocity.Zero }
                        val flingThreshold = 800f
                        val swipeThresholdPx = cardWidth * 0.20f

                        // Simple snap logic similar to NotiCard
                        if (abs(horizontalOffsetX.value) > swipeThresholdPx) {
                            if (swipeDeleteLeft) {
                                // Swiping Left (Positive X visual? No, negative)
                                // NotiCard logic: Left Swipe -> negative X
                                if (horizontalOffsetX.value < -swipeThresholdPx) {
                                    // Full Dismiss
                                    horizontalOffsetX.animateTo(-cardWidth, tween(300))
                                    drawerViewModel.actOnGroup(group.groupId, "dismiss_swipe")
                                    horizontalOffsetX.snapTo(0f)
                                } else if (horizontalOffsetX.value > swipeThresholdPx) {
                                    // Reveal Actions
                                    horizontalOffsetX.animateTo(endActionsWidth)
                                } else {
                                    horizontalOffsetX.animateTo(0f)
                                }
                            } else {
                                // Swipe Right to Delete
                                if (horizontalOffsetX.value > swipeThresholdPx) {
                                    horizontalOffsetX.animateTo(cardWidth, tween(300))
                                    drawerViewModel.actOnGroup(group.groupId, "dismiss_swipe")
                                    horizontalOffsetX.snapTo(0f)
                                } else if (horizontalOffsetX.value < -swipeThresholdPx) {
                                    horizontalOffsetX.animateTo(-endActionsWidth)
                                } else {
                                    horizontalOffsetX.animateTo(0f)
                                }
                            }
                        } else {
                            horizontalOffsetX.animateTo(0f)
                        }
                    }
                }
            }
        }
    }

    val collapse: suspend () -> Unit = { try { horizontalOffsetX.animateTo(0f) } catch (_: Throwable) {} }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .onSizeChanged { cardWidth = it.width.toFloat(); endActionsWidth = cardWidth * 0.8f }
            .then(if (isSortingMode) Modifier else swipeHandler) // Disable swipe in sort mode
    ) {
        // --- BACKGROUND ACTIONS ---
        Row(
            modifier = Modifier
                .align(if (swipeDeleteLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .onSizeChanged { endActionsWidth = it.width.toFloat() }
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    translationX = 0f
                    val safeWidth = maxOf(1f, endActionsWidth)
                    val t = if (swipeDeleteLeft) (horizontalOffsetX.value / safeWidth) else (-horizontalOffsetX.value / safeWidth)
                    alpha = t.coerceIn(0f, 1f)
                }
                .zIndex(0f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotiActionIconButton(R.drawable.close, "Hide Actions", {
                coroutineScope.launch { collapse() }
            })

            // NEW: Batch To-Top Action
            // Logic: If ANY child is to-topped, the button allows you to Undo all.
            // Otherwise, it allows you to Set Top all.
            val isAnyChildTopped = children.any { it.notiUnit.isSetToTop }

            NotiActionIconButton(
                if (isAnyChildTopped) R.drawable.undo_totop else R.drawable.totop,
                if (isAnyChildTopped) "Undo Group Top" else "Group To Top",
                {
                    val action = if (isAnyChildTopped) "undo_to_top" else "to_top"
                    drawerViewModel.actOnGroup(group.groupId, action)
                    coroutineScope.launch { collapse() }
                }
            )

            NotiActionIconButton(if (representativeCategory == NOTI_CATEGORY_MAKETASK) R.drawable.task_yes else R.drawable.task_no, "Make-Task Group", {
                val action = if (representativeCategory == NOTI_CATEGORY_MAKETASK) "dismiss_task" else "make_task"
                drawerViewModel.actOnGroup(group.groupId, action)
                coroutineScope.launch { collapse() }
            })

            NotiActionIconButton(if (representativeCategory == NOTI_CATEGORY_SAVE) R.drawable.save_yes else R.drawable.save_no, "Save Group", {
                val action = if (representativeCategory == NOTI_CATEGORY_SAVE) "unsave" else "save"
                drawerViewModel.actOnGroup(group.groupId, action)
                coroutineScope.launch { collapse() }
            })

            NotiActionIconButton(if (representativeCategory == NOTI_CATEGORY_ARCHIVE) R.drawable.archive_yes else R.drawable.archive_no, "Archive Group", {
                val action = if (representativeCategory == NOTI_CATEGORY_ARCHIVE) "unarchive" else "archive"
                drawerViewModel.actOnGroup(group.groupId, action)
                coroutineScope.launch { collapse() }
            })
        }

        // --- FOREGROUND CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = horizontalOffsetX.value }
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.large
                )
                .zIndex(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                // 跳過 Lowest/Low，直接用標準 Container
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { drawerViewModel.toggleGroupExpansion(group.groupId, expanded) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(if (expanded) "▼" else "▶", fontWeight = FontWeight.Bold)
                    }

                    // Feature 1: Rename via Combined Clickable
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                            .clickable(
                                onClick = {
                                    drawerViewModel.toggleGroupExpansion(group.groupId, expanded)
                                }
                            )
                    ) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val countText = "${children.size} notifications" +
                            if (!expanded && children.size > 1) " (${children.size - 1} more...)" else ""
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            renameText = group.title
                            showRenameDialog = true
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit group", modifier = Modifier.size(16.dp))
                    }

                    if (isSortingMode) {
                        IconButton(
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            onClick = { drawerViewModel.onUngroup(group.groupId) }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.leave_group),
                                contentDescription = "Ungroup"
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Children Container

                val unreadChildren = children.filter { !it.notiUnit.isRead }
                val itemsToShow = if (expanded) {
                    children
                } else unreadChildren.ifEmpty {
                    children.take(1)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsToShow.forEach { unit ->
                        // Reuse NotiCard
                        NotiCard(
                            context = context,
                            notiDisplayUnit = unit,
                            isDragging = false,
                            drawerViewModel = drawerViewModel,
                            isCardVisible = true,
                            parentViewport = parentViewport,
                            category = unit.category,
                            appCategory = unit.appCategory,
                            isMergeTarget = false,
                            isInGroup = true
                        )
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Group") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameText.isNotBlank()) {
                        drawerViewModel.renameGroup(group.groupId, renameText)
                    }
                    showRenameDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}