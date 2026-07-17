package org.muilab.notigpt.ui.notification.screen

import org.muilab.notigpt.ui.common.feedback.AppSnackbar
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.ui.common.component.AppIconImage
import org.muilab.notigpt.ui.common.component.EmptyState
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.util.time.getAbsoluteTimeStr
import org.muilab.notigpt.util.time.getRelativeTimeStr
import org.muilab.notigpt.util.unescapeUserText

private enum class HistoryMode { Grouped, Timeline }

/** A long-pressed record (or group of records) the action menu operates on. */
private data class HistoryMenuTarget(
    val notiKey: String,
    val recordIds: List<String>,
    val copyText: String,
)

@Composable
fun NotificationHistoryScreen(
    drawerViewModel: DrawerViewModel,
    searchQuery: String = "",
) {
    var mode by remember { mutableStateOf(HistoryMode.Grouped) }
    var records by remember { mutableStateOf<List<NotiRecord>>(emptyList()) }
    var unitsByKey by remember { mutableStateOf<Map<String, NotiUnit>>(emptyMap()) }
    var menuTarget by remember { mutableStateOf<HistoryMenuTarget?>(null) }

    val normalizedQuery = searchQuery.trim().lowercase()

    LaunchedEffect(normalizedQuery) {
        records = if (normalizedQuery.isBlank()) {
            drawerViewModel.getLatestRecordsForHistory(500)
        } else {
            // Search is DB-backed and scans all notification records, not just the currently loaded page.
            drawerViewModel.searchRecordsForHistory(searchQuery)
        }
    }

    // Resolve app icons for the visible records so the history cards read like NotiCards.
    LaunchedEffect(records) {
        unitsByKey = drawerViewModel.getNotiUnitsForHistory(records.map { it.notiKey })
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
                        label = { Text(stringResource(R.string.history_grouped)) },
                    )
                    Spacer(Modifier.padding(4.dp))
                    FilterChip(
                        selected = mode == HistoryMode.Timeline,
                        onClick = { mode = HistoryMode.Timeline },
                        label = { Text(stringResource(R.string.history_timeline)) },
                    )
                }
            }
        }

        if (mode == HistoryMode.Timeline) {
            items(filtered, key = { it.notiRecordId }) { record ->
                HistoryRecordCard(
                    record = record,
                    unit = unitsByKey[record.notiKey],
                    onLongPress = { menuTarget = it },
                )
            }
        } else {
            val groups = filtered.groupBy { it.notiKey }.toList().sortedByDescending { pair -> pair.second.maxOfOrNull { it.time } ?: 0L }
            items(groups, key = { it.first }) { (notiKey, groupRecords) ->
                HistoryGroupCard(
                    notiKey = notiKey,
                    records = groupRecords,
                    unit = unitsByKey[notiKey],
                    autoExpanded = normalizedQuery.isNotBlank(),
                    onLongPress = { menuTarget = it },
                )
            }
        }

        if (filtered.isEmpty()) {
            item { EmptyState(R.drawable.search_off, stringResource(R.string.history_no_match)) }
        }
    }

    menuTarget?.let { target ->
        HistoryActionMenu(
            drawerViewModel = drawerViewModel,
            target = target,
            onDismiss = { menuTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryGroupCard(
    notiKey: String,
    records: List<NotiRecord>,
    unit: NotiUnit?,
    autoExpanded: Boolean,
    onLongPress: (HistoryMenuTarget) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember(notiKey, autoExpanded) { mutableStateOf(autoExpanded) }
    val latest = records.maxByOrNull { it.time }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = {
                    onLongPress(
                        HistoryMenuTarget(
                            notiKey = notiKey,
                            recordIds = records.map { it.notiRecordId },
                            copyText = groupCopyText(records),
                        )
                    )
                },
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                unit?.bitmap?.let { bmp ->
                    AppIconImage(bitmap = bmp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    latest?.title?.ifBlank { stringResource(R.string.history_notification) } ?: stringResource(R.string.history_notification),
                    fontWeight = FontWeight.Bold,
                )
            }
            val latestTime = latest?.time ?: 0L
            val absRel = if (latestTime > 0L) {
                "${getAbsoluteTimeStr(latestTime, context)} (${getRelativeTimeStr(latestTime, context)})"
            } else ""
            Text(
                stringResource(R.string.history_records_count, records.size) + if (absRel.isNotBlank()) " · $absRel" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                records.sortedByDescending { it.time }.forEach { record ->
                    HistoryRecordContent(record = record, onLongPress = onLongPress)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRecordCard(
    record: NotiRecord,
    unit: NotiUnit?,
    onLongPress: (HistoryMenuTarget) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { onLongPress(record.toMenuTarget()) },
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            unit?.bitmap?.let { bmp ->
                AppIconImage(bitmap = bmp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column { HistoryRecordContent(record = record, onLongPress = onLongPress) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRecordContent(record: NotiRecord, onLongPress: (HistoryMenuTarget) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = { onLongPress(record.toMenuTarget()) },
        )
    ) {
        Text(record.title.ifBlank { stringResource(R.string.history_notification) }, style = MaterialTheme.typography.titleSmall)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryActionMenu(
    drawerViewModel: DrawerViewModel,
    target: HistoryMenuTarget,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val copiedMessage = stringResource(R.string.history_copied)
    val extractRequestedMessage = stringResource(R.string.history_extract_requested)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.history_action_copy)) },
                leadingContent = { Icon(painterResource(R.drawable.copy), contentDescription = null, modifier = Modifier.size(24.dp)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString(target.copyText))
                    AppSnackbar.show(copiedMessage)
                    onDismiss()
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.ui_action_extract_saved_item)) },
                leadingContent = { Icon(painterResource(R.drawable.task), contentDescription = null, modifier = Modifier.size(24.dp)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable {
                    scope.launch {
                        val idsJson = com.google.gson.Gson().toJson(target.recordIds.distinct())
                        drawerViewModel.actOnNoti(target.notiKey, "extract_saved_item_with_records::$idsJson")
                        AppSnackbar.show(extractRequestedMessage)
                    }
                    onDismiss()
                },
            )
        }
    }
}

private fun NotiRecord.toMenuTarget(): HistoryMenuTarget =
    HistoryMenuTarget(
        notiKey = notiKey,
        recordIds = listOf(notiRecordId),
        copyText = recordCopyText(this),
    )

private fun recordCopyText(record: NotiRecord): String =
    listOf(record.title, unescapeUserText(record.content))
        .filter { it.isNotBlank() }
        .joinToString("\n")

private fun groupCopyText(records: List<NotiRecord>): String =
    records.sortedByDescending { it.time }.joinToString("\n\n") { recordCopyText(it) }
