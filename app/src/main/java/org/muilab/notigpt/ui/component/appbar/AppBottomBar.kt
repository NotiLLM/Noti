package org.muilab.notigpt.ui.component.appbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.muilab.notigpt.R

@Composable
private fun BadgeIcon(
    iconRes: Int,
    contentDescription: String,
    badgeCount: Int,
    badgeColor: Color,
    badgeTextColor: Color,
) {
    Box {
        Icon(painter = painterResource(iconRes), contentDescription = contentDescription)
        if (badgeCount > 0) {
            val text = when {
                badgeCount > 99 -> "99+"
                else -> badgeCount.toString()
            }
            val fontSize = if (text.length >= 3) 8.sp else 10.sp

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-8).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = badgeTextColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun AppBottomBar(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    unreadNotificationCount: Int,
    pendingTaskCount: Int,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        NavigationBarItem(
            selected = selectedTab == Tab.Notifications,
            onClick = { onTabSelected(Tab.Notifications) },
            icon = {
                BadgeIcon(
                    iconRes = R.drawable.notifications,
                    contentDescription = "Notifications",
                    badgeCount = unreadNotificationCount,
                    badgeColor = MaterialTheme.colorScheme.error,
                    badgeTextColor = MaterialTheme.colorScheme.onError,
                )
            },
            label = { Text("Notifications") }
        )
        NavigationBarItem(
            selected = selectedTab == Tab.Reminders,
            onClick = { onTabSelected(Tab.Reminders) },
            icon = {
                BadgeIcon(
                    iconRes = R.drawable.task_no,
                    contentDescription = "Reminders",
                    badgeCount = pendingTaskCount,
                    badgeColor = MaterialTheme.colorScheme.primary,
                    badgeTextColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
            label = { Text("Reminders") }
        )
        NavigationBarItem(
            selected = selectedTab == Tab.ESM,
            onClick = { onTabSelected(Tab.ESM) },
            icon = {
                BadgeIcon(
                    iconRes = R.drawable.settings,
                    contentDescription = "ESM",
                    badgeCount = 0,
                    badgeColor = MaterialTheme.colorScheme.tertiary,
                    badgeTextColor = MaterialTheme.colorScheme.onTertiary,
                )
            },
            label = { Text("ESM") }
        )
    }
}

enum class Tab {
    Notifications,
    Reminders,
    ESM,
}