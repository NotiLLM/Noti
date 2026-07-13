package org.muilab.notigpt.ui.review

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.ui.common.component.EmptyState
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.review.component.ReviewCardStack
import org.muilab.notigpt.ui.review.component.ReviewDetailSheet
import org.muilab.notigpt.ui.review.viewmodel.ReviewViewModel
import org.muilab.notigpt.ui.reminder.viewmodel.ReminderViewModel
import org.muilab.notigpt.ui.theme.NotiTheme

/**
 * Tinder-style review of new/updated generated items. Swipe right to approve (acknowledge), left to
 * reject (delete a new item, or revert a pending LLM edit). Chips filter the stack; tapping a card
 * opens its full detail; a snackbar offers single-step undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun ReviewScreen(
    drawerViewModel: DrawerViewModel,
    reminderViewModel: ReminderViewModel,
    onBack: () -> Unit,
    reviewViewModel: ReviewViewModel = viewModel(),
) {
    val allNew by reviewViewModel.newItems.collectAsState()
    val filter by reviewViewModel.filter.collectAsState()

    val filtered = remember(allNew, filter) { allNew.filter { reviewViewModel.matchesFilter(it, filter) } }

    // Undo snackbar.
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val undoLabel = stringResource(R.string.review_undo)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        reviewViewModel.snackbar.collect { msgResId ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(msgResId),
                actionLabel = undoLabel,
                duration = androidx.compose.material3.SnackbarDuration.Short,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) reviewViewModel.undoLast()
        }
    }

    // Detail bottom sheet.
    var expanded by remember { mutableStateOf<SavedItem?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ReviewFilterChips(
                items = allNew,
                selected = filter,
                onSelect = reviewViewModel::setFilter,
            )

            if (filtered.isEmpty()) {
                EmptyState(
                    iconRes = R.drawable.inbox,
                    text = stringResource(R.string.review_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ReviewCardStack(
                    items = filtered,
                    modifier = Modifier.weight(1f),
                    onApprove = reviewViewModel::approve,
                    onReject = reviewViewModel::reject,
                    onExpand = { expanded = it },
                    minimalCard = { item, approveProgress, rejectProgress ->
                        MinimalReviewCard(item, approveProgress, rejectProgress, reviewViewModel)
                    },
                )

                // Bottom action bar: explicit reject / approve buttons + approve-all.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val top = filtered.firstOrNull()
                    FilledIconButton(
                        onClick = { top?.let(reviewViewModel::reject) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.review_reject))
                    }
                    TextButton(
                        onClick = { reviewViewModel.approveAll(filtered) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.review_approve_all))
                    }
                    FilledIconButton(
                        onClick = { top?.let(reviewViewModel::approve) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = NotiTheme.semantic.keepContainer,
                            contentColor = NotiTheme.semantic.onKeepContainer,
                        ),
                    ) {
                        Icon(painterResource(R.drawable.check), contentDescription = stringResource(R.string.review_approve))
                    }
                }
            }
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    expanded?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { expanded = null },
            sheetState = sheetState,
        ) {
            ReviewDetailSheet(
                item = item,
                reviewViewModel = reviewViewModel,
                onApprove = { reviewViewModel.approve(item); expanded = null },
                onReject = { reviewViewModel.reject(item); expanded = null },
            )
        }
    }
}

@Composable
private fun ReviewFilterChips(
    items: List<SavedItem>,
    selected: ReviewViewModel.ReviewFilter,
    onSelect: (ReviewViewModel.ReviewFilter) -> Unit,
) {
    val newTasks = items.count { it.isTask && it.state == SavedItemState.New }
    val updatedTasks = items.count { it.isTask && it.state == SavedItemState.Updated }
    val newKeeps = items.count { !it.isTask && it.state == SavedItemState.New }
    val updatedKeeps = items.count { !it.isTask && it.state == SavedItemState.Updated }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == ReviewViewModel.ReviewFilter.All,
            onClick = { onSelect(ReviewViewModel.ReviewFilter.All) },
            label = { Text(stringResource(R.string.noti_filter_all)) },
        )
        if (newTasks > 0) FilterChip(
            selected = selected == ReviewViewModel.ReviewFilter.NewTasks,
            onClick = { onSelect(ReviewViewModel.ReviewFilter.NewTasks) },
            label = { Text(stringResource(R.string.review_chip_new_tasks, newTasks)) },
        )
        if (updatedTasks > 0) FilterChip(
            selected = selected == ReviewViewModel.ReviewFilter.UpdatedTasks,
            onClick = { onSelect(ReviewViewModel.ReviewFilter.UpdatedTasks) },
            label = { Text(stringResource(R.string.review_chip_updated_tasks, updatedTasks)) },
        )
        if (newKeeps > 0) FilterChip(
            selected = selected == ReviewViewModel.ReviewFilter.NewKeeps,
            onClick = { onSelect(ReviewViewModel.ReviewFilter.NewKeeps) },
            label = { Text(stringResource(R.string.review_chip_new_keeps, newKeeps)) },
        )
        if (updatedKeeps > 0) FilterChip(
            selected = selected == ReviewViewModel.ReviewFilter.UpdatedKeeps,
            onClick = { onSelect(ReviewViewModel.ReviewFilter.UpdatedKeeps) },
            label = { Text(stringResource(R.string.review_chip_updated_keeps, updatedKeeps)) },
        )
    }
}

@Composable
private fun MinimalReviewCard(
    item: SavedItem,
    approveProgress: Float,
    rejectProgress: Float,
    reviewViewModel: ReviewViewModel,
) {
    // Updated items surface the LLM's one-line "what changed"; new items show a content preview.
    var changeSummary by remember(item.savedItemId) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(item.savedItemId) {
        if (item.state == SavedItemState.Updated) changeSummary = reviewViewModel.latestChangeSummary(item)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp,
    ) {
        Box {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReviewBadge(item)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (item.isTask) stringResource(R.string.home_collection_tasks)
                        else stringResource(R.string.home_collection_keep),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = item.title.ifBlank { stringResource(R.string.ui_reminders_untitled_task) },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val body = changeSummary ?: item.content.trim()
                if (body.isNotBlank()) {
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Approve/reject glyphs fade in with drag progress.
            SwipeGlyph(
                text = stringResource(R.string.review_approve),
                color = NotiTheme.semantic.keepAccent,
                alpha = approveProgress,
                alignment = Alignment.TopStart,
            )
            SwipeGlyph(
                text = stringResource(R.string.review_reject),
                color = MaterialTheme.colorScheme.error,
                alpha = rejectProgress,
                alignment = Alignment.TopEnd,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SwipeGlyph(
    text: String,
    color: Color,
    alpha: Float,
    alignment: Alignment,
) {
    Surface(
        modifier = Modifier
            .align(alignment)
            .padding(16.dp)
            .graphicsLayer { this.alpha = alpha },
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.16f),
        border = androidx.compose.foundation.BorderStroke(2.dp, color),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ReviewBadge(item: SavedItem) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(
                if (item.state == SavedItemState.New) R.string.reminder_badge_new
                else R.string.reminder_badge_updated
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
