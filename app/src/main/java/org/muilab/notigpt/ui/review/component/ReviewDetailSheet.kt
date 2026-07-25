package org.muilab.notigpt.ui.review.component

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.muilab.notigpt.R
import org.muilab.notigpt.data.repository.saveditem.PendingProposedOpRepository
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.ui.notification.component.RelatedNotificationPreview
import org.muilab.notigpt.ui.review.viewmodel.ReviewViewModel
import org.muilab.notigpt.ui.saveditem.component.SavedItemChangeHistorySection
import java.text.DateFormat
import java.util.Date

private data class FieldDelta(val name: String, val oldValue: String, val newValue: String)

private data class ReviewDigest(
    val appendedContent: List<String>,
    val addedSteps: List<String>,
    val addedButtons: List<String>,
    val fields: List<FieldDelta>,
    val sourceTitles: List<String>,
)

/** Visual review digest followed by optional evidence/history and the existing full editor path. */
@Composable
fun ReviewDetailSheet(
    entry: ReviewViewModel.ReviewEntry,
    reviewViewModel: ReviewViewModel,
    onFurtherReview: () -> Unit,
    onEditSplitChild: (Int) -> Unit = {},
    onAddSplitChild: () -> Unit = {},
) {
    val item = entry.preview
    val changes by remember(entry.key) { reviewViewModel.changeLogFlow(item.savedItemId) }
        .collectAsState(initial = emptyList())
    val related by reviewViewModel.related.collectAsState()
    val digest = remember(entry.key) { buildDigest(entry) }
    var showCompare by remember(entry.key) { mutableStateOf(false) }
    var showEvidence by remember(entry.key) { mutableStateOf(false) }
    var showHistory by remember(entry.key) { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(entry.key, showEvidence) {
        if (showEvidence) reviewViewModel.loadRelated(entry)
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
            text = if (entry.splitChildren.isNotEmpty()) stringResource(
                R.string.review_split_detail_title,
                entry.splitChildren.size,
            ) else item.title.ifBlank { stringResource(R.string.ui_saved_items_untitled_task) },
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        )

        val explanation = entry.reason.trim()
        if (explanation.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(painterResource(R.drawable.info), contentDescription = null)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.review_why), style = MaterialTheme.typography.labelLarge)
                        Text(explanation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (digest.sourceTitles.isNotEmpty()) {
            DigestSection(
                icon = R.drawable.link,
                title = stringResource(R.string.review_digest_merged),
                values = digest.sourceTitles,
                container = MaterialTheme.colorScheme.tertiaryContainer,
            )
        }
        val additions = digest.appendedContent + digest.addedSteps + digest.addedButtons
        if (additions.isNotEmpty()) {
            DigestSection(
                icon = R.drawable.add,
                title = stringResource(R.string.review_digest_added),
                values = additions,
                container = MaterialTheme.colorScheme.primaryContainer,
            )
        }
        if (digest.fields.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().animateContentSize(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.refresh), contentDescription = null)
                        Text(stringResource(R.string.review_digest_changed), style = MaterialTheme.typography.titleSmall)
                    }
                    TextButton(onClick = { showCompare = !showCompare }) {
                        Text(stringResource(if (showCompare) R.string.review_compare_hide else R.string.review_compare_show))
                    }
                    if (showCompare) digest.fields.forEach { field ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(localizedFieldName(field.name), style = MaterialTheme.typography.labelMedium)
                            Text(
                                stringResource(R.string.review_compare_value, field.oldValue, field.newValue),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        if (entry.splitChildren.isNotEmpty()) {
            entry.splitChildren.forEachIndexed { index, child ->
                CompactResultCard(child.item, child.steps.size, onEdit = { onEditSplitChild(index) })
            }
            TextButton(onClick = onAddSplitChild, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_add_split_result))
            }
            entry.survivor?.let { original ->
                TextButton(onClick = { showCompare = !showCompare }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.review_original_item))
                }
                if (showCompare) ReviewMergeItemSnapshot(stringResource(R.string.review_original_item), original)
            }
        } else {
            CompactResultCard(item, entry.previewSteps.size)
        }

        if (!entry.sourceVersionIsCurrent) {
            Text(
                stringResource(R.string.review_source_changed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TextButton(onClick = { showEvidence = !showEvidence }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (showEvidence) R.string.review_evidence_hide else R.string.review_evidence_show))
        }
        if (showEvidence) {
            entry.survivor?.let { ReviewMergeItemSnapshot(stringResource(R.string.review_merge_survivor), it) }
            entry.mergeSources.forEach { ReviewMergeItemSnapshot(stringResource(R.string.review_merge_source), it) }
            val relatedValue = related.value
            if (related.entryKey == entry.key && relatedValue.recordsByKey.isNotEmpty()) {
                relatedValue.recordsByKey.forEach { (key, records) ->
                    val unit = relatedValue.unitsByKey[key] ?: return@forEach
                    RelatedNotificationPreview(
                        notiDisplayUnit = NotiDisplayUnit(unit, records),
                        evidenceRecordIds = relatedValue.evidenceRecordIds,
                    )
                }
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
            TextButton(onClick = { showHistory = !showHistory }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (showHistory) R.string.review_history_hide else R.string.review_history_show))
            }
            if (showHistory) SavedItemChangeHistorySection(changes = allHistory)
        }

        if (entry.splitChildren.isEmpty()) {
            Button(onClick = onFurtherReview, enabled = entry.sourceVersionIsCurrent, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_further_review))
            }
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun DigestSection(icon: Int, title: String, values: List<String>, container: androidx.compose.ui.graphics.Color) {
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            values.filter(String::isNotBlank).distinct().forEach { value ->
                Text("• $value", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CompactResultCard(item: SavedItem, stepCount: Int, onEdit: (() -> Unit)? = null) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.review_result), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (item.content.isNotBlank()) {
                Text(item.content, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
            if (stepCount > 0) Text(stringResource(R.string.review_result_steps, stepCount), style = MaterialTheme.typography.labelMedium)
            if (onEdit != null) TextButton(onClick = onEdit) { Text(stringResource(R.string.review_edit_result)) }
        }
    }
}

@Composable
private fun localizedFieldName(name: String): String = when (name) {
    "title" -> stringResource(R.string.review_field_title)
    "deadlineTimeString" -> stringResource(R.string.review_field_deadline)
    "startTimeString" -> stringResource(R.string.review_field_start)
    "endTimeString" -> stringResource(R.string.review_field_end)
    else -> stringResource(R.string.review_field_other, name)
}

private fun buildDigest(entry: ReviewViewModel.ReviewEntry): ReviewDigest {
    val appended = mutableListOf<String>()
    val steps = mutableListOf<String>()
    val buttons = mutableListOf<String>()
    val fields = mutableListOf<FieldDelta>()
    val before = entry.survivor?.item
    entry.group?.ops.orEmpty().forEach { pending ->
        val root = runCatching { JSONObject(pending.payload) }.getOrNull() ?: return@forEach
        val changes = root.optJSONObject("changes") ?: JSONObject()
        changes.optString("appendedContent").trim().takeIf(String::isNotBlank)?.let(appended::add)
        changes.optJSONArray("addedSteps")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.optString("text")?.takeIf(String::isNotBlank)?.let(steps::add)
        }
        changes.optJSONArray("addedButtons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                obj.optString("buttonText").ifBlank { obj.optString("label") }.takeIf(String::isNotBlank)?.let(buttons::add)
            }
        }
        changes.optJSONObject("changedFields")?.let { changed ->
            changed.keys().forEach { name ->
                val newValue = changed.optJSONObject(name)?.opt("new")?.toString().orEmpty()
                if (newValue.isNotBlank() && newValue != "null") fields += FieldDelta(name, oldFieldValue(before, name), newValue)
            }
        }
    }
    return ReviewDigest(appended, steps, buttons, fields, entry.mergeSources.map { it.item.title })
}

private fun oldFieldValue(item: SavedItem?, name: String): String {
    if (item == null) return "—"
    fun time(value: Long): String = if (value > 0L) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value)) else "—"
    return when (name) {
        "title" -> item.title
        "deadlineTimeString" -> time(item.deadlineAtMs)
        else -> "—"
    }
}

@Composable
private fun ReviewMergeItemSnapshot(label: String, snapshot: PendingProposedOpRepository.MergeSourceSnapshot) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(snapshot.item.title, style = MaterialTheme.typography.titleSmall)
            if (snapshot.item.content.isNotBlank()) Text(snapshot.item.content, style = MaterialTheme.typography.bodyMedium)
            snapshot.steps.forEach { sub -> Text("• ${sub.text}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
