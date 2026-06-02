package org.muilab.notigpt.ui.notification.component.info

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import org.muilab.notigpt.util.time.getRelativeTimeStr

/**
 * Time renderer for notification record timestamps.
 *
 * Keep display formatting here. Any logic that decides which timestamp matters should live in the model or
 * card assembly layer before reaching this small component.
 */
@Composable
fun NotiInfoTime(notiTime: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
    ) {
        Text(
            modifier = Modifier
                .padding(end = 16.dp),
            text = getRelativeTimeStr(notiTime, context),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic
            )
        )
    }
}