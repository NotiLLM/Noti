package org.muilab.notigpt.ui.review.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.ui.notification.component.RelatedNotificationPreview
import org.muilab.notigpt.ui.reminder.component.ReminderWhatsNewBlock
import org.muilab.notigpt.ui.review.viewmodel.ReviewViewModel

/**
 * Expanded detail for a review card, shown in a bottom sheet: full content, the "what's new" block,
 * the notifications that triggered/support the item, and approve/reject actions.
 */
@Composable
fun ReviewDetailSheet(
    entry: ReviewViewModel.ReviewEntry,
    reviewViewModel: ReviewViewModel,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
) {
    val item = entry.preview
    val changes by remember(entry.key) { reviewViewModel.changeLogFlow(item.savedItemId) }
        .collectAsState(initial = emptyList())
    val related by reviewViewModel.related.collectAsState()

    androidx.compose.runtime.LaunchedEffect(entry.key) {
        reviewViewModel.loadRelated(entry)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = item.title.ifBlank { stringResource(R.string.ui_reminders_untitled_task) },
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        )

        if (item.content.isNotBlank()) {
            Text(text = item.content.trim(), style = MaterialTheme.typography.bodyLarge)
        }

        // The pipeline's explanation for this proposal — the staged model's "why you're seeing this".
        if (entry.reason.isNotBlank()) {
            Text(
                text = entry.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Staged creates propose sub-tasks that don't exist in the DB yet; list them for review.
        if (entry.group?.isCreate == true && entry.previewSubItems.isNotEmpty()) {
            entry.previewSubItems.forEach { sub ->
                Text(
                    text = "•  ${sub.title}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Reuse the existing "what's new" block; its "Got it" acts as approve here.
        ReminderWhatsNewBlock(
            reminder = item,
            changes = changes,
            onAcknowledge = onApprove,
        )

        // Related notifications, evidence highlighted (the records that triggered this item/edit).
        val relatedValue = related.value
        if (related.entryKey == entry.key && relatedValue.recordsByKey.isNotEmpty()) {
            Text(
                text = stringResource(R.string.review_triggered_this),
                style = MaterialTheme.typography.titleSmall,
            )
            relatedValue.recordsByKey.forEach { (key, records) ->
                val unit = relatedValue.unitsByKey[key] ?: return@forEach
                RelatedNotificationPreview(
                    notiDisplayUnit = NotiDisplayUnit(unit, records),
                    evidenceRecordIds = relatedValue.evidenceRecordIds,
                    contextLoaded = key in relatedValue.contextLoadedKeys,
                )
            }
        }

        // Edit opens the full editor in review mode (editing is itself the accept).
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.review_edit))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.review_reject))
            }
            Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.review_approve))
            }
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}
