package org.muilab.notigpt.ui.component.notification.card.groupcard

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.muilab.notigpt.model.notifications.NotiGroupItem
import org.muilab.notigpt.ui.component.notification.card.groupcard.elements.GroupCardActionsRow
import org.muilab.notigpt.ui.component.notification.card.groupcard.elements.GroupCardCardSurface
import org.muilab.notigpt.ui.component.notification.card.groupcard.elements.GroupCardChildren
import org.muilab.notigpt.ui.component.notification.card.groupcard.elements.GroupCardConstants
import org.muilab.notigpt.ui.component.notification.card.groupcard.elements.GroupCardHeader
import org.muilab.notigpt.ui.component.notification.card.groupcard.elements.GroupCardRenameDialog
import org.muilab.notigpt.ui.component.notification.card.groupcard.elements.SwipeDelegationState
import org.muilab.notigpt.ui.component.notification.card.groupcard.elements.computeItemsToShow
import org.muilab.notigpt.ui.component.notification.card.groupcard.elements.groupCardSwipe
import org.muilab.notigpt.ui.component.notification.card.groupcard.elements.groupCardSwipeDelegation
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_SAVE
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Renders one notification group and its currently visible child cards.
 *
 * This component assembles group header, children, swipe behavior, and group actions. Keep membership mutations
 * callback-driven so grouping policy remains in the drawer layer.
 */
@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@Suppress("AssignedValueIsNeverRead")
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

    // Feature 1 State: Rename Dialog
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(group.title) }

    // Feature 2 State: Swipe Logic
    val swipeDeleteLeft = SharedPreferencesManager.swipeDeleteLeft
    val coroutineScope = rememberCoroutineScope()
    val horizontalOffsetX = remember { Animatable(0f) }

    var cardWidth by remember { mutableFloatStateOf(0f) }
    var endActionsWidth by remember { mutableFloatStateOf(0f) }

    // Instead of using a fraction, measure the actual actions row width.
    var actionsMeasuredWidthPx by remember { mutableFloatStateOf(0f) }

    // Background color
    val backgroundColor = when {
        isMergeTarget -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceDim
    }

    // Categories have been removed.
    val representativeCategory = ""

    // NEW: Batch To-Top Action
    // Logic: If ANY child is to-topped, the button allows you to Undo all.
    // Otherwise, it allows you to Set Top all.
    val isAnyChildTopped = children.any { it.notiUnit.isSetToTop }

    // Helper to close the revealed actions.
    val collapse: suspend () -> Unit = { try { horizontalOffsetX.animateTo(0f) } catch (_: Throwable) {} }

    val touchSlopPx = LocalViewConfiguration.current.touchSlop

    // (2) Swipe delegation: if gesture starts on children area, let child NotiCards swipe; else group swipes.
    var swipeDelegate by remember { mutableStateOf(SwipeDelegationState.Group) }

    var childrenBoundsInParent by remember { mutableStateOf<Rect?>(null) }

    // Child cards should always be swipeable when not sorting.
    val enableChildSwipe = !isSortingMode

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .onSizeChanged {
                cardWidth = it.width.toFloat()
                // Fall back to the full child list until this path has measured visible rows.
                if (actionsMeasuredWidthPx <= 0f) {
                    endActionsWidth = cardWidth * GroupCardConstants.ActionsRevealFraction
                }
            }
            .groupCardSwipeDelegation(
                enabled = !isSortingMode,
                touchSlopPx = touchSlopPx,
                childrenBoundsInParent = childrenBoundsInParent,
                onDelegate = { swipeDelegate = it },
            )
            .groupCardSwipe(
                enabled = !isSortingMode && swipeDelegate == SwipeDelegationState.Group,
                cardWidthPx = cardWidth,
                endActionsWidthPx = if (actionsMeasuredWidthPx > 0f) actionsMeasuredWidthPx else endActionsWidth,
                swipeDeleteLeft = swipeDeleteLeft,
                horizontalOffsetX = horizontalOffsetX,
                scope = coroutineScope,
                touchSlopPx = touchSlopPx,
                onDismiss = { drawerViewModel.actOnGroup(group.groupId, "dismiss_swipe") },
                childrenBoundsInParent = childrenBoundsInParent,
            )
    ) {
        // --- BACKGROUND ACTIONS ---
        GroupCardActionsRow(
            modifier = Modifier
                .align(if (swipeDeleteLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .onSizeChanged { actionsMeasuredWidthPx = it.width.toFloat() },
            swipeDeleteLeft = swipeDeleteLeft,
            endActionsWidthPx = if (actionsMeasuredWidthPx > 0f) actionsMeasuredWidthPx else endActionsWidth,
            horizontalOffsetX = horizontalOffsetX.value,
            representativeCategory = representativeCategory,
            isAnyChildTopped = isAnyChildTopped,
            onHideActions = { coroutineScope.launch { collapse() } },
            onGroupTopToggle = {
                val action = if (isAnyChildTopped) "undo_to_top" else "to_top"
                drawerViewModel.actOnGroup(group.groupId, action)
                coroutineScope.launch { collapse() }
            },
            onMakeTaskToggle = null,
            onSaveToggle = null,
            onArchiveToggle = null,
        )

        // --- FOREGROUND CARD ---
        GroupCardCardSurface(horizontalOffsetX = horizontalOffsetX.value) {
            // Header
            GroupCardHeader(
                title = group.title,
                childCount = children.size,
                expanded = expanded,
                isSortingMode = isSortingMode,
                onToggleExpanded = { drawerViewModel.toggleGroupExpansion(group.groupId, expanded) },
                onEditTitle = {
                    renameText = group.title
                    showRenameDialog = true
                },
                onUngroup = { drawerViewModel.onUngroup(group.groupId) },
            )

            Spacer(Modifier.height(8.dp))

            // Children Container
            val itemsToShow = computeItemsToShow(children, expanded)

            GroupCardChildren(
                context = context,
                itemsToShow = itemsToShow,
                drawerViewModel = drawerViewModel,
                isMergeTarget = false,
                parentViewport = parentViewport,
                containerColor = backgroundColor,
                enableChildSwipe = enableChildSwipe,
                onBoundsInParent = { bounds -> childrenBoundsInParent = bounds },
            )
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        GroupCardRenameDialog(
            value = renameText,
            onValueChange = { renameText = it },
            onConfirm = {
                if (renameText.isNotBlank()) {
                    drawerViewModel.renameGroup(group.groupId, renameText)
                }
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }
}