package org.muilab.notigpt.ui.reminder.screen

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.preference.model.PreferenceEntryPoint
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.model.features.SubTask
import org.muilab.notigpt.data.export.asExportable
import org.muilab.notigpt.ui.preference.component.PreferenceLearningBottomSheet
import org.muilab.notigpt.ui.notification.component.RelatedNotificationPreview
import org.muilab.notigpt.ui.reminder.component.SubTaskRow
import org.muilab.notigpt.ui.reminder.component.SubTaskListInCard
import org.muilab.notigpt.ui.reminder.component.SubTaskDetailScreen
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModelFactory
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel
import org.muilab.notigpt.ui.reminder.viewmodel.ReminderViewModel
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    preferenceViewModel: PreferenceViewModel? = null,
) {
    val vm: ReminderViewModel = reminderViewModel ?: viewModel()
    val prefVm: PreferenceViewModel = preferenceViewModel ?: viewModel()

    // Drawer VM is needed to reuse the same notification/app launching logic as NotiRecordContextCard.
    val context = LocalContext.current
    val strGoogleTasksNotSignedIn = stringResource(R.string.google_tasks_not_signed_in)
    val strGoogleTasksSuccess = stringResource(R.string.google_tasks_success)
    val strGoogleTasksErrorFmt = stringResource(R.string.google_tasks_error, "%s")
    val strGoogleCalendarNoApp = stringResource(R.string.google_calendar_no_app)

    val reminders by vm.reminders.collectAsState()
    val filter by vm.filter.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()

    // Local mutable copy for live drag reordering visual feedback
    val localReminders = remember { androidx.compose.runtime.mutableStateListOf<ReminderUnit>() }
    LaunchedEffect(reminders) {
        // Only sync when not mid-drag (list size or content changed from DB)
        localReminders.clear()
        localReminders.addAll(reminders)
    }

    var editing by remember { mutableStateOf<ReminderUnit?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingInitialSnapshot by remember { mutableStateOf<ReminderUnit?>(null) }

    // Sub-task editing (overlaid on top of reminder detail)
    var editingSubTask by remember { mutableStateOf<SubTask?>(null) }
    var editingSubTaskInitial by remember { mutableStateOf<SubTask?>(null) }

    // Bulk sub-task observation (one DB query for all reminders)
    val allSubTasksByReminder by vm.allSubTasksByReminder.collectAsState()

    // Long-press feedback dialog
    var feedbackDialogReminder by remember { mutableStateOf<ReminderUnit?>(null) }

    // Regenerate-all confirmation dialog
    var showRegenerateAllDialog by remember { mutableStateOf(false) }

    // ===== Google Tasks integration =====
    val googleTasksExportResult by vm.googleTasksExportResult.collectAsState()
    val relatedNotificationsState by vm.relatedNotificationsState.collectAsState()

    // Reminder pending export after sign-in completes.
    var pendingGoogleTasksReminder by remember { mutableStateOf<ReminderUnit?>(null) }

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
                android.widget.Toast.makeText(
                    context,
                    strGoogleTasksSuccess,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                vm.clearGoogleTasksExportResult()
            }
            is ReminderViewModel.GoogleTasksExportResult.Error -> {
                android.widget.Toast.makeText(
                    context,
                    strGoogleTasksErrorFmt.replace("%s", r.message ?: ""),
                    android.widget.Toast.LENGTH_LONG
                ).show()
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
    val openExportDialog: (ReminderUnit, ExportType) -> Unit = { reminder, type ->
        exportDialogState = ExportDialogState(reminder, type)
    }

    val handleGoogleTasksExport: (ReminderUnit) -> Unit = { reminder ->
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
        if (reminders.firstOrNull()?.reminderId == targetId) {
            listState.animateScrollToItem(0)
        }
        pendingScrollToTopId = null
    }

    // Trigger B (v2): when a reminder card is fully visible in the list viewport, consider it "viewed".
    // We only apply this to generated reminders (has associated notifications), and only once per reminderId.
    // isViewed is NOT written to DB immediately — it is deferred until the user leaves this screen
    // (tab switch / composable disposal) or the app goes to background (ON_PAUSE), to avoid
    // re-sorting glitches while the user is still looking at the list.
    var viewedReminderIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Keep a ref to the latest set so DisposableEffect / lifecycle callbacks can read it.
    val latestViewedIds by rememberUpdatedState(viewedReminderIds)

    // Flush pending viewed IDs to DB.
    val flushViewed: () -> Unit = remember(vm) {
        { vm.markViewedBatch(latestViewedIds) }
    }

    // Flush when the composable leaves composition (tab switch, settings opened, etc.)
    DisposableEffect(Unit) {
        onDispose { flushViewed() }
    }

    // Flush when the app goes to background
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                flushViewed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(listState, reminders) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val viewportStart = layout.viewportStartOffset
            val viewportEnd = layout.viewportEndOffset

            layout.visibleItemsInfo
                .filter { info ->
                    info.offset >= viewportStart && (info.offset + info.size) <= viewportEnd
                }
                .mapNotNull { info ->
                    val idx = info.index
                    localReminders.getOrNull(idx)?.reminderId
                }
        }
            .distinctUntilChanged()
            .collect { fullyVisibleIds ->
                val newlyViewed = fullyVisibleIds.filterNot { it in viewedReminderIds }
                if (newlyViewed.isEmpty()) return@collect

                viewedReminderIds = viewedReminderIds + newlyViewed

            }
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
                FilterChip(stringResource(R.string.ui_reminders_filter_all), filter == ReminderViewModel.FilterTab.All) { vm.setFilter(ReminderViewModel.FilterTab.All) }
                FilterChip(stringResource(R.string.ui_reminders_filter_tasks), filter == ReminderViewModel.FilterTab.Tasks) { vm.setFilter(ReminderViewModel.FilterTab.Tasks) }
                FilterChip(stringResource(R.string.ui_reminders_filter_memos), filter == ReminderViewModel.FilterTab.Memos) { vm.setFilter(ReminderViewModel.FilterTab.Memos) }
                FilterChip(stringResource(R.string.ui_reminders_filter_completed), filter == ReminderViewModel.FilterTab.Completed) { vm.setFilter(ReminderViewModel.FilterTab.Completed) }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = { showRegenerateAllDialog = true }) {
                    Icon(
                        painter = painterResource(R.drawable.refresh),
                        contentDescription = stringResource(R.string.a11y_refresh_all),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Search bar
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.updateSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.ui_reminders_search_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 100),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { vm.updateSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.a11y_close_search),
                            )
                        }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                ),
                singleLine = true,
            )

            val reorderableState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
                val fromReminder = localReminders.getOrNull(from.index)
                val toReminder = localReminders.getOrNull(to.index)
                // Only allow drag within scored section (isViewed && !isPinned)
                if (fromReminder != null && toReminder != null &&
                    fromReminder.isViewed && !fromReminder.isPinned &&
                    toReminder.isViewed && !toReminder.isPinned
                ) {
                    // Move for live visual feedback
                    localReminders.add(to.index, localReminders.removeAt(from.index))
                    // Compute new score from neighbours after move
                    val toIdx = localReminders.indexOfFirst { it.reminderId == fromReminder.reminderId }
                    val scoreAbove = localReminders.getOrNull(toIdx - 1)?.sortScore
                    val scoreBelow = localReminders.getOrNull(toIdx + 1)?.sortScore
                    vm.onDragDrop(fromReminder.reminderId, scoreAbove, scoreBelow)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
            ) {
                items(localReminders, key = { it.reminderId }) { reminder ->
                    val isDraggable = reminder.isViewed && !reminder.isPinned
                    ReorderableItem(reorderableState, key = reminder.reminderId) {
                        ReminderCard(
                            reminder = reminder,
                            subTasks = allSubTasksByReminder[reminder.reminderId] ?: emptyList(),
                            onDelete = {
                                vm.delete(reminder.reminderId)
                                if (reminder.origin.contains("llm")) {
                                    prefVm.startFlow(
                                        entryPoint = PreferenceEntryPoint.DELETE,
                                        reminder = reminder,
                                    )
                                }
                            },
                            onToggleCompleted = { completed: Boolean -> vm.toggleCompleted(reminder, completed) },
                            onEdit = {
                                editing = reminder
                                editingId = reminder.reminderId
                                editingInitialSnapshot = reminder
                            },
                            onLongPress = { feedbackDialogReminder = reminder },
                            onTogglePinned = { vm.togglePinned(reminder.reminderId) },
                            onQuickExportTasks = if (reminder.isTask) {
                                { openExportDialog(reminder, ExportType.GOOGLE_TASKS) }
                            } else null,
                            onQuickExportCalendar = if (reminder.isEvent) {
                                { openExportDialog(reminder, ExportType.GOOGLE_CALENDAR) }
                            } else null,
                            showDragHandle = isDraggable,
                            dragHandleModifier = if (isDraggable) Modifier.draggableHandle() else Modifier,
                            onSubTaskToggle = { stId, checked -> vm.toggleSubTaskCompleted(stId, checked) },
                            onSubTaskClick = { st ->
                                // Open parent reminder detail first, then navigate to sub-task detail
                                editing = reminder
                                editingId = reminder.reminderId
                                editingInitialSnapshot = reminder
                                editingSubTask = st
                                editingSubTaskInitial = st
                            },
                            onSubTaskEdit = { st ->
                                editing = reminder
                                editingId = reminder.reminderId
                                editingInitialSnapshot = reminder
                                editingSubTask = st
                                editingSubTaskInitial = st
                            },
                            onSubTaskDelete = { st -> vm.deleteSubTask(st.subTaskId) },
                            onSubTaskExportGoogleTasks = { st -> vm.exportToGoogleTasks(st.asExportable()) },
                            onSubTaskExportGoogleCalendar = { st ->
                                val calIntent = Intent(Intent.ACTION_INSERT).apply {
                                    data = CalendarContract.Events.CONTENT_URI
                                    putExtra(CalendarContract.Events.TITLE, st.title)
                                    putExtra(CalendarContract.Events.DESCRIPTION, st.description)
                                    if (st.startTime > 0L) putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, st.startTime)
                                    if (st.endTime > 0L) putExtra(CalendarContract.EXTRA_EVENT_END_TIME, st.endTime)
                                }
                                try { context.startActivity(calIntent) } catch (_: Exception) {}
                            },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onClick = {
                val empty = ReminderUnit(
                    reminderId = "manual_${java.util.UUID.randomUUID()}",
                    reminderTitle = "",
                    reminderContent = "",
                    // Let users decide task vs memo in the editor.
                    isTask = false,
                    isCompleted = false,
                    lastUpdateTimestamp = System.currentTimeMillis(),
                    deadlineTimestamp = 0L,
                    estimatedCompletionTime = 0L,
                    associatedNotiRecords = emptySet(),
                    extractionSnapshotId = null,
                    origin = "manual",
                    humanEditCount = 0,
                    deletedAtMs = null,
                    userEdited = true,
                )
                editing = empty
                editingId = empty.reminderId
                editingInitialSnapshot = empty
            }
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.a11y_add))
        }

        // EDITOR OVERLAY
        editing?.let { current ->
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
                    onBack = { updatedOrNull: ReminderUnit? ->
                        val base = editingInitialSnapshot
                        val isNew = base != null && base.reminderTitle.isBlank() && base.reminderContent.isBlank() && base.userEdited

                        val contentChanged = base != null && updatedOrNull != null && (
                            base.reminderTitle != updatedOrNull.reminderTitle ||
                                base.reminderContent != updatedOrNull.reminderContent
                        )

                        val changed = base != null && updatedOrNull != null && (
                            base.reminderTitle != updatedOrNull.reminderTitle ||
                                base.reminderContent != updatedOrNull.reminderContent ||
                                base.isTask != updatedOrNull.isTask ||
                                base.isCompleted != updatedOrNull.isCompleted ||
                                base.deadlineTimestamp != updatedOrNull.deadlineTimestamp ||
                                base.estimatedCompletionTime != updatedOrNull.estimatedCompletionTime
                        )

                        if (updatedOrNull != null) {
                            val emptyNow = updatedOrNull.reminderTitle.isBlank() && updatedOrNull.reminderContent.isBlank()
                            when {
                                emptyNow -> {
                                    // For brand-new manual reminders, just discard. For existing reminders, delete.
                                    if (!isNew) vm.delete(updatedOrNull.reminderId)
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
                    onSave = { updated: ReminderUnit ->
                        val base = editingInitialSnapshot
                        val isNew = base != null && base.reminderTitle.isBlank() && base.reminderContent.isBlank() && base.userEdited

                        val contentChanged = base != null && (
                            base.reminderTitle != updated.reminderTitle ||
                                base.reminderContent != updated.reminderContent
                        )

                        val changed = base != null && (
                            base.reminderTitle != updated.reminderTitle ||
                                base.reminderContent != updated.reminderContent ||
                                base.isTask != updated.isTask ||
                                base.isCompleted != updated.isCompleted ||
                                base.deadlineTimestamp != updated.deadlineTimestamp ||
                                base.estimatedCompletionTime != updated.estimatedCompletionTime
                        )

                        val emptyNow = updated.reminderTitle.isBlank() && updated.reminderContent.isBlank()
                        when {
                            emptyNow -> {
                                if (!isNew) vm.delete(updated.reminderId)
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
                    onRegenerate = { vm.regenerateOne(current.reminderId) },
                    relatedNotificationsState = relatedNotificationsState,
                    onLoadRelatedNotifications = { reminder -> vm.loadRelatedNotifications(reminder) },
                    // Sub-task parameters
                    subTasks = allSubTasksByReminder[current.reminderId] ?: emptyList(),
                    onAddSubTask = { vm.addSubTask(current.reminderId) },
                    onSubTaskToggle = { stId, checked -> vm.toggleSubTaskCompleted(stId, checked) },
                    onSubTaskClick = { st ->
                        editingSubTask = st
                        editingSubTaskInitial = st
                    },
                    onSubTaskEdit = { st ->
                        editingSubTask = st
                        editingSubTaskInitial = st
                    },
                    onSubTaskDelete = { st -> vm.deleteSubTask(st.subTaskId) },
                    onSubTaskExportGoogleTasks = { st -> vm.exportToGoogleTasks(st.asExportable()) },
                    onSubTaskExportGoogleCalendar = { st ->
                        val calIntent = Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, st.title)
                            putExtra(CalendarContract.Events.DESCRIPTION, st.description)
                            if (st.startTime > 0L) putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, st.startTime)
                            if (st.endTime > 0L) putExtra(CalendarContract.EXTRA_EVENT_END_TIME, st.endTime)
                        }
                        try { context.startActivity(calIntent) } catch (_: Exception) {}
                    },
                )

                // Sub-task detail overlay (on top of reminder detail)
                editingSubTask?.let { stCurrent ->
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
                        SubTaskDetailScreen(
                            initial = stCurrent,
                            onBack = { updatedOrNull ->
                                if (updatedOrNull != null) {
                                    val base = editingSubTaskInitial
                                    val changed = base != null && (
                                        base.title != updatedOrNull.title ||
                                            base.description != updatedOrNull.description ||
                                            base.isTask != updatedOrNull.isTask ||
                                            base.isEvent != updatedOrNull.isEvent ||
                                            base.isCompleted != updatedOrNull.isCompleted ||
                                            base.deadlineTimestamp != updatedOrNull.deadlineTimestamp ||
                                            base.startTime != updatedOrNull.startTime ||
                                            base.endTime != updatedOrNull.endTime
                                    )
                                    if (changed) {
                                        vm.upsertSubTask(updatedOrNull)
                                    }
                                }
                                editingSubTask = null
                                editingSubTaskInitial = null
                            },
                            onDelete = { stId ->
                                vm.deleteSubTask(stId)
                                editingSubTask = null
                                editingSubTaskInitial = null
                            },
                            onSave = { updated ->
                                vm.upsertSubTask(updated)
                                editingSubTask = null
                                editingSubTaskInitial = null
                            },
                            onExportGoogleTasks = { st -> vm.exportToGoogleTasks(st.asExportable()) },
                            onExportGoogleCalendar = { st ->
                                val calIntent = Intent(Intent.ACTION_INSERT).apply {
                                    data = CalendarContract.Events.CONTENT_URI
                                    putExtra(CalendarContract.Events.TITLE, st.title)
                                    putExtra(CalendarContract.Events.DESCRIPTION, st.description)
                                    if (st.startTime > 0L) putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, st.startTime)
                                    if (st.endTime > 0L) putExtra(CalendarContract.EXTRA_EVENT_END_TIME, st.endTime)
                                }
                                try { context.startActivity(calIntent) } catch (_: Exception) {}
                            },
                        )
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
                    reminderTitle = title,
                    reminderContent = description,
                    deadlineTimestamp = deadlineMs,
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
                    android.widget.Toast.makeText(
                        context,
                        strGoogleCalendarNoApp,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
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
                            vm.submitFeedback(reminder.reminderId, "USER_FEEDBACK_IMPORTANT")
                            feedbackDialogReminder = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.reminder_feedback_important))
                    }
                    OutlinedButton(
                        onClick = {
                            vm.submitFeedback(reminder.reminderId, "USER_FEEDBACK_HANDLE_LATER")
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

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderCard(
    reminder: ReminderUnit,
    subTasks: List<SubTask> = emptyList(),
    onDelete: () -> Unit,
    onToggleCompleted: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onQuickExportTasks: (() -> Unit)? = null,
    onQuickExportCalendar: (() -> Unit)? = null,
    onLongPress: () -> Unit = {},
    onTogglePinned: () -> Unit = {},
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    // Sub-task callbacks
    onSubTaskToggle: (String, Boolean) -> Unit = { _, _ -> },
    onSubTaskClick: (SubTask) -> Unit = {},
    onSubTaskEdit: (SubTask) -> Unit = {},
    onSubTaskDelete: (SubTask) -> Unit = {},
    onSubTaskExportGoogleTasks: (SubTask) -> Unit = {},
    onSubTaskExportGoogleCalendar: (SubTask) -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = remember(context) { AndroidClipboardController(context) }

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (!reminder.isViewed)
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.small,
                    )
                else Modifier
            )
            .then(if (!reminder.isViewed) Modifier.padding(2.dp) else Modifier)
            .combinedClickable(onClick = onEdit, onLongClick = onLongPress)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left gutter: checkbox (if task) on top, drag handle below — vertically stacked
            if (reminder.isTask || showDragHandle) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    if (reminder.isTask) {
                        Checkbox(
                            checked = reminder.isCompleted,
                            onCheckedChange = { onToggleCompleted(it) },
                        )
                    }
                    if (showDragHandle) {
                        Icon(
                            painter = painterResource(R.drawable.drag_indicator),
                            contentDescription = stringResource(R.string.a11y_drag_handle),
                            modifier = Modifier
                                .size(32.dp)
                                .then(dragHandleModifier)
                                .padding(6.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Right content column
            Column(modifier = Modifier.weight(1f)) {
                // Title row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val titleStyle = if (reminder.isTask && reminder.isCompleted) {
                        MaterialTheme.typography.titleMedium.copy(textDecoration = TextDecoration.LineThrough)
                    } else MaterialTheme.typography.titleMedium

                    Text(
                        text = reminder.reminderTitle.ifBlank {
                            if (reminder.isTask) stringResource(R.string.ui_reminders_untitled_task) else stringResource(R.string.ui_reminders_untitled_memo)
                        },
                        style = titleStyle,
                        modifier = Modifier.weight(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        if (onQuickExportTasks != null) {
                            IconButton(onClick = onQuickExportTasks, modifier = Modifier.size(36.dp)) {
                                Icon(painter = painterResource(R.drawable.task_add), contentDescription = stringResource(R.string.a11y_quick_export_tasks), modifier = Modifier.size(20.dp))
                            }
                        }
                        if (onQuickExportCalendar != null) {
                            IconButton(onClick = onQuickExportCalendar, modifier = Modifier.size(36.dp)) {
                                Icon(painter = painterResource(R.drawable.calendar_add), contentDescription = stringResource(R.string.a11y_quick_export_calendar), modifier = Modifier.size(20.dp))
                            }
                        }
                        IconButton(onClick = onTogglePinned, modifier = Modifier.size(36.dp)) {
                            Icon(
                                painter = painterResource(if (reminder.isPinned) R.drawable.pin_yes else R.drawable.pin_no),
                                contentDescription = stringResource(if (reminder.isPinned) R.string.a11y_unpin else R.string.a11y_pin),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.a11y_delete))
                    }
                }

                // Deadline (no ECT)
                if (reminder.isTask) {
                    val deadline = reminder.deadlineTimestamp
                    val deadlineStr = if (deadline > 0L) {
                        "${getAbsoluteTimeStr(deadline, context)} (${getRelativeTimeStr(deadline, context)})"
                    } else stringResource(R.string.ui_reminders_no_deadline)
                    Text(
                        text = deadlineStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (deadline > 0L && deadline < System.currentTimeMillis()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // Content preview
                val contentPreview = reminder.reminderContent.lineSequence().take(2).joinToString("\n")
                if (contentPreview.isNotBlank()) {
                    Text(text = contentPreview, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
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
                    SubTaskListInCard(
                        subTasks = subTasks,
                        onToggleCompleted = onSubTaskToggle,
                        onSubTaskClick = onSubTaskClick,
                        onSubTaskEdit = onSubTaskEdit,
                        onSubTaskDelete = onSubTaskDelete,
                        onSubTaskExportGoogleTasks = onSubTaskExportGoogleTasks,
                        onSubTaskExportGoogleCalendar = onSubTaskExportGoogleCalendar,
                    )
                }
            }
        }
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
private fun ReminderDetailScreen(
    initial: ReminderUnit,
    drawerViewModel: DrawerViewModel,
    onBack: (ReminderUnit?) -> Unit,
    onDelete: (String) -> Unit,
    onSave: (ReminderUnit) -> Unit,
    onExportToGoogleTasks: (ReminderUnit) -> Unit = {},
    isGoogleTasksExporting: Boolean = false,
    onOpenExportDialog: (ReminderUnit, ExportType) -> Unit = { _, _ -> },
    onRegenerate: () -> Unit = {},
    relatedNotificationsState: ReminderViewModel.RelatedNotificationsState = ReminderViewModel.RelatedNotificationsState(),
    onLoadRelatedNotifications: (ReminderUnit) -> Unit = {},
    // Sub-task parameters
    subTasks: List<SubTask> = emptyList(),
    onAddSubTask: () -> Unit = {},
    onSubTaskToggle: (String, Boolean) -> Unit = { _, _ -> },
    onSubTaskClick: (SubTask) -> Unit = {},
    onSubTaskEdit: (SubTask) -> Unit = {},
    onSubTaskDelete: (SubTask) -> Unit = {},
    onSubTaskExportGoogleTasks: (SubTask) -> Unit = {},
    onSubTaskExportGoogleCalendar: (SubTask) -> Unit = {},
) {
    val context = LocalContext.current

    // Trigger B is now handled in RemindersScreen based on 'fully visible' reminder cards.

    var title by remember(initial.reminderId) { mutableStateOf(initial.reminderTitle) }
    var content by remember(initial.reminderId) { mutableStateOf(initial.reminderContent) }
    var isTask by remember(initial.reminderId) { mutableStateOf(initial.isTask) }
    var isCompleted by remember(initial.reminderId) { mutableStateOf(initial.isCompleted) }
    var deadlineTimestamp by remember(initial.reminderId) { mutableStateOf(initial.deadlineTimestamp) }
    var ectMinutes by remember(initial.reminderId) { mutableStateOf(initial.estimatedCompletionTime) }

    fun buildUpdated(): ReminderUnit {
        return initial.copy(
            reminderTitle = title,
            reminderContent = content,
            isTask = isTask,
            isCompleted = if (isTask) isCompleted else false,
            deadlineTimestamp = if (isTask) deadlineTimestamp else 0L,
            estimatedCompletionTime = if (isTask) ectMinutes else 0L,
        )
    }

    // Handle system back (gesture / nav button) like in-app navigation.
    BackHandler(enabled = true) {
        onBack(buildUpdated())
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
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
            },
            navigationIcon = {
                IconButton(onClick = { onBack(buildUpdated()) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back))
                }
            },
            actions = {
                IconButton(onClick = onRegenerate) {
                    Icon(painter = painterResource(R.drawable.refresh), contentDescription = stringResource(R.string.a11y_regenerate), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onDelete(initial.reminderId) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.a11y_delete))
                }
                TextButton(onClick = { onSave(buildUpdated()) }) {
                    Icon(painter = painterResource(R.drawable.save), contentDescription = stringResource(R.string.ui_action_save))
                }
            }
        )

        // Make the content scrollable so related notifications are reachable.
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Task toggle row
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_reminders_editor_task_label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = isTask, onCheckedChange = { isTask = it })
            }

            if (isTask) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = isCompleted, onCheckedChange = { isCompleted = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_reminders_editor_completed), style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider()

                // Separate date and time pickers for deadline
                val deadlineCal = remember(deadlineTimestamp) {
                    if (deadlineTimestamp > 0L) Calendar.getInstance().apply { timeInMillis = deadlineTimestamp } else null
                }
                val deadlineDateStr = if (deadlineCal != null) {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(deadlineTimestamp))
                } else stringResource(R.string.reminder_no_date)
                val deadlineTimeStr = if (deadlineCal != null) {
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(deadlineTimestamp))
                } else stringResource(R.string.reminder_no_time)

                val deadlineColor = if (deadlineTimestamp > 0L && deadlineTimestamp < System.currentTimeMillis())
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(text = "${stringResource(R.string.reminder_pick_date)}: $deadlineDateStr", color = deadlineColor)
                    }
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(text = "${stringResource(R.string.reminder_pick_time)}: $deadlineTimeStr", color = deadlineColor)
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

            // === Export to external apps (chips) ===
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Google Tasks chip
                AssistChip(
                    onClick = { onOpenExportDialog(buildUpdated(), ExportType.GOOGLE_TASKS) },
                    label = { Text(stringResource(R.string.google_tasks_export), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.task_add), contentDescription = stringResource(R.string.a11y_export_google_tasks), modifier = Modifier.size(16.dp))
                    },
                )
                // Google Calendar chip
                AssistChip(
                    onClick = { onOpenExportDialog(buildUpdated(), ExportType.GOOGLE_CALENDAR) },
                    label = { Text(stringResource(R.string.google_calendar_export), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.calendar_add), contentDescription = stringResource(R.string.a11y_export_google_calendar), modifier = Modifier.size(16.dp))
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
                        Icon(painter = painterResource(R.drawable.share), contentDescription = stringResource(R.string.a11y_share_reminder), modifier = Modifier.size(16.dp))
                    },
                )
            }

            // === Sub-tasks section ===
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
                    TextButton(onClick = onAddSubTask) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.a11y_add_subtask), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.subtask_add), style = MaterialTheme.typography.labelMedium)
                    }
                }

                subTasks.forEach { st ->
                    SubTaskRow(
                        subTask = st,
                        onToggleCompleted = { checked -> onSubTaskToggle(st.subTaskId, checked) },
                        onClick = { onSubTaskClick(st) },
                        onEdit = { onSubTaskEdit(st) },
                        onDelete = { onSubTaskDelete(st) },
                        onExportGoogleTasks = { onSubTaskExportGoogleTasks(st) },
                        onExportGoogleCalendar = { onSubTaskExportGoogleCalendar(st) },
                    )
                }
            }

            // === Related notifications ===
            var relatedExpanded by remember(initial.reminderId) { mutableStateOf(false) }
            val relatedForThisReminder = relatedNotificationsState.reminderId == initial.reminderId
            val relatedLoading = relatedForThisReminder && relatedNotificationsState.isLoading
            val relatedRecordsByKey = if (relatedForThisReminder) relatedNotificationsState.related.recordsByKey else emptyMap()
            val relatedUnitsByKey = if (relatedForThisReminder) relatedNotificationsState.related.unitsByKey else emptyMap()

            LaunchedEffect(initial.reminderId, initial.extractionSnapshotId, initial.associatedNotiRecords) {
                onLoadRelatedNotifications(initial)
            }

            // Show the section as long as this reminder claims it has associated notifications.
            if (initial.associatedNotiRecords.isNotEmpty()) {
                val relatedKeys = initial.associatedNotiKeys.toList()

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
                        imageVector = if (relatedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
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
                                    // IMPORTANT: show ALL records that were deemed related by the snapshot.
                                    val displayUnit = org.muilab.notigpt.model.notifications.NotiDisplayUnit(unit, recs)
                                    RelatedNotificationPreview(
                                        notiDisplayUnit = displayUnit,
                                        showOpenButton = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                        onOpen = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                drawerViewModel.accessNotificationByKey(key)
                                            }
                                        },
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

    // Separate date picker (only modifies the date component of deadlineTimestamp)
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (deadlineTimestamp > 0L) deadlineTimestamp else System.currentTimeMillis()
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
                                timeInMillis = if (deadlineTimestamp > 0L) deadlineTimestamp else System.currentTimeMillis()
                            }
                            val newCal = Calendar.getInstance().apply {
                                timeInMillis = selectedDate
                                set(Calendar.HOUR_OF_DAY, existingCal.get(Calendar.HOUR_OF_DAY))
                                set(Calendar.MINUTE, existingCal.get(Calendar.MINUTE))
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            deadlineTimestamp = newCal.timeInMillis
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

    // Separate time picker (only modifies the time component of deadlineTimestamp)
    if (showTimePicker) {
        LaunchedEffect(showTimePicker) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = if (deadlineTimestamp > 0L) deadlineTimestamp else System.currentTimeMillis()
            }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val c = Calendar.getInstance().apply {
                        timeInMillis = if (deadlineTimestamp > 0L) deadlineTimestamp else System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    deadlineTimestamp = c.timeInMillis
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
private enum class ExportType { GOOGLE_TASKS, GOOGLE_CALENDAR }

/**
 * Data holder for the export confirmation dialog – remembers the reminder being exported
 * and which target the user chose.
 */
private data class ExportDialogState(
    val reminder: ReminderUnit,
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

    var title by remember { mutableStateOf(state.reminder.reminderTitle) }
    var description by remember { mutableStateOf(state.reminder.reminderContent) }

    // For Google Tasks: deadline (0 = no deadline)
    var deadlineMs by remember { mutableStateOf(state.reminder.deadlineTimestamp) }

    // For Google Calendar: start / end + full-day toggle
    val initialStart = if (state.reminder.startTime > 0L) state.reminder.startTime else 0L
    var startMs by remember { mutableStateOf(initialStart) }
    var endMs by remember {
        mutableStateOf(
            when {
                state.reminder.endTime > 0L -> state.reminder.endTime
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
                    val startTimeLabel = if (startMs > 0L) stf.format(java.util.Date(startMs)) else stringResource(R.string.reminder_no_time)

                    Text(stringResource(R.string.export_dialog_field_start_time), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickingField = "start"; pickingMode = "date" }) {
                            Text("${stringResource(R.string.reminder_pick_date)}: $startDateLabel")
                        }
                        if (!isFullDay) {
                            TextButton(onClick = { pickingField = "start"; pickingMode = "time" }) {
                                Text("${stringResource(R.string.reminder_pick_time)}: $startTimeLabel")
                            }
                        }
                    }

                    // End: separate date + time
                    val endDateLabel = if (endMs > 0L) sdf.format(java.util.Date(endMs)) else stringResource(R.string.reminder_no_date)
                    val endTimeLabel = if (endMs > 0L) stf.format(java.util.Date(endMs)) else stringResource(R.string.reminder_no_time)

                    Text(stringResource(R.string.export_dialog_field_end_time), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickingField = "end"; pickingMode = "date" }) {
                            Text("${stringResource(R.string.reminder_pick_date)}: $endDateLabel")
                        }
                        if (!isFullDay) {
                            TextButton(onClick = { pickingField = "end"; pickingMode = "time" }) {
                                Text("${stringResource(R.string.reminder_pick_time)}: $endTimeLabel")
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
