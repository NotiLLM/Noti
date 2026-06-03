package org.muilab.notigpt.ui.reminder.screen

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.muilab.notigpt.model.features.Reminder
import org.muilab.notigpt.model.features.ReminderStatus
import org.muilab.notigpt.ui.reminder.viewmodel.ScheduledReminderViewModel
import org.muilab.notigpt.util.time.getAbsoluteTimeStr
import java.util.Calendar

@Composable
fun ScheduledRemindersScreen(
    viewModel: ScheduledReminderViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.refreshDueStates() }

    val reminders by viewModel.reminders.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var rescheduleTarget by remember { mutableStateOf<Reminder?>(null) }
    val filteredReminders = remember(reminders, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) reminders else reminders.filter { reminder ->
            listOf(reminder.title, reminder.content, reminder.status).any { it.lowercase().contains(q) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Scheduled reminders", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Due reminders stay here until you check them off. Seen and cancelled reminders are hidden.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true,
            label = { Text("Search Reminders") },
        )

        if (filteredReminders.isEmpty()) {
            Text("No active scheduled reminders.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredReminders, key = { it.reminderId }) { reminder ->
                    ScheduledReminderCard(
                        reminder = reminder,
                        onSeen = { viewModel.markSeen(reminder.reminderId) },
                        onCancel = { viewModel.cancel(reminder.reminderId) },
                        onReschedule = { rescheduleTarget = reminder },
                    )
                }
            }
        }
    }

    rescheduleTarget?.let { target ->
        ReminderDateTimeDialog(
            title = "Reschedule reminder",
            initialAtMs = target.remindAtMs,
            onDismiss = { rescheduleTarget = null },
            onConfirm = { remindAtMs ->
                viewModel.reschedule(target.reminderId, remindAtMs)
                rescheduleTarget = null
            },
        )
    }
}

@Composable
private fun ScheduledReminderCard(
    reminder: Reminder,
    onSeen: () -> Unit,
    onCancel: () -> Unit,
    onReschedule: () -> Unit,
) {
    val isDue = reminder.status == ReminderStatus.DueUnseen
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isDue) { onSeen() },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isDue) {
                IconButton(onClick = onSeen) { Icon(Icons.Default.Check, contentDescription = "Mark seen") }
            } else {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(reminder.title.ifBlank { "Reminder" }, style = MaterialTheme.typography.titleMedium)
                if (reminder.content.isNotBlank()) {
                    Text(reminder.content, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    getAbsoluteTimeStr(reminder.remindAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onReschedule) { Icon(Icons.Default.Notifications, contentDescription = "Reschedule") }
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
        }
    }
}

@Composable
fun ReminderDateTimeDialog(
    title: String = "Create reminder",
    initialAtMs: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val context = LocalContext.current
    val cal = remember(initialAtMs) { Calendar.getInstance().apply { timeInMillis = initialAtMs.coerceAtLeast(System.currentTimeMillis()) } }
    var selectedAtMs by remember(initialAtMs) { mutableStateOf(cal.timeInMillis) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reminder time: ${getAbsoluteTimeStr(selectedAtMs)}")
                Button(onClick = {
                    val pickerCal = Calendar.getInstance().apply { timeInMillis = selectedAtMs }
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            pickerCal.set(Calendar.HOUR_OF_DAY, hour)
                            pickerCal.set(Calendar.MINUTE, minute)
                            pickerCal.set(Calendar.SECOND, 0)
                            pickerCal.set(Calendar.MILLISECOND, 0)
                            selectedAtMs = pickerCal.timeInMillis
                        },
                        pickerCal.get(Calendar.HOUR_OF_DAY),
                        pickerCal.get(Calendar.MINUTE),
                        true,
                    ).show()
                }) { Text("Pick time") }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selectedAtMs) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
