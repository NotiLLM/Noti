package org.muilab.notigpt.ui.preference.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.preference.component.AssistantTurnCard
import org.muilab.notigpt.ui.preference.component.PersonalizationComposer
import org.muilab.notigpt.ui.preference.component.PersonalizationSection
import org.muilab.notigpt.ui.preference.model.PendingPersonalizationSuggestion
import org.muilab.notigpt.ui.preference.model.PersonalizationTranscriptItem
import org.muilab.notigpt.ui.preference.model.ACTIVATION_PENDING_MESSAGE
import org.muilab.notigpt.ui.preference.model.NETWORK_ASSISTANT_MESSAGE
import org.muilab.notigpt.ui.preference.model.STALE_SUGGESTION_MESSAGE
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel

@Composable
fun PersonalizationScreen(
    preferenceViewModel: PreferenceViewModel,
) {
    val state by preferenceViewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.isReadOnly) {
                item(key = "offline") { OfflineBanner() }
            }

            item(key = "confirmed") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        PersonalizationSection(
                            title = stringResource(R.string.personalization_attention_heading),
                            emptyBody = stringResource(R.string.personalization_attention_empty),
                            addLabel = stringResource(R.string.personalization_add_preference),
                            records = state.generalPreferences,
                            enabled = !state.isReadOnly,
                            onDelete = preferenceViewModel::deleteConfirmed,
                            onAssistedSubmit = preferenceViewModel::sendMessage,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PersonalizationSection(
                            title = stringResource(R.string.personalization_extraction_heading),
                            emptyBody = stringResource(R.string.personalization_extraction_empty),
                            addLabel = stringResource(R.string.personalization_add_preference),
                            records = state.extractionPreferences,
                            enabled = !state.isReadOnly,
                            onDelete = preferenceViewModel::deleteConfirmed,
                            onAssistedSubmit = preferenceViewModel::sendMessage,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PersonalizationSection(
                            title = stringResource(R.string.personalization_knowledge_heading),
                            emptyBody = stringResource(R.string.personalization_knowledge_empty),
                            addLabel = stringResource(R.string.personalization_add_fact),
                            records = state.userKnowledge,
                            enabled = !state.isReadOnly,
                            onDelete = preferenceViewModel::deleteConfirmed,
                            onAssistedSubmit = preferenceViewModel::sendMessage,
                        )
                    }
                }
            }

            item(key = "discovery") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.personalization_learn_disclosure),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = preferenceViewModel::discoverAboutMe,
                            enabled = !state.isReadOnly && !state.isAssistantLoading,
                        ) {
                            Text(
                                stringResource(R.string.personalization_learn_about_me),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            if (state.pendingSuggestions.isNotEmpty()) {
                item(key = "pending") {
                    PendingSuggestions(
                        suggestions = state.pendingSuggestions,
                        isReadOnly = state.isReadOnly,
                        applyingSuggestionId = state.applyingSuggestionId,
                        onConfirm = preferenceViewModel::confirmSuggestion,
                        onDismiss = preferenceViewModel::dismissSuggestion,
                    )
                }
            }

            itemsIndexed(
                items = state.transcript,
                key = { index, item -> "$index-${item::class.simpleName}" },
            ) { _, item ->
                when (item) {
                    is PersonalizationTranscriptItem.UserMessage -> UserMessage(item.text)
                    is PersonalizationTranscriptItem.AssistantTurn -> AssistantTurnCard(
                        turn = item.turn,
                        isReadOnly = state.isReadOnly,
                        applyingSuggestionId = state.applyingSuggestionId,
                        onAnswer = preferenceViewModel::answerWith,
                        onConfirm = preferenceViewModel::confirmSuggestion,
                        onDismiss = preferenceViewModel::dismissSuggestion,
                    )
                    is PersonalizationTranscriptItem.Receipt -> Receipt(item.message)
                }
            }

            if (state.isAssistantLoading) {
                item(key = "loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            stringResource(R.string.personalization_thinking),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            state.errorMessage?.let { error ->
                item(key = "error") {
                    val displayedError = when (error) {
                        STALE_SUGGESTION_MESSAGE -> stringResource(R.string.personalization_stale)
                        NETWORK_ASSISTANT_MESSAGE -> stringResource(R.string.personalization_network_error)
                        ACTIVATION_PENDING_MESSAGE -> stringResource(R.string.personalization_activation_pending)
                        else -> error
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                displayedError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            val staleSuggestionId = state.staleSuggestionId
                            val errorActionLabel = state.errorActionLabel
                            if (errorActionLabel != null && staleSuggestionId != null) {
                                TextButton(
                                    onClick = {
                                        preferenceViewModel.refreshSuggestion(staleSuggestionId)
                                    },
                                    enabled = !state.isReadOnly,
                                ) {
                                    Text(
                                        stringResource(R.string.personalization_refresh_suggestion),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PersonalizationComposer(
            value = state.draftText,
            onValueChange = preferenceViewModel::updateDraft,
            onSend = preferenceViewModel::sendMessage,
            enabled = !state.isReadOnly && !state.isAssistantLoading,
        )
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = stringResource(R.string.personalization_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun UserMessage(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun Receipt(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun PendingSuggestions(
    suggestions: List<PendingPersonalizationSuggestion>,
    isReadOnly: Boolean,
    applyingSuggestionId: String?,
    onConfirm: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.personalization_pending_suggestions),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    suggestions.size.toString(),
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
                suggestions.forEach { suggestion ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            suggestion.changeSet.resultingBehavior,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onConfirm(suggestion.changeSet.proposalId) },
                                enabled = !isReadOnly && applyingSuggestionId == null,
                            ) {
                                Text(stringResource(R.string.personalization_confirm), style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(
                                onClick = { onDismiss(suggestion.changeSet.proposalId) },
                                enabled = !isReadOnly && applyingSuggestionId == null,
                            ) {
                                Text(stringResource(R.string.personalization_dismiss), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
