package org.muilab.notigpt.ui.preference.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel.QuickSyncReview
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel.ReviewRule

/**
 * Review dialog for a quick-sync result: confirm/veto each proposed rule change. Created and updated
 * rules start checked (they already applied — unchecking reverts them); deletion proposals start
 * unchecked (checking confirms the deletion).
 */
@Composable
fun PreferenceQuickSyncReviewDialog(preferenceViewModel: PreferenceViewModel) {
    val review by preferenceViewModel.quickSyncReview.collectAsState()
    val current = review ?: return

    // Checked state per rule id; creates/updates default true, deletions default false.
    val checked = remember(current) {
        mutableStateMapOf<String, Boolean>().apply {
            current.rules.forEach { put(it.id, it.kind != ReviewRule.Kind.Deleted) }
        }
    }

    AlertDialog(
        onDismissRequest = { preferenceViewModel.dismissQuickSyncReview() },
        title = { Text(stringResource(R.string.pref_review_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                current.rules.forEach { rule ->
                    ReviewRuleRow(
                        rule = rule,
                        checked = checked[rule.id] ?: false,
                        onToggle = { checked[rule.id] = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val confirmed = checked.filterValues { it }.keys.toSet()
                preferenceViewModel.applyReviewDecisions(current, confirmed)
            }) { Text(stringResource(R.string.ui_action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { preferenceViewModel.dismissQuickSyncReview() }) {
                Text(stringResource(R.string.ui_action_cancel))
            }
        },
    )
}

@Composable
private fun ReviewRuleRow(rule: ReviewRule, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val kindLabel = when (rule.kind) {
        ReviewRule.Kind.Created -> stringResource(R.string.pref_review_kind_created)
        ReviewRule.Kind.Updated -> stringResource(R.string.pref_review_kind_updated)
        ReviewRule.Kind.Deleted -> stringResource(R.string.pref_review_kind_deleted)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Spacer(Modifier.size(4.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = kindLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = rule.statement, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
