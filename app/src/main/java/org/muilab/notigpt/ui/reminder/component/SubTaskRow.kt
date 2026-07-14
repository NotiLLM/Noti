package org.muilab.notigpt.ui.reminder.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
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
import org.json.JSONArray
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.ui.common.clipboard.AndroidClipboardController
import org.muilab.notigpt.ui.theme.NotiTheme

/**
 * A single sub-task row used in both ReminderCard (list) and ReminderDetailScreen.
 *
 * Layout: [Checkbox] [Title] ... [MoreVert], with the sub-task's LLM copy/link buttons
 * (parsed from [SavedSubItem.buttons]) shown beneath the title line as a FlowRow.
 *
 * Clicking the title row triggers [onClick] (navigate to sub-task detail).
 * The MoreVert dropdown offers: Edit, Export to Google Tasks, Export to Google Calendar, Delete.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedSubItemRow(
    subTask: SavedSubItem,
    onToggleCompleted: (Boolean) -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExportGoogleTasks: () -> Unit,
    onExportGoogleCalendar: () -> Unit,
    showActionButtons: Boolean = false,
) {
    val context = LocalContext.current
    val clipboard = remember(context) { AndroidClipboardController(context) }
    var menuExpanded by remember { mutableStateOf(false) }

    val buttons = remember(subTask.buttons) {
        try {
            val arr = JSONArray(subTask.buttons)
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskCompletionToggle(
                checked = subTask.isCompleted,
                accent = NotiTheme.semantic.taskAccent,
                onCheckedChange = onToggleCompleted,
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

            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    painterResource(R.drawable.more_vert),
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

        // Sub-task copy/link buttons, aligned under the title (past the checkbox gutter).
        if (showActionButtons && buttons.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp, end = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                buttons.forEach { (buttonText, intent, type) ->
                    val iconRes = if (type == "copy") R.drawable.copy else R.drawable.link
                    AssistChip(
                        onClick = {
                            if (type == "copy") {
                                clipboard.copyPlainText("subtask_button", intent)
                            } else {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(intent)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                } catch (_: Exception) { /* no handler */ }
                            }
                        },
                        label = { Text(buttonText, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = if (type == "copy") stringResource(R.string.a11y_copy_text) else stringResource(R.string.a11y_open_link),
                                modifier = Modifier.size(14.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}
