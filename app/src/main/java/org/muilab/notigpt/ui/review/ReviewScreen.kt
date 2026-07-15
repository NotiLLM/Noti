package org.muilab.notigpt.ui.review

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
    preferenceViewModel: org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel,
    onBack: () -> Unit,
    onOpenUndetermined: () -> Unit,
    onDetailOpenChange: (Boolean) -> Unit = {},
    reviewViewModel: ReviewViewModel = viewModel(),
) {
    val allNew by reviewViewModel.entries.collectAsState()
    val filter by reviewViewModel.filter.collectAsState()

    val filtered = remember(allNew, filter) { allNew.filter { reviewViewModel.matchesFilter(it, filter) } }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Custom snackbar: Undo + (on rejects) "Tell it why". A single state holds the current one, so a
    // fresh action supersedes the previous snackbar rather than queueing behind it.
    var reviewSnackbar by remember { mutableStateOf<ReviewViewModel.ReviewSnackbar?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        reviewViewModel.resetReviewSession()
        reviewViewModel.snackbar.collect { reviewSnackbar = it }
    }
    // Longer-lived than a default snackbar so the "Tell it why" affordance is catchable.
    androidx.compose.runtime.LaunchedEffect(reviewSnackbar) {
        if (reviewSnackbar != null) {
            kotlinx.coroutines.delay(6000)
            reviewSnackbar = null
        }
    }

    // End-of-stack offer: once the last item is reviewed, if any approved tasks lack a do-date, offer
    // to go set them in the Undetermined list.
    var showDoDateOffer by remember { mutableStateOf(false) }
    var offerCount by remember { mutableStateOf(0) }
    var everHadItems by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(allNew.size) {
        if (allNew.isNotEmpty()) {
            everHadItems = true
        } else if (everHadItems) {
            everHadItems = false
            val count = reviewViewModel.approvedNeedingDoDateCount()
            if (count > 0) {
                offerCount = count
                showDoDateOffer = true
            }
        }
    }

    // Detail bottom sheet.
    var expanded by remember { mutableStateOf<ReviewViewModel.ReviewEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Edit-in-review: hosts the full editor as a full-screen overlay; signal AppScaffold to hide its
    // bars (the editor has its own header) just like the list editor does.
    var editingItem by remember { mutableStateOf<ReviewViewModel.ReviewEntry?>(null) }
    androidx.compose.runtime.LaunchedEffect(editingItem) { onDetailOpenChange(editingItem != null) }
    val subtasksByReminder by reminderViewModel.allSavedSubItemsByReminder.collectAsState()

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
                    minimalCard = { entry, approveProgress, rejectProgress ->
                        MinimalReviewCard(entry, approveProgress, rejectProgress, reviewViewModel)
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
                        modifier = Modifier.size(56.dp),
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
                        modifier = Modifier.size(56.dp),
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

        reviewSnackbar?.let { data ->
            val undoLabel = stringResource(R.string.review_undo)
            val tellWhyLabel = stringResource(R.string.review_tell_why)
            androidx.compose.material3.Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 96.dp),
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (data.canTeach && data.item != null) {
                            TextButton(onClick = {
                                preferenceViewModel.startFlowSheet(
                                    org.muilab.notigpt.ui.preference.model.PreferenceEntryPoint.DELETE,
                                    data.item,
                                )
                                reviewSnackbar = null
                            }) { Text(tellWhyLabel) }
                        }
                        TextButton(onClick = {
                            reviewViewModel.undoLast()
                            reviewSnackbar = null
                        }) { Text(undoLabel) }
                    }
                },
            ) { Text(context.getString(data.messageRes)) }
        }
    }

    if (showDoDateOffer) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDoDateOffer = false; reviewViewModel.resetReviewSession() },
            title = { Text(stringResource(R.string.review_dodate_offer_title)) },
            text = { Text(stringResource(R.string.review_dodate_offer_body, offerCount)) },
            confirmButton = {
                TextButton(onClick = {
                    showDoDateOffer = false
                    reviewViewModel.resetReviewSession()
                    onOpenUndetermined()
                }) { Text(stringResource(R.string.review_dodate_offer_set)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDoDateOffer = false
                    reviewViewModel.resetReviewSession()
                }) { Text(stringResource(R.string.review_dodate_offer_skip)) }
            },
        )
    }

    expanded?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { expanded = null },
            sheetState = sheetState,
        ) {
            ReviewDetailSheet(
                entry = entry,
                reviewViewModel = reviewViewModel,
                onApprove = { reviewViewModel.approve(entry); expanded = null },
                onReject = { reviewViewModel.reject(entry); expanded = null },
                onEdit = { editingItem = entry; expanded = null },
            )
        }
    }

    // Full-screen editor overlay (review mode: Save & Approve / Delete footer).
    editingItem?.let { entry ->
        val item = entry.preview
        // Staged entries have no DB rows yet: sub-tasks come from the preview and editing them
        // waits until the group is applied. Legacy entries edit real rows as before.
        val staged = entry.group != null
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            org.muilab.notigpt.ui.reminder.screen.ReminderDetailScreen(
                initial = item,
                drawerViewModel = drawerViewModel,
                onBack = { editingItem = null },
                onDelete = { reviewViewModel.reject(entry); editingItem = null },
                onSave = {},
                reviewMode = true,
                onSaveApprove = { updated -> reviewViewModel.saveApprove(entry, updated); editingItem = null },
                onRejectDelete = { reviewViewModel.reject(entry); editingItem = null },
                changeLog = reviewViewModel.changeLogFlow(item.savedItemId),
                subTasks = if (staged) entry.previewSubItems else subtasksByReminder[item.savedItemId] ?: emptyList(),
                onAddSavedSubItem = { if (!staged) reminderViewModel.addSavedSubItem(item.savedItemId) },
                onSavedSubItemToggle = { stId, checked -> if (!staged) reminderViewModel.toggleSavedSubItemCompleted(stId, checked) },
                onSavedSubItemDelete = { st -> if (!staged) reminderViewModel.deleteSavedSubItem(st.savedSubItemId) },
            )
        }
    }
}

