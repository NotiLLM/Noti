package org.muilab.notigpt.ui.saveditem.screen

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.muilab.notigpt.R
import org.muilab.notigpt.domain.saveditem.SavedItemActionButton
import org.muilab.notigpt.domain.saveditem.SavedItemActionButtons
import org.muilab.notigpt.ui.preference.model.PreferenceEntryPoint
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.TodoStep
import org.muilab.notigpt.model.features.ReviewItemDraft
import org.muilab.notigpt.data.export.asExportable
import org.muilab.notigpt.ui.preference.component.PreferenceLearningBottomSheet
import org.muilab.notigpt.ui.common.feedback.AppSnackbar
import org.muilab.notigpt.ui.theme.NotiTheme
import org.muilab.notigpt.ui.theme.NotiType
import org.muilab.notigpt.ui.theme.Dimens
import org.muilab.notigpt.ui.notification.component.RelatedNotificationPreview
import org.muilab.notigpt.ui.saveditem.component.TodoStepRow
import org.muilab.notigpt.ui.saveditem.component.TodoStepListInCard
import org.muilab.notigpt.ui.saveditem.component.TodoCompletionToggle
import org.muilab.notigpt.ui.saveditem.component.SavedItemDeadlineButton
import org.muilab.notigpt.ui.saveditem.component.SavedItemDeadlinePickerDialog
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel
import org.muilab.notigpt.ui.saveditem.viewmodel.SavedItemsViewModel
import org.muilab.notigpt.ui.reminder.viewmodel.ScheduledReminderViewModel
import org.muilab.notigpt.ui.reminder.screen.ReminderDateTimeDialog
import org.muilab.notigpt.util.time.getAbsoluteTimeStr
import org.muilab.notigpt.util.time.getRelativeTimeStr
import java.util.Calendar
import org.muilab.notigpt.ui.common.clipboard.AndroidClipboardController
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.minimumInteractiveComponentSize

/**
 * Main SavedItems screen for todos, keeps, completion state, steps, export, and related notifications.
 *
 * This screen owns local editing dialogs, drag visuals, and edit drafts. Durable SavedItem, step, sync, and
 * regeneration actions should stay in SavedItemsViewModel or related repositories.
 */
