package org.muilab.notigpt.ui.reminder.screen

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
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.preference.model.PreferenceEntryPoint
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.data.export.asExportable
import org.muilab.notigpt.ui.preference.component.PreferenceLearningBottomSheet
import org.muilab.notigpt.ui.common.component.DueChip
import org.muilab.notigpt.ui.common.feedback.AppSnackbar
import org.muilab.notigpt.ui.reminder.component.ExportChooserDialog
import org.muilab.notigpt.ui.theme.NotiTheme
import org.muilab.notigpt.ui.theme.NotiType
import org.muilab.notigpt.ui.theme.Dimens
import org.muilab.notigpt.ui.notification.component.RelatedNotificationPreview
import org.muilab.notigpt.ui.reminder.component.SavedSubItemRow
import org.muilab.notigpt.ui.reminder.component.SavedSubItemListInCard
import org.muilab.notigpt.ui.reminder.component.SavedSubItemDetailScreen
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModelFactory
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel
import org.muilab.notigpt.ui.reminder.viewmodel.ReminderViewModel
import org.muilab.notigpt.ui.reminder.viewmodel.ScheduledReminderViewModel
import org.muilab.notigpt.util.time.getAbsoluteTimeStr
import org.muilab.notigpt.util.time.getRelativeTimeStr
import java.util.Calendar
import org.muilab.notigpt.ui.common.clipboard.AndroidClipboardController
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.snapshotFlow
import androidx.constraintlayout.helper.widget.Grid
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.minimumInteractiveComponentSize

/**
 * Main reminders screen for tasks, memos, completion state, sub-tasks, export, and related notifications.
 *
 * This screen owns local editing dialogs, drag visuals, and edit drafts. Durable reminder, sub-task, sync, and
 * regeneration actions should stay in ReminderViewModel or related repositories.
 */
