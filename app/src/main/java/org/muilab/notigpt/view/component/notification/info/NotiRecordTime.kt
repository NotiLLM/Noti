package org.muilab.notigpt.view.component.notification.info

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.muilab.notigpt.util.getRelativeTimeStr

@Composable
fun NotiInfoTime(notiTime: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
    ) {
        Text(
            modifier = Modifier
                .padding(end = 16.dp),
            text = getRelativeTimeStr(notiTime),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic
            )
        )
    }
}