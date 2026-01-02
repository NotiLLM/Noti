package org.muilab.notigpt.ui.component.features

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.model.features.TaskUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import org.muilab.notigpt.platform.AndroidClipboardController

@Composable
fun TaskUIUnit(
    taskUnit: TaskUnit,
    onCheckedChange: (Boolean) -> Unit = {},
    onRemove: () -> Unit = {},
    onEdit: (TaskUnit) -> Unit = {}
) {
    var showEdit by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboard = remember(context) { AndroidClipboardController(context) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        val contentAlpha = if (taskUnit.isCompleted) 0.45f else 1f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(
                checked = taskUnit.isCompleted,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.minimumInteractiveComponentSize()
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = taskUnit.taskDescription,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 2.dp)
                        .background(Color.Transparent)
                        .combinedClickable(
                            onClick = { expanded = !expanded },
                            onLongClick = {
                                val text = taskUnit.taskDescription
                                if (text.isNotBlank()) {
                                    clipboard.copyPlainText("Task", text)
                                }
                            }
                        )
                )
                Spacer(modifier = Modifier.height(2.dp))
                val deadlineText = formatDeadline(taskUnit.deadlineTimestamp)
                val estimateText = formatEstimateMinutes(taskUnit.estimatedCompletionTime)
                if (deadlineText.isNotEmpty() || estimateText.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        if (deadlineText.isNotEmpty()) {
                            Text(
                                text = deadlineText,
                                style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (deadlineText.isNotEmpty() && estimateText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (estimateText.isNotEmpty()) {
                            Text(
                                text = estimateText,
                                style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { showEdit = true },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit task", modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete task", modifier = Modifier.size(16.dp))
            }
        }
        if (showEdit) {
            TaskEditDialog(
                task = taskUnit,
                onDismiss = { showEdit = false },
                onSave = { newTask ->
                    showEdit = false
                    onEdit(newTask)
                }
            )
        }
    }
}

// Assumptions:
// - deadlineTimestamp is epoch milliseconds (0 or negative means no deadline)
// - estimatedCompletionTime is in minutes (0 or negative means not specified)

private fun formatDeadline(deadlineMs: Long): String {
    if (deadlineMs <= 0L) return ""
    return try {
        // Omit year to reduce overflow issues
        val sdf = SimpleDateFormat("MMM d HH:mm", Locale.getDefault())
        "Due: ${sdf.format(Date(deadlineMs))}"
    } catch (_: Exception) {
        ""
    }
}

private fun formatEstimateMinutes(estimateMinutes: Long): String {
    if (estimateMinutes <= 0L) return ""
    val hours = estimateMinutes / 60
    val minutes = estimateMinutes % 60
    return when {
        hours > 0 -> "Est: ${hours}h ${minutes}m"
        else -> "Est: ${minutes}m"
    }
}
