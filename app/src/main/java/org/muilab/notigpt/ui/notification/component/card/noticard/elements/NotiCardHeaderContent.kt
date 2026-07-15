package org.muilab.notigpt.ui.notification.component.card.noticard.elements

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.util.time.getRelativeTimeStr
import org.muilab.notigpt.util.unescapeUserText

/**
 * Header content for the active notification card.
 *
 * This renders app identity, title, summary, and primary metadata. Keep record-history rendering in expansion
 * components so the header remains focused on the latest drawer state.
 */
@Composable
fun NotiCardHeaderContent(
    modifier: Modifier = Modifier,
    notiDisplayUnit: NotiDisplayUnit,
    notiOverallTitle: String,
    notiSecondOverallTitle: String,
    hasSecondTitle: Boolean,
    showSummary: Boolean,
    requiresExpansionSetter: (Boolean) -> Unit,
    collapseThreshold: Float,
    isExpandedOffset: Float,
    linkedTaskCount: Int = 0,
    linkedKeepCount: Int = 0,
) {
    val notiUnit = notiDisplayUnit.notiUnit
    val notiRecords = remember(notiDisplayUnit.notiRecords) { notiDisplayUnit.notiRecords.sortedBy { it.time } }
    val appName = notiUnit.appName
    // Key the decode on the stable icon strings (not the Bitmap) so it runs once per icon rather
    // than on every recomposition. The underlying decode is also process-cached in NotiMetadata.
    val bitmap = remember(notiUnit.metadata.icon) { notiUnit.bitmap }
    val largeBitmap = remember(notiUnit.metadata.largeIcon, notiUnit.metadata.icon) { notiUnit.largeBitmap }
    val summary = notiUnit.summary

    Row(modifier = modifier.padding(start = 6.dp, end = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        val imageToDisplay = remember(bitmap, largeBitmap, isExpandedOffset) {
            if (isExpandedOffset > collapseThreshold && largeBitmap != null) largeBitmap.asImageBitmap()
            else bitmap?.asImageBitmap()
        }

        val hasTransparency = remember(bitmap) {
            if (bitmap == null) false else {
                val w = minOf(bitmap.width, 16)
                val h = minOf(bitmap.height, 16)
                val scaled = if (bitmap.width > w || bitmap.height > h) bitmap.scale(w, h) else bitmap
                val pixels = IntArray(w * h)
                scaled.getPixels(pixels, 0, w, 0, 0, w, h)
                if (bitmap.hasAlpha()) {
                    pixels.count { ((it ushr 24) and 0xFF) < 250 } / pixels.size.toFloat() > 0.1f
                } else {
                    pixels.map { it and 0xFFFFFF }.toSet().size < 12
                }
            }
        }

        if (showSummary) Spacer(Modifier.size(3.dp))

        if (imageToDisplay != null) {
            val iconModifier = Modifier.size(35.dp).padding(3.dp)
            if (isExpandedOffset > collapseThreshold && largeBitmap != null) {
                Image(bitmap = imageToDisplay, contentDescription = "Notification Icon", modifier = iconModifier)
            } else {
                if (hasTransparency) {
                    Icon(
                        bitmap = imageToDisplay,
                        contentDescription = "Notification Icon",
                        modifier = iconModifier,
                        tint = contentColorFor(MaterialTheme.colorScheme.surface),
                    )
                } else {
                    Image(bitmap = imageToDisplay, contentDescription = "Notification Icon", modifier = iconModifier)
                }
            }
        }

        Column(Modifier.align(Alignment.CenterVertically).padding(start = 8.dp).weight(1f)) {
            Row(Modifier.fillMaxWidth()) {
                if (isExpandedOffset > collapseThreshold) {
                    Text(appName, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                } else {
                    Column(Modifier.wrapContentHeight().weight(1f)) {
                        Text(
                            notiOverallTitle.ifBlank { appName },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (it.hasVisualOverflow) requiresExpansionSetter(true) },
                        )
                        if (hasSecondTitle) {
                            Text(
                                notiSecondOverallTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                onTextLayout = { if (it.hasVisualOverflow) requiresExpansionSetter(true) },
                            )
                        }
                    }
                }
                // Cross-link badge: "N ⟨task⟩ M ⟨keep⟩" for saved items this thread produced.
                if (linkedTaskCount > 0 || linkedKeepCount > 0) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (linkedTaskCount > 0) {
                            Text(
                                text = linkedTaskCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.size(2.dp))
                            Icon(
                                painter = painterResource(R.drawable.task),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        if (linkedKeepCount > 0) {
                            if (linkedTaskCount > 0) Spacer(Modifier.size(5.dp))
                            Text(
                                text = linkedKeepCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.size(2.dp))
                            Icon(
                                painter = painterResource(R.drawable.bookmark),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.size(4.dp))
                }
                val visibleRecordCount = notiRecords.size
                if (visibleRecordCount > 1) {
                    Text(
                        text = visibleRecordCount.toString(),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                val ctx = LocalContext.current
                val relativeTime = remember(notiDisplayUnit.lastUpdateTime, ctx) {
                    getRelativeTimeStr(notiDisplayUnit.lastUpdateTime, ctx)
                }
                Text(
                    relativeTime,
                    Modifier.padding(horizontal = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            if (!showSummary) {
                Row {
                    if (isExpandedOffset > collapseThreshold) {
                        Column {
                            Text(
                                notiOverallTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (hasSecondTitle) {
                                Text(
                                    notiSecondOverallTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        val notiContent = notiRecords.lastOrNull()?.content ?: ""
                        Text(
                            if (notiContent == "null") "" else unescapeUserText(notiContent),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (it.hasVisualOverflow) requiresExpansionSetter(true) },
                        )
                    }
                }
            } else {
                Text(
                    summary,
                    modifier = Modifier.padding(horizontal = 5.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(88.dp))
    }
}
