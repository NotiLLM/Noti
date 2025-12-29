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

    val borderModifier = if (hasBorder) {
        Modifier.border(1.dp, Color.Gray, shape = RoundedCornerShape(6.dp))
    } else {
        Modifier
    }

    Box(
        modifier = borderModifier
            .minimumInteractiveComponentSize() // square button
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(30.dp),
            tint = if (color == Color.Unspecified) contentColorFor(backgroundColor) else color
        )
    }
}