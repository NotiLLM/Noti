package org.muilab.notigpt.ui.screens.esm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.domain.esm.EsmUserSnapshot
import org.muilab.notigpt.util.time.getAbsoluteTimeStr
import org.muilab.notigpt.util.time.getRelativeTimeStr

@Composable
fun EsmReminderPreview(reminder: EsmUserSnapshot.ReminderPreview) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                reminder.title.ifBlank { "(Untitled)" },
                style = MaterialTheme.typography.titleMedium
            )
            if (reminder.content.isNotBlank()) {
                Text(reminder.content, style = MaterialTheme.typography.bodyMedium)
            }

            if (reminder.isTask) {
                // Deadline + ECT row (like task cards)
                val deadlineStr = if (reminder.deadlineTimestamp > 0L) {
                    "Deadline: ${getAbsoluteTimeStr(reminder.deadlineTimestamp)} (${getRelativeTimeStr(reminder.deadlineTimestamp)})"
                } else {
                    "No deadline"
                }
                val ectStr = if (reminder.estimatedCompletionMinutes > 0L) {
                    "ECT: ${reminder.estimatedCompletionMinutes}m"
                } else {
                    ""
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        deadlineStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ectStr.isNotBlank()) {
                        Text(
                            ectStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
