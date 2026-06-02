package org.muilab.notigpt.ui.reminder.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.SubTask

/**
 * Inline sub-task list shown inside ReminderCard on the list screen.
 *
 * Shows at most [maxVisible] incomplete sub-tasks, plus a clickable
 * "N more" / "N completed" summary that expands to reveal hidden items.
 */
@Composable
fun SubTaskListInCard(
    subTasks: List<SubTask>,
    maxVisible: Int = 3,
    onToggleCompleted: (String, Boolean) -> Unit,
    onSubTaskClick: (SubTask) -> Unit,
    onSubTaskEdit: (SubTask) -> Unit,
    onSubTaskDelete: (SubTask) -> Unit,
    onSubTaskExportGoogleTasks: (SubTask) -> Unit,
    onSubTaskExportGoogleCalendar: (SubTask) -> Unit,
) {
    if (subTasks.isEmpty()) return

    val incomplete = subTasks.filter { !it.isCompleted }
    val completed = subTasks.filter { it.isCompleted }
    val visible = incomplete.take(maxVisible)
    val hiddenIncomplete = incomplete.drop(maxVisible)
    var showAllIncomplete by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }

    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 5.dp.toPx()
                val strokeWidth = 2.dp.toPx()
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeWidth,
                )
            }
    ) {
    Column(modifier = Modifier.weight(1f)) {
        // Always-visible incomplete sub-tasks
        visible.forEach { st ->
            SubTaskRow(
                subTask = st,
                onToggleCompleted = { checked -> onToggleCompleted(st.subTaskId, checked) },
                onClick = { onSubTaskClick(st) },
                onEdit = { onSubTaskEdit(st) },
                onDelete = { onSubTaskDelete(st) },
                onExportGoogleTasks = { onSubTaskExportGoogleTasks(st) },
                onExportGoogleCalendar = { onSubTaskExportGoogleCalendar(st) },
            )
        }

        // Expandable hidden incomplete sub-tasks
        if (hiddenIncomplete.isNotEmpty()) {
            AnimatedVisibility(
                visible = showAllIncomplete,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    hiddenIncomplete.forEach { st ->
                        SubTaskRow(
                            subTask = st,
                            onToggleCompleted = { checked -> onToggleCompleted(st.subTaskId, checked) },
                            onClick = { onSubTaskClick(st) },
                            onEdit = { onSubTaskEdit(st) },
                            onDelete = { onSubTaskDelete(st) },
                            onExportGoogleTasks = { onSubTaskExportGoogleTasks(st) },
                            onExportGoogleCalendar = { onSubTaskExportGoogleCalendar(st) },
                        )
                    }
                }
            }

            Text(
                text = if (showAllIncomplete)
                    stringResource(R.string.subtask_show_less)
                else
                    stringResource(R.string.subtask_n_more, hiddenIncomplete.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { showAllIncomplete = !showAllIncomplete }
                    .padding(start = 48.dp, top = 6.dp, bottom = 6.dp, end = 16.dp)
                    .fillMaxWidth(),
            )
        }

        // Expandable completed sub-tasks
        if (completed.isNotEmpty()) {
            AnimatedVisibility(
                visible = showCompleted,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    completed.forEach { st ->
                        SubTaskRow(
                            subTask = st,
                            onToggleCompleted = { checked -> onToggleCompleted(st.subTaskId, checked) },
                            onClick = { onSubTaskClick(st) },
                            onEdit = { onSubTaskEdit(st) },
                            onDelete = { onSubTaskDelete(st) },
                            onExportGoogleTasks = { onSubTaskExportGoogleTasks(st) },
                            onExportGoogleCalendar = { onSubTaskExportGoogleCalendar(st) },
                        )
                    }
                }
            }

            Text(
                text = if (showCompleted)
                    stringResource(R.string.subtask_hide_completed)
                else
                    stringResource(R.string.subtask_n_completed, completed.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { showCompleted = !showCompleted }
                    .padding(start = 48.dp, top = 6.dp, bottom = 6.dp, end = 16.dp)
                    .fillMaxWidth(),
            )
        }
        }
    }
}



