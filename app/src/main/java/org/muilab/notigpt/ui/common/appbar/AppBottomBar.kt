package org.muilab.notigpt.ui.common.appbar

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

/**
 * Bottom navigation bar and tab model for the main app shell.
 *
 * Keep tab labels, icons, and badge display here. Screen routing and feature state should stay in the scaffold
 * or ViewModels.
 */
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
    unresolvedConflictCount: Int = 0,
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
        NavigationBarItem(
            selected = selectedTab == Tab.Preferences,
            onClick = { onTabSelected(Tab.Preferences) },
            icon = {
                BadgeIcon(
                    iconRes = R.drawable.task_no,
                    contentDescription = stringResource(R.string.tab_preferences),
                    badgeCount = unresolvedConflictCount,
                    badgeColor = MaterialTheme.colorScheme.error,
                    badgeTextColor = MaterialTheme.colorScheme.onError,
                )
            },
            label = { Text(stringResource(R.string.tab_preferences)) }
        )
    }
}

enum class Tab {
    Notifications,
    Reminders,
    Preferences,
}