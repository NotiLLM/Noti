package org.muilab.notigpt.ui.common.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.common.navigation.AppMenuScreen
import org.muilab.notigpt.ui.common.navigation.SavedListFilter
import org.muilab.notigpt.ui.theme.Dimens

/**
 * Hamburger drawer: an account header, then the home overview + Tasks/Keep collections, then the
 * secondary sections. Each destination has a distinct icon (previously Tasks/Reminders/Preferences all
 * reused the same glyph) and section labels group the two tiers.
 */
@Composable
fun AppDrawerContent(
    isHome: Boolean,
    accountEmail: String?,
    unresolvedConflictCount: Int,
    dueUnseenReminderCount: Int,
    activeTaskCount: Int,
    activeKeepCount: Int,
    onHomeSelected: () -> Unit,
    onSavedListSelected: (SavedListFilter) -> Unit,
    onMenuScreenSelected: (AppMenuScreen) -> Unit,
) {
    ModalDrawerSheet {
        // Account header.
        Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            if (!accountEmail.isNullOrBlank()) {
                Text(
                    text = accountEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        NavigationDrawerItem(
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            label = { Text(stringResource(R.string.menu_home)) },
            icon = { Icon(painter = painterResource(R.drawable.home), contentDescription = null) },
            selected = isHome,
            onClick = onHomeSelected,
        )

        DrawerSectionLabel(stringResource(R.string.menu_section_collections))
        // Tasks / Keep collections, with a live badge of the active (non-completed / non-archived) count.
        NavigationDrawerItem(
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            label = { Text(stringResource(R.string.home_collection_tasks)) },
            icon = { Icon(painter = painterResource(R.drawable.checklist), contentDescription = null) },
            badge = { if (activeTaskCount > 0) Text(activeTaskCount.toString()) },
            selected = false,
            onClick = { onSavedListSelected(SavedListFilter.Tasks) },
        )
        NavigationDrawerItem(
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            label = { Text(stringResource(R.string.home_collection_keep)) },
            icon = { Icon(painter = painterResource(R.drawable.bookmark), contentDescription = null) },
            badge = { if (activeKeepCount > 0) Text(activeKeepCount.toString()) },
            selected = false,
            onClick = { onSavedListSelected(SavedListFilter.Keep) },
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        DrawerSectionLabel(stringResource(R.string.menu_section_more))
        NavigationDrawerItem(
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            label = { Text(stringResource(R.string.menu_reminders)) },
            icon = { Icon(painter = painterResource(R.drawable.schedule), contentDescription = null) },
            badge = {
                if (dueUnseenReminderCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text(dueUnseenReminderCount.toString())
                    }
                }
            },
            selected = false,
            onClick = { onMenuScreenSelected(AppMenuScreen.Reminders) },
        )
        NavigationDrawerItem(
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            label = { Text(stringResource(R.string.tab_preferences)) },
            // TODO(icon): replace `info` placeholder with a `tune` glyph (see icon request list).
            icon = { Icon(painter = painterResource(R.drawable.info), contentDescription = null) },
            badge = {
                if (unresolvedConflictCount > 0) {
                    Badge { Text(unresolvedConflictCount.toString()) }
                }
            },
            selected = false,
            onClick = { onMenuScreenSelected(AppMenuScreen.Preferences) },
        )
        NavigationDrawerItem(
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            label = { Text(stringResource(R.string.menu_history)) },
            icon = { Icon(painter = painterResource(R.drawable.notifications), contentDescription = null) },
            selected = false,
            onClick = { onMenuScreenSelected(AppMenuScreen.History) },
        )
        NavigationDrawerItem(
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            label = { Text(stringResource(R.string.menu_settings)) },
            icon = { Icon(painter = painterResource(R.drawable.settings), contentDescription = null) },
            selected = false,
            onClick = { onMenuScreenSelected(AppMenuScreen.Settings) },
        )
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = Dimens.element, bottom = 4.dp),
    )
}
