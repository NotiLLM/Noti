package org.muilab.notigpt.ui.reminder.screen

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.Reminder
import org.muilab.notigpt.model.features.ReminderStatus
import org.muilab.notigpt.ui.reminder.viewmodel.ScheduledReminderViewModel
import org.muilab.notigpt.ui.theme.NotiTheme
import org.muilab.notigpt.util.time.getAbsoluteTimeStr
import java.util.Calendar

private const val COLLAPSED_CONTENT_LINES = 3

@Composable
fun ScheduledRemindersScreen(
    viewModel: ScheduledReminderViewModel = viewModel(),
    searchQuery: String = "",
) {
    LaunchedEffect(Unit) { viewModel.refreshDueStates() }

    val reminders by viewModel.reminders.collectAsState()
    var rescheduleTarget by remember { mutableStateOf<Reminder?>(null) }
    val filteredReminders = remember(reminders, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) reminders else reminders.filter { reminder ->
            listOf(reminder.title, reminder.content, reminder.status).any { it.lowercase().contains(q) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.scheduled_reminders_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.scheduled_reminders_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (filteredReminders.isEmpty()) {
            Text(stringResource(R.string.scheduled_reminders_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            title = stringResource(R.string.scheduled_reminder_reschedule_title),
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
    val accent = if (isDue) NotiTheme.semantic.overdue else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isDue) { onSeen() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isDue) {
                IconButton(onClick = onSeen) {
                    Icon(painterResource(R.drawable.check), contentDescription = stringResource(R.string.a11y_scheduled_mark_seen), tint = accent, modifier = Modifier.size(22.dp))
                }
            } else {
                Icon(
                    painterResource(R.drawable.notifications),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(reminder.title.ifBlank { stringResource(R.string.scheduled_reminder_untitled) }, style = MaterialTheme.typography.titleMedium)
                if (reminder.content.isNotBlank()) {
                    var contentExpanded by remember(reminder.reminderId) { mutableStateOf(false) }
                    var contentOverflows by remember(reminder.reminderId) { mutableStateOf(false) }
                    Text(
                        reminder.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (contentExpanded) Int.MAX_VALUE else COLLAPSED_CONTENT_LINES,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!contentExpanded) contentOverflows = it.hasVisualOverflow },
                    )
                    if (contentOverflows || contentExpanded) {
                        Text(
                            stringResource(if (contentExpanded) R.string.ui_show_less else R.string.ui_show_more),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { contentExpanded = !contentExpanded }
                                .padding(vertical = 2.dp),
                        )
                    }
                }
                Text(
                    getAbsoluteTimeStr(reminder.remindAtMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
            IconButton(onClick = onReschedule) {
                Icon(painterResource(R.drawable.schedule), contentDescription = stringResource(R.string.a11y_scheduled_reschedule), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onCancel) {
                Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.a11y_scheduled_cancel), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ReminderDateTimeDialog(
    title: String = stringResource(R.string.ui_reminder_create_button),
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
                Text(stringResource(R.string.scheduled_reminder_time, getAbsoluteTimeStr(selectedAtMs)))
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
                }) { Text(stringResource(R.string.scheduled_pick_time)) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selectedAtMs) }) { Text(stringResource(R.string.ui_action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_action_cancel)) } },
    )
}
