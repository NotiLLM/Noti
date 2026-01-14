package org.muilab.notigpt.ui.component.notification.info

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.muilab.notigpt.util.unescapeUserText

@Composable
fun NotiInfoContent(notiContent: String, modifier: Modifier = Modifier) {
    Text(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .then(modifier),
        text = if (notiContent == "null") "" else unescapeUserText(notiContent),
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 14.sp
        )
    )
}