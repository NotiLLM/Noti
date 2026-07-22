package org.muilab.notigpt.ui.review

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import org.muilab.notigpt.model.features.ReviewItemDraft
import org.muilab.notigpt.ui.common.component.EmptyState
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.review.component.ReviewCardStack
import org.muilab.notigpt.ui.review.component.ReviewDetailSheet
import org.muilab.notigpt.ui.review.viewmodel.ReviewViewModel
import org.muilab.notigpt.ui.saveditem.viewmodel.SavedItemsViewModel
import org.muilab.notigpt.ui.theme.NotiTheme
import org.muilab.notigpt.ui.settings.ExtractionLanguagePickerDialog
import org.muilab.notigpt.ui.settings.extractionLanguageLabel
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Tinder-style review of new/updated generated items. Swipe right to approve (acknowledge), left to
 * reject (delete a new item, or revert a pending LLM edit). Chips filter the stack; tapping a card
 * opens its full detail; a snackbar offers single-step undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    drawerViewModel: DrawerViewModel,
    savedItemsViewModel: SavedItemsViewModel,
    preferenceViewModel: org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel,
    onBack: () -> Unit,
    onDetailOpenChange: (Boolean) -> Unit = {},
    reviewViewModel: ReviewViewModel = viewModel(),
) {
    val allNew by reviewViewModel.entries.collectAsState()
    val filter by reviewViewModel.filter.collectAsState()
    val deferredKeys by reviewViewModel.deferredKeys.collectAsState()

    val filtered = remember(allNew, filter) { allNew.filter { reviewViewModel.matchesFilter(it, filter) } }
    val active = remember(filtered, deferredKeys) { filtered.filterNot { it.key in deferredKeys } }

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

    // Detail bottom sheet.
    var expanded by remember { mutableStateOf<ReviewViewModel.ReviewEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Edit-in-review: hosts the full editor as a full-screen overlay; signal AppScaffold to hide its
    // bars (the editor has its own header) just like the list editor does.
    var editingItem by remember { mutableStateOf<ReviewViewModel.ReviewEntry?>(null) }
    androidx.compose.runtime.LaunchedEffect(editingItem) { onDetailOpenChange(editingItem != null) }
    val stepsBySavedItem by savedItemsViewModel.allTodoStepsBySavedItem.collectAsState()
    var languageEntry by remember { mutableStateOf<ReviewViewModel.ReviewEntry?>(null) }
    var languageChoice by remember { mutableStateOf<Pair<ReviewViewModel.ReviewEntry, String>?>(null) }

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
            } else if (active.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    EmptyState(
                        iconRes = R.drawable.inbox,
                        text = stringResource(R.string.review_skipped_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                    TextButton(
                        onClick = reviewViewModel::restoreDeferred,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    ) { Text(stringResource(R.string.review_restore_skipped)) }
                }
            } else {
                val top = active.first()
                ReviewCardStack(
                    items = active,
                    modifier = Modifier.weight(1f),
                    onApprove = reviewViewModel::approve,
                    onReject = reviewViewModel::reject,
                    onExpand = { expanded = it },
                    minimalCard = { entry, approveProgress, rejectProgress ->
                        MinimalReviewCard(entry, approveProgress, rejectProgress, reviewViewModel)
                    },
                    footer = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (top.preview.isTodo) {
                                TextButton(
                                    onClick = {
                                        reviewViewModel.saveApprove(
                                            top,
                                            ReviewItemDraft(
                                                item = top.preview.copy(state = SavedItemState.Completed),
                                                steps = top.previewSteps,
                                            ),
                                        )
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(stringResource(R.string.review_mark_complete))
                                }
                            }
                            TextButton(onClick = { languageEntry = top }) {
                                Text(stringResource(R.string.review_change_language))
                            }
                            TextButton(onClick = { reviewViewModel.reviewLater(top) }) {
                                Text(stringResource(R.string.review_later))
                            }
                        }
                    },
                )
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
            ) { Text(stringResource(data.messageRes)) }
        }
    }

    expanded?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { expanded = null },
            sheetState = sheetState,
        ) {
            ReviewDetailSheet(
                entry = entry,
                reviewViewModel = reviewViewModel,
                onFurtherReview = { editingItem = entry; expanded = null },
            )
        }
    }

    // Full-screen editor overlay (review mode: Save & Approve / Delete footer).
    editingItem?.let { entry ->
        val item = entry.preview
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
            org.muilab.notigpt.ui.saveditem.screen.SavedItemDetailScreen(
                initial = item,
                drawerViewModel = drawerViewModel,
                onBack = { editingItem = null },
                onDelete = { reviewViewModel.reject(entry); editingItem = null },
                onSave = {},
                reviewMode = true,
                onSaveApprove = { updated -> reviewViewModel.saveApprove(entry, updated); editingItem = null },
                onRejectDelete = { reviewViewModel.reject(entry); editingItem = null },
                changeLog = reviewViewModel.changeLogFlow(item.savedItemId),
                steps = entry.previewSteps.ifEmpty { stepsBySavedItem[item.savedItemId] ?: emptyList() },
                stepsEditable = true,
            )
        }
    }

    val originalLanguageLabel = stringResource(R.string.ui_settings_extraction_language_original)
    languageEntry?.let { entry ->
        ExtractionLanguagePickerDialog(
            selected = SharedPreferencesManager.targetExtractionLanguage,
            originalLabel = originalLanguageLabel,
            onSelect = { language ->
                languageEntry = null
                languageChoice = entry to language
            },
            onDismiss = { languageEntry = null },
        )
    }

    languageChoice?.let { (entry, language) ->
        val languageLabel = extractionLanguageLabel(language, originalLanguageLabel)
        AlertDialog(
            onDismissRequest = { languageChoice = null },
            title = { Text(stringResource(R.string.review_change_language)) },
            text = { Text(stringResource(R.string.review_language_future_prompt, languageLabel)) },
            confirmButton = {
                TextButton(onClick = {
                    reviewViewModel.translate(entry, language, applyToFutureItems = true)
                    languageChoice = null
                }) { Text(stringResource(R.string.review_language_all_future)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        reviewViewModel.translate(entry, language, applyToFutureItems = false)
                        languageChoice = null
                    }) { Text(stringResource(R.string.review_language_this_item)) }
                    TextButton(onClick = { languageChoice = null }) {
                        Text(stringResource(R.string.ui_action_cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun ReviewFilterChips(
    items: List<ReviewViewModel.ReviewEntry>,
    selected: ReviewViewModel.ReviewFilter,
    onSelect: (ReviewViewModel.ReviewFilter) -> Unit,
) {
    val newTodos = items.count { it.preview.isTodo && it.isNewLike }
    val updatedTodos = items.count { it.preview.isTodo && !it.isNewLike }
    val newKeeps = items.count { !it.preview.isTodo && it.isNewLike }
    val updatedKeeps = items.count { !it.preview.isTodo && !it.isNewLike }

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
        if (newTodos > 0) FilterChip(
            selected = selected == ReviewViewModel.ReviewFilter.NewTodos,
            onClick = { onSelect(ReviewViewModel.ReviewFilter.NewTodos) },
            label = { Text(stringResource(R.string.review_chip_new_tasks, newTodos)) },
        )
        if (updatedTodos > 0) FilterChip(
            selected = selected == ReviewViewModel.ReviewFilter.UpdatedTodos,
            onClick = { onSelect(ReviewViewModel.ReviewFilter.UpdatedTodos) },
            label = { Text(stringResource(R.string.review_chip_updated_tasks, updatedTodos)) },
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
    // Every operation surfaces why it needs review; content is only the final fallback.
    var changeSummary by remember(entry.key) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(entry.key) {
        changeSummary = reviewViewModel.latestChangeSummary(entry)
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
                    ReviewBadge(entry.operationKind)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (item.isTodo) stringResource(R.string.home_collection_tasks)
                        else stringResource(R.string.home_collection_keep),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = item.title.ifBlank { stringResource(R.string.ui_saved_items_untitled_task) },
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
private fun ReviewBadge(kind: ReviewViewModel.ReviewOperationKind) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(
                when (kind) {
                    ReviewViewModel.ReviewOperationKind.Create -> R.string.review_badge_created
                    ReviewViewModel.ReviewOperationKind.Update -> R.string.review_badge_updated
                    ReviewViewModel.ReviewOperationKind.Merge -> R.string.review_badge_merged
                }
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
