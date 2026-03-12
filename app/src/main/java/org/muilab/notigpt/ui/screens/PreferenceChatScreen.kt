package org.muilab.notigpt.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.ChatFlowContext
import org.muilab.notigpt.model.features.ChatMessage
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.PreferenceConflict
import org.muilab.notigpt.model.features.PreferenceEntryPoint
import org.muilab.notigpt.model.features.ProposedAction
import org.muilab.notigpt.model.features.ProposedActionType
import org.muilab.notigpt.ui.viewmodel.PreferenceViewModel

/**
 * Full Chat screen for preference rule authoring (Flow 4).
 * Displayed as its own tab in the bottom navigation.
 */
@Composable
fun PreferenceChatScreen(
    preferenceViewModel: PreferenceViewModel,
) {
    val context = LocalContext.current
    val messages by preferenceViewModel.chatMessages.collectAsState()
    val pendingActions by preferenceViewModel.pendingActions.collectAsState()
    val isLoading by preferenceViewModel.isChatLoading.collectAsState()
    val activePreferences by preferenceViewModel.activePreferences.collectAsState()
    val unresolvedConflicts by preferenceViewModel.unresolvedConflicts.collectAsState()
    val chatFlowContext by preferenceViewModel.chatFlowContext.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showNoNetworkAlert by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, pendingActions.size) {
        if (messages.isNotEmpty()) {
            // Scroll to the last item (messages + proposed actions)
            val totalItems = messages.size + if (pendingActions.isNotEmpty()) 1 else 0
            listState.animateScrollToItem(maxOf(0, totalItems - 1))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top bar with clear button ───────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.pref_chat_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (messages.isNotEmpty()) {
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.pref_chat_clear_desc))
                }
            }
        }

        HorizontalDivider()

        // ── Unresolved conflicts banner ─────────────────────────
        if (unresolvedConflicts.isNotEmpty()) {
            ConflictsBanner(
                conflicts = unresolvedConflicts,
                onDismiss = { preferenceViewModel.dismissConflict(it.conflictId) },
                onResolve = { preferenceViewModel.resolveConflictInChat(it) },
            )
            HorizontalDivider()
        }

        // ── Collapsible active preferences panel ─────────────────
        ActivePreferencesPanel(
            preferences = activePreferences,
            onDelete = { preferenceViewModel.deleteActivePreference(it.id) },
        )
        HorizontalDivider()

        // ── Flow context card (when redirected from edit/delete/extract) ──
        if (chatFlowContext != null) {
            ChatFlowContextCard(flowContext = chatFlowContext!!)
        }

        // ── Empty state ─────────────────────────────────────────
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.pref_chat_empty_prompt),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.pref_chat_empty_example),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        } else {
            // ── Chat messages ───────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(messages, key = { "${it.role}_${messages.indexOf(it)}" }) { msg ->
                    ChatBubble(msg)
                }

                // Proposed actions
                if (pendingActions.isNotEmpty()) {
                    item(key = "proposed_actions") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pendingActions.forEach { action ->
                                ProposedActionCard(
                                    action = action,
                                    onConfirm = { preferenceViewModel.confirmProposedAction(action.actionId) },
                                    onDismiss = { preferenceViewModel.dismissProposedAction(action.actionId) },
                                )
                            }
                        }
                    }
                }

                // Loading indicator
                if (isLoading) {
                    item(key = "loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pref_chat_thinking), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // ── Input row ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text(stringResource(R.string.pref_chat_input_placeholder)) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                enabled = !isLoading,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (!isNetworkAvailable(context)) {
                        showNoNetworkAlert = true
                    } else {
                        preferenceViewModel.sendChatMessage(inputText.trim())
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !isLoading,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.pref_chat_send_desc))
            }
        }
    }

    // ── Clear confirmation dialog ───────────────────────────────
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.pref_chat_clear_dialog_title)) },
            text = { Text(stringResource(R.string.pref_chat_clear_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    preferenceViewModel.clearChatHistory()
                    showClearConfirm = false
                }) {
                    Text(stringResource(R.string.pref_chat_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ── No network alert ────────────────────────────────────────
    if (showNoNetworkAlert) {
        AlertDialog(
            onDismissRequest = { showNoNetworkAlert = false },
            title = { Text(stringResource(R.string.pref_chat_no_network_title)) },
            text = { Text(stringResource(R.string.pref_chat_no_network_body)) },
            confirmButton = {
                TextButton(onClick = { showNoNetworkAlert = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

// ── Chat bubble ─────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val bgColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                    )
                )
                .background(bgColor)
                .padding(12.dp),
        ) {
            Text(
                text = message.content,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ── Proposed action card ────────────────────────────────────────

@Composable
private fun ProposedActionCard(
    action: ProposedAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actionLabel = when (action.type) {
        ProposedActionType.ADD -> stringResource(R.string.pref_action_add)
        ProposedActionType.MODIFY -> stringResource(R.string.pref_action_modify)
        ProposedActionType.DELETE -> stringResource(R.string.pref_action_delete)
    }

    val cardColor = when {
        action.confirmed -> MaterialTheme.colorScheme.tertiaryContainer
        action.dismissed -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))

            if (action.type == ProposedActionType.DELETE) {
                Text(
                    text = stringResource(R.string.pref_action_remove_label, action.targetPreferenceId ?: "?"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = action.newStatement ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (action.newPreferenceType != null) {
                    Text(
                        text = stringResource(R.string.pref_action_type_label, action.newPreferenceType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!action.confirmed && !action.dismissed) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(stringResource(R.string.pref_action_confirm))
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.pref_action_dismiss))
                    }
                }
            } else if (action.confirmed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.pref_action_confirmed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
            } else if (action.dismissed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.pref_action_dismissed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Active preferences summary ──────────────────────────────────

// ── Active preferences panel (always visible, collapsible) ──────

@Composable
private fun ActivePreferencesPanel(
    preferences: List<ExtractionPreference>,
    onDelete: (ExtractionPreference) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ExtractionPreference?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.pref_active_rules_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.pref_active_rules_count, preferences.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.a11y_collapse) else stringResource(R.string.a11y_expand),
                modifier = Modifier.size(20.dp),
            )
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            if (preferences.isEmpty()) {
                Text(
                    stringResource(R.string.pref_active_rules_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )
            } else {
                preferences.forEach { pref ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "• ${pref.statement}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { deleteTarget = pref },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.pref_action_dismiss),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.pref_active_rule_delete_confirm_title)) },
            text = { Text(stringResource(R.string.pref_active_rule_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deleteTarget!!)
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.pref_chat_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

// ── Chat flow context card ──────────────────────────────────────

@Composable
private fun ChatFlowContextCard(flowContext: ChatFlowContext) {
    val headerResId = when (flowContext.entryPoint) {
        PreferenceEntryPoint.EDIT -> R.string.pref_chat_context_edit
        PreferenceEntryPoint.DELETE -> R.string.pref_chat_context_delete
        PreferenceEntryPoint.MANUAL_EXTRACT -> R.string.pref_chat_context_extract
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(headerResId),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))

            // For EDIT, show before → after
            if (flowContext.entryPoint == PreferenceEntryPoint.EDIT) {
                if (flowContext.reminderBeforeTitle != null || flowContext.reminderBeforeContent != null) {
                    Text(
                        stringResource(R.string.pref_chat_context_before),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!flowContext.reminderBeforeTitle.isNullOrBlank()) {
                        Text(flowContext.reminderBeforeTitle, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!flowContext.reminderBeforeContent.isNullOrBlank()) {
                        Text(
                            flowContext.reminderBeforeContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            maxLines = 2,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    stringResource(R.string.pref_chat_context_after),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Show the reminder title/content
            if (!flowContext.reminderTitle.isNullOrBlank()) {
                Text(flowContext.reminderTitle, style = MaterialTheme.typography.bodySmall)
            }
            if (!flowContext.reminderContent.isNullOrBlank()) {
                Text(
                    flowContext.reminderContent,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 3,
                )
            }

            // For manual extract, show notiKey if available
            if (flowContext.entryPoint == PreferenceEntryPoint.MANUAL_EXTRACT && flowContext.notiKey != null) {
                Text(
                    "Notification: ${flowContext.notiKey}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Conflicts banner ────────────────────────────────────────────

@Composable
private fun ConflictsBanner(
    conflicts: List<PreferenceConflict>,
    onDismiss: (PreferenceConflict) -> Unit,
    onResolve: (PreferenceConflict) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (conflicts.size == 1) stringResource(R.string.pref_conflict_count_one)
                       else stringResource(R.string.pref_conflict_count_many, conflicts.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded)
                    Icons.Default.KeyboardArrowUp
                else
                    Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.a11y_collapse) else stringResource(R.string.a11y_expand),
                tint = MaterialTheme.colorScheme.error,
            )
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            conflicts.forEach { conflict ->
                ConflictCard(
                    conflict = conflict,
                    onDismiss = { onDismiss(conflict) },
                    onResolve = { onResolve(conflict) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: PreferenceConflict,
    onDismiss: () -> Unit,
    onResolve: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = conflict.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(stringResource(R.string.pref_conflict_dismiss))
                }
                Button(
                    onClick = onResolve,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.pref_conflict_resolve))
                }
            }
        }
    }
}

// ── Network check ───────────────────────────────────────────────

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}


































