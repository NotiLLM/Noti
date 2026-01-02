package org.muilab.notigpt.ui.component.notification

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState // Import this
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.ui.component.notification.info.ExpandedNotiRecord
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.platform.AndroidClipboardController
import org.muilab.notigpt.platform.ClipboardController

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun SearchNotiCard(
    notiUnit: NotiUnit,
    records: List<NotiRecord>,
    drawerViewModel: DrawerViewModel
) {
    val bitmap = notiUnit.bitmap
    val appName = notiUnit.appName
    val isPeople = notiUnit.isPeople
    val scope = rememberCoroutineScope()
    val localContext = LocalContext.current
    val clipboard: ClipboardController = remember(localContext) { AndroidClipboardController(localContext) }

    // [FIX] Collect the StateFlow here instead of accessing .value in composition
    val includeHistory by drawerViewModel.includeHistory.collectAsState()

    fun String?.cleanNullish(): String = when {
        this == null -> ""
        this.equals("null", ignoreCase = true) -> ""
        else -> this
    }

    val gapCache = remember(notiUnit.notiKey, includeHistory) {
        mutableStateMapOf<String, Boolean>()
    }
    fun gapKey(start: Long, end: Long): String = "${notiUnit.notiKey}|$start|$end|$includeHistory"

    suspend fun checkGapCached(start: Long, end: Long): Boolean {
        val key = gapKey(start, end)
        val cached = gapCache[key]
        if (cached != null) return cached
        val computed = drawerViewModel.checkGapHasRecords(notiUnit.notiKey, start, end)
        gapCache[key] = computed
        return computed
    }

    // (2a) Determine Overall Title
    val lastRecord = records.lastOrNull()
    val lastRecordTitle = lastRecord?.getDisplayedTitle(isPeople).cleanNullish()
    val notiOverallTitle = when {
        lastRecord != null && lastRecord.extraConversationTitle.cleanNullish().isNotBlank() -> lastRecord.extraConversationTitle.cleanNullish()
        lastRecordTitle.isNotBlank() -> lastRecordTitle
        lastRecord != null && lastRecord.extraSubText.cleanNullish().isNotBlank() -> lastRecord.extraSubText.cleanNullish()
        else -> ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // --- Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        // [FIX] Use ifBlank to default to appName
                        text = notiOverallTitle.ifBlank { appName },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )

                    // Only show secondary appName if we are not already showing it as the main title
                    if (notiOverallTitle.isNotBlank() && notiOverallTitle != appName) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (notiUnit.isVisible) {
                        Text(
                            text = "• ${notiUnit.category}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // (2b) Access Button
                IconButton(
                    onClick = { drawerViewModel.accessNotification(notiUnit) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.external_access),
                        contentDescription = "Open App",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider()

            // Load Older Context (Top)
            var hasOlderContextAtTop by remember(records) { mutableStateOf(false) }

            // [FIX] Use the collected `includeHistory` state variable
            LaunchedEffect(records, includeHistory) {
                if (records.isNotEmpty()) {
                    val firstRecordTime = records.first().time
                    hasOlderContextAtTop = checkGapCached(0L, firstRecordTime)
                } else {
                    hasOlderContextAtTop = false
                }
            }

            if (hasOlderContextAtTop) {
                Button(
                    onClick = { drawerViewModel.loadSearchContext(notiUnit.notiKey, isOlder = true) },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.textButtonColors()
                ) {
                    Text("Load Older...")
                }
            }

            // --- Records List with Gap Detection ---
            Column(modifier = Modifier.padding(vertical = 4.dp)) {

                records.forEachIndexed { index, record ->

                    // 1. GAP LOGIC (Rendered BEFORE the current record)
                    // Checks the space between records[index-1] and records[index]
                    if (index > 0) {
                        val prevRecord = records[index - 1]
                        val timeDiff = record.time - prevRecord.time
                        val threshold = 10 * 60 * 1000L // 10 minutes

                        // Optimization: Only run the DB check/LaunchedEffect if the time difference is large enough
                        if (timeDiff > threshold) {

                            // State specific to this gap
                            var hasMoreInThisGap by remember(prevRecord.time, record.time, includeHistory) { mutableStateOf(false) }

                            LaunchedEffect(prevRecord.time, record.time, includeHistory) {
                                hasMoreInThisGap = checkGapCached(prevRecord.time, record.time)
                            }

                            if (hasMoreInThisGap) {
                                // Changed Row to Column for vertical stacking
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp) // Space between the two buttons
                                ) {
                                    // Button: Expand DOWN from Previous (Load Newer)
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                drawerViewModel.loadGapRecords(
                                                    notiUnit.notiKey,
                                                    prevRecord.time,
                                                    record.time,
                                                    fromStart = true // Load Newer
                                                )
                                                gapCache.remove(gapKey(prevRecord.time, record.time))
                                            }
                                        },
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Load Context", fontSize = 10.sp)
                                    }

                                    // Button: Expand UP from Current (Load Older)
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                drawerViewModel.loadGapRecords(
                                                    notiUnit.notiKey,
                                                    prevRecord.time,
                                                    record.time,
                                                    fromStart = false // Load Older
                                                )
                                                gapCache.remove(gapKey(prevRecord.time, record.time))
                                            }
                                        },
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Load Context", fontSize = 10.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. RECORD CONTENT LOGIC
                    val currentTitle = record.getDisplayedTitle(isPeople).cleanNullish()
                    val contentText = record.content.cleanNullish()

                    // Determine if we should show the title header
                    val showTitle = if (index == 0) {
                        currentTitle != notiOverallTitle
                    } else {
                        val prevTitle = records[index - 1].getDisplayedTitle(isPeople).cleanNullish()
                        prevTitle != currentTitle
                    }

                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (contentText.isNotBlank()) {
                                        clipboard.copyPlainText("Noti record", contentText)
                                    }
                                }
                            )
                    ) {
                        ExpandedNotiRecord(currentTitle, record.time, contentText, showTitle)
                    }
                }
            }

            // Load Newer Context (Bottom)
            var hasNewerContextAtBottom by remember(records) { mutableStateOf(false) }

            // [FIX] Use the collected `includeHistory` state variable
            LaunchedEffect(records, includeHistory) {
                if (records.isNotEmpty()) {
                    val lastRecordTime = records.last().time
                    hasNewerContextAtBottom = checkGapCached(lastRecordTime, Long.MAX_VALUE)
                } else {
                    hasNewerContextAtBottom = false
                }
            }

            if (hasNewerContextAtBottom) {
                Button(
                    onClick = { drawerViewModel.loadSearchContext(notiUnit.notiKey, isOlder = false) },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.textButtonColors()
                ) {
                    Text("Load Newer...")
                }
            }
        }
    }
}