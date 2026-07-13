package org.muilab.notigpt.ui.common.component

import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.common.navigation.AppMenuScreen
import org.muilab.notigpt.ui.common.navigation.SavedListFilter

/** Hamburger drawer content: back to the home overview plus the secondary app sections. */
@Composable
fun AppDrawerContent(
    isHome: Boolean,
    unresolvedConflictCount: Int,
    dueUnseenReminderCount: Int,
    activeTaskCount: Int,
    activeKeepCount: Int,
    onHomeSelected: () -> Unit,
    onSavedListSelected: (SavedListFilter) -> Unit,
    onMenuScreenSelected: (AppMenuScreen) -> Unit,
) {
    ModalDrawerSheet {
        // The overview lives at the home root; the drawer just offers a way back to it plus the
        // Tasks/Keep collections and the secondary sections (scheduled reminders, preferences,
        // history, settings).
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.menu_home)) },
            icon = { Icon(painter = painterResource(R.drawable.home), contentDescription = null) },
            selected = isHome,
            onClick = onHomeSelected,
        )
        // Tasks / Keep collections, with a live badge of the active (non-completed / non-archived) count.
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.home_collection_tasks)) },
            icon = { Icon(painter = painterResource(R.drawable.task), contentDescription = null) },
            badge = { if (activeTaskCount > 0) Text(activeTaskCount.toString()) },
            selected = false,
            onClick = { onSavedListSelected(SavedListFilter.Tasks) },
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.home_collection_keep)) },
            icon = { Icon(painter = painterResource(R.drawable.keep), contentDescription = null) },
            badge = { if (activeKeepCount > 0) Text(activeKeepCount.toString()) },
            selected = false,
            onClick = { onSavedListSelected(SavedListFilter.Keep) },
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
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
