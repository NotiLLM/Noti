package org.muilab.notigpt.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
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
import org.muilab.notigpt.R
import org.muilab.notigpt.domain.esm.EsmTriggerTypes
import org.muilab.notigpt.domain.esm.EsmStatuses
import org.muilab.notigpt.domain.esm.EsmUserSnapshot
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.repository.EsmRepository
import org.muilab.notigpt.ui.screens.esm.EsmNotiCardLikePreview
import org.muilab.notigpt.ui.viewmodel.ReminderViewModel
import org.muilab.notigpt.util.getAbsoluteTimeStr
import org.muilab.notigpt.util.getRelativeTimeStr
import java.util.Calendar
import org.muilab.notigpt.platform.AndroidClipboardController

@Composable
fun RemindersScreen(
    reminderViewModel: ReminderViewModel? = null,
) {
    val vm: ReminderViewModel = reminderViewModel ?: viewModel()
    val reminders by vm.reminders.collectAsState()
    val filter by vm.filter.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ReminderUnit?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingInitialSnapshot by remember { mutableStateOf<ReminderUnit?>(null) }

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
            onClick = { showAddDialog = true }
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.a11y_add))
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(stringResource(R.string.ui_reminders_add_dialog_title)) },
                text = { Text(stringResource(R.string.ui_reminders_add_dialog_body)) },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            vm.addNew(isTask = true)
                            showAddDialog = false
                        }) { Text(stringResource(R.string.ui_reminders_add_dialog_task)) }
                        TextButton(onClick = {
                            vm.addNew(isTask = false)
                            showAddDialog = false
                        }) { Text(stringResource(R.string.ui_reminders_add_dialog_memo)) }
                    }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.ui_action_cancel)) } }
            )
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
                    onBack = { updatedOrNull: ReminderUnit? ->
                        val base = editingInitialSnapshot
                        val changed = base != null && updatedOrNull != null && (
                                base.reminderTitle != updatedOrNull.reminderTitle ||
                                        base.reminderContent != updatedOrNull.reminderContent ||
                                        base.isTask != updatedOrNull.isTask ||
                                        base.isCompleted != updatedOrNull.isCompleted ||
                                        base.deadlineTimestamp != updatedOrNull.deadlineTimestamp ||
                                        base.estimatedCompletionTime != updatedOrNull.estimatedCompletionTime
                                )

                        if (updatedOrNull != null) {
                            if (updatedOrNull.reminderTitle.isBlank() && updatedOrNull.reminderContent.isBlank()) {
                                vm.delete(updatedOrNull.reminderId)
                            } else if (changed) {
                                vm.upsert(
                                    updatedOrNull.copy(
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
                    onDelete = { id: String ->
                        vm.delete(id)
                        editing = null
                        editingId = null
                        editingInitialSnapshot = null
                        pendingScrollToTopId = null
                    },
                    onSave = { updated: ReminderUnit ->
                        val base = editingInitialSnapshot
                        val changed = base != null && (
                                base.reminderTitle != updated.reminderTitle ||
                                        base.reminderContent != updated.reminderContent ||
                                        base.isTask != updated.isTask ||
                                        base.isCompleted != updated.isCompleted ||
                                        base.deadlineTimestamp != updated.deadlineTimestamp ||
                                        base.estimatedCompletionTime != updated.estimatedCompletionTime
                                )

                        if (updated.reminderTitle.isBlank() && updated.reminderContent.isBlank()) {
                            vm.delete(updated.reminderId)
                        } else if (changed) {
                            vm.upsert(
                                updated.copy(
                                    userEdited = true,
                                    lastUpdateTimestamp = System.currentTimeMillis(),
                                )
                            )
                        }

                        val id = if (changed) editingId else null
                        editing = null
                        editingId = null
                        editingInitialSnapshot = null
                        if (id != null) pendingScrollToTopId = id
                    },
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

        val contentPreview = reminder.reminderContent.lineSequence().take(3).joinToString("\n")
        if (contentPreview.isNotBlank()) {
            Text(
                text = contentPreview,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (urls.isNotEmpty()) {
            Row(
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
    onBack: (ReminderUnit?) -> Unit,
    onDelete: (String) -> Unit,
    onSave: (ReminderUnit) -> Unit,
) {
    val context = LocalContext.current

    // Trigger B: entered edit page for a generated reminder (has associated notifications).
    LaunchedEffect(initial.reminderId) {
        if (initial.associatedNotis.isNotEmpty()) {
            try {
                val repo = EsmRepository(context.applicationContext)
                // v1: if there's an available ESM for this reminder, upgrade trigger to B.
                val avail = repo.getInstancesByStatuses(listOf("AVAILABLE"))
                    .firstOrNull { it.reminderId == initial.reminderId }
                if (avail != null) {
                    repo.setTriggerType(avail.instanceId, EsmTriggerTypes.B_ENTERED_EDIT_PAGE)
                }
            } catch (_: Exception) {
            }
        }
    }

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
                if (initial.associatedNotis.isNotEmpty()) {
                    IconButton(onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val repo = EsmRepository(context.applicationContext)
                                repo.createTestEsmForReminder(initial)
                            } catch (_: Throwable) {
                            }
                        }
                    }) {
                        Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.a11y_test_esm))
                    }
                }

                IconButton(onClick = { onDelete(initial.reminderId) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.a11y_delete))
                }
                TextButton(onClick = { onSave(buildUpdated()) }) {
                    Icon(painter = painterResource(R.drawable.save), contentDescription = stringResource(R.string.ui_action_save))
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
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

            // === Related notifications (from stored ESM snapshot) ===
            var relatedExpanded by remember(initial.reminderId) { mutableStateOf(false) }
            var relatedSnapshotJson by remember(initial.reminderId) { mutableStateOf<String?>(null) }

            LaunchedEffect(initial.reminderId) {
                // Try to find an existing ESM snapshot for this reminder so we can render NotiCard-like previews.
                try {
                    val repo = EsmRepository(context.applicationContext)
                    val instances = repo.getInstancesByStatuses(listOf(EsmStatuses.AVAILABLE))
                    val inst = instances.lastOrNull { it.reminderId == initial.reminderId }
                    val snap = inst?.let { repo.getSnapshot(it.snapshotId) }
                    relatedSnapshotJson = snap?.payloadJson
                } catch (_: Exception) {
                    relatedSnapshotJson = null
                }
            }

            val relatedCtx = remember(relatedSnapshotJson) {
                relatedSnapshotJson?.let { EsmUserSnapshot.parse(it) }
            }

            // === Related notifications section at bottom ===
            val relatedNotis = relatedCtx?.notis.orEmpty()
            if (relatedNotis.isNotEmpty()) {
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
                        text = stringResource(R.string.esm_related_notifications, relatedNotis.size),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Icon(
                        imageVector = if (relatedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (relatedExpanded) stringResource(R.string.a11y_collapse) else stringResource(R.string.a11y_expand),
                    )
                }

                if (relatedExpanded) {
                    relatedNotis.forEach { np ->
                        EsmNotiCardLikePreview(notiDisplayUnit = np.displayUnit)
                        Spacer(Modifier.size(8.dp))
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
