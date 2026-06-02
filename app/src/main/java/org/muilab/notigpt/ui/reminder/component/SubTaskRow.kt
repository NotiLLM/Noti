package org.muilab.notigpt.ui.reminder.component

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.SubTask

/**
 * A single sub-task row used in both ReminderCard (list) and ReminderDetailScreen.
 *
 * Layout: [Checkbox] [Title] ... [MoreVert]
 *
 * Clicking the row triggers [onClick] (navigate to sub-task detail).
 * The MoreVert dropdown offers: Edit, Export to Google Tasks, Export to Google Calendar, Delete.
 */
@Composable
fun SubTaskRow(
    subTask: SubTask,
    onToggleCompleted: (Boolean) -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExportGoogleTasks: () -> Unit,
    onExportGoogleCalendar: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = subTask.isCompleted,
            onCheckedChange = onToggleCompleted,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(4.dp))

        val titleStyle = if (subTask.isCompleted) {
            MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough)
        } else {
            MaterialTheme.typography.bodyMedium
        }

        Text(
            text = subTask.title.ifBlank { stringResource(R.string.subtask_untitled) },
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // "More" icon button
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.a11y_subtask_more),
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.subtask_edit)) },
                onClick = { menuExpanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.google_tasks_export)) },
                onClick = { menuExpanded = false; onExportGoogleTasks() },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.task_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.google_calendar_export)) },
                onClick = { menuExpanded = false; onExportGoogleCalendar() },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.calendar_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.subtask_delete), color = MaterialTheme.colorScheme.error) },
                onClick = { menuExpanded = false; onDelete() },
            )
        }
    }
}

