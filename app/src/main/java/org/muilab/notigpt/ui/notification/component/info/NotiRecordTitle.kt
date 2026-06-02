package org.muilab.notigpt.ui.notification.component.info

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Title renderer for notification record rows.
 *
 * Keep this component styling-focused. Choosing the best title source belongs to NotiRecord/NotiMetadata
 * normalization or the card assembly layer.
 */
@Composable
fun NotiInfoTitle(notiTitle: String, modifier: Modifier = Modifier) {
    Text(
        modifier = Modifier
            .padding(start = 16.dp, top = 8.dp)
            .then(modifier),
        text = if (notiTitle == "null") "" else notiTitle,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    )
}