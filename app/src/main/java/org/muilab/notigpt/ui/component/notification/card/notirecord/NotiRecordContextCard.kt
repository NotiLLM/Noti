package org.muilab.notigpt.ui.component.notification.card.notirecord

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.util.getRelativeTimeStr
import org.muilab.notigpt.util.unescapeUserText
import org.muilab.notigpt.platform.AndroidClipboardController

/**
 * One card per notiKey.
 * - Header is shown once (icon + titles + access button)
 * - Records are shown inside the card (no redundant repeated headers)
 * - Built-in Load Older/Newer buttons expand context above/below
 * - Long-press copies a *single record's* content (not the whole card)
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NotiRecordContextCard(
    notiKey: String,
    records: List<NotiRecord>,
    drawerViewModel: DrawerViewModel,
    modifier: Modifier = Modifier,
    // Optional unit from search cache to avoid extra DB fetch.
    cachedUnit: NotiUnit? = null
) {
    val context = LocalContext.current
    val clipboard = remember(context) { AndroidClipboardController(context) }
    val coroutineScope = rememberCoroutineScope()
    var showLoadButtons by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // In history mode, the caller typically passes only 1 record; we keep expanded context locally.
    var expandedRecords by remember(notiKey) { mutableStateOf(records) }
    LaunchedEffect(records) {
        // If caller gave us a newer/different seed (e.g., during recomposition), merge it in.
        expandedRecords = (expandedRecords + records)
            .distinctBy { it.notiRecordId }
            .sortedBy { it.time }
    }

    var notiUnit: NotiUnit? by remember(notiKey, cachedUnit) { mutableStateOf(cachedUnit) }
    LaunchedEffect(notiKey, cachedUnit) {
        if (notiUnit == null) {
            notiUnit = drawerViewModel.getNotiUnitForHistory(notiKey)
        }
    }

    val safeRecords = remember(expandedRecords) { expandedRecords.sortedBy { it.time } }

    val displayOverallTitle = remember(safeRecords) {
        val r = safeRecords.lastOrNull()
        when {
            r == null -> ""
            r.extraConversationTitle.isNotBlank() && r.extraConversationTitle != "null" -> r.extraConversationTitle
            r.extraTitle.isNotBlank() && r.extraTitle != "null" -> r.extraTitle
            r.extraSubText.isNotBlank() && r.extraSubText != "null" -> r.extraSubText
            else -> ""
        }
    }

    val displaySecondTitle = remember(safeRecords) {
        val r = safeRecords.lastOrNull() ?: return@remember ""
        when {
            r.extraConversationTitle.isNotBlank() && r.extraConversationTitle != "null" &&
                r.extraTitle.isNotBlank() && r.extraTitle != "null" -> r.extraTitle

            r.extraConversationTitle == "null" &&
                r.extraTitle.isNotBlank() && r.extraTitle != "null" &&
                r.extraSubText.isNotBlank() && r.extraSubText != "null" -> r.extraSubText

            else -> ""
        }
    }

    val hasSecondTitle = displaySecondTitle.isNotBlank() && displaySecondTitle != displayOverallTitle

    // Context availability is derived from whether the search controller can load more.
    // We keep this best-effort and cheap: if current list is non-empty, we allow loading.
    // The repository will simply return empty if no more records.
    val canLoadOlder = safeRecords.isNotEmpty()
    val canLoadNewer = safeRecords.isNotEmpty()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bitmap = notiUnit?.bitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "App icon",
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayOverallTitle.ifBlank { notiUnit?.appName ?: "" },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                    )
                    if (hasSecondTitle) {
                        Text(
                            text = displaySecondTitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.a11y_show_more_context))
                }
                IconButton(onClick = { drawerViewModel.accessNotificationByKey(notiKey) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.ui_action_open))
                }
            }

            if (showMenu) {
                AlertDialog(
                    onDismissRequest = { showMenu = false },
                    title = { Text(stringResource(R.string.ui_noti_menu_title)) },
                    text = {
                        Column {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val idsJson = com.google.gson.Gson().toJson(safeRecords.map { it.notiRecordId }.distinct())
                                        drawerViewModel.actOnNoti(notiKey, "extract_reminder_with_records::$idsJson")
                                    }
                                    showMenu = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.task_yes),
                                        contentDescription = stringResource(R.string.ui_action_extract_reminder),
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(R.string.ui_action_extract_reminder))
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            TextButton(
                                onClick = {
                                    showLoadButtons = !showLoadButtons
                                    showMenu = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = if (showLoadButtons)
                                            stringResource(R.string.ui_noti_hide_more_context)
                                        else
                                            stringResource(R.string.ui_noti_show_more_context)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showMenu = false }) {
                            Text(stringResource(R.string.ui_action_close))
                        }
                    },
                )
            }

            if (showLoadButtons) {
                // Built-in load older
                if (canLoadOlder) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                val oldest = safeRecords.firstOrNull()?.time ?: return@launch
                                val older = drawerViewModel.getRecordsBeforeForHistory(oldest, 10)
                                    .filter { it.notiKey == notiKey }
                                if (older.isNotEmpty()) {
                                    expandedRecords = (expandedRecords + older)
                                        .distinctBy { it.notiRecordId }
                                        .sortedBy { it.time }
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally).minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.ui_noti_load_older))
                    }
                }
            }

            // Records content
            safeRecords.forEach { record ->
                val content = record.content.takeIf { it.isNotBlank() && it != "null" }?.let { unescapeUserText(it) } ?: ""
                if (content.isBlank()) return@forEach

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { /* no-op */ },
                            onLongClick = {
                                clipboard.copyPlainText("record", content)
                            }
                        )
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = getRelativeTimeStr(record.time, context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (showLoadButtons) {
                // Built-in load newer
                if (canLoadNewer) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                val newest = safeRecords.lastOrNull()?.time ?: return@launch
                                val newer = drawerViewModel.getRecordsAfterForHistory(newest, 10)
                                    .filter { it.notiKey == notiKey }
                                if (newer.isNotEmpty()) {
                                    expandedRecords = (expandedRecords + newer)
                                        .distinctBy { it.notiRecordId }
                                        .sortedBy { it.time }
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally).minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.ui_noti_load_newer))
                    }
                }
            }
        }
    }
}
