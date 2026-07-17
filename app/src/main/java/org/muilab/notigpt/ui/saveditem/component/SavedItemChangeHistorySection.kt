package org.muilab.notigpt.ui.saveditem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemChangeType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-language change views for a saved item's detail screen.
 *
 * [SavedItemWhatsNewBlock] shows the changes the user has not yet acknowledged (rows newer than
 * SavedItem.lastViewedChangeAt) with an explicit "Got it" action — items stay in the New/Updated
 * review state until that tap, never cleared by mere scrolling. [SavedItemChangeHistorySection] is
 * the full collapsible history. Both render for general phone users: short labeled rows, no
 * +/− code-diff styling.
 */

@Composable
fun SavedItemWhatsNewBlock(
    item: SavedItem,
    changes: List<SavedItemChangeLog>,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!item.isNewLike) return
    val unseen = changes.filter { it.createdAt > item.lastViewedChangeAt }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.saved_item_whats_new),
                style = MaterialTheme.typography.titleSmall,
            )
            if (unseen.isEmpty()) {
                // State says pending review but every row was already seen (e.g. cursor moved on
                // another device) — still explain why the badge is showing.
                Text(
                    text = stringResource(R.string.saved_item_whats_new_generic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                unseen.forEach { ChangeRow(it) }
            }
            Button(
                onClick = onAcknowledge,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.saved_item_acknowledge))
            }
        }
    }
}

@Composable
fun SavedItemChangeHistorySection(
    changes: List<SavedItemChangeLog>,
    modifier: Modifier = Modifier,
) {
    if (changes.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.saved_item_change_history, changes.size),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                painter = painterResource(if (expanded) R.drawable.keyboard_arrow_up else R.drawable.keyboard_arrow_down),
                contentDescription = if (expanded) stringResource(R.string.a11y_collapse) else stringResource(R.string.a11y_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                changes.forEach { ChangeRow(it, showDivider = true) }
            }
        }
    }
}

@Composable
private fun ChangeRow(change: SavedItemChangeLog, showDivider: Boolean = false) {
    val timeStr = remember(change.changeId) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(change.createdAt))
    }
    val typeLabel = when (change.changeType) {
        SavedItemChangeType.Created -> stringResource(R.string.change_type_created)
        SavedItemChangeType.UserEdit -> stringResource(R.string.change_type_user_edit)
        SavedItemChangeType.Regenerated -> stringResource(R.string.change_type_regenerated)
        SavedItemChangeType.Reverted -> stringResource(R.string.change_type_reverted)
        else -> stringResource(R.string.change_type_llm_update)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (showDivider) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (change.changeSummary.isNotBlank()) {
                Text(text = change.changeSummary, style = MaterialTheme.typography.bodySmall)
            }
            if (change.appendedContent.isNotBlank()) {
                Text(
                    text = stringResource(R.string.change_added_content, change.appendedContent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            parseSubTaskTitles(change.addedSubTasksJson).forEach { title ->
                Text(
                    text = stringResource(R.string.change_added_step, title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            parseRemovedSubTasks(change.removedSubTasksJson).forEach { (title, reason) ->
                val label = if (reason.isBlank()) {
                    stringResource(R.string.change_removed_step, title)
                } else {
                    stringResource(R.string.change_removed_step_reason, title, reason)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            parseChangedFields(change.changedFieldsJson).forEach { (field, oldNew) ->
                val fieldLabel = when (field) {
                    "title" -> stringResource(R.string.change_field_title)
                    "deadlineTimeString", "deadlineAtMs" -> stringResource(R.string.change_field_deadline)
                    "startTimeString" -> stringResource(R.string.change_field_start)
                    "endTimeString" -> stringResource(R.string.change_field_end)
                    // Estimated-time was removed from the app; legacy rows still carry it — hide.
                    "estimatedCompletionMinutes" -> null
                    "content" -> null // regeneration rewrites; the summary line covers it
                    else -> field
                }
                if (fieldLabel != null) {
                    Text(
                        text = stringResource(R.string.change_field_row, fieldLabel, oldNew.first, oldNew.second),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun parseSubTaskTitles(json: String): List<String> = try {
    val arr = JSONArray(json)
    buildList {
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i)?.optString("title").orEmpty()
            if (t.isNotBlank()) add(t)
        }
    }
} catch (_: Exception) {
    emptyList()
}

private fun parseRemovedSubTasks(json: String): List<Pair<String, String>> = try {
    val arr = JSONArray(json)
    buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title").ifBlank { o.optString("savedSubItemId") }
            if (title.isNotBlank()) add(title to o.optString("reason"))
        }
    }
} catch (_: Exception) {
    emptyList()
}

/** Field name → (old, new) display values, timestamps rendered as local dates. */
private fun parseChangedFields(json: String): List<Pair<String, Pair<String, String>>> = try {
    val obj = JSONObject(json)
    buildList {
        obj.keys().forEach { key ->
            val pair = obj.optJSONObject(key) ?: return@forEach
            add(key to (displayValue(key, pair.opt("old")) to displayValue(key, pair.opt("new"))))
        }
    }
} catch (_: Exception) {
    emptyList()
}

private fun displayValue(field: String, value: Any?): String {
    if (value == null) return ""
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return when {
        value is Number && field.endsWith("Ms") ->
            if (value.toLong() > 0L) fmt.format(Date(value.toLong())) else "—"
        value is String && value == "-1" -> "—"
        value is String && field.endsWith("TimeString") -> try {
            fmt.format(Date(java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()))
        } catch (_: Exception) {
            value
        }
        else -> value.toString()
    }
}
