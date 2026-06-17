package org.muilab.notigpt.ui.notification.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.util.time.getAbsoluteTimeStr
import org.muilab.notigpt.util.time.getRelativeTimeStr
import org.muilab.notigpt.util.unescapeUserText

private enum class HistoryMode { Grouped, Timeline }

@Composable
fun NotificationHistoryScreen(
    drawerViewModel: DrawerViewModel,
    searchQuery: String = "",
) {
    var mode by remember { mutableStateOf(HistoryMode.Grouped) }
    var records by remember { mutableStateOf<List<NotiRecord>>(emptyList()) }

    val normalizedQuery = searchQuery.trim().lowercase()

    LaunchedEffect(normalizedQuery) {
        records = if (normalizedQuery.isBlank()) {
            drawerViewModel.getLatestRecordsForHistory(500)
        } else {
            // Search is DB-backed and scans all notification records, not just the currently loaded page.
            drawerViewModel.searchRecordsForHistory(searchQuery)
        }
    }

    val filtered = records

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item {
            Column(Modifier.padding(16.dp)) {
                Row {
                    FilterChip(
                        selected = mode == HistoryMode.Grouped,
                        onClick = { mode = HistoryMode.Grouped },
                        label = { Text("Grouped") },
                    )
                    Spacer(Modifier.padding(4.dp))
                    FilterChip(
                        selected = mode == HistoryMode.Timeline,
                        onClick = { mode = HistoryMode.Timeline },
                        label = { Text("Timeline") },
                    )
                }
            }
        }

        if (mode == HistoryMode.Timeline) {
            items(filtered, key = { it.notiRecordId }) { record ->
                HistoryRecordCard(record = record)
            }
        } else {
            val groups = filtered.groupBy { it.notiKey }.toList().sortedByDescending { pair -> pair.second.maxOfOrNull { it.time } ?: 0L }
            items(groups, key = { it.first }) { (notiKey, groupRecords) ->
                HistoryGroupCard(notiKey = notiKey, records = groupRecords, autoExpanded = normalizedQuery.isNotBlank())
            }
        }

        if (filtered.isEmpty()) {
            item { Text("No matching notifications", modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun HistoryGroupCard(
    notiKey: String,
    records: List<NotiRecord>,
    autoExpanded: Boolean,
) {
    val context = LocalContext.current
    var expanded by remember(notiKey, autoExpanded) { mutableStateOf(autoExpanded) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            val latest = records.maxByOrNull { it.time }
            Text(latest?.title?.ifBlank { "Notification" } ?: "Notification", fontWeight = FontWeight.Bold)
            val latestTime = latest?.time ?: 0L
            val absRel = if (latestTime > 0L) {
                "${getAbsoluteTimeStr(latestTime, context)} (${getRelativeTimeStr(latestTime, context)})"
            } else ""
            Text(
                "${records.size} records${if (absRel.isNotBlank()) " · $absRel" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                records.sortedByDescending { it.time }.forEach { record ->
                    HistoryRecordContent(record)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(record: NotiRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) { HistoryRecordContent(record) }
    }
}

@Composable
private fun HistoryRecordContent(record: NotiRecord) {
    val context = LocalContext.current
    Text(record.title.ifBlank { "Notification" }, style = MaterialTheme.typography.titleSmall)
    if (record.content.isNotBlank()) {
        Text(unescapeUserText(record.content), style = MaterialTheme.typography.bodyMedium)
    }
    val absRel = if (record.time > 0L) {
        "${getAbsoluteTimeStr(record.time, context)} (${getRelativeTimeStr(record.time, context)})"
    } else ""
    if (absRel.isNotBlank()) {
        Text(
            text = absRel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
