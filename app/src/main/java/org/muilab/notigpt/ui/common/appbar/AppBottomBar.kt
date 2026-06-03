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
import org.muilab.notigpt.ui.common.navigation.AppPrimaryTab

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
    selectedTab: AppPrimaryTab,
    onTabSelected: (AppPrimaryTab) -> Unit,
    unreadNotificationCount: Int,
    pendingTaskCount: Int,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        NavigationBarItem(
            selected = selectedTab == AppPrimaryTab.New,
            onClick = { onTabSelected(AppPrimaryTab.New) },
            icon = {
                BadgeIcon(
                    iconRes = R.drawable.notifications,
                    contentDescription = "New",
                    badgeCount = unreadNotificationCount,
                    badgeColor = MaterialTheme.colorScheme.error,
                    badgeTextColor = MaterialTheme.colorScheme.onError,
                )
            },
            label = { Text("New") }
        )
        NavigationBarItem(
            selected = selectedTab == AppPrimaryTab.Tasks,
            onClick = { onTabSelected(AppPrimaryTab.Tasks) },
            icon = {
                BadgeIcon(
                    iconRes = R.drawable.task,
                    contentDescription = stringResource(R.string.tab_tasks),
                    badgeCount = pendingTaskCount,
                    badgeColor = MaterialTheme.colorScheme.primary,
                    badgeTextColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
            label = { Text(stringResource(R.string.tab_tasks)) }
        )
        NavigationBarItem(
            selected = selectedTab == AppPrimaryTab.Keep,
            onClick = { onTabSelected(AppPrimaryTab.Keep) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.keep),
                    contentDescription = stringResource(R.string.tab_keep),
                )
            },
            label = { Text(stringResource(R.string.tab_keep)) }
        )
    }
}
