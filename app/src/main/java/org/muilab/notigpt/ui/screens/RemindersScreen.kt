package org.muilab.notigpt.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.repository.EsmRepository
import org.muilab.notigpt.repository.NotiRepositoryProvider
import org.muilab.notigpt.ui.screens.esm.EsmNotiCardLikePreview
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.viewmodel.DrawerViewModelFactory
import org.muilab.notigpt.ui.viewmodel.ReminderViewModel
import org.muilab.notigpt.util.getAbsoluteTimeStr
import org.muilab.notigpt.util.getRelativeTimeStr
import java.util.Calendar
import org.muilab.notigpt.platform.AndroidClipboardController
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.snapshotFlow
import androidx.constraintlayout.helper.widget.Grid
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.CircularProgressIndicator

@Composable
fun RemindersScreen(
    drawerViewModel: DrawerViewModel,
    reminderViewModel: ReminderViewModel? = null,
) {
    val vm: ReminderViewModel = reminderViewModel ?: viewModel()

    // Drawer VM is needed to reuse the same notification/app launching logic as NotiRecordContextCard.
    val context = LocalContext.current

    val reminders by vm.reminders.collectAsState()
    val filter by vm.filter.collectAsState()

    var editing by remember { mutableStateOf<ReminderUnit?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingInitialSnapshot by remember { mutableStateOf<ReminderUnit?>(null) }

    // ===== Google Tasks integration =====
    val googleTasksExportResult by vm.googleTasksExportResult.collectAsState()

    // Reminder pending export after sign-in completes.
    var pendingGoogleTasksReminder by remember { mutableStateOf<ReminderUnit?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        CoroutineScope(Dispatchers.IO).launch {
            val account = org.muilab.notigpt.platform.GoogleTasksAuthManager.handleSignInResult(result.data)
            withContext(Dispatchers.Main) {
                if (account != null) {
                    // Sign-in succeeded – export the pending reminder if any
                    pendingGoogleTasksReminder?.let { reminder ->
                        vm.exportToGoogleTasks(reminder)
                    }
                } else {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.google_tasks_not_signed_in),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                pendingGoogleTasksReminder = null
            }
        }
    }

    // Show Toast when Google Tasks export result changes.
    LaunchedEffect(googleTasksExportResult) {
        when (val r = googleTasksExportResult) {
            is ReminderViewModel.GoogleTasksExportResult.Success -> {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.google_tasks_success),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                vm.clearGoogleTasksExportResult()
            }
            is ReminderViewModel.GoogleTasksExportResult.Error -> {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.google_tasks_error, r.message),
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
    // Reuse the same Context we already grabbed above.
    var viewedReminderIds by remember { mutableStateOf<Set<String>>(emptySet()) }

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
                    reminders.getOrNull(idx)?.reminderId
                }
        }
            .distinctUntilChanged()
            .collect { fullyVisibleIds ->
                val newlyViewed = fullyVisibleIds.filterNot { it in viewedReminderIds }
                if (newlyViewed.isEmpty()) return@collect

                viewedReminderIds = viewedReminderIds + newlyViewed

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repo = EsmRepository(context.applicationContext)
                        newlyViewed.forEach { rid ->
                            val reminder = reminders.firstOrNull { it.reminderId == rid } ?: return@forEach
                            if (reminder.associatedNotis.isEmpty()) return@forEach

                            // Trigger B: schedule delivery using the A/B timing rule.
                            val requestedDelay = repo.computeTriggerAbRequestedDelayMs(
                                org.muilab.notigpt.domain.esm.EsmConfig.TRIGGER_B_AVAILABLE_DELAY_MS,
                            )
                            repo.promoteToTriggerBAndReschedule(
                                reminderId = rid,
                                requestedDelayMs = requestedDelay,
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(stringResource(R.string.ui_reminders_filter_all), filter == ReminderViewModel.FilterTab.All) { vm.setFilter(ReminderViewModel.FilterTab.All) }
                FilterChip(stringResource(R.string.ui_reminders_filter_tasks), filter == ReminderViewModel.FilterTab.Tasks) { vm.setFilter(ReminderViewModel.FilterTab.Tasks) }
                FilterChip(stringResource(R.string.ui_reminders_filter_memos), filter == ReminderViewModel.FilterTab.Memos) { vm.setFilter(ReminderViewModel.FilterTab.Memos) }
                FilterChip(stringResource(R.string.ui_reminders_filter_completed), filter == ReminderViewModel.FilterTab.Completed) { vm.setFilter(ReminderViewModel.FilterTab.Completed) }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
            ) {
                items(reminders, key = { it.reminderId }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onDelete = { vm.delete(reminder.reminderId) },
                        onToggleCompleted = { completed: Boolean -> vm.toggleCompleted(reminder, completed) },
                        onEdit = {
                            editing = reminder
                            editingId = reminder.reminderId
                            editingInitialSnapshot = reminder
                        },
                    )
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
                    associatedNotis = emptySet(),
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
                        vm.delete(id)
                        editing = null
                        editingId = null
                        editingInitialSnapshot = null
                        pendingScrollToTopId = null
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
                            val signInIntent = org.muilab.notigpt.platform.GoogleTasksAuthManager.getSignInIntent(context)
                            googleSignInLauncher.launch(signInIntent)
                        }
                    },
                    isGoogleTasksExporting = googleTasksExportResult is ReminderViewModel.GoogleTasksExportResult.Loading,
                )
            }
        }
    }
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

