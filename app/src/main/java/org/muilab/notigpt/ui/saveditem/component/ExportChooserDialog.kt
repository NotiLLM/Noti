package org.muilab.notigpt.ui.saveditem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R

/**
 * Simple chooser presented by a card's Export action: pick Google Tasks or Google Calendar, which
 * then hands off to the shared export-confirmation flow. Options with a null callback are hidden.
 */
@Composable
fun ExportChooserDialog(
    onDismiss: () -> Unit,
    onExportTasks: (() -> Unit)?,
    onExportCalendar: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_chooser_title)) },
        text = {
            Column {
                if (onExportTasks != null) {
                    ExportOption(
                        iconRes = R.drawable.task_add,
                        label = stringResource(R.string.google_tasks_export),
                        onClick = onExportTasks,
                    )
                }
                if (onExportCalendar != null) {
                    ExportOption(
                        iconRes = R.drawable.calendar_add,
                        label = stringResource(R.string.google_calendar_export),
                        onClick = onExportCalendar,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_action_cancel)) }
        },
    )
}

@Composable
private fun ExportOption(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
