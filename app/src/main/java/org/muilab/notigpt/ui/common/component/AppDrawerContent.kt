package org.muilab.notigpt.ui.common.component

import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.common.navigation.AppMenuScreen
import org.muilab.notigpt.ui.common.navigation.AppPrimaryTab

/** Hamburger drawer content for secondary app sections. */
@Composable
fun AppDrawerContent(
    selectedPrimaryTab: AppPrimaryTab?,
    unresolvedConflictCount: Int,
    dueUnseenReminderCount: Int,
    onPrimaryTabSelected: (AppPrimaryTab) -> Unit,
    onMenuScreenSelected: (AppMenuScreen) -> Unit,
) {
    ModalDrawerSheet {
        // New / Tasks / Keep live in the bottom bar; the drawer just offers a way back to the
        // main screen (defaulting to the New tab).
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.menu_home)) },
            icon = { Icon(painter = painterResource(R.drawable.home), contentDescription = null) },
            selected = selectedPrimaryTab != null,
            onClick = { onPrimaryTabSelected(AppPrimaryTab.New) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.menu_reminders)) },
            icon = { Icon(painter = painterResource(R.drawable.task), contentDescription = null) },
            badge = {
                if (dueUnseenReminderCount > 0) {
                    Badge(containerColor = androidx.compose.ui.graphics.Color.Red) {
                        Text(dueUnseenReminderCount.toString())
                    }
                }
            },
            selected = false,
            onClick = { onMenuScreenSelected(AppMenuScreen.Reminders) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.tab_preferences)) },
            icon = { Icon(painter = painterResource(R.drawable.task), contentDescription = null) },
            badge = {
                if (unresolvedConflictCount > 0) {
                    Badge { Text(unresolvedConflictCount.toString()) }
                }
            },
            selected = false,
            onClick = { onMenuScreenSelected(AppMenuScreen.Preferences) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.menu_history)) },
            icon = { Icon(painter = painterResource(R.drawable.notifications), contentDescription = null) },
            selected = false,
            onClick = { onMenuScreenSelected(AppMenuScreen.History) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.menu_settings)) },
            icon = { Icon(painter = painterResource(R.drawable.settings), contentDescription = null) },
            selected = false,
            onClick = { onMenuScreenSelected(AppMenuScreen.Settings) },
        )
    }
}
