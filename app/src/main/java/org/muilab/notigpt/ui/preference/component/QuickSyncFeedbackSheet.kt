package org.muilab.notigpt.ui.preference.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel

/** Explicit, process-local Quick Sync feedback; dismissing creates no personalization. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSyncFeedbackSheet(
    preferenceViewModel: PreferenceViewModel,
) {
    val state by preferenceViewModel.uiState.collectAsStateWithLifecycle()
    val feedback = state.quickSyncFeedback ?: return
    ModalBottomSheet(
        onDismissRequest = preferenceViewModel::dismissQuickSyncFeedback,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.personalization_tell_why),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.personalization_feedback_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            feedback.reasonStarters.forEach { reason ->
                AssistChip(
                    onClick = { preferenceViewModel.submitQuickSyncFeedback(reason) },
                    enabled = !state.isReadOnly && !state.isAssistantLoading,
                    label = { Text(reason, style = MaterialTheme.typography.bodyMedium) },
                )
            }
            OutlinedTextField(
                value = feedback.draftText,
                onValueChange = preferenceViewModel::updateQuickSyncDraft,
                enabled = !state.isReadOnly && !state.isAssistantLoading,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource(R.string.personalization_feedback_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
            )
            Button(
                onClick = { preferenceViewModel.submitQuickSyncFeedback(feedback.draftText) },
                enabled = !state.isReadOnly &&
                    !state.isAssistantLoading &&
                    feedback.draftText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.personalization_send), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
