package org.muilab.notigpt.ui.notification.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.ui.notification.component.info.ExpandedNotiRecord
import org.muilab.notigpt.util.unescapeUserText

/**
 * Reminder-facing preview of notification records associated with a reminder.
 *
 * One instance = one notiKey group. The header is tap-to-collapse; expanded records render
 * content-left / time-right exactly like the expanded notification card on the New screen.
 */
@Composable
fun RelatedNotificationPreview(
    notiDisplayUnit: NotiDisplayUnit,
    showOpenButton: Boolean = false,
    onOpen: (() -> Unit)? = null,
) {
    val notiUnit = notiDisplayUnit.notiUnit
    val notiRecords = notiDisplayUnit.notiRecords.sortedBy { it.time }

    val lastRecord = notiRecords.lastOrNull()
    val notiOverallTitle = when {
        lastRecord != null && lastRecord.extraConversationTitle != "null" -> lastRecord.extraConversationTitle
        notiDisplayUnit.title != "null" -> notiDisplayUnit.title
        lastRecord != null && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
        else -> ""
    }
    val notiSecondOverallTitle = when {
        lastRecord != null && lastRecord.extraConversationTitle != "null" && notiDisplayUnit.title != "null" -> notiDisplayUnit.title
        lastRecord != null && lastRecord.extraConversationTitle == "null" && notiDisplayUnit.title != "null" && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
        lastRecord != null && lastRecord.extraConversationTitle == "null" && notiDisplayUnit.title != "null" -> ""
        else -> ""
    }
    val hasSecondTitle = notiSecondOverallTitle.isNotBlank() && notiSecondOverallTitle != notiOverallTitle

    var expanded by remember(notiUnit.notiKey) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            val displayTitle = notiOverallTitle.ifBlank { notiUnit.appName }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val bitmap = notiUnit.bitmap
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
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                    )
                    if (hasSecondTitle) {
                        Text(
                            text = notiSecondOverallTitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Collapsed: preview the latest record so the card isn't just a bare title.
                    if (!expanded) {
                        val latest = notiRecords.lastOrNull()?.content
                        if (!latest.isNullOrBlank() && latest != "null") {
                            Text(
                                text = unescapeUserText(latest),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Icon(
                    painter = painterResource(if (expanded) R.drawable.keyboard_arrow_up else R.drawable.keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (showOpenButton && onOpen != null) {
                    IconButton(onClick = onOpen) {
                        Icon(
                            painterResource(R.drawable.external_access),
                            contentDescription = "Open",
                        )
                    }
                }
            }

            if (expanded) {
                val recordsToShow = if (notiRecords.size > 30) notiRecords.takeLast(30) else notiRecords
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    recordsToShow.forEach { record ->
                        val content = record.content
                        if (content.isBlank() || content == "null") return@forEach
                        // content-left / time-right, matching the expanded notification card.
                        ExpandedNotiRecord(
                            notiTitle = "",
                            notiTime = record.time,
                            notiContent = content,
                            showTitle = false,
                        )
                    }
                }
            }
        }
    }
}
