package org.muilab.notigpt.ui.component.appbar

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.muilab.notigpt.R

@Composable
private fun BadgeIcon(
    iconRes: Int,
    contentDescription: String,
    badgeCount: Int,
    badgeColor: Color,
    badgeTextColor: Color,
) {
    BadgedBox(
        badge = {
            if (badgeCount > 0) {
                val text = when {
                    badgeCount > 99 -> "99+"
                    else -> badgeCount.toString()
                }
                Badge(
                    containerColor = badgeColor,
                    contentColor = badgeTextColor,
                ) {
                    Text(text)
                }
            }
        }
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = contentDescription)
    }
}

@Composable
fun AppBottomBar(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    unreadNotificationCount: Int,
    pendingTaskCount: Int,
    pendingEsmCount: Int,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        NavigationBarItem(
            selected = selectedTab == Tab.Notifications,
            onClick = { onTabSelected(Tab.Notifications) },
            icon = {
                BadgeIcon(
                    iconRes = R.drawable.notifications,
                    contentDescription = stringResource(R.string.tab_notifications),
                    badgeCount = unreadNotificationCount,
                    badgeColor = MaterialTheme.colorScheme.error,
                    badgeTextColor = MaterialTheme.colorScheme.onError,
                )
            },
            label = { Text(stringResource(R.string.tab_notifications)) }
        )
        NavigationBarItem(
            selected = selectedTab == Tab.Reminders,
            onClick = { onTabSelected(Tab.Reminders) },
            icon = {
                BadgeIcon(
                    iconRes = R.drawable.task_no,
                    contentDescription = stringResource(R.string.tab_reminders),
                    badgeCount = pendingTaskCount,
                    badgeColor = MaterialTheme.colorScheme.primary,
                    badgeTextColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
            label = { Text(stringResource(R.string.tab_reminders)) }
        )
        // TODO: ESM Survey tab disabled (hide, no notifications, no Firestore sync)
        // NavigationBarItem(
        //     selected = selectedTab == Tab.ESM,
        //     onClick = { onTabSelected(Tab.ESM) },
        //     icon = {
        //         BadgeIcon(
        //             iconRes = R.drawable.esm,
        //             contentDescription = stringResource(R.string.tab_esm),
        //             badgeCount = pendingEsmCount,
        //             badgeColor = MaterialTheme.colorScheme.tertiary,
        //             badgeTextColor = MaterialTheme.colorScheme.onTertiary,
        //         )
        //     },
        //     label = { Text(stringResource(R.string.tab_esm)) }
        // )
    }
}

enum class Tab {
    Notifications,
    Reminders,
    ESM,
}