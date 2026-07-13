package org.muilab.notigpt.ui.settings

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import org.muilab.notigpt.ui.theme.Dimens
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Settings route for runtime configuration, account integrations, and developer controls.
 *
 * Grouped into iOS-style section cards (neutral surface, hairline dividers) rendered with Material
 * [ListItem]s. Side effects call platform/repository helpers rather than embedding long-running work
 * in composables.
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

    var useDynamicColor by remember { mutableStateOf(SharedPreferencesManager.useDynamicColor) }
    var isLeftSwipe by remember { mutableStateOf(SharedPreferencesManager.swipeDeleteLeft) }
    var extractionLanguage by remember { mutableStateOf(SharedPreferencesManager.targetExtractionLanguage) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val originalLabel = stringResource(R.string.ui_settings_extraction_language_original)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Appearance ──
        SettingsSection(stringResource(R.string.settings_section_appearance)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.ui_settings_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.ui_settings_dynamic_color_desc)) },
                trailingContent = {
                    Switch(
                        checked = useDynamicColor,
                        onCheckedChange = {
                            useDynamicColor = it
                            SharedPreferencesManager.useDynamicColor = it
                            // Re-run onCreate so NotiTheme re-reads the preference and re-applies the scheme.
                            (context as? Activity)?.recreate()
                        },
                    )
                },
                colors = transparentListItem(),
            )
            RowDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.ui_settings_delete_on_swipe)) },
                trailingContent = {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isLeftSwipe,
                            onClick = { isLeftSwipe = true; SharedPreferencesManager.swipeDeleteLeft = true },
                            label = { Text(stringResource(R.string.ui_settings_left)) },
                        )
                        FilterChip(
                            selected = !isLeftSwipe,
                            onClick = { isLeftSwipe = false; SharedPreferencesManager.swipeDeleteLeft = false },
                            label = { Text(stringResource(R.string.ui_settings_right)) },
                        )
                    }
                },
                colors = transparentListItem(),
            )
        }

        // ── Extraction ──
        SettingsSection(stringResource(R.string.settings_section_extraction)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.ui_settings_extraction_language)) },
                supportingContent = { Text(extractionLanguageLabel(extractionLanguage, originalLabel)) },
                colors = transparentListItem(),
                modifier = Modifier.clickable { showLanguagePicker = true },
            )
        }

        // ── Data ──
        SettingsSection(stringResource(R.string.settings_section_data)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.ui_settings_export_data)) },
                supportingContent = { Text(stringResource(R.string.ui_settings_export_description)) },
                colors = transparentListItem(),
                modifier = Modifier.clickable { showExportDialog = true },
            )
        }
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

    if (showExportDialog) {
        ExportDataDialog(
            onDismiss = { showExportDialog = false },
            onExport = { includeContext, includeDismissed ->
                drawerViewModel.exportAllData(includeContext, includeDismissed)
                showExportDialog = false
            },
        )
    }
}

/** A titled group of settings rows rendered as one neutral card with hairline dividers. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = Dimens.element, bottom = 6.dp),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenH),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column { content() }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun transparentListItem() =
    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

/** Export options as a confirm dialog (was an inline expanding section). */
@Composable
private fun ExportDataDialog(
    onDismiss: () -> Unit,
    onExport: (includeContext: Boolean, includeDismissed: Boolean) -> Unit,
) {
    var includeContext by remember { mutableStateOf(true) }
    var includeDismissed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_settings_export_data)) },
        text = {
            Column {
                CheckRow(
                    checked = includeContext,
                    onCheckedChange = { includeContext = it },
                    label = stringResource(R.string.ui_settings_export_include_context),
                )
                CheckRow(
                    checked = includeDismissed,
                    onCheckedChange = { includeDismissed = it },
                    label = stringResource(R.string.ui_settings_export_include_dismissed),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(includeContext, includeDismissed) }) {
                Text(stringResource(R.string.ui_action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_action_cancel)) }
        },
    )
}

@Composable
private fun CheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = checked, onClick = { onCheckedChange(!checked) }, role = Role.Checkbox)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(start = 8.dp))
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
