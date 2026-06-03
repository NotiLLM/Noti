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
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel

private enum class HistoryMode { Grouped, Timeline }

@Composable
fun NotificationHistoryScreen(
    drawerViewModel: DrawerViewModel,
) {
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(HistoryMode.Grouped) }
    var records by remember { mutableStateOf<List<NotiRecord>>(emptyList()) }

    LaunchedEffect(Unit) {
        records = drawerViewModel.getLatestRecordsForHistory(500).filter { !it.isNew }
    }

    val normalizedQuery = query.trim().lowercase()
    val filtered = remember(records, normalizedQuery) {
        if (normalizedQuery.isBlank()) records else records.filter { record ->
            listOf(record.title, record.content, record.person, record.extraSubText)
                .any { it.lowercase().contains(normalizedQuery) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search History") },
                )
                Spacer(Modifier.height(8.dp))
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
    var expanded by remember(notiKey, autoExpanded) { mutableStateOf(autoExpanded) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(16.dp)) {
            val latest = records.maxByOrNull { it.time }
            Text(latest?.title?.ifBlank { "Notification" } ?: "Notification", fontWeight = FontWeight.Bold)
            Text("${records.size} records", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    ) {
        Column(Modifier.padding(16.dp)) { HistoryRecordContent(record) }
    }
}

@Composable
private fun HistoryRecordContent(record: NotiRecord) {
    Text(record.title.ifBlank { "Notification" }, style = MaterialTheme.typography.titleSmall)
    if (record.content.isNotBlank()) {
        Text(record.content, style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        text = if (record.isNew) "New" else "History",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