@Composable
fun SavedItemsScreen(
    drawerViewModel: DrawerViewModel,
    savedItemsViewModel: SavedItemsViewModel? = null,
    scheduledReminderViewModel: ScheduledReminderViewModel? = null,
    preferenceViewModel: PreferenceViewModel? = null,
    listMode: SavedItemsViewModel.ListMode = SavedItemsViewModel.ListMode.All,
    /** When set, this screen shows a home smart-filter list (planned-date bucket or starred) instead of the tab chips. */
    smartFilter: org.muilab.notigpt.ui.common.navigation.SavedListFilter? = null,
    /** When set, opens this item's detail screen as soon as it's loaded (e.g. jumping in from a notification's linked-items sheet). Consumed once. */
    initialDetailItemId: String? = null,
    onDetailOpenChange: (Boolean) -> Unit = {},
) {
    val vm: SavedItemsViewModel = savedItemsViewModel ?: viewModel()
    val scheduledVm: ScheduledReminderViewModel = scheduledReminderViewModel ?: viewModel()
    val prefVm: PreferenceViewModel = preferenceViewModel ?: viewModel()

    LaunchedEffect(listMode, smartFilter) {
        vm.setSmartFilter(smartFilter)
        vm.setListMode(listMode)
        vm.setFilter(
            when (listMode) {
                SavedItemsViewModel.ListMode.Todos -> SavedItemsViewModel.FilterTab.Pending
                SavedItemsViewModel.ListMode.Keep -> SavedItemsViewModel.FilterTab.Keep
                else -> SavedItemsViewModel.FilterTab.All
            }
        )
    }

    // Drawer VM is needed to reuse the same notification/app launching logic as NotiRecordContextCard.
    val context = LocalContext.current
    val strGoogleTasksNotSignedIn = stringResource(R.string.google_tasks_not_signed_in)
    val strGoogleTasksSuccess = stringResource(R.string.google_tasks_success)
    val strGoogleTasksErrorFmt = stringResource(R.string.google_tasks_error, "%s")
    val strGoogleCalendarNoApp = stringResource(R.string.google_calendar_no_app)

    val savedItems by vm.savedItems.collectAsState()
    val pendingPreviews by vm.pendingPreviews.collectAsState()
    val filter by vm.filter.collectAsState()

    // Mixed attention-filter pages get an in-page Todo/Keep filter,
    // but only when the page actually contains both types. null = show all. Reset when the page changes.
    var smartTypeFilter by remember(smartFilter) { mutableStateOf<String?>(null) }
    val smartHasTask = smartFilter != null && savedItems.any { it.isTodo }
    val smartHasKeep = smartFilter != null && savedItems.any { !it.isTodo }
    val smartShowTypeChips = smartHasTask && smartHasKeep
    val displayedSavedItems = if (smartFilter != null && smartTypeFilter != null) {
        savedItems.filter { it.itemType == smartTypeFilter }
    } else {
        savedItems
    }

    // Flash the destination filter chip when a card changes status (e.g. completed -> Completed chip).
    var flashTarget by remember { mutableStateOf<SavedItemsViewModel.FilterTab?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    val flashChip: (SavedItemsViewModel.FilterTab) -> Unit = { tab -> flashTarget = tab; flashTick++ }

    // Section identity color for the active tab (Tasks=amber, Keep=teal). Drives card accents + FAB.
    val tabAccent: Color? = when (listMode) {
        SavedItemsViewModel.ListMode.Todos -> NotiTheme.semantic.taskAccent
        SavedItemsViewModel.ListMode.Keep -> NotiTheme.semantic.keepAccent
        else -> null
    }
    val fabContainer = when (listMode) {
        SavedItemsViewModel.ListMode.Todos -> NotiTheme.semantic.taskContainer
        SavedItemsViewModel.ListMode.Keep -> NotiTheme.semantic.keepContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val fabContent = when (listMode) {
        SavedItemsViewModel.ListMode.Todos -> NotiTheme.semantic.onTaskContainer
        SavedItemsViewModel.ListMode.Keep -> NotiTheme.semantic.onKeepContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    var editing by remember { mutableStateOf<SavedItem?>(null) }
    var lastEditing by remember { mutableStateOf<SavedItem?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingInitialSnapshot by remember { mutableStateOf<SavedItem?>(null) }
    var pendingEditRequest by remember { mutableStateOf<SavedItem?>(null) }

    fun openEditor(item: SavedItem) {
        editing = item
        editingId = item.savedItemId
        editingInitialSnapshot = item
    }

    fun requestEdit(item: SavedItem) {
        if (pendingPreviews[item.savedItemId] != null) {
            pendingEditRequest = item
        } else {
            openEditor(item)
        }
    }

    // Auto-open a specific item's detail once it shows up in the loaded list (consumed once so
    // re-composition or manually closing the detail doesn't reopen it).
    var consumedInitialDetailItemId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialDetailItemId, savedItems) {
        if (initialDetailItemId != null && initialDetailItemId != consumedInitialDetailItemId) {
            savedItems.find { it.savedItemId == initialDetailItemId }?.let { match ->
                requestEdit(match)
                consumedInitialDetailItemId = initialDetailItemId
            }
        }
    }

    // Bulk sub-task observation (one DB query for all savedItems)
    val allTodoStepsBySavedItem by vm.allTodoStepsBySavedItem.collectAsState()

    var scheduledReminderTarget by remember { mutableStateOf<SavedItem?>(null) }

    // ===== Google Tasks integration =====
    val googleTasksExportResult by vm.googleTasksExportResult.collectAsState()
    val relatedNotificationsState by vm.relatedNotificationsState.collectAsState()

    // Reminder pending export after sign-in completes.
    var pendingGoogleTasksReminder by remember { mutableStateOf<SavedItem?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        vm.handleGoogleTasksSignInResult(result.data, pendingGoogleTasksReminder)
        pendingGoogleTasksReminder = null
    }

    // Show Toast when Google Tasks export result changes.
    LaunchedEffect(googleTasksExportResult) {
        when (val r = googleTasksExportResult) {
            is SavedItemsViewModel.GoogleTasksExportResult.Success -> {
                AppSnackbar.show(strGoogleTasksSuccess)
                vm.clearGoogleTasksExportResult()
            }
            is SavedItemsViewModel.GoogleTasksExportResult.Error -> {
                AppSnackbar.show(strGoogleTasksErrorFmt.replace("%s", r.message ?: ""))
                vm.clearGoogleTasksExportResult()
            }
            is SavedItemsViewModel.GoogleTasksExportResult.NotSignedIn -> {
                // Launch sign-in flow
                vm.clearGoogleTasksExportResult()
            }
            else -> { /* Idle or Loading – no-op */ }
        }
    }

    // ===== Export confirmation dialog =====
    var exportDialogState by remember { mutableStateOf<ExportDialogState?>(null) }

    // Helper lambdas to open the dialog
    val openExportDialog: (SavedItem, ExportType) -> Unit = { item, type ->
        exportDialogState = ExportDialogState(item, type)
    }

    val handleGoogleTasksExport: (SavedItem) -> Unit = { item ->
        if (vm.isGoogleSignedIn()) {
            vm.exportToGoogleTasks(item)
        } else {
            pendingGoogleTasksReminder = item
            val signInIntent = org.muilab.notigpt.data.remote.googletasks.GoogleTasksAuthManager.getSignInIntent(context)
            googleSignInLauncher.launch(signInIntent)
        }
    }

    // Keep list state while editor is shown.
    val listState = rememberLazyListState()

    var pendingScrollToTopId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedItems, pendingScrollToTopId) {
        val targetId = pendingScrollToTopId ?: return@LaunchedEffect
        if (savedItems.firstOrNull()?.savedItemId == targetId) {
            listState.animateScrollToItem(0)
        }
        pendingScrollToTopId = null
    }

    Box(Modifier.fillMaxSize()) {
        // LIST
        Column(Modifier.fillMaxSize()) {
            // Simple tab row (chip-like)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Attention-filter lists use their title from the shell.
                // the shell's top bar carries the filter name.
                if (smartFilter == null) when (listMode) {
                    SavedItemsViewModel.ListMode.All -> {
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_all), filter == SavedItemsViewModel.FilterTab.All, { vm.setFilter(SavedItemsViewModel.FilterTab.All) })
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_tasks), filter == SavedItemsViewModel.FilterTab.Todos, { vm.setFilter(SavedItemsViewModel.FilterTab.Todos) })
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_memos), filter == SavedItemsViewModel.FilterTab.Keeps, { vm.setFilter(SavedItemsViewModel.FilterTab.Keeps) })
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_completed), filter == SavedItemsViewModel.FilterTab.Completed, { vm.setFilter(SavedItemsViewModel.FilterTab.Completed) })
                    }
                    SavedItemsViewModel.ListMode.Todos -> {
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_pending), filter == SavedItemsViewModel.FilterTab.Pending, { vm.setFilter(SavedItemsViewModel.FilterTab.Pending) }, leadingIconRes = R.drawable.check_box_unchecked, iconTint = NotiTheme.semantic.taskAccent, flashTick = if (flashTarget == SavedItemsViewModel.FilterTab.Pending) flashTick else 0)
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_completed), filter == SavedItemsViewModel.FilterTab.Completed, { vm.setFilter(SavedItemsViewModel.FilterTab.Completed) }, leadingIconRes = R.drawable.check_box_checked, iconTint = NotiTheme.semantic.taskAccent, flashTick = if (flashTarget == SavedItemsViewModel.FilterTab.Completed) flashTick else 0)
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_starred), filter == SavedItemsViewModel.FilterTab.Starred, { vm.setFilter(SavedItemsViewModel.FilterTab.Starred) }, leadingIconRes = R.drawable.star_yes, iconTint = NotiTheme.semantic.taskAccent)
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_all), filter == SavedItemsViewModel.FilterTab.All, { vm.setFilter(SavedItemsViewModel.FilterTab.All) })
                    }
                    SavedItemsViewModel.ListMode.Keep -> {
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_keep), filter == SavedItemsViewModel.FilterTab.Keep, { vm.setFilter(SavedItemsViewModel.FilterTab.Keep) }, leadingIconRes = R.drawable.bookmark, iconTint = NotiTheme.semantic.keepAccent, flashTick = if (flashTarget == SavedItemsViewModel.FilterTab.Keep) flashTick else 0)
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_archived), filter == SavedItemsViewModel.FilterTab.Archived, { vm.setFilter(SavedItemsViewModel.FilterTab.Archived) }, leadingIconRes = R.drawable.archive_yes, iconTint = NotiTheme.semantic.keepAccent, flashTick = if (flashTarget == SavedItemsViewModel.FilterTab.Archived) flashTick else 0)
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_starred), filter == SavedItemsViewModel.FilterTab.Starred, { vm.setFilter(SavedItemsViewModel.FilterTab.Starred) }, leadingIconRes = R.drawable.star_yes, iconTint = NotiTheme.semantic.keepAccent)
                        SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_all), filter == SavedItemsViewModel.FilterTab.All, { vm.setFilter(SavedItemsViewModel.FilterTab.All) })
                    }
                }

                // Smart-filter pages get task/keep chips only when both types are present.
                if (smartShowTypeChips) {
                    SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_all), smartTypeFilter == null, { smartTypeFilter = null })
                    SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_tasks), smartTypeFilter == SavedItemType.Todo, { smartTypeFilter = SavedItemType.Todo }, leadingIconRes = R.drawable.check_box_unchecked, iconTint = NotiTheme.semantic.taskAccent)
                    SavedItemFilterChip(stringResource(R.string.ui_saved_items_filter_keep), smartTypeFilter == SavedItemType.Keep, { smartTypeFilter = SavedItemType.Keep }, leadingIconRes = R.drawable.bookmark, iconTint = NotiTheme.semantic.keepAccent)
                }

                Spacer(Modifier.weight(1f))
            }

            // Offline banner: extraction requests are failing and records are queued locally.
            val extractionStatus by vm.extractionStatus.collectAsState()
            val pendingExtractionCount by vm.pendingExtractionCount.collectAsState()
            if (extractionStatus.consecutiveFailures > 0 && pendingExtractionCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.extraction_pending_banner, pendingExtractionCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.retryExtraction() }) {
                            Text(stringResource(R.string.extraction_retry), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
            ) {
                items(displayedSavedItems, key = { it.savedItemId }, contentType = { "reminderCard" }) { item ->
                    val pendingPreview = pendingPreviews[item.savedItemId]
                    SavedItemCard(
                        item = item,
                        steps = pendingPreview?.steps
                            ?: allTodoStepsBySavedItem[item.savedItemId].orEmpty(),
                        pendingReview = pendingPreview != null,
                        pendingMergeSourceCount = pendingPreview?.mergeSourceItemIds?.size ?: 0,
                        onToggleCompleted = { completed: Boolean ->
                            vm.toggleCompleted(item, completed)
                            flashChip(if (completed) SavedItemsViewModel.FilterTab.Completed else SavedItemsViewModel.FilterTab.Pending)
                        },
                        onEdit = {
                            requestEdit(item)
                        },
                        onToggleStarred = { vm.toggleStarred(item) },
                        onSetDeadline = if (item.isTodo) {
                            { deadlineAtMs -> vm.setDeadline(item.savedItemId, deadlineAtMs) }
                        } else null,
                        onCreateReminder = { scheduledReminderTarget = item },
                        sectionAccent = tabAccent,
                        onArchive = {
                            flashChip(if (item.isArchived) SavedItemsViewModel.FilterTab.Keep else SavedItemsViewModel.FilterTab.Archived)
                            vm.archiveKeep(item.savedItemId)
                        },
                        onTodoStepToggle = { stId, checked ->
                            if (pendingPreview != null) requestEdit(item)
                            else vm.toggleTodoStepCompleted(stId, checked)
                        },
                        onTodoStepClick = { requestEdit(item) },
                        onTodoStepEdit = { requestEdit(item) },
                        onTodoStepDelete = { st ->
                            if (pendingPreview != null) requestEdit(item)
                            else vm.deleteTodoStep(st.todoStepId)
                        },
                    )
                }
            }
        }

        scheduledReminderTarget?.let { target ->
            ReminderDateTimeDialog(
                title = stringResource(R.string.ui_reminder_create_button),
                initialAtMs = System.currentTimeMillis(),
                onDismiss = { scheduledReminderTarget = null },
                onConfirm = { remindAtMs ->
                    scheduledVm.createForSavedItem(target, remindAtMs)
                    scheduledReminderTarget = null
                },
            )
        }

        pendingEditRequest?.let { target ->
            val pending = pendingPreviews[target.savedItemId]
            val mergeCount = pending?.mergeSourceItemIds?.size ?: 0
            AlertDialog(
                onDismissRequest = { pendingEditRequest = null },
                title = { Text(stringResource(R.string.pending_review_edit_title)) },
                text = {
                    Text(
                        if (mergeCount > 0) {
                            stringResource(R.string.pending_review_merge_message, mergeCount)
                        } else {
                            stringResource(R.string.pending_review_edit_message)
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingEditRequest = null
                            vm.approvePendingForEdit(target.savedItemId) { approved ->
                                openEditor(approved)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.pending_review_edit_accept))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingEditRequest = null }) {
                        Text(stringResource(R.string.pending_review_edit_cancel))
                    }
                },
            )
        }

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = fabContainer,
            contentColor = fabContent,
            onClick = {
                val empty = SavedItem(
                    savedItemId = "manual_${java.util.UUID.randomUUID()}",
                    title = "",
                    content = "",
                    // Let users decide task vs memo in the editor.
                    itemType = SavedItemType.Keep,
                    state = SavedItemState.Saved,
                    lastUpdateTimestamp = System.currentTimeMillis(),
                    deadlineAtMs = 0L,
                    origin = "manual",
                    humanEditCount = 0,
                    userEdited = true,
                )
                editing = empty
                editingId = empty.savedItemId
                editingInitialSnapshot = empty
            }
        ) {
            Icon(painterResource(R.drawable.add), contentDescription = stringResource(R.string.a11y_add))
        }

        // EDITOR OVERLAY — drift in from the right, out to the right.
        LaunchedEffect(editing) {
            editing?.let { lastEditing = it }
            onDetailOpenChange(editing != null)
        }
        AnimatedVisibility(
            visible = editing != null,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
          val current = editing ?: lastEditing
          if (current != null) {
            // Full-screen overlay that blocks/consumes all clicks so nothing underneath is clickable.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { /* consume */ }
                    )
            ) {
                SavedItemDetailScreen(
                    initial = current,
                    drawerViewModel = drawerViewModel,
                    onCreateReminder = { scheduledReminderTarget = current },
                    onBack = { updatedOrNull: SavedItem? ->
                        val base = editingInitialSnapshot
                        val isNew = base != null && base.title.isBlank() && base.content.isBlank() && base.userEdited

                        val contentChanged = base != null && updatedOrNull != null && (
                            base.title != updatedOrNull.title ||
                                base.content != updatedOrNull.content
                        )

                        val changed = base != null && updatedOrNull != null && (
                            base.title != updatedOrNull.title ||
                                base.content != updatedOrNull.content ||
                                base.isTodo != updatedOrNull.isTodo ||
                                base.isCompleted != updatedOrNull.isCompleted ||
                                base.deadlineAtMs != updatedOrNull.deadlineAtMs
                        )

                        if (updatedOrNull != null) {
                            val emptyNow = updatedOrNull.title.isBlank() && updatedOrNull.content.isBlank()
                            when {
                                emptyNow -> {
                                    // For brand-new manual savedItems, just discard. For existing savedItems, delete.
                                    if (!isNew) vm.delete(updatedOrNull.savedItemId)
                                }
                                changed -> {
                                    vm.upsert(
                                        updatedOrNull.copy(
                                            origin = "manual",
                                            userEdited = true,
                                            humanEditCount = if (contentChanged) (base?.humanEditCount ?: updatedOrNull.humanEditCount) + 1 else (base?.humanEditCount ?: updatedOrNull.humanEditCount),
                                            lastUpdateTimestamp = System.currentTimeMillis(),
                                        )
                                    )
                                }
                                // If not changed but non-empty and new: still create it.
                                isNew -> {
                                    vm.upsert(
                                        updatedOrNull.copy(
                                            origin = "manual",
                                            userEdited = true,
                                            lastUpdateTimestamp = System.currentTimeMillis(),
                                        )
                                    )
                                }
                            }
                        }

                        val id = if (changed) editingId else null
                        editing = null
                        editingId = null
                        editingInitialSnapshot = null
                        if (id != null) pendingScrollToTopId = id
                    },
                    onDelete = { id: String ->
                        val deletedReminder = editingInitialSnapshot
                        vm.delete(id)
                        editing = null
                        editingId = null
                        editingInitialSnapshot = null
                        pendingScrollToTopId = null
                        // Trigger preference learning for LLM-generated savedItems deleted from detail
                        if (deletedReminder != null && deletedReminder.origin.contains("llm")) {
                            prefVm.startFlow(
                                entryPoint = PreferenceEntryPoint.DELETE,
                                item = deletedReminder,
                            )
                        }
                    },
                    onSave = { updated: SavedItem ->
                        val base = editingInitialSnapshot
                        val isNew = base != null && base.title.isBlank() && base.content.isBlank() && base.userEdited

                        val contentChanged = base != null && (
                            base.title != updated.title ||
                                base.content != updated.content
                        )

                        val changed = base != null && (
                            base.title != updated.title ||
                                base.content != updated.content ||
                                base.isTodo != updated.isTodo ||
                                base.isCompleted != updated.isCompleted ||
                                base.deadlineAtMs != updated.deadlineAtMs
                        )

                        val emptyNow = updated.title.isBlank() && updated.content.isBlank()
                        when {
                            emptyNow -> {
                                if (!isNew) vm.delete(updated.savedItemId)
                            }
                            changed || isNew -> {
                                vm.upsert(
                                    updated.copy(
                                        origin = "manual",
                                        humanEditCount = if (contentChanged) (base?.humanEditCount ?: updated.humanEditCount) + 1 else (base?.humanEditCount ?: updated.humanEditCount),
                                        userEdited = true,
                                        lastUpdateTimestamp = System.currentTimeMillis(),
                                    )
                                )
                            }
                        }

                        // Trigger preference learning for LLM-generated savedItems that were edited
                        if (changed && !isNew && base != null && base.origin.contains("llm")) {
                            prefVm.startFlow(
                                entryPoint = PreferenceEntryPoint.EDIT,
                                item = updated,
                                savedItemBefore = base,
                            )
                        }

                        val id = if (changed) editingId else null
                        editing = null
                        editingId = null
                        editingInitialSnapshot = null
                        if (id != null) pendingScrollToTopId = id
                    },
                    onExportToGoogleTasks = { item ->
                        if (vm.isGoogleSignedIn()) {
                            vm.exportToGoogleTasks(item)
                        } else {
                            pendingGoogleTasksReminder = item
                            val signInIntent = org.muilab.notigpt.data.remote.googletasks.GoogleTasksAuthManager.getSignInIntent(context)
                            googleSignInLauncher.launch(signInIntent)
                        }
                    },
                    isGoogleTasksExporting = googleTasksExportResult is SavedItemsViewModel.GoogleTasksExportResult.Loading,
                    onOpenExportDialog = openExportDialog,
                    onRegenerate = { vm.regenerateOne(current.savedItemId) },
                    relatedNotificationsState = relatedNotificationsState,
                    onLoadRelatedNotifications = { item -> vm.loadRelatedNotifications(item) },
                    changeLog = remember(current.savedItemId) { vm.changeLogFlow(current.savedItemId) },
                    onAcknowledgeReview = { vm.acknowledgeReview(current.savedItemId) },
                    // Sub-task parameters
                    steps = allTodoStepsBySavedItem[current.savedItemId] ?: emptyList(),
                    onAddTodoStep = { vm.addTodoStep(current.savedItemId) },
                    onTodoStepToggle = { stId, checked -> vm.toggleTodoStepCompleted(stId, checked) },
                    onTodoStepEdit = { st -> vm.upsertTodoStep(st) },
                    onTodoStepDelete = { st -> vm.deleteTodoStep(st.todoStepId) },
                )
            }
        }
        }
    }

    // Export confirmation dialog (shared between list quick-export and detail screen)
    exportDialogState?.let { dlgState ->
        ExportConfirmationDialog(
            state = dlgState,
            isGoogleTasksExporting = googleTasksExportResult is SavedItemsViewModel.GoogleTasksExportResult.Loading,
            onDismiss = { exportDialogState = null },
            onConfirmGoogleTasks = { title, description, deadlineMs ->
                val exportReminder = dlgState.item.copy(
                    title = title,
                    content = description,
                    deadlineAtMs = deadlineMs,
                )
                exportDialogState = null
                handleGoogleTasksExport(exportReminder)
            },
            onConfirmGoogleCalendar = { title, description, startMs, endMs, allDay, reminderMinutes ->
                exportDialogState = null
                val calIntent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, title)
                    putExtra(CalendarContract.Events.DESCRIPTION, description)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
                    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
                    if (reminderMinutes >= 0) {
                        putExtra(CalendarContract.Events.HAS_ALARM, true)
                        putExtra("reminderMinutes", reminderMinutes)
                    }
                }
                try {
                    context.startActivity(calIntent)
                } catch (_: Exception) {
                    AppSnackbar.show(strGoogleCalendarNoApp)
                }
            },
        )
    }

    // Preference learning BottomSheet (Flows 1-3)
    PreferenceLearningBottomSheet(preferenceViewModel = prefVm)
}

