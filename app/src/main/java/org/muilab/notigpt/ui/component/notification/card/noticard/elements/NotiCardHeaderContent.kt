package org.muilab.notigpt.ui.component.notification.card.noticard.elements

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
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
    isExpandedOffset: Float
) {
    val notiUnit = notiDisplayUnit.notiUnit
    val notiRecords = notiDisplayUnit.notiRecords
    val appName = notiUnit.appName
    val bitmap = notiUnit.bitmap
    val largeBitmap = notiUnit.largeBitmap
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
                    Text(appName, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                } else {
                    Column(Modifier.wrapContentHeight().weight(1f)) {
                        Text(
                            notiOverallTitle.ifBlank { appName },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                            onTextLayout = { if (it.hasVisualOverflow) requiresExpansionSetter(true) },
                        )
                        if (hasSecondTitle) {
                            Text(
                                notiSecondOverallTitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                onTextLayout = { if (it.hasVisualOverflow) requiresExpansionSetter(true) },
                            )
                        }
                    }
                }
                val ctx = LocalContext.current
                Text(
                    getRelativeTimeStr(notiDisplayUnit.lastUpdateTime, ctx),
                    Modifier.padding(horizontal = 5.dp),
                    maxLines = 1,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                )
            }

            if (!showSummary) {
                Row {
                    if (isExpandedOffset > collapseThreshold) {
                        Column {
                            Text(
                                notiOverallTitle,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                            )
                            if (hasSecondTitle) {
                                Text(
                                    notiSecondOverallTitle,
                                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        val notiContent = notiRecords.lastOrNull()?.content ?: ""
                        Text(
                            if (notiContent == "null") "" else unescapeUserText(notiContent),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (it.hasVisualOverflow) requiresExpansionSetter(true) },
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                Text(
                    summary,
                    modifier = Modifier.padding(horizontal = 5.dp),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(88.dp))
    }
}
