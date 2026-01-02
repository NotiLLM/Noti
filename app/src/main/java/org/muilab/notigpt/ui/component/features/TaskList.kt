package org.muilab.notigpt.ui.component.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.model.features.TaskUnit
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.ui.viewmodel.TaskViewModel
import java.util.UUID

/**
 * TaskList composable now expects the caller to provide a Context and TaskViewModel.
 * The list is collapsible by tapping the header and will hide entirely when there are no tasks.
 * When expanded, it wraps content if small, and is limited to half of the available parent height if large.
 */
@Composable
fun TaskList(
    taskViewModel: TaskViewModel
) {

    val tasksState = taskViewModel.tasks.collectAsState()
    // Sort: deadline (if nonzero/non-negative) ascending, then estimated time ascending
    val tasks: List<TaskUnit> = tasksState.value.sortedWith(compareBy<TaskUnit> {
        if (it.deadlineTimestamp > 0) it.deadlineTimestamp else Long.MAX_VALUE
    }.thenBy { it.estimatedCompletionTime })
    val unfinished = taskViewModel.unfinishedCount.collectAsState().value

    // Hide entire card when no tasks are present
    if (tasks.isEmpty()) return

    var expanded by remember { mutableStateOf(SharedPreferencesManager.taskListDefaultExpanded) }
    // state to show the new-task dialog
    var showNewTaskDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large
            )
        ,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (expanded) "Tasks ($unfinished) ▾" else "Tasks ($unfinished) ▸",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = 16.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (expanded) {
                        // New Task button (on the left of Clear Completed)
                        OutlinedButton(
                            onClick = { showNewTaskDialog = true },
                            modifier = Modifier.padding(end = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text("New Task")
                        }

                        OutlinedButton(
                            onClick = { taskViewModel.clearCompleted() },
                            // keep it small and subtle
                            content = { Text("Clear Completed", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (expanded) {
                // Constrain the list to at most half of the parent's height but allow wrapping when small
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val halfHeight = this.maxHeight * 0.5f
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = halfHeight)
                    ) {
                        items(tasks, key = { it.taskId }) { task ->
                            TaskUIUnit(
                                taskUnit = task,
                                onCheckedChange = { checked -> taskViewModel.toggleComplete(task.taskId, checked) },
                                onRemove = { taskViewModel.delete(task.taskId) },
                                onEdit = { updated -> taskViewModel.editTask(updated) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    // Show the new-task dialog outside the Card so it isn't clipped; construct a blank TaskUnit to edit
    if (showNewTaskDialog) {
        val newTask = TaskUnit(
            taskId = UUID.randomUUID().toString(),
            taskDescription = "",
            deadlineTimestamp = -1L,
            estimatedCompletionTime = 0L,
            associatedNotis = emptySet()
        )
        TaskEditDialog(
            task = newTask,
            onDismiss = { showNewTaskDialog = false },
            onSave = { saved ->
                showNewTaskDialog = false
                taskViewModel.editTask(saved)
            }
        )
    }
}