@Composable
private fun ReminderCard(
    reminder: ReminderUnit,
    onDelete: () -> Unit,
    onToggleCompleted: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = remember(context) { AndroidClipboardController(context) }

    val urls = remember(reminder.reminderContent) {
        // Robust URL detection (http/https) with trailing punctuation/bracket trimming.
        val regex = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
        val trimChars = charArrayOf(
            ')', ']', '}', '>',
            ',', '.', ';', ':', '"', '\'',
            '。', '，', '；', '：', '、',
            '）', '］', '｝', '＞',
            '「', '」', '『', '』',
            '”', '’'
        )

        regex.findAll(reminder.reminderContent)
            .map { match ->
                // trimEnd takes a vararg of chars. This removes cases like ")." or "）。".
                match.value.trim().trimEnd(*trimChars)
            }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .combinedClickable(
                onClick = onEdit,
                onLongClick = {
                    if (reminder.reminderContent.isNotBlank()) {
                        CoroutineScope(Dispatchers.Main).launch { clipboard.copyPlainText("reminder", reminder.reminderContent) }
                    }
                }
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (reminder.isTask) {
                Checkbox(
                    checked = reminder.isCompleted,
                    onCheckedChange = { onToggleCompleted(it) }
                )
                Spacer(Modifier.width(8.dp))
            }

            val titleStyle = if (reminder.isTask && reminder.isCompleted) {
                MaterialTheme.typography.titleMedium.copy(textDecoration = TextDecoration.LineThrough)
            } else {
                MaterialTheme.typography.titleMedium
            }

            Text(
                text = reminder.reminderTitle.ifBlank {
                    if (reminder.isTask) stringResource(R.string.ui_reminders_untitled_task) else stringResource(R.string.ui_reminders_untitled_memo)
                },
                style = titleStyle,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.a11y_delete))
            }
        }

        if (reminder.isTask) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val deadline = reminder.deadlineTimestamp
                val deadlineStr = if (deadline > 0L) {
                    val abs = getAbsoluteTimeStr(deadline, context)
                    val rel = getRelativeTimeStr(deadline, context)
                    "$abs ($rel)"
                } else {
                    stringResource(R.string.ui_reminders_no_deadline)
                }

                Text(
                    text = deadlineStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (deadline > 0L && deadline < System.currentTimeMillis()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                val ectStr = if (reminder.estimatedCompletionTime > 0)
                    stringResource(R.string.ui_reminders_ect_short, reminder.estimatedCompletionTime)
                else
                    ""
                Text(text = ectStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val contentPreview = reminder.reminderContent.lineSequence().take(2).joinToString("\n")
        if (contentPreview.isNotBlank()) {
            Text(
                text = contentPreview,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (urls.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                urls.forEachIndexed { idx, url ->
                    val host = try { Uri.parse(url).host } catch (_: Exception) { null }
                    val label = host?.takeIf { it.isNotBlank() } ?: "Link ${idx + 1}"
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                // no-op: malformed URL or no handler
                            }
                        }
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDetailScreen(
    initial: ReminderUnit,
    drawerViewModel: DrawerViewModel,
    onBack: (ReminderUnit?) -> Unit,
    onDelete: (String) -> Unit,
    onSave: (ReminderUnit) -> Unit,
    onExportToGoogleTasks: (ReminderUnit) -> Unit = {},
    isGoogleTasksExporting: Boolean = false,
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
                // Test ESM button: only show when reminder has associated notifications.
//                if (initial.associatedNotis.isNotEmpty()) {
//                    IconButton(onClick = {
//                        CoroutineScope(Dispatchers.IO).launch {
//                            try {
//                                val repo = EsmRepository(context.applicationContext)
//                                repo.createTestEsmForReminder(initial)
//                            } catch (_: Throwable) {
//                            }
//                        }
//                    }) {
//                        Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.a11y_test_esm))
//                    }
//                }

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

                val deadlineStr = if (deadlineTimestamp > 0L) {
                    val abs = getAbsoluteTimeStr(deadlineTimestamp, context)
                    val rel = getRelativeTimeStr(deadlineTimestamp, context)
                    "$abs ($rel)"
                } else {
                    stringResource(R.string.ui_reminders_no_deadline)
                }

                TextButton(onClick = { showDatePicker = true }) {
                    Text(
                        text = deadlineStr,
                        color = if (deadlineTimestamp > 0L && deadlineTimestamp < System.currentTimeMillis())
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = if (ectMinutes == 0L) "" else ectMinutes.toString(),
                    onValueChange = { ectMinutes = it.toLongOrNull() ?: 0L },
                    label = { Text(stringResource(R.string.ui_reminders_editor_ect_label)) },
                    singleLine = true,
                )
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

            // === Export to Google Tasks ===
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            Button(
                onClick = { onExportToGoogleTasks(buildUpdated()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isGoogleTasksExporting,
            ) {
                if (isGoogleTasksExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.google_tasks_exporting))
                } else {
                    Text(stringResource(R.string.google_tasks_export))
                }
            }

            // === Related notifications (from extraction snapshot) ===
            var relatedExpanded by remember(initial.reminderId) { mutableStateOf(false) }
            var relatedRecordsByKey by remember(initial.reminderId) { mutableStateOf<Map<String, List<NotiRecord>>>(emptyMap()) }
            var relatedUnitsByKey by remember(initial.reminderId) { mutableStateOf<Map<String, org.muilab.notigpt.model.notifications.NotiUnit>>(emptyMap()) }
            var relatedLoading by remember(initial.reminderId) { mutableStateOf(false) }

            LaunchedEffect(initial.reminderId, initial.extractionSnapshotId) {
                val TAG = "ReminderRelatedNotis"
                relatedLoading = true
                relatedRecordsByKey = emptyMap()
                relatedUnitsByKey = emptyMap()

                val snapshotId = initial.extractionSnapshotId
                if (snapshotId.isNullOrBlank() || initial.associatedNotis.isEmpty()) {
                    Log.d(TAG, "Skip loading: snapshotId=$snapshotId, associatedNotis=${initial.associatedNotis.size}")
                    relatedLoading = false
                    return@LaunchedEffect
                }

                Log.d(TAG, "Load related: reminderId=${initial.reminderId}, snapshotId=$snapshotId, keys=${initial.associatedNotis}")

                try {
                    val db = org.muilab.notigpt.database.room.AppDatabase.getInstance(context.applicationContext)

                    // Run all Room I/O off the main thread.
                    val (finalRecords, unitsMap) = withContext(Dispatchers.IO) {
                        val snap = db.reminderSnapshotDao().getSnapshot(snapshotId) ?: return@withContext (emptyList<NotiRecord>() to emptyMap())

                        Log.d(TAG, "Snapshot found. status=${snap.status}, reminderId=${snap.reminderId}, payloadLen=${snap.payloadJson.length}")

                        val obj = JSONObject(snap.payloadJson)
                        val v = obj.optInt("v", 1)
                        Log.d(TAG, "Snapshot payload version v=$v")
                        if (v != 2) return@withContext (emptyList<NotiRecord>() to emptyMap())

                        val wantedKeys = initial.associatedNotis.toList()

                        val mappingObj = obj.optJSONObject("notiKeyToRecordIds")
                        val mappedIds = mutableListOf<String>()
                        if (mappingObj != null) {
                            wantedKeys.forEach { key ->
                                val arr = mappingObj.optJSONArray(key)
                                val cnt = arr?.length() ?: 0
                                Log.d(TAG, "Mapping: key=$key -> $cnt ids")
                                if (arr == null) return@forEach
                                for (i in 0 until arr.length()) {
                                    val rid = arr.optString(i)
                                    if (!rid.isNullOrBlank()) mappedIds += rid
                                }
                            }
                        } else {
                            Log.w(TAG, "Snapshot payload missing notiKeyToRecordIds")
                        }

                        val recordIds: List<String> = when {
                            mappedIds.isNotEmpty() -> mappedIds.distinct().also {
                                Log.d(TAG, "Using mappedIds count=${it.size}")
                            }
                            else -> {
                                val arr = obj.optJSONArray("recordIds")
                                val ids = if (arr == null) {
                                    Log.w(TAG, "Snapshot payload missing recordIds")
                                    emptyList()
                                } else buildList {
                                    for (i in 0 until arr.length()) {
                                        val rid = arr.optString(i)
                                        if (!rid.isNullOrBlank()) add(rid)
                                    }
                                }.distinct()
                                Log.d(TAG, "Using fallback recordIds count=${ids.size}")
                                ids
                            }
                        }

                        if (recordIds.isEmpty()) {
                            Log.w(TAG, "No recordIds resolved from snapshot.")
                            return@withContext (emptyList<NotiRecord>() to emptyMap())
                        } else {
                            Log.d(TAG, "First recordId=${recordIds.firstOrNull()}")
                        }

                        val allForKeys = db.recordDao().getRecordsByKeys(wantedKeys)
                        Log.d(TAG, "Fetched records by keys: keys=${wantedKeys.size} -> records=${allForKeys.size}")

                        val idSet = recordIds.toHashSet()
                        val matched = allForKeys.filter { it.notiRecordId in idSet }
                        Log.d(TAG, "Matched by ID filter: matched=${matched.size} (expected around ${recordIds.size})")

                        val records = if (matched.isNotEmpty()) {
                            matched
                        } else {
                            val byIds = db.recordDao().getRecordsByIds(recordIds)
                            Log.w(TAG, "Fallback getRecordsByIds returned=${byIds.size}")
                            byIds
                        }

                        val units = db.drawerDao().getByNotiKeys(wantedKeys).associateBy { it.notiKey }
                        (records to units)
                    }

                    relatedRecordsByKey = finalRecords.groupBy { it.notiKey }
                    relatedUnitsByKey = unitsMap
                    Log.d(TAG, "Grouped relatedRecordsByKey keys=${relatedRecordsByKey.keys.size}, totalRecords=${finalRecords.size}, units=${unitsMap.size}")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed loading related notifications", t)
                    relatedRecordsByKey = emptyMap()
                    relatedUnitsByKey = emptyMap()
                } finally {
                    relatedLoading = false
                }
            }

            // Show the section as long as this reminder claims it has associated notifications.
            if (initial.associatedNotis.isNotEmpty()) {
                val relatedKeys = initial.associatedNotis.toList()

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
                        text = stringResource(R.string.esm_related_notifications, relatedKeys.size),
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
                                text = stringResource(R.string.esm_loading_context),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        relatedRecordsByKey.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.esm_no_related_notifications),
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
                                    EsmNotiCardLikePreview(
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
                            // After selecting date, choose time via native TimePickerDialog
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                            val initialHour = cal.get(Calendar.HOUR_OF_DAY)
                            val initialMinute = cal.get(Calendar.MINUTE)

                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val c = Calendar.getInstance().apply {
                                        timeInMillis = selectedDate
                                        set(Calendar.HOUR_OF_DAY, hour)
                                        set(Calendar.MINUTE, minute)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    deadlineTimestamp = c.timeInMillis
                                },
                                initialHour,
                                initialMinute,
                                true
                            ).show()
                        }
                        showDatePicker = false
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
}
