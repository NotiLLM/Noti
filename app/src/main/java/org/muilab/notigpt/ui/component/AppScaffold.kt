package org.muilab.notigpt.ui.component

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.muilab.notigpt.ui.component.appbar.AppTopBar
import org.muilab.notigpt.ui.screen.HomeScreen
import org.muilab.notigpt.ui.screen.SettingsScreen
import org.muilab.notigpt.ui.screens.EsmScreen
import org.muilab.notigpt.ui.screens.RemindersScreen
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.viewmodel.EsmViewModel
import org.muilab.notigpt.ui.viewmodel.ReminderViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppScaffold(
    drawerViewModel: DrawerViewModel,
) {

    Log.d("AppScaffold", "composed with DrawerViewModel hash=${drawerViewModel.hashCode()}")

    var isSearchExpanded by remember { mutableStateOf(false) }
    var isSettingsShown by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity

    var selectedTab by remember { mutableStateOf(org.muilab.notigpt.ui.component.appbar.Tab.Notifications) }

    LaunchedEffect(activity?.intent) {
        val openEsm = activity?.intent?.getBooleanExtra("open_esm", false) ?: false
        if (openEsm) {
            selectedTab = org.muilab.notigpt.ui.component.appbar.Tab.ESM
            // consume
            activity?.intent?.removeExtra("open_esm")
        }
    }

    val reminderViewModel: ReminderViewModel = viewModel()
    val esmViewModel: EsmViewModel = viewModel()

    val unreadNotiCount by drawerViewModel.unreadActiveCount.collectAsState()
    val reminderList by reminderViewModel.reminders.collectAsState()
    val pendingTaskCount = remember(reminderList) {
        reminderList.count { it.isTask && !it.isCompleted }
    }

    val esmAvailable by esmViewModel.available.collectAsState()
    val pendingEsmCount = remember(esmAvailable) { esmAvailable.size }

    Scaffold(
        topBar = {
            AppTopBar(
                drawerViewModel = drawerViewModel,
                isSearchExpanded = isSearchExpanded,
                onSearchToggled = { isSearchExpanded = it },
                isSettingsShown = isSettingsShown,
                onSettingsShown = {
                    // Leaving Notifications into Settings: persist read marks.
                    if (selectedTab == org.muilab.notigpt.ui.component.appbar.Tab.Notifications && it) {
                        drawerViewModel.persistReadStatus()
                    }
                    isSettingsShown = it
                }
            )
        },
        bottomBar = {
            if (!isSettingsShown) {
                org.muilab.notigpt.ui.component.appbar.AppBottomBar(
                    selectedTab = selectedTab,
                    unreadNotificationCount = unreadNotiCount,
                    pendingTaskCount = pendingTaskCount,
                    pendingEsmCount = pendingEsmCount,
                    onTabSelected = { tab ->
                        // Leaving Notifications: persist any pending read marks so border colors update.
                        if (selectedTab == org.muilab.notigpt.ui.component.appbar.Tab.Notifications && tab != selectedTab) {
                            drawerViewModel.persistReadStatus()
                        }
                        selectedTab = tab
                        drawerViewModel.updateQueryString("")
                        isSearchExpanded = false
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceDim
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (isSettingsShown) {
                SettingsScreen()
            } else {
                when (selectedTab) {
                    org.muilab.notigpt.ui.component.appbar.Tab.Notifications -> HomeScreen(drawerViewModel = drawerViewModel)
                    org.muilab.notigpt.ui.component.appbar.Tab.Reminders -> RemindersScreen()
                    org.muilab.notigpt.ui.component.appbar.Tab.ESM -> EsmScreen()
                }
            }
        }
    }
}