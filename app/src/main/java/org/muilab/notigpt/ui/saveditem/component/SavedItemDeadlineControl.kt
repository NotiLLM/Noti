package org.muilab.notigpt.ui.saveditem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Calendar
import org.muilab.notigpt.R
import org.muilab.notigpt.util.time.getRelativeTimeStr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedItemDeadlinePickerDialog(
    currentDeadlineAtMs: Long,
    onDismiss: () -> Unit,
    onSet: (Long) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentDeadlineAtMs.takeIf { it > 0L }
            ?: System.currentTimeMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { selectedDate ->
                    val hadDeadline = currentDeadlineAtMs > 0L
                    val existing = Calendar.getInstance().apply {
                        timeInMillis = if (hadDeadline) currentDeadlineAtMs else System.currentTimeMillis()
                    }
                    val selected = Calendar.getInstance().apply {
                        timeInMillis = selectedDate
                        set(Calendar.HOUR_OF_DAY, if (hadDeadline) existing.get(Calendar.HOUR_OF_DAY) else 23)
                        set(Calendar.MINUTE, if (hadDeadline) existing.get(Calendar.MINUTE) else 59)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onSet(selected.timeInMillis)
                }
            }) { Text(stringResource(R.string.ui_action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onSet(0L) }) {
                Text(stringResource(R.string.ui_action_clear))
            }
        },
    ) { DatePicker(state = pickerState) }
}

@Composable
fun SavedItemDeadlineButton(
    deadlineAtMs: Long,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasDeadline = deadlineAtMs > 0L
    val label = if (hasDeadline) {
        getRelativeTimeStr(deadlineAtMs, context)
    } else {
        stringResource(R.string.a11y_set_deadline)
    }
    val tint = when {
        hasDeadline && deadlineAtMs < System.currentTimeMillis() -> MaterialTheme.colorScheme.error
        hasDeadline -> accent
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.flag),
            contentDescription = stringResource(R.string.a11y_set_deadline),
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}
