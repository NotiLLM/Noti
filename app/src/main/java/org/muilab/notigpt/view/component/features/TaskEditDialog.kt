package org.muilab.notigpt.view.component.features

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.muilab.notigpt.model.features.TaskUnit

@Composable
fun TaskEditDialog(
    task: TaskUnit,
    onDismiss: () -> Unit,
    onSave: (TaskUnit) -> Unit
) {
    val context = LocalContext.current
    var description by remember { mutableStateOf(task.taskDescription) }
    // Keep internal deadline as epoch millis or -1
    var deadlineMs by remember { mutableLongStateOf(task.deadlineTimestamp) }
    var estimateText by remember { mutableStateOf(task.estimatedCompletionTime.toString()) }
    var errorMessage by remember { mutableStateOf("") }

    // Display string for deadline
    val deadlineDisplay = remember(deadlineMs) {
        if (deadlineMs <= 0L) "" else SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(java.util.Date(deadlineMs))
    }

    // Date/time pickers
    fun showDateTimePicker(ctx: Context, initialMs: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { if (initialMs > 0) timeInMillis = initialMs }
        DatePickerDialog(ctx, { _, y, m, d ->
            cal.set(Calendar.YEAR, y)
            cal.set(Calendar.MONTH, m)
            cal.set(Calendar.DAY_OF_MONTH, d)
            TimePickerDialog(ctx, { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                onPicked(cal.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                // Validate description
                if (description.isBlank()) {
                    errorMessage = "Description cannot be empty"
                    return@Button
                }
                // Validate estimate (integer)
                val estimate = estimateText.toLongOrNull()
                if (estimate == null) {
                    errorMessage = "Estimated minutes must be an integer"
                    return@Button
                }

                onSave(task.copy(
                    taskDescription = description,
                    deadlineTimestamp = deadlineMs,
                    estimatedCompletionTime = estimate,
                    userEdited = true
                ))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Task description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.padding(4.dp))
                // Show deadline text above the pick/clear buttons
                Text(
                    text = "Deadline: " + if (deadlineMs <= 0L) "No deadline" else deadlineDisplay,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { showDateTimePicker(context, deadlineMs) { deadlineMs = it } }) {
                        Text("Pick deadline")
                    }
                    if (deadlineMs > 0L) {
                        TextButton(onClick = { deadlineMs = -1L }) {
                            Text("Clear")
                        }
                    }
                }
                Spacer(modifier = Modifier.padding(4.dp))
                OutlinedTextField(
                    value = estimateText,
                    onValueChange = { estimateText = it },
                    label = { Text("Estimated minutes") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = Color.Red, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    )
}