@Composable
private fun ReviewFilterChips(
    items: List<ReviewViewModel.ReviewEntry>,
    selected: ReviewViewModel.ReviewFilter,
    onSelect: (ReviewViewModel.ReviewFilter) -> Unit,
) {
    val newTasks = items.count { it.preview.isTask && it.isNewLike }
    val updatedTasks = items.count { it.preview.isTask && !it.isNewLike }
    val newKeeps = items.count { !it.preview.isTask && it.isNewLike }
    val updatedKeeps = items.count { !it.preview.isTask && !it.isNewLike }

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
    entry: ReviewViewModel.ReviewEntry,
    approveProgress: Float,
    rejectProgress: Float,
    reviewViewModel: ReviewViewModel,
) {
    val item = entry.preview
    // Updated items surface the pipeline's one-line "what changed"; new items show a content preview.
    var changeSummary by remember(entry.key) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(entry.key) {
        if (!entry.isNewLike) changeSummary = reviewViewModel.latestChangeSummary(entry)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 6.dp,
    ) {
        Box {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReviewBadge(isNew = entry.isNewLike)
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
            // Whole-card approve/reject wash that grows with drag progress (not a corner badge).
            SwipeOverlay(
                approveText = stringResource(R.string.review_approve),
                rejectText = stringResource(R.string.review_reject),
                approveColor = NotiTheme.semantic.keepAccent,
                rejectColor = MaterialTheme.colorScheme.error,
                approveProgress = approveProgress,
                rejectProgress = rejectProgress,
            )
        }
    }
}

/**
 * Full-card tint + large label that intensifies with drag. Approve/reject are mutually exclusive
 * (only one progress is > 0 at a time), so a single overlay covers the whole card in the active color.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SwipeOverlay(
    approveText: String,
    rejectText: String,
    approveColor: Color,
    rejectColor: Color,
    approveProgress: Float,
    rejectProgress: Float,
) {
    val approving = approveProgress >= rejectProgress
    val progress = if (approving) approveProgress else rejectProgress
    if (progress <= 0f) return
    val color = if (approving) approveColor else rejectColor
    val label = if (approving) approveText else rejectText
    val alignment = if (approving) Alignment.CenterStart else Alignment.CenterEnd

    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(MaterialTheme.shapes.extraLarge)
            .graphicsLayer { alpha = progress }
            .background(color.copy(alpha = 0.20f))
            .border(3.dp, color.copy(alpha = 0.9f), MaterialTheme.shapes.extraLarge),
    ) {
        Surface(
            modifier = Modifier
                .align(alignment)
                .padding(24.dp),
            shape = MaterialTheme.shapes.small,
            color = color,
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ReviewBadge(isNew: Boolean) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(
                if (isNew) R.string.reminder_badge_new
                else R.string.reminder_badge_updated
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
