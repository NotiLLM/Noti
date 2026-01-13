package org.muilab.notigpt.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.ui.viewmodel.ReminderViewModel
import org.muilab.notigpt.util.getAbsoluteTimeStr
import org.muilab.notigpt.util.getRelativeTimeStr

@Composable
fun RemindersScreen(
    reminderViewModel: ReminderViewModel = viewModel(),
) {
    val reminders by reminderViewModel.reminders.collectAsState()
    val filter by reminderViewModel.filter.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ReminderUnit?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Simple tab row (chip-like)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("All", filter == ReminderViewModel.FilterTab.All) { reminderViewModel.setFilter(ReminderViewModel.FilterTab.All) }
                FilterChip("Tasks", filter == ReminderViewModel.FilterTab.Tasks) { reminderViewModel.setFilter(ReminderViewModel.FilterTab.Tasks) }
                FilterChip("Memos", filter == ReminderViewModel.FilterTab.Memos) { reminderViewModel.setFilter(ReminderViewModel.FilterTab.Memos) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
            ) {
                items(reminders, key = { it.reminderId }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onDelete = { reminderViewModel.delete(reminder.reminderId) },
                        onToggleCompleted = { completed -> reminderViewModel.toggleCompleted(reminder, completed) },
                        onEdit = { editing = reminder },
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
            Icon(Icons.Default.Add, contentDescription = "Add")
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add") },
                text = { Text("Create a Task or a Memo") },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            reminderViewModel.addNew(isTask = true)
                            showAddDialog = false
                        }) { Text("Task") }
                        TextButton(onClick = {
                            reminderViewModel.addNew(isTask = false)
                            showAddDialog = false
                        }) { Text("Memo") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                }
            )
        }

        editing?.let { r ->
            ReminderEditDialog(
                reminder = r,
                onDismiss = { editing = null },
                onSave = { updated ->
                    // If both empty, delete.
                    if (updated.reminderTitle.isBlank() && updated.reminderContent.isBlank()) {
                        reminderViewModel.delete(updated.reminderId)
                    } else {
                        reminderViewModel.upsert(updated.copy(userEdited = true))
                    }
                    editing = null
                }
            )
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
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .combinedClickable(
                onClick = onEdit,
                onLongClick = {
                    if (reminder.reminderContent.isNotBlank()) {
                        clipboard.setText(AnnotatedString(reminder.reminderContent))
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
                text = reminder.reminderTitle.ifBlank { if (reminder.isTask) "(Untitled task)" else "(Untitled memo)" },
                style = titleStyle,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
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
                    val abs = getAbsoluteTimeStr(deadline)
                    val rel = getRelativeTimeStr(deadline)
                    "$abs ($rel)"
                } else {
                    "No deadline"
                }

                Text(
                    text = deadlineStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (deadline > 0L && deadline < System.currentTimeMillis()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                val ectStr = if (reminder.estimatedCompletionTime > 0) "ECT: ${reminder.estimatedCompletionTime}m" else ""
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
    }
}

@Composable
private fun ReminderEditDialog(
    reminder: ReminderUnit,
    onDismiss: () -> Unit,
    onSave: (ReminderUnit) -> Unit,
) {
    var title by remember(reminder.reminderId) { mutableStateOf(reminder.reminderTitle) }
    var content by remember(reminder.reminderId) { mutableStateOf(reminder.reminderContent) }
    var isTask by remember(reminder.reminderId) { mutableStateOf(reminder.isTask) }
    var deadline by remember(reminder.reminderId) { mutableStateOf(reminder.deadlineTimestamp.toString()) }
    var ect by remember(reminder.reminderId) { mutableStateOf(reminder.estimatedCompletionTime.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    minLines = 5
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isTask, onCheckedChange = { isTask = it })
                    Text("Is task")
                }

                OutlinedTextField(value = deadline, onValueChange = { deadline = it }, label = { Text("Deadline timestamp (ms)") })
                OutlinedTextField(value = ect, onValueChange = { ect = it }, label = { Text("Estimated completion time (minutes)") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = reminder.copy(
                    reminderTitle = title,
                    reminderContent = content,
                    isTask = isTask,
                    deadlineTimestamp = deadline.toLongOrNull() ?: 0L,
                    estimatedCompletionTime = ect.toLongOrNull() ?: 0L,
                )
                onSave(updated)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
