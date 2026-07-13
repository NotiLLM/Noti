package org.muilab.notigpt.ui.settings

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.muilab.notigpt.R
import org.muilab.notigpt.data.repository.notification.NotiRepositoryProvider
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModelFactory
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Settings route for runtime configuration, account integrations, and developer controls.
 *
 * Keep settings grouped by user-visible concern. Side effects should call platform/repository helpers rather than
 * being embedded as long-running work in composables.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    // Get or create DrawerViewModel for export functionality
    val drawerViewModel: DrawerViewModel = viewModel(
        factory = DrawerViewModelFactory(
            application = context.applicationContext as Application,
            notiRepository = NotiRepositoryProvider.provideNotiRepository(context)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // --- Dynamic color (Material You) toggle ---
        var useDynamicColor by remember { mutableStateOf(SharedPreferencesManager.useDynamicColor) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    stringResource(R.string.ui_settings_dynamic_color),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.ui_settings_dynamic_color_desc),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = useDynamicColor,
                onCheckedChange = {
                    useDynamicColor = it
                    SharedPreferencesManager.useDynamicColor = it
                    // Re-run onCreate so NotiTheme re-reads the preference and re-applies the scheme.
                    (context as? Activity)?.recreate()
                },
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        var isLeftSwipe by remember { mutableStateOf(SharedPreferencesManager.swipeDeleteLeft) }

        // Simple radio row to choose swipe-delete direction
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ui_settings_delete_on_swipe), modifier = Modifier.padding(end = 8.dp))

            // Left Option
            Row(modifier = Modifier.selectable(
                selected = isLeftSwipe,
                onClick = {
                    isLeftSwipe = true
                    SharedPreferencesManager.swipeDeleteLeft = true
                },
                role = Role.RadioButton
            )) {
                RadioButton(
                    selected = isLeftSwipe,
                    onClick = {
                        isLeftSwipe = true
                        SharedPreferencesManager.swipeDeleteLeft = true
                    }
                )
                Text(stringResource(R.string.ui_settings_left), modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            }

            Spacer(modifier = Modifier.size(16.dp))

            // Right Option
            Row(modifier = Modifier.selectable(
                selected = !isLeftSwipe,
                onClick = {
                    isLeftSwipe = false
                    SharedPreferencesManager.swipeDeleteLeft = false
                },
                role = Role.RadioButton
            )) {
                RadioButton(
                    selected = !isLeftSwipe,
                    onClick = {
                        isLeftSwipe = false
                        SharedPreferencesManager.swipeDeleteLeft = false
                    }
                )
                Text(stringResource(R.string.ui_settings_right), modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            }
        }

        Spacer(modifier = Modifier.size(12.dp))


        // --- Extraction language preference ---
        var extractionLanguage by remember { mutableStateOf(SharedPreferencesManager.targetExtractionLanguage) }
        var showLanguagePicker by remember { mutableStateOf(false) }
        val originalLabel = stringResource(R.string.ui_settings_extraction_language_original)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLanguagePicker = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.ui_settings_extraction_language),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            Text(
                extractionLanguageLabel(extractionLanguage, originalLabel),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showLanguagePicker) {
            ExtractionLanguagePickerDialog(
                selected = extractionLanguage,
                originalLabel = originalLabel,
                onSelect = { value ->
                    extractionLanguage = value
                    SharedPreferencesManager.targetExtractionLanguage = value
                    showLanguagePicker = false
                },
                onDismiss = { showLanguagePicker = false },
            )
        }

        Spacer(modifier = Modifier.size(24.dp))

        // --- Export Data Section ---
        var showExportOptions by remember { mutableStateOf(false) }
        var includeContext by remember { mutableStateOf(true) }
        var includeDismissed by remember { mutableStateOf(false) }

        Text(
            stringResource(R.string.ui_settings_export_data),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
        )

        Text(
            stringResource(R.string.ui_settings_export_description),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )

        // Export options
        if (!showExportOptions) {
            Button(
                onClick = { showExportOptions = true },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                Text(stringResource(R.string.ui_settings_export_data))
            }
        } else {
            // Show options when expanded
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeContext,
                    onCheckedChange = { includeContext = it }
                )
                Text(
                    stringResource(R.string.ui_settings_export_include_context),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeDismissed,
                    onCheckedChange = { includeDismissed = it }
                )
                Text(
                    stringResource(R.string.ui_settings_export_include_dismissed),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        drawerViewModel.exportAllData(includeContext, includeDismissed)
                        showExportOptions = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.ui_action_ok))
                }
                TextButton(
                    onClick = { showExportOptions = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.ui_action_cancel))
                }
            }
        }
    }
}

/** Human-readable label for a stored extraction-language [code]. */
private fun extractionLanguageLabel(code: String, originalLabel: String): String {
    if (code == EXTRACTION_LANGUAGE_ORIGINAL) return originalLabel
    val match = EXTRACTION_LANGUAGES.firstOrNull { it.code == code } ?: return code
    return if (match.nativeName == match.englishName) match.englishName
    else "${match.englishName} (${match.nativeName})"
}

/**
 * Searchable language picker. "Original language" is pinned to the top as the default; the search box
 * filters the curated list by English name, native name, or code.
 */
@Composable
private fun ExtractionLanguagePickerDialog(
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
    Row(
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

