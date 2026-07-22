package org.muilab.notigpt.ui.saveditem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.TodoStep
import org.muilab.notigpt.ui.theme.NotiTheme

/** A checklist row. Cards render it read-only; the SavedItem detail surface edits it inline. */
@Composable
fun TodoStepRow(
    step: TodoStep,
    onToggleCompleted: (Boolean) -> Unit,
    onDelete: () -> Unit = {},
    editable: Boolean = false,
    completionEnabled: Boolean = true,
    onTextChange: (String) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onClick: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onEdit: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onExportGoogleTasks: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onExportGoogleCalendar: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") showActionButtons: Boolean = false,
) {
    var draft by remember(step.todoStepId) { mutableStateOf(step.text) }
    LaunchedEffect(step.text) {
        // Preserve a user's trailing space while they type, but still accept real external updates.
        if (TodoStep.normalizeText(draft) != step.text) draft = step.text
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TodoCompletionToggle(
            checked = step.isCompleted,
            accent = NotiTheme.semantic.taskAccent,
            onCheckedChange = onToggleCompleted,
            enabled = completionEnabled,
        )
        Spacer(Modifier.width(4.dp))

        val textStyle = if (step.isCompleted) {
            MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough)
        } else {
            MaterialTheme.typography.bodyMedium
        }

        if (editable) {
            BasicTextField(
                value = draft,
                onValueChange = { value ->
                    val withoutLineBreaks = value.replace(Regex("[\\r\\n]+"), " ")
                    draft = withoutLineBreaks
                    onTextChange(withoutLineBreaks)
                },
                singleLine = false,
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (draft.isBlank()) {
                        Text(
                            text = stringResource(R.string.step_untitled),
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = stringResource(R.string.a11y_delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Text(
                text = step.text.ifBlank { stringResource(R.string.step_untitled) },
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
