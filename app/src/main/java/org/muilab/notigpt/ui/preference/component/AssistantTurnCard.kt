package org.muilab.notigpt.ui.preference.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.domain.personalization.AlternativeSetTurn
import org.muilab.notigpt.domain.personalization.KnowledgeCandidatesTurn
import org.muilab.notigpt.domain.personalization.MessageTurn
import org.muilab.notigpt.domain.personalization.PersonalizationAssistantTurn
import org.muilab.notigpt.domain.personalization.PersonalizationChangeSet
import org.muilab.notigpt.domain.personalization.PersonalizationMutation
import org.muilab.notigpt.domain.personalization.QuestionTurn

@Composable
fun AssistantTurnCard(
    turn: PersonalizationAssistantTurn,
    isReadOnly: Boolean,
    applyingSuggestionId: String?,
    onAnswer: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (turn) {
                is QuestionTurn -> QuestionContent(turn, isReadOnly, onAnswer)
                is AlternativeSetTurn -> AlternativeContent(
                    turn = turn,
                    isReadOnly = isReadOnly,
                    applyingSuggestionId = applyingSuggestionId,
                    onAnswer = onAnswer,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                )
                is KnowledgeCandidatesTurn -> KnowledgeContent(
                    turn = turn,
                    isReadOnly = isReadOnly,
                    applyingSuggestionId = applyingSuggestionId,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                )
                is MessageTurn -> Text(turn.message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun QuestionContent(
    turn: QuestionTurn,
    isReadOnly: Boolean,
    onAnswer: (String) -> Unit,
) {
    val skip = stringResource(R.string.personalization_skip)
    val finishForNow = stringResource(R.string.personalization_finish_for_now)
    Text(turn.question, style = MaterialTheme.typography.bodyLarge)
    Text(
        turn.rationale,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    turn.answerStarters.forEach { starter ->
        AssistChip(
            onClick = { onAnswer(starter) },
            enabled = !isReadOnly,
            label = { Text(starter, style = MaterialTheme.typography.bodyMedium) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onAnswer(skip) }, enabled = !isReadOnly) {
            Text(skip, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(
            onClick = { onAnswer(finishForNow) },
            enabled = !isReadOnly,
        ) {
            Text(finishForNow, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AlternativeContent(
    turn: AlternativeSetTurn,
    isReadOnly: Boolean,
    applyingSuggestionId: String?,
    onAnswer: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val describeDifferent = stringResource(R.string.personalization_describe_different)
    Text(turn.decisionQuestion, style = MaterialTheme.typography.bodyLarge)
    turn.alternatives.forEach { option ->
        AlternativeOption(
            option = option,
            enabled = !isReadOnly && applyingSuggestionId == null,
            onConfirm = { onConfirm(option.proposalId) },
            onDismiss = { onDismiss(option.proposalId) },
        )
    }
    TextButton(
        onClick = { onAnswer(describeDifferent) },
        enabled = !isReadOnly,
    ) {
        Text(describeDifferent, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AlternativeOption(
    option: PersonalizationChangeSet,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showReason by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (option.recommended) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        stringResource(R.string.personalization_recommended),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(option.resultingBehavior, style = MaterialTheme.typography.bodyLarge)
            option.consequence?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val reason = option.recommendationReason ?: option.reason
            if (!reason.isNullOrBlank()) {
                TextButton(onClick = { showReason = !showReason }, enabled = enabled) {
                    Text(stringResource(R.string.personalization_why_option), style = MaterialTheme.typography.bodyMedium)
                }
                if (showReason) {
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, enabled = enabled) {
                    Text(stringResource(R.string.personalization_confirm), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onDismiss, enabled = enabled) {
                    Text(stringResource(R.string.personalization_dismiss), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun KnowledgeContent(
    turn: KnowledgeCandidatesTurn,
    isReadOnly: Boolean,
    applyingSuggestionId: String?,
    onConfirm: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    turn.candidates.forEach { candidate ->
        KnowledgeCandidate(
            candidate = candidate,
            enabled = !isReadOnly && applyingSuggestionId == null,
            onConfirm = { onConfirm(candidate.proposalId) },
            onDismiss = { onDismiss(candidate.proposalId) },
        )
    }
}

@Composable
private fun KnowledgeCandidate(
    candidate: PersonalizationMutation,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showEvidence by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(candidate.statement.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            if (!candidate.reason.isNullOrBlank() || candidate.evidenceRefs.isNotEmpty()) {
                TextButton(onClick = { showEvidence = !showEvidence }, enabled = enabled) {
                    Text(stringResource(R.string.personalization_why_noti_thinks), style = MaterialTheme.typography.bodyMedium)
                }
                if (showEvidence) {
                    candidate.reason?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    candidate.evidenceRefs.forEach { reference ->
                        Text(
                            reference,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, enabled = enabled) {
                    Text(stringResource(R.string.personalization_confirm), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onDismiss, enabled = enabled) {
                    Text(stringResource(R.string.personalization_dismiss), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
