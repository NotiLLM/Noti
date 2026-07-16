package org.muilab.notigpt.ui.reminder.component

import org.muilab.notigpt.model.features.SavedItemType
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.ui.common.clipboard.AndroidClipboardController
import org.muilab.notigpt.util.time.getAbsoluteTimeStr
import org.muilab.notigpt.util.time.getRelativeTimeStr
import java.util.Calendar

/**
 * Full-screen detail/edit screen for a sub-task.
 * Mirrors the layout of ReminderDetailScreen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SavedSubItemDetailScreen(
    initial: SavedSubItem,
    onBack: (SavedSubItem?) -> Unit,
    onDelete: (String) -> Unit,
    onSave: (SavedSubItem) -> Unit,
    onExportGoogleTasks: (SavedSubItem) -> Unit = {},
    onExportGoogleCalendar: (SavedSubItem) -> Unit = {},
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    var title by remember(initial.savedSubItemId) { mutableStateOf(initial.title) }
    var description by remember(initial.savedSubItemId) { mutableStateOf(initial.description) }
    var isTask by remember(initial.savedSubItemId) { mutableStateOf(initial.isTask) }
    var isEvent by remember(initial.savedSubItemId) { mutableStateOf(initial.isEvent) }
    var isCompleted by remember(initial.savedSubItemId) { mutableStateOf(initial.isCompleted) }
    var deadlineAtMs by remember(initial.savedSubItemId) { mutableStateOf(initial.deadlineAtMs) }
    var startAtMs by remember(initial.savedSubItemId) { mutableStateOf(initial.startAtMs) }
    var endAtMs by remember(initial.savedSubItemId) { mutableStateOf(initial.endAtMs) }

    fun buildUpdated(): SavedSubItem {
        return initial.copy(
            title = title,
            description = description,
            itemType = if (isTask) SavedItemType.Task else SavedItemType.Keep,
            isCompleted = if (isTask) isCompleted else false,
            deadlineAtMs = if (isTask) deadlineAtMs else 0L,
            startAtMs = if (isEvent) startAtMs else 0L,
            endAtMs = if (isEvent) endAtMs else 0L,
        )
    }

    BackHandler(enabled = true) {
        onBack(buildUpdated())
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Track which field the picker targets
    var pickingField by remember { mutableStateOf<String?>(null) } // "deadline", "start", "end"

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
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (title.isBlank()) {
                            Text(
                                text = stringResource(R.string.subtask_untitled),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    },
                )
                IconButton(onClick = { onDelete(initial.savedSubItemId) }) {
                    Icon(painterResource(R.drawable.delete), contentDescription = stringResource(R.string.a11y_delete), tint = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { onSave(buildUpdated()) }) {
                    Icon(painter = painterResource(R.drawable.save), contentDescription = stringResource(R.string.ui_action_save))
                }
            }
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Task toggle
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_reminders_editor_task_label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = isTask, onCheckedChange = { isTask = it })
            }

            if (isTask) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui_reminders_editor_completed), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = isCompleted, onCheckedChange = { isCompleted = it })
                }

                // Deadline
                val deadlineColor = if (deadlineAtMs > 0L && deadlineAtMs < System.currentTimeMillis()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
                val deadlineDateStr = if (deadlineAtMs > 0L) getAbsoluteTimeStr(deadlineAtMs, context) else stringResource(R.string.reminder_no_date)
                val deadlineTimeStr = if (deadlineAtMs > 0L) {
                    java.text.SimpleDateFormat("HH:mm", locale).format(java.util.Date(deadlineAtMs))
                } else stringResource(R.string.reminder_no_time)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pickingField = "deadline"; showDatePicker = true }) {
                        Text(text = "${stringResource(R.string.reminder_pick_date)}: $deadlineDateStr", color = deadlineColor)
                    }
                    TextButton(onClick = { pickingField = "deadline"; showTimePicker = true }) {
                        Text(text = "${stringResource(R.string.reminder_pick_time)}: $deadlineTimeStr", color = deadlineColor)
                    }
                }
            }

            // Event toggle
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Event", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = isEvent, onCheckedChange = { isEvent = it })
            }

            if (isEvent) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", locale)
                val startLabel = if (startAtMs > 0L) sdf.format(java.util.Date(startAtMs)) else stringResource(R.string.reminder_no_date)
                val endLabel = if (endAtMs > 0L) sdf.format(java.util.Date(endAtMs)) else stringResource(R.string.reminder_no_date)

                Text("Start", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pickingField = "start"; showDatePicker = true }) {
                        Text("${stringResource(R.string.reminder_pick_date)}: $startLabel")
                    }
                }
                Text("End", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pickingField = "end"; showDatePicker = true }) {
                        Text("${stringResource(R.string.reminder_pick_date)}: $endLabel")
                    }
                }
            }

            Text(stringResource(R.string.ui_reminders_editor_note), style = MaterialTheme.typography.titleMedium)

            Surface(
                tonalElevation = 0.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BasicTextField(
                    value = description,
                    onValueChange = { description = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    decorationBox = { innerTextField ->
                        if (description.isBlank()) {
                            Text(
                                text = stringResource(R.string.ui_reminders_editor_note_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    },
                )
            }

            // === Action buttons (from sub-task buttons JSON) ===
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
                val clipboard = remember(context) { AndroidClipboardController(context) }
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    detailButtons.forEach { (buttonText, intent, type) ->
                        val iconRes = when (type) {
                            "copy" -> R.drawable.copy
                            else -> R.drawable.link
                        }
                        AssistChip(
                            onClick = {
                                when (type) {
                                    "copy" -> {
                                        CoroutineScope(Dispatchers.Main).launch {
                                            clipboard.copyPlainText("subtask_button", intent)
                                        }
                                    }
                                    else -> {
                                        try {
                                            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(intent)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(viewIntent)
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                            label = { Text(buttonText, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }

            // === Export chips ===
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { onExportGoogleTasks(buildUpdated()) },
                    label = { Text(stringResource(R.string.google_tasks_export), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.task_add), contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                AssistChip(
                    onClick = { onExportGoogleCalendar(buildUpdated()) },
                    label = { Text(stringResource(R.string.google_calendar_export), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.calendar_add), contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                AssistChip(
                    onClick = {
                        val shareText = "${title}\n\n${description}"
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    },
                    label = { Text(stringResource(R.string.reminder_share), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.share), contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
            }
        }
    }

    // Date picker
    if (showDatePicker && pickingField != null) {
        val currentMs = when (pickingField) {
            "deadline" -> deadlineAtMs
            "start" -> startAtMs
            "end" -> endAtMs
            else -> 0L
        }
        val initialMs = if (currentMs > 0L) currentMs else System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs)
        val currentField = pickingField

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false; pickingField = null },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = datePickerState.selectedDateMillis
                    if (selectedDate != null) {
                        val existingMs = when (currentField) {
                            "deadline" -> deadlineAtMs
                            "start" -> startAtMs
                            "end" -> endAtMs
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
                            "deadline" -> deadlineAtMs = newCal.timeInMillis
                            "start" -> startAtMs = newCal.timeInMillis
                            "end" -> endAtMs = newCal.timeInMillis
                        }
                    }
                    showDatePicker = false
                    pickingField = null
                }) { Text(stringResource(R.string.ui_action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false; pickingField = null }) {
                    Text(stringResource(R.string.ui_action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time picker
    if (showTimePicker && pickingField != null) {
        val currentField = pickingField
        LaunchedEffect(showTimePicker, pickingField) {
            val existingMs = when (currentField) {
                "deadline" -> deadlineAtMs
                "start" -> startAtMs
                "end" -> endAtMs
                else -> 0L
            }
            val cal = Calendar.getInstance().apply {
                timeInMillis = if (existingMs > 0L) existingMs else System.currentTimeMillis()
            }
            withContext(Dispatchers.Main) {
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
                            "deadline" -> deadlineAtMs = c.timeInMillis
                            "start" -> startAtMs = c.timeInMillis
                            "end" -> endAtMs = c.timeInMillis
                        }
                        showTimePicker = false
                        pickingField = null
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true,
                ).apply {
                    setOnCancelListener { showTimePicker = false; pickingField = null }
                }.show()
            }
        }
    }
}


