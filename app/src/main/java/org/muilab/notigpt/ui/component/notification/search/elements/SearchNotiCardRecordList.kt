package org.muilab.notigpt.ui.component.notification.search.elements

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.platform.ClipboardController
import org.muilab.notigpt.ui.component.notification.info.ExpandedNotiRecord

@Composable
internal fun SearchNotiCardRecordList(
    notiKey: String,
    records: List<NotiRecord>,
    notiOverallTitle: String,
    isPeople: Boolean,
    includeHistory: Boolean,
    clipboard: ClipboardController,
    checkGap: suspend (start: Long, end: Long) -> Boolean,
    onLoadGap: (start: Long, end: Long, fromStart: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()

    fun String?.cleanNullish(): String = when {
        this == null -> ""
        this.equals("null", ignoreCase = true) -> ""
        else -> this
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        records.forEachIndexed { index, record ->

            if (index > 0) {
                val prevRecord = records[index - 1]
                val timeDiff = record.time - prevRecord.time
                val threshold = 10 * 60 * 1000L

                if (timeDiff > threshold) {
                    var hasMoreInThisGap by remember(prevRecord.time, record.time, includeHistory) { mutableStateOf(false) }

                    LaunchedEffect(prevRecord.time, record.time, includeHistory) {
                        hasMoreInThisGap = checkGap(prevRecord.time, record.time)
                    }

                    if (hasMoreInThisGap) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        onLoadGap(prevRecord.time, record.time, true)
                                    }
                                },
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Load Context", fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        onLoadGap(prevRecord.time, record.time, false)
                                    }
                                },
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Load Context", fontSize = 10.sp)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            val currentTitle = record.getDisplayedTitle(isPeople).cleanNullish()
            val contentText = record.content.cleanNullish()

            val showTitle = if (index == 0) {
                currentTitle != notiOverallTitle
            } else {
                val prevTitle = records[index - 1].getDisplayedTitle(isPeople).cleanNullish().cleanNullish()
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
}
