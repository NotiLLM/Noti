package org.muilab.notigpt.ui.review.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import org.muilab.notigpt.ui.saveditem.component.SavedItemChangeHistorySection
import org.muilab.notigpt.ui.review.viewmodel.ReviewViewModel
import org.json.JSONObject

/**
 * Reasoning layer for a review card: explanation, triggering evidence, history, and one path to the
 * complete editable review page. Decisions deliberately do not live on this intermediate layer.
 */
@Composable
fun ReviewDetailSheet(
    entry: ReviewViewModel.ReviewEntry,
    reviewViewModel: ReviewViewModel,
    onFurtherReview: () -> Unit,
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
            text = item.title.ifBlank { stringResource(R.string.ui_saved_items_untitled_task) },
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        )

        Text(
            text = stringResource(
                when (entry.operationKind) {
                    ReviewViewModel.ReviewOperationKind.Create -> R.string.review_why_created
                    ReviewViewModel.ReviewOperationKind.Update -> R.string.review_why_updated
                    ReviewViewModel.ReviewOperationKind.Merge -> R.string.review_why_merged
                }
            ),
            style = MaterialTheme.typography.titleSmall,
        )
        val explanation = entry.reason.ifBlank { item.content.trim() }
        if (explanation.isNotBlank()) {
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (entry.operationKind == ReviewViewModel.ReviewOperationKind.Merge) {
            Text(stringResource(R.string.review_merge_into, item.title), style = MaterialTheme.typography.titleSmall)
            entry.survivor?.let { survivor ->
                ReviewMergeItemSnapshot(
                    label = stringResource(R.string.review_merge_survivor),
                    snapshot = survivor,
                )
            }
            entry.mergeSources.forEach { source ->
                ReviewMergeItemSnapshot(
                    label = stringResource(R.string.review_merge_source),
                    snapshot = source,
                )
            }
            val mergeChanges = remember(entry.key) {
                entry.group?.ops.orEmpty().mapNotNull { pending ->
                    runCatching { JSONObject(pending.payload).optJSONObject("changes") }.getOrNull()
                }
            }
            mergeChanges.forEach { change ->
                val appended = change.optString("appendedContent").trim()
                if (appended.isNotBlank()) {
                    Text(stringResource(R.string.review_merge_description), style = MaterialTheme.typography.labelLarge)
                    Text(appended, style = MaterialTheme.typography.bodyMedium)
                }
                val added = change.optJSONArray("addedSubTasks")
                if (added != null && added.length() > 0) {
                    Text(stringResource(R.string.review_merge_subtasks), style = MaterialTheme.typography.labelLarge)
                    for (i in 0 until added.length()) {
                        added.optJSONObject(i)?.optString("text")?.takeIf(String::isNotBlank)?.let { text ->
                            Text("• $text", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                val fields = change.optJSONObject("changedFields")
                if (fields != null && fields.length() > 0) {
                    Text(stringResource(R.string.review_merge_fields), style = MaterialTheme.typography.labelLarge)
                    fields.keys().asSequence().forEach { name ->
                        val fieldChange = fields.optJSONObject(name)
                        val value = fieldChange?.opt("new")?.toString().orEmpty()
                        if (value.isNotBlank() && value != "null") {
                            Text(
                                stringResource(R.string.review_merge_field_value, name, value),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                val buttons = change.optJSONArray("addedButtons")
                if (buttons != null && buttons.length() > 0) {
                    Text(stringResource(R.string.review_merge_buttons), style = MaterialTheme.typography.labelLarge)
                    for (i in 0 until buttons.length()) {
                        val button = buttons.optJSONObject(i)
                        val label = button?.optString("buttonText").orEmpty()
                            .ifBlank { button?.optString("label").orEmpty() }
                            .ifBlank { button?.optString("title").orEmpty() }
                        if (label.isNotBlank()) Text("• $label", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

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
                )
            }
        }

        val sourceHistory = entry.mergeSources.flatMap { source ->
            source.history.map { row ->
                row.copy(
                    sourceSavedItemId = row.sourceSavedItemId.ifBlank { source.item.savedItemId },
                    sourceItemTitle = row.sourceItemTitle.ifBlank { source.item.title },
                )
            }
        }
        val allHistory = (changes + sourceHistory).distinctBy { it.changeId }.sortedByDescending { it.createdAt }
        if (allHistory.isNotEmpty()) {
            SavedItemChangeHistorySection(changes = allHistory)
        }

        Button(onClick = onFurtherReview, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.review_further_review))
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun ReviewMergeItemSnapshot(
    label: String,
    snapshot: org.muilab.notigpt.data.repository.saveditem.PendingProposedOpRepository.MergeSourceSnapshot,
) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(snapshot.item.title, style = MaterialTheme.typography.titleSmall)
            if (snapshot.item.content.isNotBlank()) Text(snapshot.item.content, style = MaterialTheme.typography.bodyMedium)
            snapshot.subItems.forEach { sub -> Text("• ${sub.text}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
