package org.muilab.notigpt.ui.saveditem.component

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
import org.muilab.notigpt.model.features.TodoStep

/**
 * Inline Todo-step list shown inside SavedItemCard on the list screen.
 *
 * Shows at most [maxVisible] incomplete steps, plus a clickable
 * "N more" / "N completed" summary that expands to reveal hidden items.
 */
@Composable
fun TodoStepListInCard(
    steps: List<TodoStep>,
    onToggleCompleted: (String, Boolean) -> Unit,
    onTodoStepClick: (TodoStep) -> Unit,
    onTodoStepEdit: (TodoStep) -> Unit,
    onTodoStepDelete: (TodoStep) -> Unit,
    onTodoStepExportGoogleTasks: (TodoStep) -> Unit,
    onTodoStepExportGoogleCalendar: (TodoStep) -> Unit,
    forceExpanded: Boolean = false,
) {
    if (steps.isEmpty()) return

    val incomplete = steps.filter { !it.isCompleted }
    val completed = steps.filter { it.isCompleted }
    // Show all when there are 3 or fewer; otherwise the first 2 + an "X more" expander.
    val visibleCount = when {
        forceExpanded -> incomplete.size
        incomplete.size <= 3 -> incomplete.size
        else -> 2
    }
    val visible = incomplete.take(visibleCount)
    val hiddenIncomplete = incomplete.drop(visibleCount)
    var showAllIncomplete by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }
    val completedVisible = showCompleted || forceExpanded

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
        // Always-visible incomplete steps.
        visible.forEach { st ->
            TodoStepRow(
                step = st,
                onToggleCompleted = { checked -> onToggleCompleted(st.todoStepId, checked) },
                onClick = { onTodoStepClick(st) },
                onEdit = { onTodoStepEdit(st) },
                onDelete = { onTodoStepDelete(st) },
                onExportGoogleTasks = { onTodoStepExportGoogleTasks(st) },
                onExportGoogleCalendar = { onTodoStepExportGoogleCalendar(st) },
            )
        }

        // Expandable hidden incomplete steps.
        if (hiddenIncomplete.isNotEmpty()) {
            AnimatedVisibility(
                visible = showAllIncomplete,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    hiddenIncomplete.forEach { st ->
                        TodoStepRow(
                            step = st,
                            onToggleCompleted = { checked -> onToggleCompleted(st.todoStepId, checked) },
                            onClick = { onTodoStepClick(st) },
                            onEdit = { onTodoStepEdit(st) },
                            onDelete = { onTodoStepDelete(st) },
                            onExportGoogleTasks = { onTodoStepExportGoogleTasks(st) },
                            onExportGoogleCalendar = { onTodoStepExportGoogleCalendar(st) },
                        )
                    }
                }
            }

            Text(
                text = if (showAllIncomplete)
                    stringResource(R.string.step_show_less)
                else
                    stringResource(R.string.step_n_more, hiddenIncomplete.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { showAllIncomplete = !showAllIncomplete }
                    .padding(start = 48.dp, top = 6.dp, bottom = 6.dp, end = 16.dp)
                    .fillMaxWidth(),
            )
        }

        // Expandable completed steps.
        if (completed.isNotEmpty()) {
            AnimatedVisibility(
                visible = completedVisible,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    completed.forEach { st ->
                        TodoStepRow(
                            step = st,
                            onToggleCompleted = { checked -> onToggleCompleted(st.todoStepId, checked) },
                            onClick = { onTodoStepClick(st) },
                            onEdit = { onTodoStepEdit(st) },
                            onDelete = { onTodoStepDelete(st) },
                            onExportGoogleTasks = { onTodoStepExportGoogleTasks(st) },
                            onExportGoogleCalendar = { onTodoStepExportGoogleCalendar(st) },
                        )
                    }
                }
            }

            if (!forceExpanded) {
                Text(
                    text = if (showCompleted)
                        stringResource(R.string.step_hide_completed)
                    else
                        stringResource(R.string.step_n_completed, completed.size),
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
}
