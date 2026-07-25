package org.muilab.notigpt.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.data.remote.auth.GoogleAuthManager
import org.muilab.notigpt.ui.auth.AccountSwitchChoice
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.common.feedback.AppSnackbar
import org.muilab.notigpt.ui.theme.Dimens
import org.muilab.notigpt.util.SharedPreferencesManager

/** Flip back on if NotiCard ever returns to single-direction swipe-to-dismiss. */
private const val SHOW_SWIPE_DIRECTION_SETTING = false

/**
 * Settings route for runtime configuration, account integrations, and developer controls.
 *
 * Grouped into iOS-style section cards (neutral surface, hairline dividers) rendered with Material
 * [ListItem]s. Side effects call platform/repository helpers rather than embedding long-running work
 * in composables.
 */
@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    onAccountChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The activity's Hilt default factory supplies the injected drawer ViewModel.
    val drawerViewModel: DrawerViewModel = viewModel()
    val deletionViewModel: DataDeletionViewModel = viewModel()
    val deletionState by deletionViewModel.state.collectAsStateWithLifecycle()

    var isLeftSwipe by remember { mutableStateOf(SharedPreferencesManager.swipeDeleteLeft) }
    var extractionLanguage by remember { mutableStateOf(SharedPreferencesManager.targetExtractionLanguage) }
    var approveSimpleOnUse by remember { mutableStateOf(SharedPreferencesManager.approveCreationsAndUpdatesOnUse) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var homeWindowHours by remember { mutableStateOf(SharedPreferencesManager.homeNotiWindowHours) }
    var showHomeWindowDialog by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<DataDeletionViewModel.Target?>(null) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    var showSwitchAccountDialog by remember { mutableStateOf(false) }
    var keepNotificationHistory by remember { mutableStateOf(true) }
    var accountActionWorking by remember { mutableStateOf(false) }
    var accountRevision by remember { mutableStateOf(0) }
    val currentAccount = remember(accountRevision) { GoogleAuthManager.currentUser() }
    val originalLabel = stringResource(R.string.ui_settings_extraction_language_original)
    val cloudDeletedMessage = stringResource(R.string.ui_settings_delete_cloud_done)
    val historyClearedMessage = stringResource(R.string.ui_settings_clear_history_done)
    val deletionFailedMessage = stringResource(R.string.ui_settings_delete_failed)
    val switchCancelledMessage = stringResource(
        R.string.ui_settings_switch_cancelled,
        currentAccount?.email ?: currentAccount?.displayName ?: currentAccount?.uid.orEmpty(),
    )
    val switchFailedMessage = stringResource(R.string.ui_settings_switch_failed)
    val switchCompleteMessage = stringResource(R.string.ui_settings_switch_complete)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Account ──
        SettingsSection(stringResource(R.string.settings_section_account)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.ui_settings_signed_in_as)) },
                supportingContent = {
                    Text(currentAccount?.email ?: currentAccount?.displayName ?: currentAccount?.uid.orEmpty())
                },
                colors = transparentListItem(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.ui_settings_switch_account)) },
                supportingContent = { Text(stringResource(R.string.ui_settings_switch_account_desc)) },
                colors = transparentListItem(),
                modifier = Modifier.clickable(enabled = !accountActionWorking) {
                    showSwitchAccountDialog = true
                },
            )
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.ui_settings_sign_out),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                supportingContent = { Text(stringResource(R.string.ui_settings_sign_out_desc)) },
                colors = transparentListItem(),
                modifier = Modifier.clickable(enabled = !accountActionWorking) {
                    showSignOutConfirmation = true
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.ui_settings_approve_simple_on_use)) },
                supportingContent = { Text(stringResource(R.string.ui_settings_approve_simple_on_use_desc)) },
                trailingContent = {
                    Checkbox(
                        checked = approveSimpleOnUse,
                        onCheckedChange = null,
                    )
                },
                colors = transparentListItem(),
                modifier = Modifier.clickable {
                    approveSimpleOnUse = !approveSimpleOnUse
                    SharedPreferencesManager.approveCreationsAndUpdatesOnUse = approveSimpleOnUse
                },
            )
        }

        // ── Appearance ──
        // Swipe-dismiss now works both directions, so the swipe-direction setting below is hidden
        // (not deleted — flip SHOW_SWIPE_DIRECTION_SETTING back on if single-direction swipe returns).
        if (SHOW_SWIPE_DIRECTION_SETTING) {
            SettingsSection(stringResource(R.string.settings_section_appearance)) {
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

        // ── Home ──
        SettingsSection(stringResource(R.string.settings_section_home)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.ui_settings_home_window)) },
                supportingContent = { Text(stringResource(R.string.ui_settings_home_window_desc)) },
                trailingContent = {
                    Text(
                        text = stringResource(R.string.home_noti_window_hours, homeWindowHours),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = transparentListItem(),
                modifier = Modifier.clickable { showHomeWindowDialog = true },
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
            ListItem(
                headlineContent = { Text(stringResource(R.string.ui_settings_delete_cloud_data)) },
                supportingContent = { Text(stringResource(R.string.ui_settings_delete_cloud_data_desc)) },
                colors = transparentListItem(),
                modifier = Modifier.clickable {
                    pendingDeletion = DataDeletionViewModel.Target.CloudGeneratedData
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.ui_settings_clear_notification_history)) },
                supportingContent = { Text(stringResource(R.string.ui_settings_clear_notification_history_desc)) },
                colors = transparentListItem(),
                modifier = Modifier.clickable {
                    pendingDeletion = DataDeletionViewModel.Target.LocalNotificationHistory
                },
            )
        }
    }

    if (showSwitchAccountDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchAccountDialog = false },
            title = { Text(stringResource(R.string.ui_settings_switch_account)) },
            text = {
                Column {
                    Text(stringResource(R.string.ui_settings_switch_prepare_message))
                    AccountSwitchChoice(
                        selected = keepNotificationHistory,
                        onClick = { keepNotificationHistory = true },
                        title = stringResource(R.string.account_switch_keep_history),
                        warning = stringResource(R.string.account_switch_keep_history_warning),
                    )
                    AccountSwitchChoice(
                        selected = !keepNotificationHistory,
                        onClick = { keepNotificationHistory = false },
                        title = stringResource(R.string.account_switch_start_clean),
                        warning = stringResource(R.string.account_switch_start_clean_warning),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !accountActionWorking,
                    onClick = {
                        showSwitchAccountDialog = false
                        accountActionWorking = true
                        scope.launch {
                            try {
                                GoogleAuthManager.prepareAccountSwitch(context)
                                when (val outcome = GoogleAuthManager.signIn(context)) {
                                    is GoogleAuthManager.SignInOutcome.SignedIn -> {
                                        accountRevision += 1
                                        onAccountChanged()
                                        AppSnackbar.show(switchCompleteMessage)
                                    }
                                    is GoogleAuthManager.SignInOutcome.AccountSwitchRequired -> {
                                        GoogleAuthManager.completeAccountSwitch(
                                            context = context,
                                            user = outcome.user,
                                            keepNotificationHistory = keepNotificationHistory,
                                        )
                                        accountRevision += 1
                                        onAccountChanged()
                                        AppSnackbar.show(switchCompleteMessage)
                                    }
                                    GoogleAuthManager.SignInOutcome.Cancelled -> {
                                        AppSnackbar.show(switchCancelledMessage)
                                    }
                                    is GoogleAuthManager.SignInOutcome.Failed -> {
                                        AppSnackbar.show(switchFailedMessage)
                                    }
                                }
                            } catch (_: Throwable) {
                                AppSnackbar.show(switchFailedMessage)
                            } finally {
                                accountActionWorking = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.ui_action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchAccountDialog = false }) {
                    Text(stringResource(R.string.ui_action_cancel))
                }
            },
        )
    }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text(stringResource(R.string.ui_settings_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.ui_settings_sign_out_confirm_message)) },
            confirmButton = {
                TextButton(
                    enabled = !accountActionWorking,
                    onClick = {
                        showSignOutConfirmation = false
                        accountActionWorking = true
                        scope.launch {
                            GoogleAuthManager.signOut(context)
                            onSignedOut()
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.ui_settings_sign_out),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) {
                    Text(stringResource(R.string.ui_action_cancel))
                }
            },
        )
    }

    pendingDeletion?.let { target ->
        val isCloud = target == DataDeletionViewModel.Target.CloudGeneratedData
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = {
                Text(
                    stringResource(
                        if (isCloud) R.string.ui_settings_delete_cloud_confirm_title
                        else R.string.ui_settings_clear_history_confirm_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (isCloud) R.string.ui_settings_delete_cloud_confirm_message
                        else R.string.ui_settings_clear_history_confirm_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = deletionState != DataDeletionViewModel.State.Working,
                    onClick = {
                        pendingDeletion = null
                        deletionViewModel.delete(target)
                    },
                ) { Text(stringResource(R.string.ui_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(R.string.ui_action_cancel))
                }
            },
        )
    }

    LaunchedEffect(deletionState) {
        when (val state = deletionState) {
            is DataDeletionViewModel.State.Finished -> {
                AppSnackbar.show(
                    if (state.target == DataDeletionViewModel.Target.CloudGeneratedData) {
                        cloudDeletedMessage
                    } else {
                        historyClearedMessage
                    },
                )
                deletionViewModel.consumeResult()
            }
            is DataDeletionViewModel.State.Failed -> {
                AppSnackbar.show(deletionFailedMessage)
                deletionViewModel.consumeResult()
            }
            else -> Unit
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

    if (showHomeWindowDialog) {
        HomeWindowDialog(
            current = homeWindowHours,
            onConfirm = { hours ->
                homeWindowHours = hours
                SharedPreferencesManager.homeNotiWindowHours = hours
                showHomeWindowDialog = false
            },
            onDismiss = { showHomeWindowDialog = false },
        )
    }
}

/** Numeric entry for the home "Past X hours" window. Positive integers only (no zero / "All"). */
@Composable
private fun HomeWindowDialog(current: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed >= 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_settings_home_window)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.ui_settings_home_window_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(4) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.ui_settings_home_window_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { parsed?.let(onConfirm) }) {
                Text(stringResource(R.string.ui_action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_action_cancel)) } },
    )
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