@Composable
fun RemindersScreen(
    drawerViewModel: DrawerViewModel,
    reminderViewModel: ReminderViewModel? = null,
    scheduledReminderViewModel: ScheduledReminderViewModel? = null,
    preferenceViewModel: PreferenceViewModel? = null,
    listMode: ReminderViewModel.ListMode = ReminderViewModel.ListMode.All,
    /** When set, this screen shows a home smart-filter list (planned-date bucket or starred) instead of the tab chips. */
    smartFilter: org.muilab.notigpt.ui.common.navigation.SavedListFilter? = null,
    onDetailOpenChange: (Boolean) -> Unit = {},
) {
    val vm: ReminderViewModel = reminderViewModel ?: viewModel()
    val scheduledVm: ScheduledReminderViewModel = scheduledReminderViewModel ?: viewModel()
    val prefVm: PreferenceViewModel = preferenceViewModel ?: viewModel()

    LaunchedEffect(listMode, smartFilter) {
        vm.setSmartFilter(smartFilter)
        vm.setListMode(listMode)
        vm.setFilter(
            when (listMode) {
                ReminderViewModel.ListMode.Tasks -> ReminderViewModel.FilterTab.Pending
                ReminderViewModel.ListMode.Keep -> ReminderViewModel.FilterTab.Keep
                else -> ReminderViewModel.FilterTab.All
            }
        )
    }

    // Drawer VM is needed to reuse the same notification/app launching logic as NotiRecordContextCard.
    val context = LocalContext.current
    val strGoogleTasksNotSignedIn = stringResource(R.string.google_tasks_not_signed_in)
    val strGoogleTasksSuccess = stringResource(R.string.google_tasks_success)
    val strGoogleTasksErrorFmt = stringResource(R.string.google_tasks_error, "%s")
    val strGoogleCalendarNoApp = stringResource(R.string.google_calendar_no_app)

    val reminders by vm.reminders.collectAsState()
    val filter by vm.filter.collectAsState()

    // Flash the destination filter chip when a card changes status (e.g. completed -> Completed chip).
    var flashTarget by remember { mutableStateOf<ReminderViewModel.FilterTab?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    val flashChip: (ReminderViewModel.FilterTab) -> Unit = { tab -> flashTarget = tab; flashTick++ }

    // Section identity color for the active tab (Tasks=amber, Keep=teal). Drives card accents + FAB.
    val tabAccent: Color? = when (listMode) {
        ReminderViewModel.ListMode.Tasks -> NotiTheme.semantic.taskAccent
        ReminderViewModel.ListMode.Keep -> NotiTheme.semantic.keepAccent
        else -> null
    }
    val fabContainer = when (listMode) {
        ReminderViewModel.ListMode.Tasks -> NotiTheme.semantic.taskContainer
        ReminderViewModel.ListMode.Keep -> NotiTheme.semantic.keepContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val fabContent = when (listMode) {
        ReminderViewModel.ListMode.Tasks -> NotiTheme.semantic.onTaskContainer
        ReminderViewModel.ListMode.Keep -> NotiTheme.semantic.onKeepContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    var editing by remember { mutableStateOf<SavedItem?>(null) }
    var lastEditing by remember { mutableStateOf<SavedItem?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingInitialSnapshot by remember { mutableStateOf<SavedItem?>(null) }

    // Sub-task editing (overlaid on top of reminder detail)
    var editingSavedSubItem by remember { mutableStateOf<SavedSubItem?>(null) }
    var editingSavedSubItemInitial by remember { mutableStateOf<SavedSubItem?>(null) }

    // Bulk sub-task observation (one DB query for all reminders)
    val allSavedSubItemsByReminder by vm.allSavedSubItemsByReminder.collectAsState()

    // Long-press feedback dialog
    var feedbackDialogReminder by remember { mutableStateOf<SavedItem?>(null) }
    var reminderDialogSavedItem by remember { mutableStateOf<SavedItem?>(null) }

    // Regenerate-all confirmation dialog
    var showRegenerateAllDialog by remember { mutableStateOf(false) }

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
            is ReminderViewModel.GoogleTasksExportResult.Success -> {
                AppSnackbar.show(strGoogleTasksSuccess)
                vm.clearGoogleTasksExportResult()
            }
            is ReminderViewModel.GoogleTasksExportResult.Error -> {
                AppSnackbar.show(strGoogleTasksErrorFmt.replace("%s", r.message ?: ""))
                vm.clearGoogleTasksExportResult()
            }
            is ReminderViewModel.GoogleTasksExportResult.NotSignedIn -> {
                // Launch sign-in flow
                vm.clearGoogleTasksExportResult()
            }
            else -> { /* Idle or Loading – no-op */ }
        }
    }

    // ===== Export confirmation dialog =====
    var exportDialogState by remember { mutableStateOf<ExportDialogState?>(null) }

    // Helper lambdas to open the dialog
    val openExportDialog: (SavedItem, ExportType) -> Unit = { reminder, type ->
        exportDialogState = ExportDialogState(reminder, type)
    }

    val handleGoogleTasksExport: (SavedItem) -> Unit = { reminder ->
        if (vm.isGoogleSignedIn()) {
            vm.exportToGoogleTasks(reminder)
        } else {
            pendingGoogleTasksReminder = reminder
            val signInIntent = org.muilab.notigpt.data.remote.googletasks.GoogleTasksAuthManager.getSignInIntent(context)
            googleSignInLauncher.launch(signInIntent)
        }
    }

    // Keep list state while editor is shown.
    val listState = rememberLazyListState()

    var pendingScrollToTopId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reminders, pendingScrollToTopId) {
        val targetId = pendingScrollToTopId ?: return@LaunchedEffect
        if (reminders.firstOrNull()?.savedItemId == targetId) {
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
                // Smart-filter lists (Today/Upcoming/Someday/Undetermined/Starred) show no tab chips;
                // the shell's top bar carries the filter name.
                if (smartFilter == null) when (listMode) {
                    ReminderViewModel.ListMode.All -> {
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_all), filter == ReminderViewModel.FilterTab.All, { vm.setFilter(ReminderViewModel.FilterTab.All) })
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_tasks), filter == ReminderViewModel.FilterTab.Tasks, { vm.setFilter(ReminderViewModel.FilterTab.Tasks) })
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_memos), filter == ReminderViewModel.FilterTab.Memos, { vm.setFilter(ReminderViewModel.FilterTab.Memos) })
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_completed), filter == ReminderViewModel.FilterTab.Completed, { vm.setFilter(ReminderViewModel.FilterTab.Completed) })
                    }
                    ReminderViewModel.ListMode.Tasks -> {
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_pending), filter == ReminderViewModel.FilterTab.Pending, { vm.setFilter(ReminderViewModel.FilterTab.Pending) }, leadingIconRes = R.drawable.check_box_unchecked, iconTint = NotiTheme.semantic.taskAccent, flashTick = if (flashTarget == ReminderViewModel.FilterTab.Pending) flashTick else 0)
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_completed), filter == ReminderViewModel.FilterTab.Completed, { vm.setFilter(ReminderViewModel.FilterTab.Completed) }, leadingIconRes = R.drawable.check_box_checked, iconTint = NotiTheme.semantic.taskAccent, flashTick = if (flashTarget == ReminderViewModel.FilterTab.Completed) flashTick else 0)
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_starred), filter == ReminderViewModel.FilterTab.Starred, { vm.setFilter(ReminderViewModel.FilterTab.Starred) }, leadingIconRes = R.drawable.star_yes, iconTint = NotiTheme.semantic.taskAccent)
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_all), filter == ReminderViewModel.FilterTab.All, { vm.setFilter(ReminderViewModel.FilterTab.All) })
                    }
                    ReminderViewModel.ListMode.Keep -> {
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_keep), filter == ReminderViewModel.FilterTab.Keep, { vm.setFilter(ReminderViewModel.FilterTab.Keep) }, leadingIconRes = R.drawable.bookmark, iconTint = NotiTheme.semantic.keepAccent, flashTick = if (flashTarget == ReminderViewModel.FilterTab.Keep) flashTick else 0)
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_archived), filter == ReminderViewModel.FilterTab.Archived, { vm.setFilter(ReminderViewModel.FilterTab.Archived) }, leadingIconRes = R.drawable.archive_yes, iconTint = NotiTheme.semantic.keepAccent, flashTick = if (flashTarget == ReminderViewModel.FilterTab.Archived) flashTick else 0)
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_starred), filter == ReminderViewModel.FilterTab.Starred, { vm.setFilter(ReminderViewModel.FilterTab.Starred) }, leadingIconRes = R.drawable.star_yes, iconTint = NotiTheme.semantic.keepAccent)
                        ReminderFilterChip(stringResource(R.string.ui_reminders_filter_all), filter == ReminderViewModel.FilterTab.All, { vm.setFilter(ReminderViewModel.FilterTab.All) })
                    }
                }

                Spacer(Modifier.weight(1f))

                // Regenerate-all is hidden for now (kept for easy restore).
                // IconButton(onClick = { showRegenerateAllDialog = true }) {
                //     Icon(
                //         painter = painterResource(R.drawable.refresh),
                //         contentDescription = stringResource(R.string.a11y_refresh_all),
                //         modifier = Modifier.size(20.dp),
                //     )
                // }
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
                items(reminders, key = { it.savedItemId }, contentType = { "reminderCard" }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        subTasks = allSavedSubItemsByReminder[reminder.savedItemId] ?: emptyList(),
                        onDelete = {
                            vm.delete(reminder.savedItemId)
                            if (reminder.origin.contains("llm")) {
                                prefVm.startFlow(
                                    entryPoint = PreferenceEntryPoint.DELETE,
                                    reminder = reminder,
                                )
                            }
                        },
                        onToggleCompleted = { completed: Boolean ->
                            vm.toggleCompleted(reminder, completed)
                            flashChip(if (completed) ReminderViewModel.FilterTab.Completed else ReminderViewModel.FilterTab.Pending)
                        },
                        onEdit = {
                            editing = reminder
                            editingId = reminder.savedItemId
                            editingInitialSnapshot = reminder
                        },
                        onToggleStarred = { vm.toggleStarred(reminder) },
                        onSetDoDate = { vm.setDoDate(reminder.savedItemId, it) },
                        onLongPress = { feedbackDialogReminder = reminder },
                        onCreateReminder = { reminderDialogSavedItem = reminder },
                        sectionAccent = tabAccent,
                        onArchive = {
                            flashChip(if (reminder.isArchived) ReminderViewModel.FilterTab.Keep else ReminderViewModel.FilterTab.Archived)
                            vm.archiveKeep(reminder.savedItemId)
                        },
                        // Export is available on both task and keep cards (users may push kept info to Tasks/Calendar).
                        onQuickExportTasks = { openExportDialog(reminder, ExportType.GOOGLE_TASKS) },
                        onQuickExportCalendar = { openExportDialog(reminder, ExportType.GOOGLE_CALENDAR) },
                        onSavedSubItemToggle = { stId, checked -> vm.toggleSavedSubItemCompleted(stId, checked) },
                        onSavedSubItemClick = { st ->
                            // Open parent reminder detail first, then navigate to sub-task detail
                            editing = reminder
                            editingId = reminder.savedItemId
                            editingInitialSnapshot = reminder
                            editingSavedSubItem = st
                            editingSavedSubItemInitial = st
                        },
                        onSavedSubItemEdit = { st ->
                            editing = reminder
                            editingId = reminder.savedItemId
                            editingInitialSnapshot = reminder
                            editingSavedSubItem = st
                            editingSavedSubItemInitial = st
                        },
                        onSavedSubItemDelete = { st -> vm.deleteSavedSubItem(st.savedSubItemId) },
                        onSavedSubItemExportGoogleTasks = { st -> vm.exportToGoogleTasks(st.asExportable()) },
                        onSavedSubItemExportGoogleCalendar = { st ->
                            val calIntent = Intent(Intent.ACTION_INSERT).apply {
                                data = CalendarContract.Events.CONTENT_URI
                                putExtra(CalendarContract.Events.TITLE, st.title)
                                putExtra(CalendarContract.Events.DESCRIPTION, st.description)
                                if (st.startAtMs > 0L) putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, st.startAtMs)
                                if (st.endAtMs > 0L) putExtra(CalendarContract.EXTRA_EVENT_END_TIME, st.endAtMs)
                            }
                            try { context.startActivity(calIntent) } catch (_: Exception) {}
                        },
                    )
                }
            }
        }

        reminderDialogSavedItem?.let { target ->
            ReminderDateTimeDialog(
                title = stringResource(R.string.ui_reminders_create_button),
                initialAtMs = System.currentTimeMillis(),
                onDismiss = { reminderDialogSavedItem = null },
                onConfirm = { remindAtMs ->
                    scheduledVm.createForSavedItem(target, remindAtMs)
                    reminderDialogSavedItem = null
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
                    estimatedCompletionTime = 0L,
                    origin = "manual",
                    humanEditCount = 0,
                    deletedAtMs = null,
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
                ReminderDetailScreen(
                    initial = current,
                    drawerViewModel = drawerViewModel,
                    onCreateReminder = { reminderDialogSavedItem = current },
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
                                base.isTask != updatedOrNull.isTask ||
                                base.isCompleted != updatedOrNull.isCompleted ||
                                base.deadlineAtMs != updatedOrNull.deadlineAtMs ||
                                base.estimatedCompletionTime != updatedOrNull.estimatedCompletionTime
                        )

                        if (updatedOrNull != null) {
                            val emptyNow = updatedOrNull.title.isBlank() && updatedOrNull.content.isBlank()
                            // Do date is user-owned planning: persist it via the targeted setter so a
                            // do-date-only edit does not flip userEdited (which shields content from LLM updates).
                            if (!emptyNow && base != null && base.doAtMs != updatedOrNull.doAtMs && !changed) {
                                vm.setDoDate(updatedOrNull.savedItemId, updatedOrNull.doAtMs)
                            }
                            when {
                                emptyNow -> {
                                    // For brand-new manual reminders, just discard. For existing reminders, delete.
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
                        // Trigger preference learning for LLM-generated reminders deleted from detail
                        if (deletedReminder != null && deletedReminder.origin.contains("llm")) {
                            prefVm.startFlow(
                                entryPoint = PreferenceEntryPoint.DELETE,
                                reminder = deletedReminder,
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
                                base.isTask != updated.isTask ||
                                base.isCompleted != updated.isCompleted ||
                                base.deadlineAtMs != updated.deadlineAtMs ||
                                base.estimatedCompletionTime != updated.estimatedCompletionTime
                        )

                        val emptyNow = updated.title.isBlank() && updated.content.isBlank()
                        // See onBack: do-date-only edits persist without flipping userEdited.
                        if (!emptyNow && base != null && base.doAtMs != updated.doAtMs && !changed) {
                            vm.setDoDate(updated.savedItemId, updated.doAtMs)
                        }
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

                        // Trigger preference learning for LLM-generated reminders that were edited
                        if (changed && !isNew && base != null && base.origin.contains("llm")) {
                            prefVm.startFlow(
                                entryPoint = PreferenceEntryPoint.EDIT,
                                reminder = updated,
                                reminderBefore = base,
                            )
                        }

                        val id = if (changed) editingId else null
                        editing = null
                        editingId = null
                        editingInitialSnapshot = null
                        if (id != null) pendingScrollToTopId = id
                    },
                    onExportToGoogleTasks = { reminder ->
                        if (vm.isGoogleSignedIn()) {
                            vm.exportToGoogleTasks(reminder)
                        } else {
                            pendingGoogleTasksReminder = reminder
                            val signInIntent = org.muilab.notigpt.data.remote.googletasks.GoogleTasksAuthManager.getSignInIntent(context)
                            googleSignInLauncher.launch(signInIntent)
                        }
                    },
                    isGoogleTasksExporting = googleTasksExportResult is ReminderViewModel.GoogleTasksExportResult.Loading,
                    onOpenExportDialog = openExportDialog,
                    onRegenerate = { vm.regenerateOne(current.savedItemId) },
                    relatedNotificationsState = relatedNotificationsState,
                    onLoadRelatedNotifications = { reminder -> vm.loadRelatedNotifications(reminder) },
                    changeLog = remember(current.savedItemId) { vm.changeLogFlow(current.savedItemId) },
                    onAcknowledgeReview = { vm.acknowledgeReview(current.savedItemId) },
                    onLoadSurroundingContext = { key -> vm.loadSurroundingContext(current.savedItemId, key) },
                    // Sub-task parameters
                    subTasks = allSavedSubItemsByReminder[current.savedItemId] ?: emptyList(),
                    onAddSavedSubItem = { vm.addSavedSubItem(current.savedItemId) },
                    onSavedSubItemToggle = { stId, checked -> vm.toggleSavedSubItemCompleted(stId, checked) },
                    onSavedSubItemClick = { st ->
                        editingSavedSubItem = st
                        editingSavedSubItemInitial = st
                    },
                    onSavedSubItemEdit = { st ->
                        editingSavedSubItem = st
                        editingSavedSubItemInitial = st
                    },
                    onSavedSubItemDelete = { st -> vm.deleteSavedSubItem(st.savedSubItemId) },
                    onSavedSubItemExportGoogleTasks = { st -> vm.exportToGoogleTasks(st.asExportable()) },
                    onSavedSubItemExportGoogleCalendar = { st ->
                        val calIntent = Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, st.title)
                            putExtra(CalendarContract.Events.DESCRIPTION, st.description)
                            if (st.startAtMs > 0L) putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, st.startAtMs)
                            if (st.endAtMs > 0L) putExtra(CalendarContract.EXTRA_EVENT_END_TIME, st.endAtMs)
                        }
                        try { context.startActivity(calIntent) } catch (_: Exception) {}
                    },
                )

                // Sub-task detail overlay (on top of reminder detail)
                editingSavedSubItem?.let { stCurrent ->
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
                        SavedSubItemDetailScreen(
                            initial = stCurrent,
                            onBack = { updatedOrNull ->
                                if (updatedOrNull != null) {
                                    val base = editingSavedSubItemInitial
                                    val changed = base != null && (
                                        base.title != updatedOrNull.title ||
                                            base.description != updatedOrNull.description ||
                                            base.isTask != updatedOrNull.isTask ||
                                            base.isEvent != updatedOrNull.isEvent ||
                                            base.isCompleted != updatedOrNull.isCompleted ||
                                            base.deadlineAtMs != updatedOrNull.deadlineAtMs ||
                                            base.startAtMs != updatedOrNull.startAtMs ||
                                            base.endAtMs != updatedOrNull.endAtMs
                                    )
                                    if (changed) {
                                        vm.upsertSavedSubItem(updatedOrNull)
                                    }
                                }
                                editingSavedSubItem = null
                                editingSavedSubItemInitial = null
                            },
                            onDelete = { stId ->
                                vm.deleteSavedSubItem(stId)
                                editingSavedSubItem = null
                                editingSavedSubItemInitial = null
                            },
                            onSave = { updated ->
                                vm.upsertSavedSubItem(updated)
                                editingSavedSubItem = null
                                editingSavedSubItemInitial = null
                            },
                            onExportGoogleTasks = { st -> vm.exportToGoogleTasks(st.asExportable()) },
                            onExportGoogleCalendar = { st ->
                                val calIntent = Intent(Intent.ACTION_INSERT).apply {
                                    data = CalendarContract.Events.CONTENT_URI
                                    putExtra(CalendarContract.Events.TITLE, st.title)
                                    putExtra(CalendarContract.Events.DESCRIPTION, st.description)
                                    if (st.startAtMs > 0L) putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, st.startAtMs)
                                    if (st.endAtMs > 0L) putExtra(CalendarContract.EXTRA_EVENT_END_TIME, st.endAtMs)
                                }
                                try { context.startActivity(calIntent) } catch (_: Exception) {}
                            },
                        )
                    }
                }
            }
        }
        }
    }

    // Export confirmation dialog (shared between list quick-export and detail screen)
    exportDialogState?.let { dlgState ->
        ExportConfirmationDialog(
            state = dlgState,
            isGoogleTasksExporting = googleTasksExportResult is ReminderViewModel.GoogleTasksExportResult.Loading,
            onDismiss = { exportDialogState = null },
            onConfirmGoogleTasks = { title, description, deadlineMs ->
                val exportReminder = dlgState.reminder.copy(
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

    // Regenerate-all confirmation dialog
    if (showRegenerateAllDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateAllDialog = false },
            title = { Text(stringResource(R.string.dialog_regenerate_all_title)) },
            text  = { Text(stringResource(R.string.dialog_regenerate_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRegenerateAllDialog = false
                    vm.regenerateAll()
                }) { Text(stringResource(R.string.dialog_regenerate_all_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateAllDialog = false }) {
                    Text(stringResource(R.string.ui_action_cancel))
                }
            },
        )
    }

    // Long-press feedback dialog
    feedbackDialogReminder?.let { reminder ->
        AlertDialog(
            onDismissRequest = { feedbackDialogReminder = null },
            title = { Text(stringResource(R.string.reminder_feedback_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.submitFeedback(reminder.savedItemId, "USER_FEEDBACK_IMPORTANT")
                            feedbackDialogReminder = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.reminder_feedback_important))
                    }
                    OutlinedButton(
                        onClick = {
                            vm.submitFeedback(reminder.savedItemId, "USER_FEEDBACK_HANDLE_LATER")
                            feedbackDialogReminder = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.reminder_feedback_handle_later))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { feedbackDialogReminder = null }) {
                    Text(stringResource(R.string.ui_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * Single-select filter chip used by the Task/Keep filter rows.
 *
 * Wraps Material3 [FilterChip]; pass [leadingIconRes] to show a leading icon (e.g. checkbox glyphs for
 * Pending/Completed). These are mutually-exclusive filters, which is exactly FilterChip's role.
 */
@Composable
private fun ReminderFilterChip(
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
fun ReminderCard(
    reminder: SavedItem,
    subTasks: List<SavedSubItem> = emptyList(),
    onDelete: () -> Unit,
    onToggleCompleted: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onToggleStarred: (() -> Unit)? = null,
    onCreateReminder: (() -> Unit)? = null,
    onQuickExportTasks: (() -> Unit)? = null,
    onQuickExportCalendar: (() -> Unit)? = null,
    /** Set/clear the user's planned "do date" straight from the card. Null hides the affordance. */
    onSetDoDate: ((Long) -> Unit)? = null,
    onLongPress: () -> Unit = {},
    onArchive: () -> Unit = {},
    /** Optional left-edge accent identifying the section (e.g. Tasks/Keep). Null = no accent. */
    sectionAccent: Color? = null,
    // Multi-select (New screen triage). When selectionMode, the card toggles selection instead of opening.
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    // Sub-task callbacks
    onSavedSubItemToggle: (String, Boolean) -> Unit = { _, _ -> },
    onSavedSubItemClick: (SavedSubItem) -> Unit = {},
    onSavedSubItemEdit: (SavedSubItem) -> Unit = {},
    onSavedSubItemDelete: (SavedSubItem) -> Unit = {},
    onSavedSubItemExportGoogleTasks: (SavedSubItem) -> Unit = {},
    onSavedSubItemExportGoogleCalendar: (SavedSubItem) -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = remember(context) { AndroidClipboardController(context) }
    val haptic = LocalHapticFeedback.current

    // Parse LLM-generated buttons
    val buttons = remember(reminder.buttons) {
        try {
            val arr = JSONArray(reminder.buttons)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(Triple(
                        obj.optString("buttonText", ""),
                        obj.optString("intent", ""),
                        obj.optString("type", "link"),
                    ))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    var expanded by remember(reminder.savedItemId) { mutableStateOf(false) }
    var showExportChooser by remember(reminder.savedItemId) { mutableStateOf(false) }
    var confirmDelete by remember(reminder.savedItemId) { mutableStateOf(false) }
    var showDoDatePicker by remember(reminder.savedItemId) { mutableStateOf(false) }
    // Star tint follows the item's own type (fixes the previously hardcoded task accent on keep cards).
    val rowAccent = if (reminder.isTask) NotiTheme.semantic.taskAccent else NotiTheme.semantic.keepAccent
    val selectedBorder = sectionAccent ?: MaterialTheme.colorScheme.primary
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
        if (sectionAccent != null) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(sectionAccent),
            )
        }
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
                } else if (reminder.isTask) {
                    Checkbox(
                        checked = reminder.isCompleted,
                        onCheckedChange = {
                            haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            onToggleCompleted(it)
                        },
                    )
                } else {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(if (!reminder.isArchived) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                            onArchive()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            painter = painterResource(if (reminder.isArchived) R.drawable.archive_yes else R.drawable.archive_no),
                            contentDescription = stringResource(R.string.a11y_archive),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Right content column
            Column(modifier = Modifier.weight(1f)) {
                // Review badge: stays until the user explicitly acknowledges in the detail view.
                if (reminder.isNewLike && !selectionMode) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(bottom = 2.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (reminder.state == SavedItemState.New) R.string.reminder_badge_new
                                else R.string.reminder_badge_updated
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                // Title row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val completed = reminder.isTask && reminder.isCompleted
                    val titleStyle = if (completed) {
                        NotiType.cardTitle.copy(textDecoration = TextDecoration.LineThrough)
                    } else NotiType.cardTitle

                    Text(
                        text = reminder.title.ifBlank {
                            if (reminder.isTask) stringResource(R.string.ui_reminders_untitled_task) else stringResource(R.string.ui_reminders_untitled_memo)
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

                // Deadline urgency chip (tasks only). The do-date lives on the bottom action row.
                if (reminder.isTask) {
                    val deadline = reminder.deadlineAtMs
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        if (deadline > 0L) {
                            DueChip(deadlineAtMs = deadline)
                        } else {
                            Text(
                                text = stringResource(R.string.ui_reminders_no_deadline),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Content preview — capped at 2 visual lines.
                val contentPreview = reminder.content.trim()
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
                    val reminderActionScrollState = rememberScrollState()
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(reminderActionScrollState), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        buttons.forEach { (buttonText, intent, type) ->
                            ReminderActionChip(buttonText = buttonText, intent = intent, type = type, context = context, clipboard = clipboard)
                        }
                    }
                }

                // Inline sub-tasks
                if (subTasks.isNotEmpty()) {
                    SavedSubItemListInCard(
                        subTasks = subTasks,
                        onToggleCompleted = onSavedSubItemToggle,
                        onSavedSubItemClick = onSavedSubItemClick,
                        onSavedSubItemEdit = onSavedSubItemEdit,
                        onSavedSubItemDelete = onSavedSubItemDelete,
                        onSavedSubItemExportGoogleTasks = onSavedSubItemExportGoogleTasks,
                        onSavedSubItemExportGoogleCalendar = onSavedSubItemExportGoogleCalendar,
                        forceExpanded = expanded,
                    )
                }

                // Bottom action row: do-date affordance on the left, icon controls on the right.
                if (!selectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onSetDoDate != null) {
                            DoDateBottomButton(
                                doAtMs = reminder.doAtMs,
                                accent = rowAccent,
                                onClick = { showDoDatePicker = true },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (onToggleStarred != null) {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(if (!reminder.isStarred) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                onToggleStarred()
                            }) {
                                Icon(
                                    painter = painterResource(if (reminder.isStarred) R.drawable.star_yes else R.drawable.star_no),
                                    contentDescription = stringResource(if (reminder.isStarred) R.string.a11y_unstar else R.string.a11y_star),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (reminder.isStarred) rowAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
                        if (onQuickExportTasks != null || onQuickExportCalendar != null) {
                            IconButton(onClick = { showExportChooser = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.task_add),
                                    contentDescription = stringResource(R.string.a11y_export),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = {
                            // Already-reviewed items confirm; new/updated ones (from the review flow) delete directly.
                            if (reminder.isNewLike) onDelete() else confirmDelete = true
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.a11y_delete),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        }
      }
    }

    if (showExportChooser) {
        ExportChooserDialog(
            onDismiss = { showExportChooser = false },
            onExportTasks = onQuickExportTasks?.let { { showExportChooser = false; it() } },
            onExportCalendar = onQuickExportCalendar?.let { { showExportChooser = false; it() } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.ui_action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.ui_action_cancel)) }
            },
        )
    }
    if (showDoDatePicker && onSetDoDate != null) {
        CardDoDatePickerDialog(
            currentDoAtMs = reminder.doAtMs,
            onDismiss = { showDoDatePicker = false },
            onSet = { newVal ->
                onSetDoDate(newVal)
                showDoDatePicker = false
            },
        )
    }
}

/**
 * Compact do-date picker used from a reminder card's bottom row: pick a concrete day, or mark the
 * task "Someday" / clear it. Mirrors the detail editor's date-merge (keeps existing time-of-day, else
 * defaults to 09:00 — a work-start plan, not a 23:59 deadline).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardDoDatePickerDialog(
    currentDoAtMs: Long,
    onDismiss: () -> Unit,
    onSet: (Long) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (SavedItem.hasPlannedDate(currentDoAtMs)) currentDoAtMs else System.currentTimeMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selectedDate = pickerState.selectedDateMillis
                if (selectedDate != null) {
                    val hadTime = SavedItem.hasPlannedDate(currentDoAtMs)
                    val existingCal = Calendar.getInstance().apply {
                        timeInMillis = if (hadTime) currentDoAtMs else System.currentTimeMillis()
                    }
                    val newCal = Calendar.getInstance().apply {
                        timeInMillis = selectedDate
                        set(Calendar.HOUR_OF_DAY, if (hadTime) existingCal.get(Calendar.HOUR_OF_DAY) else 9)
                        set(Calendar.MINUTE, if (hadTime) existingCal.get(Calendar.MINUTE) else 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onSet(newCal.timeInMillis)
                }
            }) { Text(stringResource(R.string.ui_action_ok)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onSet(0L) }) { Text(stringResource(R.string.ui_action_clear)) }
                TextButton(onClick = { onSet(SavedItem.DO_AT_SOMEDAY) }) {
                    Text(stringResource(R.string.do_date_someday))
                }
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/**
 * The bottom-row do-date affordance: a calendar icon plus a "when I'll handle it" label
 * (a concrete relative date, "Someday", or a muted "Set do date" when unset). Tapping opens the
 * picker. Sits on the left of the card's bottom action row.
 */
@Composable
private fun DoDateBottomButton(
    doAtMs: Long,
    accent: Color,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val hasDo = doAtMs > 0L
    val label = when {
        SavedItem.isSomeday(doAtMs) -> stringResource(R.string.do_date_someday)
        SavedItem.hasPlannedDate(doAtMs) ->
            stringResource(R.string.ui_reminders_do_date_chip, getRelativeTimeStr(doAtMs, context))
        else -> stringResource(R.string.a11y_set_do_date)
    }
    val tint = if (hasDo) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_calendar_today),
            contentDescription = stringResource(R.string.a11y_set_do_date),
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

/**
 * A chip button for LLM-generated reminder actions (copy text or open link).
 */
@Composable
private fun ReminderActionChip(
    buttonText: String,
    intent: String,
    type: String,
    context: android.content.Context,
    clipboard: AndroidClipboardController,
) {
    val iconRes = when (type) {
        "copy" -> R.drawable.copy
        else -> R.drawable.link
    }
    AssistChip(
        onClick = {
            when (type) {
                "copy" -> {
                    clipboard.copyPlainText("reminder_button", intent)
                }
                else -> {
                    try {
                        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(intent)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(viewIntent)
                    } catch (_: Exception) { /* no handler */ }
                }
            }
        },
        label = { Text(buttonText, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = if (type == "copy") stringResource(R.string.a11y_copy_text) else stringResource(R.string.a11y_open_link),
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderDetailScreen(
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
    relatedNotificationsState: ReminderViewModel.RelatedNotificationsState = ReminderViewModel.RelatedNotificationsState(),
    onLoadRelatedNotifications: (SavedItem) -> Unit = {},
    // Review flow + change history
    changeLog: kotlinx.coroutines.flow.Flow<List<org.muilab.notigpt.model.features.SavedItemChangeLog>>? = null,
    onAcknowledgeReview: (() -> Unit)? = null,
    onLoadSurroundingContext: ((String) -> Unit)? = null,
    // Sub-task parameters
    subTasks: List<SavedSubItem> = emptyList(),
    onAddSavedSubItem: () -> Unit = {},
    onSavedSubItemToggle: (String, Boolean) -> Unit = { _, _ -> },
    onSavedSubItemClick: (SavedSubItem) -> Unit = {},
    onSavedSubItemEdit: (SavedSubItem) -> Unit = {},
    onSavedSubItemDelete: (SavedSubItem) -> Unit = {},
    onSavedSubItemExportGoogleTasks: (SavedSubItem) -> Unit = {},
    onSavedSubItemExportGoogleCalendar: (SavedSubItem) -> Unit = {},
) {
    val context = LocalContext.current

    // Trigger B is now handled in RemindersScreen based on 'fully visible' reminder cards.

    var title by remember(initial.savedItemId) { mutableStateOf(initial.title) }
    var content by remember(initial.savedItemId) { mutableStateOf(initial.content) }
    var isTask by remember(initial.savedItemId) { mutableStateOf(initial.isTask) }
    var isCompleted by remember(initial.savedItemId) { mutableStateOf(initial.isCompleted) }
    var deadlineAtMs by remember(initial.savedItemId) { mutableStateOf(initial.deadlineAtMs) }
    var doAtMs by remember(initial.savedItemId) { mutableStateOf(initial.doAtMs) }
    var ectMinutes by remember(initial.savedItemId) { mutableStateOf(initial.estimatedCompletionTime) }

    // Per-type accent: indigo for Task, green for Keep.
    val accent = if (isTask) NotiTheme.semantic.taskAccent else NotiTheme.semantic.keepAccent
    val accentContainer = if (isTask) NotiTheme.semantic.taskContainer else NotiTheme.semantic.keepContainer
    val onAccentContainer = if (isTask) NotiTheme.semantic.onTaskContainer else NotiTheme.semantic.onKeepContainer

    fun buildUpdated(): SavedItem {
        return initial.copy(
            title = title,
            content = content,
            itemType = if (isTask) SavedItemType.Task else SavedItemType.Keep,
            state = if (isTask && isCompleted) SavedItemState.Completed else SavedItemState.Saved,
            deadlineAtMs = if (isTask) deadlineAtMs else 0L,
            doAtMs = if (isTask) doAtMs else 0L,
            estimatedCompletionTime = if (isTask) ectMinutes else 0L,
        )
    }

    // Handle system back (gesture / nav button) like in-app navigation.
    BackHandler(enabled = true) {
        onBack(buildUpdated())
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDoDatePicker by remember { mutableStateOf(false) }
    var showDoTimePicker by remember { mutableStateOf(false) }
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
                                text = stringResource(R.string.ui_reminders_editor_title_placeholder),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                )
                Box {
                    IconButton(onClick = { headerMenuOpen = true }) {
                        Icon(painterResource(R.drawable.more_vert), contentDescription = stringResource(R.string.a11y_subtask_more))
                    }
                    DropdownMenu(expanded = headerMenuOpen, onDismissRequest = { headerMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.a11y_regenerate)) },
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

        // Make the content scrollable so related notifications are reachable.
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Review flow: unacknowledged LLM changes stay flagged until the explicit "Got it" tap.
            val changes by (changeLog ?: kotlinx.coroutines.flow.flowOf(emptyList()))
                .collectAsState(initial = emptyList())
            var reviewAcknowledged by remember(initial.savedItemId) { mutableStateOf(false) }
            if (!reviewAcknowledged && onAcknowledgeReview != null) {
                org.muilab.notigpt.ui.reminder.component.ReminderWhatsNewBlock(
                    reminder = initial,
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
                    selected = isTask,
                    onClick = { isTask = true },
                    label = { Text(stringResource(R.string.tab_tasks)) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.check_box_checked),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isTask) NotiTheme.semantic.taskAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                FilterChip(
                    selected = !isTask,
                    onClick = { isTask = false },
                    label = { Text(stringResource(R.string.tab_keep)) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.bookmark),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (!isTask) NotiTheme.semantic.keepAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            if (isTask) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = isCompleted,
                        onCheckedChange = { isCompleted = it },
                        colors = CheckboxDefaults.colors(checkedColor = accent),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_reminders_editor_completed), style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider()

                // Separate date and time pickers for deadline
                val deadlineCal = remember(deadlineAtMs) {
                    if (deadlineAtMs > 0L) Calendar.getInstance().apply { timeInMillis = deadlineAtMs } else null
                }
                val deadlineDateStr = if (deadlineCal != null) {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(deadlineAtMs))
                } else stringResource(R.string.reminder_no_date)
                val deadlineTimeStr = if (deadlineCal != null) {
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(deadlineAtMs))
                } else stringResource(R.string.reminder_no_time)

                val deadlineColor = if (deadlineAtMs > 0L && deadlineAtMs < System.currentTimeMillis())
                    MaterialTheme.colorScheme.error else accent

                // Deadline on two rows: Date / Time.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${stringResource(R.string.reminder_pick_date)}:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(text = deadlineDateStr, color = deadlineColor)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${stringResource(R.string.reminder_pick_time)}:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(text = deadlineTimeStr, color = deadlineColor)
                    }
                }

                // User-set do date (when to work on it), independent of the deadline.
                val hasRealDoDate = SavedItem.hasPlannedDate(doAtMs)
                val isSomeday = SavedItem.isSomeday(doAtMs)
                val doDateStr = when {
                    isSomeday -> stringResource(R.string.do_date_someday)
                    hasRealDoDate -> java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(doAtMs))
                    else -> stringResource(R.string.reminder_no_date)
                }
                val doTimeStr = if (hasRealDoDate) {
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(doAtMs))
                } else stringResource(R.string.reminder_no_time)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${stringResource(R.string.ui_reminders_editor_do_date)}:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showDoDatePicker = true }) {
                        Text(text = doDateStr, color = accent)
                    }
                    if (hasRealDoDate) {
                        TextButton(onClick = { showDoTimePicker = true }) {
                            Text(text = doTimeStr, color = accent)
                        }
                    }
                    // "Someday": intended eventually, no committed date. Toggles the sentinel.
                    // A FilterChip (not plain text) so the selected state reads clearly.
                    Spacer(Modifier.width(4.dp))
                    FilterChip(
                        selected = isSomeday,
                        onClick = { doAtMs = if (isSomeday) 0L else SavedItem.DO_AT_SOMEDAY },
                        label = { Text(stringResource(R.string.do_date_someday)) },
                        leadingIcon = if (isSomeday) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                    )
                    if (hasRealDoDate || isSomeday) {
                        IconButton(onClick = { doAtMs = 0L }) {
                            Icon(
                                painterResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.a11y_clear_do_date),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(stringResource(R.string.ui_reminders_editor_note), style = MaterialTheme.typography.titleMedium)

            // Note-like editor (no outlined box)
            Surface(
                tonalElevation = 0.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    decorationBox = { innerTextField ->
                        if (content.isBlank()) {
                            Text(
                                text = stringResource(R.string.ui_reminders_editor_note_placeholder),
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
                try {
                    val arr = JSONArray(initial.buttons)
                    buildList {
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            add(Triple(
                                obj.optString("buttonText", ""),
                                obj.optString("intent", ""),
                                obj.optString("type", "link"),
                            ))
                        }
                    }
                } catch (_: Exception) { emptyList() }
            }

            if (detailButtons.isNotEmpty()) {
                val detailClipboard = remember(context) { AndroidClipboardController(context) }
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    detailButtons.forEach { (buttonText, intent, type) ->
                        ReminderActionChip(buttonText = buttonText, intent = intent, type = type, context = context, clipboard = detailClipboard)
                    }
                }
            }

            // === Sub-tasks section (above action chips) ===
            if (subTasks.isNotEmpty() || isTask) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.subtask_section_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = onAddSavedSubItem) {
                        Icon(painterResource(R.drawable.add), contentDescription = stringResource(R.string.a11y_add_subtask), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.subtask_add), style = MaterialTheme.typography.labelMedium)
                    }
                }

                subTasks.forEach { st ->
                    SavedSubItemRow(
                        subTask = st,
                        onToggleCompleted = { checked -> onSavedSubItemToggle(st.savedSubItemId, checked) },
                        onClick = { onSavedSubItemClick(st) },
                        onEdit = { onSavedSubItemEdit(st) },
                        onDelete = { onSavedSubItemDelete(st) },
                        onExportGoogleTasks = { onSavedSubItemExportGoogleTasks(st) },
                        onExportGoogleCalendar = { onSavedSubItemExportGoogleCalendar(st) },
                        showActionButtons = true,
                    )
                }
            }

            // === Export to external apps (chips) ===
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Remind me (schedule a push reminder) — same flow as the outer cards.
                if (onCreateReminder != null) {
                    AssistChip(
                        onClick = onCreateReminder,
                        label = { Text(stringResource(R.string.ui_reminders_create_button), style = MaterialTheme.typography.labelSmall) },
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
                    label = { Text(stringResource(R.string.reminder_share), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.share), contentDescription = stringResource(R.string.a11y_share_reminder), tint = accent, modifier = Modifier.size(16.dp))
                    },
                )
            }

            // === Change history ===
            if (changes.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                org.muilab.notigpt.ui.reminder.component.ReminderChangeHistorySection(changes = changes)
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
                        text = stringResource(R.string.reminder_related_notifications, relatedKeys.size),
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
                                text = stringResource(R.string.reminder_related_notifications_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        relatedRecordsByKey.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.reminder_no_related_notifications),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        else -> {
                            relatedKeys.forEach { key ->
                                val recs = relatedRecordsByKey[key]?.sortedBy { it.time } ?: return@forEach

                                val unit = relatedUnitsByKey[key]

                                if (unit != null) {
                                    // Evidence records come from the link table; the rest of the
                                    // thread loads lazily via "show surrounding messages".
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
                                        contextLoaded = key in relatedNotificationsState.related.contextLoadedKeys,
                                        onLoadContext = onLoadSurroundingContext?.let { load -> { load(key) } },
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

    // Do-date pickers. Unset do dates default to 09:00 on the picked day (a work-start plan, not a 23:59 deadline).
    if (showDoDatePicker) {
        val doDatePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (SavedItem.hasPlannedDate(doAtMs)) doAtMs else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDoDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDate = doDatePickerState.selectedDateMillis
                        if (selectedDate != null) {
                            val hadTime = SavedItem.hasPlannedDate(doAtMs)
                            val existingCal = Calendar.getInstance().apply {
                                timeInMillis = if (hadTime) doAtMs else System.currentTimeMillis()
                            }
                            val newCal = Calendar.getInstance().apply {
                                timeInMillis = selectedDate
                                set(Calendar.HOUR_OF_DAY, if (hadTime) existingCal.get(Calendar.HOUR_OF_DAY) else 9)
                                set(Calendar.MINUTE, if (hadTime) existingCal.get(Calendar.MINUTE) else 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            doAtMs = newCal.timeInMillis
                            showDoDatePicker = false
                        }
                    }
                ) { Text(stringResource(R.string.ui_action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDoDatePicker = false }) { Text(stringResource(R.string.ui_action_cancel)) }
            }
        ) {
            DatePicker(state = doDatePickerState)
        }
    }

    if (showDoTimePicker) {
        LaunchedEffect(showDoTimePicker) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = if (SavedItem.hasPlannedDate(doAtMs)) doAtMs else System.currentTimeMillis()
            }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val c = Calendar.getInstance().apply {
                        timeInMillis = if (SavedItem.hasPlannedDate(doAtMs)) doAtMs else System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    doAtMs = c.timeInMillis
                    showDoTimePicker = false
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).apply {
                setOnCancelListener { showDoTimePicker = false }
            }.show()
        }
    }
}

/** Which external app the export dialog targets. */
enum class ExportType { GOOGLE_TASKS, GOOGLE_CALENDAR }

/**
 * Data holder for the export confirmation dialog – remembers the reminder being exported
 * and which target the user chose.
 */
private data class ExportDialogState(
    val reminder: SavedItem,
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
    val isCalendar = state.type == ExportType.GOOGLE_CALENDAR

    var title by remember { mutableStateOf(state.reminder.title) }
    var description by remember { mutableStateOf(state.reminder.content) }

    // For Google Tasks: deadline (0 = no deadline)
    var deadlineMs by remember { mutableStateOf(state.reminder.deadlineAtMs) }

    // For Google Calendar: start / end + full-day toggle
    val initialStart = if (state.reminder.startAtMs > 0L) state.reminder.startAtMs else 0L
    var startMs by remember { mutableStateOf(initialStart) }
    var endMs by remember {
        mutableStateOf(
            when {
                state.reminder.endAtMs > 0L -> state.reminder.endAtMs
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
        -1   -> context.getString(R.string.export_dialog_reminder_none)
        0    -> context.getString(R.string.export_dialog_reminder_at_time)
        60   -> context.getString(R.string.export_dialog_reminder_1_hour)
        120  -> context.getString(R.string.export_dialog_reminder_2_hours)
        1440 -> context.getString(R.string.export_dialog_reminder_1_day)
        else -> context.getString(R.string.export_dialog_reminder_minutes, minutes)
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
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val stf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

                    val startDateLabel = if (startMs > 0L) sdf.format(java.util.Date(startMs)) else stringResource(R.string.reminder_no_date)
                    val startAtMsLabel = if (startMs > 0L) stf.format(java.util.Date(startMs)) else stringResource(R.string.reminder_no_time)

                    Text(stringResource(R.string.export_dialog_field_start_time), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickingField = "start"; pickingMode = "date" }) {
                            Text("${stringResource(R.string.reminder_pick_date)}: $startDateLabel")
                        }
                        if (!isFullDay) {
                            TextButton(onClick = { pickingField = "start"; pickingMode = "time" }) {
                                Text("${stringResource(R.string.reminder_pick_time)}: $startAtMsLabel")
                            }
                        }
                    }

                    // End: separate date + time
                    val endDateLabel = if (endMs > 0L) sdf.format(java.util.Date(endMs)) else stringResource(R.string.reminder_no_date)
                    val endAtMsLabel = if (endMs > 0L) stf.format(java.util.Date(endMs)) else stringResource(R.string.reminder_no_time)

                    Text(stringResource(R.string.export_dialog_field_end_time), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickingField = "end"; pickingMode = "date" }) {
                            Text("${stringResource(R.string.reminder_pick_date)}: $endDateLabel")
                        }
                        if (!isFullDay) {
                            TextButton(onClick = { pickingField = "end"; pickingMode = "time" }) {
                                Text("${stringResource(R.string.reminder_pick_time)}: $endAtMsLabel")
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
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(deadlineMs))
                    } else stringResource(R.string.reminder_no_date)
                    val dlTimeLabel = if (deadlineMs > 0L) {
                        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(deadlineMs))
                    } else stringResource(R.string.reminder_no_time)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { pickingField = "deadline"; pickingMode = "date" }) {
                            Text("${stringResource(R.string.reminder_pick_date)}: $dlDateLabel")
                        }
                        TextButton(onClick = { pickingField = "deadline"; pickingMode = "time" }) {
                            Text("${stringResource(R.string.reminder_pick_time)}: $dlTimeLabel")
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
