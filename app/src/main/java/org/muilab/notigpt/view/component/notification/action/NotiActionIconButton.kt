package org.muilab.notigpt.view.component.notification.action

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun NotiActionIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    hasBorder: Boolean = true,
    color: Color = Color.Unspecified
) {
    val buttonBgColor = MaterialTheme.colorScheme.surfaceContainerHighest // 讓按鈕最亮，看起來可點擊

    Box(
        modifier = Modifier
            .size(48.dp) // 稍微加大一點點，方便手指點擊
            .clip(RoundedCornerShape(12.dp)) // 圓角跟卡片呼應
            .background(buttonBgColor) // 使用更亮的背景
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            // 圖示顏色使用對應的 On 背景色
            tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
        )
    }
}