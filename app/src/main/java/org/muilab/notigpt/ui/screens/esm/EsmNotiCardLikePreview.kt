package org.muilab.notigpt.ui.screens.esm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

/**
 * Read-only NotiCard-like preview.
 *
 * Uses a lightweight header similar to NotiRecordContextCard, and renders all snapshotted records.
 */
@Composable
fun EsmNotiCardLikePreview(
    notiDisplayUnit: NotiDisplayUnit,
    showOpenButton: Boolean = false,
    onOpen: (() -> Unit)? = null,
) {
    val notiUnit = notiDisplayUnit.notiUnit
    // Ensure stable order for context display.
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // === Header (NotiRecordContextCard-like) ===
            val displayTitle = notiOverallTitle.ifBlank { notiUnit.appName }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                    androidx.compose.material3.Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                    )
                    if (hasSecondTitle) {
                        androidx.compose.material3.Text(
                            text = notiSecondOverallTitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (showOpenButton && onOpen != null) {
                    IconButton(onClick = onOpen) {
                        Icon(
                            painterResource(R.drawable.external_access),
                            contentDescription = "Open",
                        )
                    }
                }
            }

            // Full content preview (no interactive expansion): show all snapshotted records.
            // Guardrail: avoid huge vertical blow-ups by showing at most the last 30 records.
            val recordsToShow = if (notiRecords.size > 30) notiRecords.takeLast(30) else notiRecords

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                recordsToShow.forEachIndexed { idx, r ->
                    val content = r.content
                    if (content.isBlank() || content == "null") return@forEachIndexed

                    // A light separator between messages.
                    if (idx != 0) {
                        Spacer(Modifier.height(8.dp))
                    }

                    androidx.compose.material3.Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
