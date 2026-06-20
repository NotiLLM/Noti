package org.muilab.notigpt.ui.common.component

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.preference.model.PreferenceEntryPoint
import org.muilab.notigpt.ui.common.appbar.AppTopBar
import org.muilab.notigpt.ui.home.NewScreen
import org.muilab.notigpt.ui.settings.SettingsScreen
import org.muilab.notigpt.ui.preference.component.PreferenceLearningBottomSheet
import org.muilab.notigpt.ui.preference.screen.PreferenceChatScreen
import org.muilab.notigpt.ui.reminder.screen.RemindersScreen
import org.muilab.notigpt.ui.reminder.screen.ScheduledRemindersScreen
import org.muilab.notigpt.ui.common.navigation.AppMenuScreen
import org.muilab.notigpt.ui.common.navigation.AppPrimaryTab
import org.muilab.notigpt.ui.notification.screen.NotificationHistoryScreen
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel
import org.muilab.notigpt.ui.reminder.viewmodel.ReminderViewModel
import org.muilab.notigpt.ui.reminder.viewmodel.ScheduledReminderViewModel

/**
 * Top-level Compose scaffold that places app bars, navigation, and the selected screen content.
 *
 * Keep app-shell layout here. Screen state and feature actions should flow through ViewModels rather than being
 * stored in the scaffold.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppScaffold(
    drawerViewModel: DrawerViewModel,
) {

    Log.d("AppScaffold", "composed with DrawerViewModel hash=${drawerViewModel.hashCode()}")

    var isSearchExpanded by remember { mutableStateOf(false) }
    var menuScreen by remember { mutableStateOf<AppMenuScreen?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(AppPrimaryTab.New) }

    // A task/keep detail editor is open → hide the app bars (full-screen detail).
    var detailOpen by remember { mutableStateOf(false) }
    // Slide bars away on scroll-down, back on scroll-up — only on the list-style screens.
    var barsVisible by remember { mutableStateOf(true) }
    val barsScrollEnabled = menuScreen == null ||
        menuScreen == AppMenuScreen.History ||
        menuScreen == AppMenuScreen.Reminders
    LaunchedEffect(selectedTab, menuScreen) { barsVisible = true }
    val barHideConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f) barsVisible = false
                else if (available.y > 2f) barsVisible = true
                return Offset.Zero
            }
        }
    }
    val barsShown = !detailOpen && (barsVisible || !barsScrollEnabled)

    val reminderViewModel: ReminderViewModel = viewModel()
    val scheduledReminderViewModel: ScheduledReminderViewModel = viewModel()
    val preferenceViewModel: PreferenceViewModel = viewModel()

    val appSearchQuery by drawerViewModel.queryString.collectAsState()
    val newNotiRecords by drawerViewModel.newNotificationRecords.collectAsState()
    val unreadNotiCount = remember(newNotiRecords) { newNotiRecords.values.sumOf { it.size } }
    val reminderList by reminderViewModel.allReminders.collectAsState()
    val pendingTaskCount = remember(reminderList) {
        reminderList.count { it.isTask && !it.isCompleted && !it.isNewLike }
    }

    val unresolvedConflicts by preferenceViewModel.unresolvedConflicts.collectAsState()
    val dueUnseenReminderCount by scheduledReminderViewModel.dueUnseenCount.collectAsState()

    // ── Snackbar for delete / manual-extract preference prompt ───
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarEvent by preferenceViewModel.snackbarEvent.collectAsState()

    // Resolve string resources outside the LaunchedEffect (Composable context)
    val snackDeleteMsg = stringResource(R.string.pref_snackbar_deleted)
    val snackExtractMsg = stringResource(R.string.pref_snackbar_extracted)
    val snackAction = stringResource(R.string.pref_snackbar_action)

    // Use a Channel so rapid successive events are queued rather than dropped.
    // LaunchedEffect(snackbarEvent) would cancel the in-flight showSnackbar coroutine
    // each time a new event arrives, causing snackbars to silently disappear.
    val snackbarChannel = remember { Channel<PreferenceViewModel.SnackbarEvent>(Channel.UNLIMITED) }

    LaunchedEffect(snackbarEvent) {
        val event = snackbarEvent ?: return@LaunchedEffect
        snackbarChannel.trySend(event)
        preferenceViewModel.dismissSnackbar() // clear the StateFlow so next event can arrive
    }

    LaunchedEffect(snackbarChannel) {
        snackbarChannel.receiveAsFlow().collect { event ->
            val message = when (event.entryPoint) {
                PreferenceEntryPoint.DELETE -> snackDeleteMsg
                PreferenceEntryPoint.MANUAL_EXTRACT -> snackExtractMsg
                PreferenceEntryPoint.EDIT -> return@collect // edits use BottomSheet directly
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = snackAction,
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> preferenceViewModel.promoteSnackbarToFlow(event)
                SnackbarResult.Dismissed -> { /* already cleared */ }
            }
        }
    }

    LaunchedEffect(appSearchQuery, selectedTab, menuScreen) {
        if (menuScreen == null && (selectedTab == AppPrimaryTab.Tasks || selectedTab == AppPrimaryTab.Keep)) {
            reminderViewModel.updateSearchQuery(appSearchQuery)
        }
    }

    // Navigate to Chat tab when preference flow redirects there
    val navigateToChat by preferenceViewModel.navigateToChat.collectAsState()
    LaunchedEffect(navigateToChat) {
        if (navigateToChat) {
            menuScreen = AppMenuScreen.Preferences
            isSearchExpanded = false
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
                val db = org.muilab.notigpt.data.local.room.AppDatabase.getInstance(context.applicationContext)
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

    val openMenuScreen: (AppMenuScreen) -> Unit = { screen ->
        menuScreen = screen
        drawerViewModel.updateQueryString("")
        isSearchExpanded = false
        scope.launch { drawerState.close() }
    }

    val openPrimaryTab: (AppPrimaryTab) -> Unit = { tab ->
        selectedTab = tab
        menuScreen = null
        drawerViewModel.updateQueryString("")
        isSearchExpanded = false
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            AppDrawerContent(
                selectedPrimaryTab = if (menuScreen == null) selectedTab else null,
                unresolvedConflictCount = unresolvedConflicts.size,
                dueUnseenReminderCount = dueUnseenReminderCount,
                onPrimaryTabSelected = openPrimaryTab,
                onMenuScreenSelected = openMenuScreen,
            )
        },
    ) {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = barsShown,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                ) {
                    AppTopBar(
                        drawerViewModel = drawerViewModel,
                        isSearchExpanded = isSearchExpanded,
                        onSearchToggled = { isSearchExpanded = it },
                        showMenuButton = true,
                        onMenuClicked = { scope.launch { drawerState.open() } },
                        menuScreenTitle = null,
                        onMenuScreenClosed = { menuScreen = null },
                        // Search lives in this shared top bar for every screen.
                        showNotificationActions = false,
                        searchQuery = appSearchQuery,
                        onSearchQueryChange = drawerViewModel::updateQueryString,
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (menuScreen == null) {
                    AnimatedVisibility(
                        visible = barsShown,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        org.muilab.notigpt.ui.common.appbar.AppBottomBar(
                            selectedTab = selectedTab,
                            unreadNotificationCount = unreadNotiCount,
                            pendingTaskCount = pendingTaskCount,
                            onTabSelected = { tab ->
                                selectedTab = tab
                                drawerViewModel.updateQueryString("")
                                isSearchExpanded = false
                            }
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceDim
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .then(if (barsScrollEnabled) Modifier.nestedScroll(barHideConnection) else Modifier)
            ) {
                when (menuScreen) {
                    AppMenuScreen.Reminders -> ScheduledRemindersScreen(
                        viewModel = scheduledReminderViewModel,
                        searchQuery = appSearchQuery,
                    )
                    AppMenuScreen.Preferences -> PreferenceChatScreen(
                        preferenceViewModel = preferenceViewModel,
                    )
                    AppMenuScreen.History -> NotificationHistoryScreen(
                        drawerViewModel = drawerViewModel,
                        searchQuery = appSearchQuery,
                    )
                    AppMenuScreen.Settings -> SettingsScreen()
                    null -> when (selectedTab) {
                        AppPrimaryTab.New -> NewScreen(
                            drawerViewModel = drawerViewModel,
                            reminderViewModel = reminderViewModel,
                            scheduledReminderViewModel = scheduledReminderViewModel,
                            preferenceViewModel = preferenceViewModel,
                            searchQuery = appSearchQuery,
                            onDetailOpenChange = { detailOpen = it },
                        )
                        AppPrimaryTab.Tasks -> RemindersScreen(
                            drawerViewModel = drawerViewModel,
                            reminderViewModel = reminderViewModel,
                            scheduledReminderViewModel = scheduledReminderViewModel,
                            preferenceViewModel = preferenceViewModel,
                            listMode = ReminderViewModel.ListMode.Tasks,
                            onDetailOpenChange = { detailOpen = it },
                        )
                        AppPrimaryTab.Keep -> RemindersScreen(
                            drawerViewModel = drawerViewModel,
                            reminderViewModel = reminderViewModel,
                            scheduledReminderViewModel = scheduledReminderViewModel,
                            preferenceViewModel = preferenceViewModel,
                            listMode = ReminderViewModel.ListMode.Keep,
                            onDetailOpenChange = { detailOpen = it },
                        )
                    }
                }
            }
        }
    }
}
