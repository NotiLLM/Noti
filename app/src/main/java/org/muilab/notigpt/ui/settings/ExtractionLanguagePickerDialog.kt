package org.muilab.notigpt.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R

/** Human-readable label for a stored extraction-language [code]. */
fun extractionLanguageLabel(code: String, originalLabel: String): String {
    if (code == EXTRACTION_LANGUAGE_ORIGINAL) return originalLabel
    val match = EXTRACTION_LANGUAGES.firstOrNull { it.code == code } ?: return code
    return if (match.nativeName == match.englishName) match.englishName
    else "${match.englishName} (${match.nativeName})"
}

/** Searchable language picker shared by Settings and the review translation action. */
@Composable
fun ExtractionLanguagePickerDialog(
    selected: String,
    originalLabel: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val normalized = query.trim().lowercase()
    val showOriginal = normalized.isBlank() ||
        originalLabel.lowercase().contains(normalized) ||
        EXTRACTION_LANGUAGE_ORIGINAL.contains(normalized)
    val filtered = EXTRACTION_LANGUAGES.filter { it.matches(normalized) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_settings_extraction_language)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.ui_settings_extraction_language_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    if (showOriginal) {
                        item {
                            LanguageRow(
                                label = originalLabel,
                                selected = selected == EXTRACTION_LANGUAGE_ORIGINAL,
                                onClick = { onSelect(EXTRACTION_LANGUAGE_ORIGINAL) },
                            )
                        }
                    }
                    items(filtered, key = { it.code }) { lang ->
                        LanguageRow(
                            label = extractionLanguageLabel(lang.code, originalLabel),
                            selected = selected == lang.code,
                            onClick = { onSelect(lang.code) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_action_cancel)) } },
    )
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
