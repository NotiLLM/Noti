package org.muilab.notigpt.ui.screens.esm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.ui.component.notification.card.noticard.elements.NotiCardHeaderContent

/**
 * Read-only NotiCard-like preview.
 *
 * Uses the same header composable as the real NotiCard (icons, spacing, typography),
 * but strips all interactions (swipe, click, expansion to load more, etc.).
 */
@Composable
fun EsmNotiCardLikePreview(
    notiDisplayUnit: NotiDisplayUnit,
) {
    val notiUnit = notiDisplayUnit.notiUnit
    val notiRecords = notiDisplayUnit.notiRecords

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

    val requiresExpansion = remember(notiUnit.notiKey) { mutableStateOf(false) }

    // If snapshot doesn't have icon blobs, avoid header icon rendering path.
    val hasAnyIconData = (notiUnit.metadata.icon.isNotBlank() && notiUnit.metadata.icon != "null") ||
        (notiUnit.metadata.largeIcon.isNotBlank() && notiUnit.metadata.largeIcon != "null")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Matches real NotiCard header look.
            NotiCardHeaderContent(
                notiDisplayUnit = notiDisplayUnit,
                notiOverallTitle = notiOverallTitle,
                notiSecondOverallTitle = notiSecondOverallTitle,
                hasSecondTitle = hasSecondTitle,
                showSummary = (!hasAnyIconData) || notiUnit.summary.isNotBlank(),
                requiresExpansionSetter = { requiresExpansion.value = it },
                collapseThreshold = 0f,
                isExpandedOffset = 0f,
            )

            // Full content preview (no interactive expansion)
            val content = notiRecords.lastOrNull()?.content.orEmpty()
            if (content.isNotBlank() && content != "null") {
                androidx.compose.material3.Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
