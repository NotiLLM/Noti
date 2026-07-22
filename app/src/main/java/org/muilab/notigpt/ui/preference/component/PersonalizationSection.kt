package org.muilab.notigpt.ui.preference.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.domain.personalization.PersonalizationRecordSnapshot

@Composable
fun PersonalizationSection(
    title: String,
    emptyBody: String,
    addLabel: String,
    records: List<PersonalizationRecordSnapshot>,
    enabled: Boolean,
    onDelete: (PersonalizationRecordSnapshot) -> Unit,
    onAssistedSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    var editorText by remember { mutableStateOf("") }
    var editingRecord by remember { mutableStateOf<PersonalizationRecordSnapshot?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    val expansionState = stringResource(
        if (expanded) R.string.personalization_expanded else R.string.personalization_collapsed,
    )
    val editLabel = stringResource(R.string.personalization_edit)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { expanded = !expanded }
                .semantics { stateDescription = "$title, $expansionState" }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = records.size.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                painter = painterResource(
                    if (expanded) R.drawable.keyboard_arrow_up else R.drawable.keyboard_arrow_down,
                ),
                contentDescription = stringResource(
                    if (expanded) R.string.a11y_collapse else R.string.a11y_expand,
                ),
                modifier = Modifier.size(24.dp),
            )
        }

        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (records.isEmpty()) {
                Text(
                    text = emptyBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            } else {
                records.forEachIndexed { index, record ->
                    PersonalizationRow(
                        record = record,
                        enabled = enabled,
                        onEdit = {
                            editingRecord = record
                            editorText = record.statement
                            editorVisible = true
                        },
                        onDelete = { onDelete(record) },
                    )
                    if (index != records.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            TextButton(
                onClick = {
                    editingRecord = null
                    editorText = ""
                    editorVisible = true
                },
                enabled = enabled,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(addLabel, style = MaterialTheme.typography.bodyMedium)
            }
            if (editorVisible) {
                PersonalizationComposer(
                    value = editorText,
                    onValueChange = { editorText = it },
                    onSend = {
                        val prefix = if (editingRecord == null) addLabel else editLabel
                        onAssistedSubmit("$prefix: ${editorText.trim()}")
                        editorText = ""
                        editingRecord = null
                        editorVisible = false
                    },
                    enabled = enabled,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun PersonalizationRow(
    record: PersonalizationRecordSnapshot,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = record.statement,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEdit, enabled = enabled) {
            Icon(
                painter = painterResource(R.drawable.info),
                contentDescription = stringResource(R.string.personalization_edit_statement, record.statement),
            )
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(
                painter = painterResource(R.drawable.delete),
                contentDescription = stringResource(R.string.personalization_delete_statement, record.statement),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