/**
 * Single-select filter chip used by the Task/Keep filter rows.
 *
 * Wraps Material3 [FilterChip]; pass [leadingIconRes] to show a leading icon (e.g. checkbox glyphs for
 * Pending/Completed). These are mutually-exclusive filters, which is exactly FilterChip's role.
 */
@Composable
private fun SavedItemFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIconRes: Int? = null,
    iconTint: Color? = null,
    flashTick: Int = 0,
) {
    // A quick scale "pop" when flashTick increments — signals an item just landed in this filter.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(flashTick) {
        if (flashTick > 0) {
            scale.snapTo(1f)
            scale.animateTo(1.18f, tween(110))
            scale.animateTo(1f, tween(260))
        }
    }
    FilterChip(
        modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = leadingIconRes?.let {
            {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconTint ?: androidx.compose.material3.LocalContentColor.current,
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedItemCard(
    item: SavedItem,
    steps: List<TodoStep> = emptyList(),
    pendingReview: Boolean = false,
    pendingMergeSourceCount: Int = 0,
    onToggleCompleted: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onToggleStarred: (() -> Unit)? = null,
    onCreateReminder: (() -> Unit)? = null,
    /** Set/clear a task deadline date straight from the card. Time editing stays on the detail screen. */
    onSetDeadline: ((Long) -> Unit)? = null,
    onLongPress: () -> Unit = {},
    onArchive: () -> Unit = {},
    /** Optional left-edge accent identifying the section (e.g. Tasks/Keep). Null = no accent. */
    sectionAccent: Color? = null,
    // Multi-select (New screen triage). When selectionMode, the card toggles selection instead of opening.
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    // Sub-task callbacks
    onTodoStepToggle: (String, Boolean) -> Unit = { _, _ -> },
    onTodoStepClick: (TodoStep) -> Unit = {},
    onTodoStepEdit: (TodoStep) -> Unit = {},
    onTodoStepDelete: (TodoStep) -> Unit = {},
    onTodoStepExportGoogleTasks: (TodoStep) -> Unit = {},
    onTodoStepExportGoogleCalendar: (TodoStep) -> Unit = {},
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val clipboard = remember(context) { AndroidClipboardController(context) }
    val haptic = LocalHapticFeedback.current

    // Parse LLM-generated buttons
    val buttons = remember(item.buttons) {
        SavedItemActionButtons.parse(item.buttons)
    }

    var expanded by remember(item.savedItemId) { mutableStateOf(false) }
    var showDeadlinePicker by remember(item.savedItemId) { mutableStateOf(false) }
    // Star tint follows the item's own type (fixes the previously hardcoded task accent on keep cards).
    val rowAccent = if (item.isTodo) NotiTheme.semantic.taskAccent else NotiTheme.semantic.keepAccent
    val selectedBorder = sectionAccent ?: MaterialTheme.colorScheme.primary
    // Left-edge accent: use the section accent in single-type lists, else fall back to the item's own
    // type accent so Task (indigo) vs Keep (green) stays legible in mixed / smart-filter lists.
    val edgeAccent = sectionAccent ?: rowAccent
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenH, vertical = Dimens.cardOuterV),
        shape = MaterialTheme.shapes.medium,
        color = if (selectionMode && selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            if (selectionMode && selected) 1.5.dp else 0.5.dp,
            if (selectionMode && selected) selectedBorder else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
      Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(edgeAccent),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = { if (selectionMode && onSelectedChange != null) onSelectedChange(!selected) else onEdit() },
                    onLongClick = { if (!selectionMode) onLongPress() },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left gutter: selection checkbox in select mode; otherwise task completion / keep archive.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 4.dp),
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onSelectedChange?.invoke(it) },
                        colors = CheckboxDefaults.colors(checkedColor = selectedBorder),
                    )
                } else if (item.isTodo) {
                    TodoCompletionToggle(
                        checked = item.isCompleted,
                        accent = rowAccent,
                        onCheckedChange = onToggleCompleted,
                    )
                } else {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(if (!item.isArchived) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            onArchive()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            painter = painterResource(if (item.isArchived) R.drawable.archive_yes else R.drawable.archive_no),
                            contentDescription = stringResource(R.string.a11y_archive),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Right content column
            Column(modifier = Modifier.weight(1f)) {
                // Review badge: staged previews remain gated until the user explicitly accepts them.
                if ((item.isNewLike || pendingReview) && !selectionMode) {
                    val badgeText = when {
                        pendingReview && pendingMergeSourceCount > 0 ->
                            stringResource(R.string.pending_review_merge_badge, pendingMergeSourceCount)
                        pendingReview -> stringResource(R.string.pending_review_badge)
                        item.state == SavedItemState.New -> stringResource(R.string.saved_item_badge_new)
                        else -> stringResource(R.string.saved_item_badge_updated)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (pendingReview) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(bottom = 2.dp),
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pendingReview) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                // Title row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val completed = item.isTodo && item.isCompleted
                    val titleStyle = if (completed) {
                        NotiType.cardTitle.copy(textDecoration = TextDecoration.LineThrough)
                    } else NotiType.cardTitle

                    Text(
                        text = item.title.ifBlank {
                            if (item.isTodo) stringResource(R.string.ui_saved_items_untitled_task) else stringResource(R.string.ui_saved_items_untitled_memo)
                        },
                        style = titleStyle,
                        color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Actions moved to the bottom row; the title row keeps only expand/collapse.
                    if (!selectionMode) {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                painter = painterResource(if (expanded) R.drawable.keyboard_arrow_up else R.drawable.keyboard_arrow_down),
                                contentDescription = if (expanded) stringResource(R.string.a11y_collapse) else stringResource(R.string.a11y_expand),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Content preview — capped at 2 visual lines.
                val contentPreview = item.content.trim()
                if (contentPreview.isNotBlank()) {
                    Text(
                        text = contentPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                // Action buttons
                if (buttons.isNotEmpty()) {
                    val savedItemActionScrollState = rememberScrollState()
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(savedItemActionScrollState), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        buttons.forEach { button ->
                            SavedItemActionChip(button = button, context = context, clipboard = clipboard)
                        }
                    }
                }

                // Inline sub-tasks
                if (steps.isNotEmpty()) {
                    TodoStepListInCard(
                        steps = steps,
                        onToggleCompleted = onTodoStepToggle,
                        onTodoStepClick = onTodoStepClick,
                        onTodoStepEdit = onTodoStepEdit,
                        onTodoStepDelete = onTodoStepDelete,
                        onTodoStepExportGoogleTasks = onTodoStepExportGoogleTasks,
                        onTodoStepExportGoogleCalendar = onTodoStepExportGoogleCalendar,
                        forceExpanded = expanded,
                    )
                }

                // Bottom action row: deadline date, reminder, and star.
                if (!selectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onSetDeadline != null) {
                            SavedItemDeadlineButton(
                                deadlineAtMs = item.deadlineAtMs,
                                accent = rowAccent,
                                onClick = { showDeadlinePicker = true },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (onCreateReminder != null) {
                            IconButton(onClick = onCreateReminder) {
                                Icon(
                                    painter = painterResource(R.drawable.notifications),
                                    contentDescription = stringResource(R.string.a11y_set_reminder),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (onToggleStarred != null) {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(if (!item.isStarred) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                onToggleStarred()
                            }) {
                                Icon(
                                    painter = painterResource(if (item.isStarred) R.drawable.star_yes else R.drawable.star_no),
                                    contentDescription = stringResource(if (item.isStarred) R.string.a11y_unstar else R.string.a11y_star),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (item.isStarred) rowAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        }
      }
    }

    if (showDeadlinePicker && onSetDeadline != null) {
        SavedItemDeadlinePickerDialog(
            currentDeadlineAtMs = item.deadlineAtMs,
            onDismiss = { showDeadlinePicker = false },
            onSet = { newVal ->
                onSetDeadline(newVal)
                showDeadlinePicker = false
            },
        )
    }
}

/**
 * A chip button for LLM-generated item actions (copy text or open link).
 */
@Composable
private fun SavedItemActionChip(
    button: SavedItemActionButton,
    context: android.content.Context,
    clipboard: AndroidClipboardController,
) {
    val iconRes = when (button.type) {
        "copy" -> R.drawable.copy
        else -> R.drawable.link
    }
    AssistChip(
        onClick = {
            when (button.type) {
                "copy" -> {
                    clipboard.copyPlainText("saved_item_button", button.intent)
                }
                else -> {
                    try {
                        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(button.intent)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(viewIntent)
                    } catch (_: Exception) { /* no handler */ }
                }
            }
        },
        label = { Text(button.buttonText, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = if (button.type == "copy") stringResource(R.string.a11y_copy_text) else stringResource(R.string.a11y_open_link),
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SavedItemDetailScreen(
    initial: SavedItem,
    drawerViewModel: DrawerViewModel,
    onBack: (SavedItem?) -> Unit,
    onDelete: (String) -> Unit,
    onSave: (SavedItem) -> Unit,
    onExportToGoogleTasks: (SavedItem) -> Unit = {},
    isGoogleTasksExporting: Boolean = false,
    onOpenExportDialog: (SavedItem, ExportType) -> Unit = { _, _ -> },
    onRegenerate: () -> Unit = {},
    onCreateReminder: (() -> Unit)? = null,
    relatedNotificationsState: SavedItemsViewModel.RelatedNotificationsState = SavedItemsViewModel.RelatedNotificationsState(),
    onLoadRelatedNotifications: (SavedItem) -> Unit = {},
    // Review flow + change history
    changeLog: kotlinx.coroutines.flow.Flow<List<org.muilab.notigpt.model.features.SavedItemChangeLog>>? = null,
    onAcknowledgeReview: (() -> Unit)? = null,
    // Edit-in-review: replaces the inline "Got it" / export chips with a pinned Save&Approve / Delete
    // footer. Editing an item during review is itself the accept (per product decision).
    reviewMode: Boolean = false,
    onSaveApprove: ((ReviewItemDraft) -> Unit)? = null,
    onRejectDelete: (() -> Unit)? = null,
    // Sub-task parameters
    steps: List<TodoStep> = emptyList(),
    stepsEditable: Boolean = true,
    onAddTodoStep: () -> Unit = {},
    onTodoStepToggle: (String, Boolean) -> Unit = { _, _ -> },
    onTodoStepClick: (TodoStep) -> Unit = {},
    onTodoStepEdit: (TodoStep) -> Unit = {},
    onTodoStepDelete: (TodoStep) -> Unit = {},
    onTodoStepExportGoogleTasks: (TodoStep) -> Unit = {},
    onTodoStepExportGoogleCalendar: (TodoStep) -> Unit = {},
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    // Trigger B is now handled in SavedItemsScreen based on 'fully visible' item cards.

    var title by remember(initial.savedItemId) { mutableStateOf(initial.title) }
    var content by remember(initial.savedItemId) { mutableStateOf(initial.content) }
    var isTodo by remember(initial.savedItemId) { mutableStateOf(initial.isTodo) }
    var isCompleted by remember(initial.savedItemId) { mutableStateOf(initial.isCompleted) }
    var deadlineAtMs by remember(initial.savedItemId) { mutableStateOf(initial.deadlineAtMs) }
    var reviewSteps by remember(initial.savedItemId) { mutableStateOf(steps) }
    val visibleSteps = if (reviewMode) reviewSteps else steps

    // Per-type accent: indigo for Task, green for Keep.
    val accent = if (isTodo) NotiTheme.semantic.taskAccent else NotiTheme.semantic.keepAccent
    val accentContainer = if (isTodo) NotiTheme.semantic.taskContainer else NotiTheme.semantic.keepContainer
    val onAccentContainer = if (isTodo) NotiTheme.semantic.onTaskContainer else NotiTheme.semantic.onKeepContainer

    fun buildUpdated(): SavedItem {
        return initial.copy(
            title = title,
            content = content,
            itemType = if (isTodo) SavedItemType.Todo else SavedItemType.Keep,
            state = if (isTodo && isCompleted) SavedItemState.Completed else SavedItemState.Saved,
            // Keep this value until persistence so Task -> Keep conversion can merge it into content.
            // SavedItemNormalization clears it before the Keep row is stored.
            deadlineAtMs = deadlineAtMs,
        )
    }

    // Handle system back (gesture / nav button) like in-app navigation.
    BackHandler(enabled = true) {
        onBack(buildUpdated())
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showTaskToKeepDialog by remember { mutableStateOf(false) }
    var headerMenuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onBack(buildUpdated()) }) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.a11y_back))
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = false,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (title.isBlank()) {
                            Text(
                                text = stringResource(R.string.ui_saved_items_editor_title_placeholder),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                )
                if (!reviewMode) Box {
                    IconButton(onClick = { headerMenuOpen = true }) {
                        Icon(painterResource(R.drawable.more_vert), contentDescription = stringResource(R.string.a11y_step_more))
                    }
                    DropdownMenu(expanded = headerMenuOpen, onDismissRequest = { headerMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.a11y_regenerate_saved_item)) },
                            leadingIcon = { Icon(painterResource(R.drawable.refresh), contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = { headerMenuOpen = false; onRegenerate() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.a11y_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(painterResource(R.drawable.delete), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                            onClick = { headerMenuOpen = false; onDelete(initial.savedItemId) },
                        )
                    }
                }
            }
        }

        // Make the content scrollable so related notifications are reachable. In review mode the
        // content takes weight so the Save&Approve / Delete footer can pin below it.
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .then(if (reviewMode) Modifier.weight(1f) else Modifier.fillMaxSize())
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Review flow: unacknowledged LLM changes stay flagged until the explicit "Got it" tap.
            val changes by (changeLog ?: kotlinx.coroutines.flow.flowOf(emptyList()))
                .collectAsState(initial = emptyList())
            var reviewAcknowledged by remember(initial.savedItemId) { mutableStateOf(false) }
            // In edit-in-review the footer handles approval, so hide the inline "Got it" block.
            if (!reviewMode && !reviewAcknowledged && onAcknowledgeReview != null) {
                org.muilab.notigpt.ui.saveditem.component.SavedItemWhatsNewBlock(
                    item = initial,
                    changes = changes,
                    onAcknowledge = {
                        reviewAcknowledged = true
                        onAcknowledgeReview()
                    },
                )
            }

            // Type selector chips: Task (indigo) / Keep (green)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isTodo,
                    onClick = { isTodo = true },
                    label = { Text(stringResource(R.string.tab_tasks)) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.check_box_checked),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isTodo) NotiTheme.semantic.taskAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                FilterChip(
                    selected = !isTodo,
                    onClick = {
                        if (isTodo && (deadlineAtMs > 0L || visibleSteps.isNotEmpty())) {
                            showTaskToKeepDialog = true
                        } else {
                            isTodo = false
                        }
                    },
                    label = { Text(stringResource(R.string.tab_keep)) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.bookmark),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (!isTodo) NotiTheme.semantic.keepAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            if (isTodo) {
                // Task completion remains its own compact control; scheduling controls follow below.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        TodoCompletionToggle(
                            checked = isCompleted,
                            accent = accent,
                            onCheckedChange = { isCompleted = it },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_saved_items_editor_completed), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Deadlines remain available for todos; event-like start/end timing is not persisted.
            if (isTodo) {
                val hasDeadline = deadlineAtMs > 0L
                val deadlineDateStr = if (hasDeadline) {
                    java.text.SimpleDateFormat("yyyy-MM-dd", locale).format(java.util.Date(deadlineAtMs))
                } else stringResource(R.string.saved_item_no_date)
                val deadlineTimeStr = if (hasDeadline) {
                    java.text.SimpleDateFormat("HH:mm", locale).format(java.util.Date(deadlineAtMs))
                } else stringResource(R.string.saved_item_no_time)
                val deadlineColor = if (hasDeadline && deadlineAtMs < System.currentTimeMillis()) {
                    MaterialTheme.colorScheme.error
                } else accent

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.flag),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = deadlineColor,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.ui_saved_items_editor_deadline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(text = deadlineDateStr, color = deadlineColor)
                        }
                        TextButton(onClick = { showTimePicker = true }) {
                            Text(text = deadlineTimeStr, color = deadlineColor)
                        }
                        if (hasDeadline) {
                            IconButton(onClick = { deadlineAtMs = 0L }) {
                                Icon(
                                    painterResource(R.drawable.delete),
                                    contentDescription = stringResource(R.string.a11y_clear_deadline),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Text(stringResource(R.string.ui_saved_items_editor_note), style = MaterialTheme.typography.titleMedium)

            // Note-like editor, grouped card to match the task-detail surface above.
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    decorationBox = { innerTextField ->
                        if (content.isBlank()) {
                            Text(
                                text = stringResource(R.string.ui_saved_items_editor_note_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                )
            }

            // === LLM-generated button chips ===
            val detailButtons = remember(initial.buttons) {
                SavedItemActionButtons.parse(initial.buttons)
            }

            if (detailButtons.isNotEmpty()) {
                val detailClipboard = remember(context) { AndroidClipboardController(context) }
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    detailButtons.forEach { button ->
                        SavedItemActionChip(button = button, context = context, clipboard = detailClipboard)
                    }
                }
            }

            // === Sub-tasks section (above action chips) ===
            if (isTodo) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.step_section_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (stepsEditable || reviewMode) {
                        TextButton(onClick = {
                            if (reviewMode) {
                                reviewSteps = reviewSteps + TodoStep(
                                    todoStepId = "st_${java.util.UUID.randomUUID().toString().take(8)}",
                                    parentSavedItemId = initial.savedItemId,
                                    position = reviewSteps.size,
                                )
                            } else onAddTodoStep()
                        }) {
                            Icon(painterResource(R.drawable.add), contentDescription = stringResource(R.string.a11y_add_step), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.step_add), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                visibleSteps.forEach { st ->
                    TodoStepRow(
                        step = st,
                        onToggleCompleted = { checked ->
                            if (reviewMode) reviewSteps = reviewSteps.map {
                                if (it.todoStepId == st.todoStepId) it.copy(isCompleted = checked) else it
                            } else onTodoStepToggle(st.todoStepId, checked)
                        },
                        onDelete = {
                            if (reviewMode) reviewSteps = reviewSteps.filterNot { it.todoStepId == st.todoStepId }
                            else onTodoStepDelete(st)
                        },
                        editable = stepsEditable || reviewMode,
                        completionEnabled = stepsEditable || reviewMode,
                        onTextChange = { text ->
                            if (reviewMode) reviewSteps = reviewSteps.map {
                                if (it.todoStepId == st.todoStepId) it.copy(text = text) else it
                            } else onTodoStepEdit(st.copy(text = text))
                        },
                    )
                }
            }

            // === Export to external apps (chips) === (hidden during edit-in-review; export later)
            if (!reviewMode) {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Remind me (schedule a push item) — same flow as the outer cards.
                if (onCreateReminder != null) {
                    AssistChip(
                        onClick = onCreateReminder,
                        label = { Text(stringResource(R.string.ui_reminder_create_button), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(painter = painterResource(R.drawable.notifications), contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                        },
                    )
                }
                // Google Tasks chip
                AssistChip(
                    onClick = { onOpenExportDialog(buildUpdated(), ExportType.GOOGLE_TASKS) },
                    label = { Text(stringResource(R.string.google_tasks_export), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.task_add), contentDescription = stringResource(R.string.a11y_export_google_tasks), tint = accent, modifier = Modifier.size(16.dp))
                    },
                )
                // Google Calendar chip
                AssistChip(
                    onClick = { onOpenExportDialog(buildUpdated(), ExportType.GOOGLE_CALENDAR) },
                    label = { Text(stringResource(R.string.google_calendar_export), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.calendar_add), contentDescription = stringResource(R.string.a11y_export_google_calendar), tint = accent, modifier = Modifier.size(16.dp))
                    },
                )
                // Share chip
                AssistChip(
                    onClick = {
                        val shareText = "${title}\n\n${content}"
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    },
                    label = { Text(stringResource(R.string.saved_item_share), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.share), contentDescription = stringResource(R.string.a11y_share_saved_item), tint = accent, modifier = Modifier.size(16.dp))
                    },
                )
            }
            }

            // === Change history ===
            if (changes.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                org.muilab.notigpt.ui.saveditem.component.SavedItemChangeHistorySection(changes = changes)
            }

            // === Related notifications ===
            var relatedExpanded by remember(initial.savedItemId) { mutableStateOf(false) }
            val relatedForThisReminder = relatedNotificationsState.savedItemId == initial.savedItemId
            val relatedLoading = relatedForThisReminder && relatedNotificationsState.isLoading
            val relatedRecordsByKey = if (relatedForThisReminder) relatedNotificationsState.related.recordsByKey else emptyMap()
            val relatedUnitsByKey = if (relatedForThisReminder) relatedNotificationsState.related.unitsByKey else emptyMap()

            LaunchedEffect(initial.savedItemId, initial.lastUpdateTimestamp) {
                onLoadRelatedNotifications(initial)
            }

            // Keys are derived from the loaded link-backed related notifications.
            val relatedKeys = relatedRecordsByKey.keys.toList()

            // Show the section while loading or whenever related notifications resolve.
            if (relatedLoading || relatedRecordsByKey.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { relatedExpanded = !relatedExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.saved_item_related_notifications, relatedKeys.size),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Icon(
                        painter = painterResource(if (relatedExpanded) R.drawable.keyboard_arrow_up else R.drawable.keyboard_arrow_down),
                        contentDescription = if (relatedExpanded) stringResource(R.string.a11y_collapse) else stringResource(R.string.a11y_expand),
                    )
                }

                if (relatedExpanded) {
                    when {
                        relatedLoading -> {
                            Text(
                                text = stringResource(R.string.saved_item_related_notifications_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        relatedRecordsByKey.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.saved_item_no_related_notifications),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        else -> {
                            relatedKeys.forEach { key ->
                                val recs = relatedRecordsByKey[key]?.sortedBy { it.time } ?: return@forEach

                                val unit = relatedUnitsByKey[key]

                                if (unit != null) {
                                    val displayUnit = org.muilab.notigpt.model.notifications.NotiDisplayUnit(unit, recs)
                                    RelatedNotificationPreview(
                                        notiDisplayUnit = displayUnit,
                                        showOpenButton = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                        onOpen = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                drawerViewModel.accessNotificationByKey(key)
                                            }
                                        },
                                        evidenceRecordIds = relatedNotificationsState.related.evidenceRecordIds,
                                    )
                                } else {
                                    // Fallback: drawer entry missing; show text-only context.
                                    Surface(
                                        tonalElevation = 1.dp,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.size(6.dp))
                                            val preview = recs.joinToString("\n") { r ->
                                                val t = r.getDisplayedTitle(false)
                                                val c = r.content
                                                listOf(t, c).filter { it.isNotBlank() }.joinToString(": ")
                                            }
                                            Text(
                                                text = preview,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.size(8.dp))
                            }
                        }
                    }
                }
            }
        }

        // Edit-in-review footer: editing is itself the accept, so Save & Approve / Delete.
        if (reviewMode) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { onRejectDelete?.invoke() }) {
                        Text(stringResource(R.string.review_reject_change), color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = { onSaveApprove?.invoke(ReviewItemDraft(buildUpdated(), reviewSteps)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.review_save_approve))
                    }
                }
            }
        }
    }

    // Separate date picker (only modifies the date component of deadlineAtMs)
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (deadlineAtMs > 0L) deadlineAtMs else System.currentTimeMillis()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDate = datePickerState.selectedDateMillis
                        if (selectedDate != null) {
                            // Preserve existing time component if any, else default to noon
                            val existingCal = Calendar.getInstance().apply {
                                timeInMillis = if (deadlineAtMs > 0L) deadlineAtMs else System.currentTimeMillis()
                            }
                            val newCal = Calendar.getInstance().apply {
                                timeInMillis = selectedDate
                                set(Calendar.HOUR_OF_DAY, existingCal.get(Calendar.HOUR_OF_DAY))
                                set(Calendar.MINUTE, existingCal.get(Calendar.MINUTE))
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            deadlineAtMs = newCal.timeInMillis
                            showDatePicker = false
                        }
                    }
                ) { Text(stringResource(R.string.ui_action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.ui_action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTaskToKeepDialog) {
        AlertDialog(
            onDismissRequest = { showTaskToKeepDialog = false },
            title = { Text(stringResource(R.string.task_to_keep_title)) },
            text = { Text(stringResource(R.string.task_to_keep_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isTodo = false
                        isCompleted = false
                        showTaskToKeepDialog = false
                    },
                ) { Text(stringResource(R.string.task_to_keep_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTaskToKeepDialog = false }) {
                    Text(stringResource(R.string.ui_action_cancel))
                }
            },
        )
    }

    // Separate time picker (only modifies the time component of deadlineAtMs)
    if (showTimePicker) {
        LaunchedEffect(showTimePicker) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = if (deadlineAtMs > 0L) deadlineAtMs else System.currentTimeMillis()
            }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val c = Calendar.getInstance().apply {
                        timeInMillis = if (deadlineAtMs > 0L) deadlineAtMs else System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    deadlineAtMs = c.timeInMillis
                    showTimePicker = false
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).apply {
                setOnCancelListener { showTimePicker = false }
            }.show()
        }
    }

}

/** Which external app the export dialog targets. */
enum class ExportType { GOOGLE_TASKS, GOOGLE_CALENDAR }

/**
 * Data holder for the export confirmation dialog – remembers the item being exported
 * and which target the user chose.
 */
private data class ExportDialogState(
    val item: SavedItem,
    val type: ExportType,
)

/**
 * Confirmation dialog that lets the user review/edit fields before exporting
 * to Google Tasks or Google Calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportConfirmationDialog(
    state: ExportDialogState,
    isGoogleTasksExporting: Boolean,
    onDismiss: () -> Unit,
    onConfirmGoogleTasks: (title: String, description: String, deadlineMs: Long) -> Unit,
    onConfirmGoogleCalendar: (title: String, description: String, startMs: Long, endMs: Long, allDay: Boolean, reminderMinutes: Int) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val isCalendar = state.type == ExportType.GOOGLE_CALENDAR

    var title by remember { mutableStateOf(state.item.title) }
    var description by remember { mutableStateOf(state.item.content) }

    // For Google Tasks: deadline (0 = no deadline)
    var deadlineMs by remember { mutableStateOf(state.item.deadlineAtMs) }

    // For Google Calendar: start / end + full-day toggle
    val initialStart = 0L
    var startMs by remember { mutableStateOf(initialStart) }
    var endMs by remember {
        mutableStateOf(
            when {
                // No end time: default end date to same day as start so picker opens correctly
                initialStart > 0L -> {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = initialStart
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    cal.timeInMillis
                }
                else -> 0L
            }
        )
    }
    var isFullDay by remember { mutableStateOf(false) }

    // Reminder alarm offset. -1 = none.
    val reminderOptions = remember {
        listOf(-1, 0, 5, 10, 15, 30, 60, 120, 1440) // -1=none, 0=at time, others in minutes
    }
    var selectedReminderMinutes by remember { mutableIntStateOf(30) }
    var reminderDropdownExpanded by remember { mutableStateOf(false) }

    // Separate picking for date and time, per field
    var pickingField by remember { mutableStateOf<String?>(null) }
    var pickingMode by remember { mutableStateOf<String?>(null) }

    fun reminderLabel(minutes: Int): String = when (minutes) {
        -1   -> resources.getString(R.string.export_dialog_reminder_none)
        0    -> resources.getString(R.string.export_dialog_reminder_at_time)
        60   -> resources.getString(R.string.export_dialog_reminder_1_hour)
        120  -> resources.getString(R.string.export_dialog_reminder_2_hours)
        1440 -> resources.getString(R.string.export_dialog_reminder_1_day)
        else -> resources.getString(R.string.export_dialog_reminder_minutes, minutes)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isCalendar) R.string.export_dialog_title_calendar
                    else R.string.export_dialog_title_tasks
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.export_dialog_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.export_dialog_field_description)) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isCalendar) {
                    // Full-day toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.export_dialog_full_day),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(checked = isFullDay, onCheckedChange = { isFullDay = it })
                    }

                    // Start: separate date + time
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", locale)
                    val stf = java.text.SimpleDateFormat("HH:mm", locale)

                    val startDateLabel = if (startMs > 0L) sdf.format(java.util.Date(startMs)) else stringResource(R.string.saved_item_no_date)
                    val startAtMsLabel = if (startMs > 0L) stf.format(java.util.Date(startMs)) else stringResource(R.string.saved_item_no_time)

                    Text(stringResource(R.string.export_dialog_field_start_time), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickingField = "start"; pickingMode = "date" }) {
                            Text("${stringResource(R.string.saved_item_pick_date)}: $startDateLabel")
                        }
                        if (!isFullDay) {
                            TextButton(onClick = { pickingField = "start"; pickingMode = "time" }) {
                                Text("${stringResource(R.string.saved_item_pick_time)}: $startAtMsLabel")
                            }
                        }
                    }

                    // End: separate date + time
                    val endDateLabel = if (endMs > 0L) sdf.format(java.util.Date(endMs)) else stringResource(R.string.saved_item_no_date)
                    val endAtMsLabel = if (endMs > 0L) stf.format(java.util.Date(endMs)) else stringResource(R.string.saved_item_no_time)

                    Text(stringResource(R.string.export_dialog_field_end_time), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickingField = "end"; pickingMode = "date" }) {
                            Text("${stringResource(R.string.saved_item_pick_date)}: $endDateLabel")
                        }
                        if (!isFullDay) {
                            TextButton(onClick = { pickingField = "end"; pickingMode = "time" }) {
                                Text("${stringResource(R.string.saved_item_pick_time)}: $endAtMsLabel")
                            }
                        }
                    }

                    // Reminder alarm
                    Text(stringResource(R.string.export_dialog_field_reminder), style = MaterialTheme.typography.labelMedium)
                    Box {
                        TextButton(onClick = { reminderDropdownExpanded = true }) {
                            Text(reminderLabel(selectedReminderMinutes))
                        }
                        DropdownMenu(
                            expanded = reminderDropdownExpanded,
                            onDismissRequest = { reminderDropdownExpanded = false },
                        ) {
                            reminderOptions.forEach { minutes ->
                                DropdownMenuItem(
                                    text = { Text(reminderLabel(minutes)) },
                                    onClick = {
                                        selectedReminderMinutes = minutes
                                        reminderDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                } else {
                    // Google Tasks: deadline with separate date + time + clear option
                    val dlDateLabel = if (deadlineMs > 0L) {
                        java.text.SimpleDateFormat("yyyy-MM-dd", locale).format(java.util.Date(deadlineMs))
                    } else stringResource(R.string.saved_item_no_date)
                    val dlTimeLabel = if (deadlineMs > 0L) {
                        java.text.SimpleDateFormat("HH:mm", locale).format(java.util.Date(deadlineMs))
                    } else stringResource(R.string.saved_item_no_time)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { pickingField = "deadline"; pickingMode = "date" }) {
                            Text("${stringResource(R.string.saved_item_pick_date)}: $dlDateLabel")
                        }
                        TextButton(onClick = { pickingField = "deadline"; pickingMode = "time" }) {
                            Text("${stringResource(R.string.saved_item_pick_time)}: $dlTimeLabel")
                        }
                        if (deadlineMs > 0L) {
                            TextButton(onClick = { deadlineMs = 0L }) {
                                Text(
                                    stringResource(R.string.export_dialog_clear_deadline),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCalendar) {
                        val s = if (startMs > 0L) startMs else System.currentTimeMillis()
                        val e = if (endMs > 0L) endMs else s + 60 * 60 * 1000L
                        onConfirmGoogleCalendar(title, description, s, e, isFullDay, selectedReminderMinutes)
                    } else {
                        onConfirmGoogleTasks(title, description, deadlineMs)
                    }
                },
                enabled = !isGoogleTasksExporting || isCalendar,
            ) {
                if (!isCalendar && isGoogleTasksExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.export_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_action_cancel))
            }
        },
    )

    // ── Separate Date picker ──
    if (pickingMode == "date" && pickingField != null) {
        val initialMs = when (pickingField) {
            "start" -> if (startMs > 0L) startMs else System.currentTimeMillis()
            "end" -> if (endMs > 0L) endMs else System.currentTimeMillis()
            "deadline" -> if (deadlineMs > 0L) deadlineMs else System.currentTimeMillis()
            else -> System.currentTimeMillis()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs)
        val currentField = pickingField

        DatePickerDialog(
            onDismissRequest = { pickingMode = null; pickingField = null },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = datePickerState.selectedDateMillis
                    if (selectedDate != null) {
                        // Preserve existing time component, only change date
                        val existingMs = when (currentField) {
                            "start" -> startMs
                            "end" -> endMs
                            "deadline" -> deadlineMs
                            else -> 0L
                        }
                        val existingCal = Calendar.getInstance().apply {
                            timeInMillis = if (existingMs > 0L) existingMs else System.currentTimeMillis()
                        }
                        val newCal = Calendar.getInstance().apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, existingCal.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, existingCal.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        when (currentField) {
                            "start" -> startMs = newCal.timeInMillis
                            "end" -> endMs = newCal.timeInMillis
                            "deadline" -> deadlineMs = newCal.timeInMillis
                        }
                        pickingMode = null; pickingField = null
                    }
                }) { Text(stringResource(R.string.ui_action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingMode = null; pickingField = null }) {
                    Text(stringResource(R.string.ui_action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Separate Time picker ──
    if (pickingMode == "time" && pickingField != null) {
        val currentField = pickingField
        LaunchedEffect(pickingMode, pickingField) {
            val existingMs = when (currentField) {
                "start" -> startMs
                "end" -> endMs
                "deadline" -> deadlineMs
                else -> 0L
            }
            val cal = Calendar.getInstance().apply {
                timeInMillis = if (existingMs > 0L) existingMs else System.currentTimeMillis()
            }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val c = Calendar.getInstance().apply {
                        timeInMillis = if (existingMs > 0L) existingMs else System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    when (currentField) {
                        "start" -> startMs = c.timeInMillis
                        "end" -> endMs = c.timeInMillis
                        "deadline" -> deadlineMs = c.timeInMillis
                    }
                    pickingMode = null; pickingField = null
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).apply {
                setOnCancelListener { pickingMode = null; pickingField = null }
            }.show()
        }
    }
}
