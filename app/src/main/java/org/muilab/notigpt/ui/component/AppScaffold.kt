package org.muilab.notigpt.ui.component

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.PreferenceEntryPoint
import org.muilab.notigpt.ui.component.appbar.AppTopBar
import org.muilab.notigpt.ui.screen.HomeScreen
import org.muilab.notigpt.ui.screen.SettingsScreen
import org.muilab.notigpt.ui.screens.EsmScreen
import org.muilab.notigpt.ui.screens.PreferenceChatScreen
import org.muilab.notigpt.ui.screens.RemindersScreen
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.viewmodel.EsmViewModel
import org.muilab.notigpt.ui.viewmodel.PreferenceViewModel
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
    val preferenceViewModel: PreferenceViewModel = viewModel()

    val unreadNotiCount by drawerViewModel.unreadActiveCount.collectAsState()
    val reminderList by reminderViewModel.reminders.collectAsState()
    val pendingTaskCount = remember(reminderList) {
        reminderList.count { it.isTask && !it.isCompleted }
    }

    val esmAvailable by esmViewModel.available.collectAsState()
    val pendingEsmCount = remember(esmAvailable) { esmAvailable.size }

    val unresolvedConflicts by preferenceViewModel.unresolvedConflicts.collectAsState()

    // ── Snackbar for delete / manual-extract preference prompt ───
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarEvent by preferenceViewModel.snackbarEvent.collectAsState()

    // Resolve string resources outside the LaunchedEffect (Composable context)
    val snackDeleteMsg = stringResource(R.string.pref_snackbar_deleted)
    val snackExtractMsg = stringResource(R.string.pref_snackbar_extracted)
    val snackAction = stringResource(R.string.pref_snackbar_action)

    LaunchedEffect(snackbarEvent) {
        val event = snackbarEvent ?: return@LaunchedEffect
        val message = when (event.entryPoint) {
            PreferenceEntryPoint.DELETE -> snackDeleteMsg
            PreferenceEntryPoint.MANUAL_EXTRACT -> snackExtractMsg
            PreferenceEntryPoint.EDIT -> return@LaunchedEffect // edits use BottomSheet directly
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = snackAction,
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> preferenceViewModel.promoteSnackbarToFlow()
            SnackbarResult.Dismissed -> preferenceViewModel.dismissSnackbar()
        }
    }

    // Navigate to Chat tab when preference flow redirects there
    val navigateToChat by preferenceViewModel.navigateToChat.collectAsState()
    LaunchedEffect(navigateToChat) {
        if (navigateToChat) {
            selectedTab = org.muilab.notigpt.ui.component.appbar.Tab.Preferences
            preferenceViewModel.onChatNavigated()
        }
    }

    // Observe manual extraction events to trigger preference learning (Flow 3)
    LaunchedEffect(Unit) {
        drawerViewModel.manualExtractEvent.collect { notiKey ->
            // Enrich contextData with notification info + semantic content for n8n
            val notiUnit = drawerViewModel.getNotiUnitForHistory(notiKey)
            val contextData = mutableMapOf<String, Any?>("notiKey" to notiKey)
            if (notiUnit != null) {
                // Fetch recent notification records to include actual title/content
                val db = org.muilab.notigpt.database.room.AppDatabase.getInstance(context.applicationContext)
                val records = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    db.recordDao().getActiveRecordsByKey(notiKey).sortedByDescending { it.time }.take(5)
                }
                val notiContentList = records.map { r ->
                    mapOf(
                        "title" to r.getDisplayedTitle(notiUnit.isPeople),
                        "content" to r.content,
                    )
                }
                contextData["notification"] = mapOf(
                    "appName" to notiUnit.appName,
                    "pkgName" to notiUnit.pkgName,
                    "isPeople" to notiUnit.isPeople,
                    "notiContent" to notiContentList,
                )
            }
            preferenceViewModel.startFlow(
                entryPoint = PreferenceEntryPoint.MANUAL_EXTRACT,
                reminder = null,
                contextData = contextData,
            )
        }
    }

    // Show BottomSheet for preference learning (only EDIT opens this directly now)
    PreferenceLearningBottomSheet(preferenceViewModel = preferenceViewModel)

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
                },
                showNotificationActions = selectedTab == org.muilab.notigpt.ui.component.appbar.Tab.Notifications
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!isSettingsShown) {
                org.muilab.notigpt.ui.component.appbar.AppBottomBar(
                    selectedTab = selectedTab,
                    unreadNotificationCount = unreadNotiCount,
                    pendingTaskCount = pendingTaskCount,
                    pendingEsmCount = pendingEsmCount,
                    unresolvedConflictCount = unresolvedConflicts.size,
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
                    org.muilab.notigpt.ui.component.appbar.Tab.Reminders -> RemindersScreen(
                        drawerViewModel = drawerViewModel,
                        preferenceViewModel = preferenceViewModel,
                    )
                    org.muilab.notigpt.ui.component.appbar.Tab.ESM -> EsmScreen()
                    org.muilab.notigpt.ui.component.appbar.Tab.Preferences -> PreferenceChatScreen(
                        preferenceViewModel = preferenceViewModel,
                    )
                }
            }
        }
    }
}