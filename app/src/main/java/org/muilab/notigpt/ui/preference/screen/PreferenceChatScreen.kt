package org.muilab.notigpt.ui.preference.screen

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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.res.painterResource
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.preference.model.ChatFlowContext
import org.muilab.notigpt.ui.preference.model.ChatMessage
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.PreferenceConflict
import org.muilab.notigpt.ui.preference.model.PreferenceEntryPoint
import org.muilab.notigpt.ui.preference.model.ProposedAction
import org.muilab.notigpt.ui.preference.model.ProposedActionType
import org.muilab.notigpt.model.features.UserContext
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel
import org.muilab.notigpt.ui.theme.NotiTheme

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
    val activeContexts by preferenceViewModel.activeContexts.collectAsState()
    val unresolvedConflicts by preferenceViewModel.unresolvedConflicts.collectAsState()
    val chatFlowContext by preferenceViewModel.chatFlowContext.collectAsState()
    val chatMode by preferenceViewModel.chatMode.collectAsState()

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
                    Icon(painterResource(R.drawable.delete), contentDescription = stringResource(R.string.pref_chat_clear_desc))
                }
            }
        }

        // ── Chat mode toggle — iOS-style segmented control ──────
        ChatModeSegmentedControl(
            selected = chatMode,
            onSelect = { preferenceViewModel.switchChatMode(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // ── Unresolved conflicts banner ─────────────────────────
        if (unresolvedConflicts.isNotEmpty()) {
            ConflictsBanner(
                conflicts = unresolvedConflicts,
                onDismiss = { preferenceViewModel.dismissConflict(it.conflictId) },
                onResolve = { preferenceViewModel.resolveConflictInChat(it) },
            )
        }

        // ── Collapsible panels, grouped into a single card (relevant panel first) ──
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                if (chatMode == "ABOUT_ME") {
                    UserContextPanel(
                        contexts = activeContexts,
                        onDelete = { preferenceViewModel.deleteActiveContext(it.id) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ActivePreferencesPanel(
                        preferences = activePreferences,
                        onDelete = { preferenceViewModel.deleteActivePreference(it.id) },
                    )
                } else {
                    ActivePreferencesPanel(
                        preferences = activePreferences,
                        onDelete = { preferenceViewModel.deleteActivePreference(it.id) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    UserContextPanel(
                        contexts = activeContexts,
                        onDelete = { preferenceViewModel.deleteActiveContext(it.id) },
                    )
                }
            }
        }

        // ── Flow context card (when redirected from edit/delete/extract) ──
        if (chatFlowContext != null) {
            ChatFlowContextCard(flowContext = chatFlowContext!!)
        }

        // ── Empty state ─────────────────────────────────────────
        if (messages.isEmpty()) {
            val emptyPromptRes = if (chatMode == "ABOUT_ME") R.string.pref_chat_empty_prompt_about_me else R.string.pref_chat_empty_prompt
            val emptyExampleRes = if (chatMode == "ABOUT_ME") R.string.pref_chat_empty_example_about_me else R.string.pref_chat_empty_example

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(emptyPromptRes),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(emptyExampleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    // "Learn About Me" button — only in ABOUT_ME mode
                    if (chatMode == "ABOUT_ME") {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                if (!isNetworkAvailable(context)) {
                                    showNoNetworkAlert = true
                                } else {
                                    preferenceViewModel.discoverUserContext()
                                }
                            },
                            enabled = !isLoading,
                        ) {
                            Text(stringResource(R.string.pref_chat_discover_button))
                        }
                    }
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
                            // Batch bar when the LLM proposes several rules at once. Per-card actions
                            // stay below, and the chat input remains usable for natural-language replies.
                            val unresolved = pendingActions.count { !it.confirmed && !it.dismissed }
                            if (unresolved >= 2) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.pref_batch_count, unresolved),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = { preferenceViewModel.dismissAllProposedActions() }) {
                                            Text(stringResource(R.string.pref_batch_dismiss_all))
                                        }
                                        Button(
                                            onClick = { preferenceViewModel.confirmAllProposedActions() },
                                            shape = MaterialTheme.shapes.extraLarge,
                                        ) {
                                            Text(stringResource(R.string.pref_batch_accept_all))
                                        }
                                    }
                                }
                            }
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Input row — iMessage-style pill composer + circular send button ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text(stringResource(R.string.pref_chat_input_placeholder)) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                enabled = !isLoading,
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            Spacer(Modifier.width(8.dp))
            val sendEnabled = inputText.isNotBlank() && !isLoading
            FilledIconButton(
                onClick = {
                    if (!isNetworkAvailable(context)) {
                        showNoNetworkAlert = true
                    } else {
                        preferenceViewModel.sendChatMessage(inputText.trim())
                        inputText = ""
                    }
                },
                enabled = sendEnabled,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(painterResource(R.drawable.send), contentDescription = stringResource(R.string.pref_chat_send_desc))
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

// ── Chat mode segmented control (iOS-style) ─────────────────────

@Composable
private fun ChatModeSegmentedControl(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = listOf(
        "RULES" to R.string.pref_chat_mode_rules,
        "ABOUT_ME" to R.string.pref_chat_mode_about_me,
    )
    Surface(
        modifier = modifier.height(36.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            segments.forEach { (value, labelRes) ->
                val isSelected = selected == value
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(value) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
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
    val actionLabel = when {
        action.targetType == "CONTEXT" -> when (action.type) {
            ProposedActionType.ADD -> stringResource(R.string.pref_action_add_fact)
            ProposedActionType.MODIFY -> stringResource(R.string.pref_action_modify_fact)
            ProposedActionType.DELETE -> stringResource(R.string.pref_action_delete_fact)
        }
        else -> when (action.type) {
            ProposedActionType.ADD -> stringResource(R.string.pref_action_add)
            ProposedActionType.MODIFY -> stringResource(R.string.pref_action_modify)
            ProposedActionType.DELETE -> stringResource(R.string.pref_action_delete)
        }
    }

    val cardColor: androidx.compose.ui.graphics.Color
    val cardContentColor: androidx.compose.ui.graphics.Color
    when {
        action.confirmed -> {
            cardColor = NotiTheme.semantic.keepContainer
            cardContentColor = NotiTheme.semantic.onKeepContainer
        }
        action.dismissed -> {
            cardColor = MaterialTheme.colorScheme.surfaceVariant
            cardContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> {
            cardColor = MaterialTheme.colorScheme.secondaryContainer
            cardContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = cardContentColor),
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
                    color = NotiTheme.semantic.keepAccent,
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
                painter = painterResource(if (expanded) R.drawable.keyboard_arrow_up else R.drawable.keyboard_arrow_down),
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
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { deleteTarget = pref },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.pref_action_dismiss),
                                modifier = Modifier.size(20.dp),
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

// ── User context (About Me) panel ───────────────────────────────

@Composable
private fun UserContextPanel(
    contexts: List<UserContext>,
    onDelete: (UserContext) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<UserContext?>(null) }

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
                stringResource(R.string.pref_user_context_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.pref_user_context_count, contexts.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(if (expanded) R.drawable.keyboard_arrow_up else R.drawable.keyboard_arrow_down),
                contentDescription = if (expanded) stringResource(R.string.a11y_collapse) else stringResource(R.string.a11y_expand),
                modifier = Modifier.size(20.dp),
            )
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            if (contexts.isEmpty()) {
                Text(
                    stringResource(R.string.pref_user_context_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )
            } else {
                contexts.forEach { ctx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "• ${ctx.statement}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { deleteTarget = ctx },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.pref_action_dismiss),
                                modifier = Modifier.size(20.dp),
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
            title = { Text(stringResource(R.string.pref_user_context_delete_confirm_title)) },
            text = { Text(stringResource(R.string.pref_user_context_delete_confirm_body)) },
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
            if (!flowContext.title.isNullOrBlank()) {
                Text(flowContext.title, style = MaterialTheme.typography.bodySmall)
            }
            if (!flowContext.content.isNullOrBlank()) {
                Text(
                    flowContext.content,
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
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                painter = painterResource(
                    if (expanded) R.drawable.keyboard_arrow_up else R.drawable.keyboard_arrow_down
                ),
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


































