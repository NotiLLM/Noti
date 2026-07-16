package org.muilab.notigpt.ui.common.component

import android.util.Log
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.NotiCategory
import org.muilab.notigpt.ui.preference.model.PreferenceEntryPoint
import org.muilab.notigpt.ui.common.feedback.AppSnackbar
import org.muilab.notigpt.ui.common.appbar.AppTopBar
import org.muilab.notigpt.ui.home.HomeScreen
import org.muilab.notigpt.ui.home.viewmodel.HomeViewModel
import org.muilab.notigpt.ui.settings.SettingsScreen
import org.muilab.notigpt.ui.preference.component.PreferenceLearningBottomSheet
import org.muilab.notigpt.ui.preference.screen.PreferenceChatScreen
import org.muilab.notigpt.ui.reminder.screen.RemindersScreen
import org.muilab.notigpt.ui.reminder.screen.ScheduledRemindersScreen
import org.muilab.notigpt.ui.common.navigation.AppMenuScreen
import org.muilab.notigpt.ui.common.navigation.HomeDestination
import org.muilab.notigpt.ui.common.navigation.SavedListFilter
import org.muilab.notigpt.ui.notification.screen.NotiCategoryScreen
import org.muilab.notigpt.ui.notification.screen.NotificationHistoryScreen
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.preference.viewmodel.PreferenceViewModel
import org.muilab.notigpt.ui.review.ReviewScreen
import org.muilab.notigpt.ui.reminder.viewmodel.ReminderViewModel
import org.muilab.notigpt.ui.reminder.viewmodel.ScheduledReminderViewModel
import org.muilab.notigpt.ui.theme.MotionSpecs
import kotlin.coroutines.cancellation.CancellationException

/**
 * Immutable snapshot of the current navigation position, used as the [AnimatedContent] key so the
 * outgoing and incoming screens each render from their own state during a transition. [depth]
 * (back-stack size, +1 while a menu screen is open) drives push-vs-pop direction.
 */
private data class NavSnapshot(
    val menuScreen: AppMenuScreen?,
    val dest: HomeDestination,
    val depth: Int,
)

/**
 * Top-level Compose scaffold that places app bars, navigation, and the selected screen content.
 *
 * Navigation is a small explicit back stack of [HomeDestination] (home overview + pushed Review /
 * notification-category / saved-list screens), plus the hamburger [AppMenuScreen] sections. Screen
 * state and feature actions flow through ViewModels rather than being stored here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    drawerViewModel: DrawerViewModel,
    onSignedOut: () -> Unit,
) {
    Log.d("AppScaffold", "composed with DrawerViewModel hash=${drawerViewModel.hashCode()}")

    var isSearchExpanded by remember { mutableStateOf(false) }
    var menuScreen by remember { mutableStateOf<AppMenuScreen?>(null) }
    var accountRevision by remember { mutableStateOf(0) }
    val backStack = remember { mutableStateListOf<HomeDestination>(HomeDestination.Home) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current

    val currentDest = backStack.last()
    val atHomeRoot = menuScreen == null && currentDest is HomeDestination.Home

    // A task/keep detail editor is open → hide the app bars (full-screen detail).
    var detailOpen by remember { mutableStateOf(false) }
    // Category pending "clear all" confirmation (the clear is a hard delete, so we confirm first).
    var clearConfirmCategory by remember { mutableStateOf<String?>(null) }
    var clearConfirmDontShowAgain by remember { mutableStateOf(false) }
    // The home root uses a collapsing large-title bar; list screens use an enter-always small bar;
    // everything else has a pinned small bar (no scroll behavior).
    val listScrollEnabled = menuScreen == AppMenuScreen.History ||
        menuScreen == AppMenuScreen.Reminders ||
        (menuScreen == null && (currentDest is HomeDestination.SavedList || currentDest is HomeDestination.NotiList))
    val topBarState = rememberTopAppBarState()
    // Both behaviors share topBarState; only the active one is attached to the Scaffold's nestedScroll.
    val exitUntilCollapsed = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val enterAlways = TopAppBarDefaults.enterAlwaysScrollBehavior(topBarState)
    val topBarScrollBehavior = when {
        atHomeRoot -> exitUntilCollapsed
        listScrollEnabled -> enterAlways
        else -> null
    }
    LaunchedEffect(currentDest, menuScreen) {
        topBarState.heightOffset = 0f
        topBarState.contentOffset = 0f
    }

    val reminderViewModel: ReminderViewModel = viewModel()
    val scheduledReminderViewModel: ScheduledReminderViewModel = viewModel()
    val preferenceViewModel: PreferenceViewModel = viewModel()
    // Shared with HomeScreen (same activity ViewModelStoreOwner) — used here only for the drawer badges.
    val homeViewModel: HomeViewModel = viewModel()
    val activeTaskCount by homeViewModel.activeTaskCount.collectAsState()
    val activeKeepCount by homeViewModel.activeKeepCount.collectAsState()

    val appSearchQuery by drawerViewModel.queryString.collectAsState()

    val unresolvedConflicts by preferenceViewModel.unresolvedConflicts.collectAsState()
    val dueUnseenReminderCount by scheduledReminderViewModel.dueUnseenCount.collectAsState()

    // ── Snackbar for delete / manual-extract preference prompt ───
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarEvent by preferenceViewModel.snackbarEvent.collectAsState()

    val snackDeleteMsg = stringResource(R.string.pref_snackbar_deleted)
    val snackExtractMsg = stringResource(R.string.pref_snackbar_extracted)
    val snackAction = stringResource(R.string.pref_snackbar_action)

    val snackbarChannel = remember { Channel<PreferenceViewModel.SnackbarEvent>(Channel.UNLIMITED) }

    LaunchedEffect(snackbarEvent) {
        val event = snackbarEvent ?: return@LaunchedEffect
        snackbarChannel.trySend(event)
        preferenceViewModel.dismissSnackbar()
    }

    LaunchedEffect(snackbarChannel) {
        snackbarChannel.receiveAsFlow().collect { event ->
            val message = when (event.entryPoint) {
                PreferenceEntryPoint.DELETE -> snackDeleteMsg
                PreferenceEntryPoint.MANUAL_EXTRACT -> snackExtractMsg
                PreferenceEntryPoint.EDIT -> return@collect
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

    // App-wide status messages (replaces scattered Toasts). Emitted via AppSnackbar from UI or VMs.
    LaunchedEffect(Unit) {
        AppSnackbar.messages.collect { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.onAction?.invoke()
        }
    }

    // Saved-list screens honor the shared search bar; other destinations ignore it.
    LaunchedEffect(appSearchQuery, currentDest, menuScreen) {
        if (menuScreen == null && currentDest is HomeDestination.SavedList) {
            reminderViewModel.updateSearchQuery(appSearchQuery)
        }
    }

    // Navigate to Preferences chat when the preference flow redirects there.
    val navigateToChat by preferenceViewModel.navigateToChat.collectAsState()
    LaunchedEffect(navigateToChat) {
        if (navigateToChat) {
            menuScreen = AppMenuScreen.Preferences
            isSearchExpanded = false
            preferenceViewModel.onChatNavigated()
        }
    }

    // Observe manual extraction events to trigger preference learning (Flow 3).
    LaunchedEffect(Unit) {
        drawerViewModel.manualExtractEvent.collect { notiKey ->
            val notiUnit = drawerViewModel.getNotiUnitForHistory(notiKey)
            val contextData = mutableMapOf<String, Any?>("notiKey" to notiKey)
            if (notiUnit != null) {
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

    PreferenceLearningBottomSheet(preferenceViewModel = preferenceViewModel)
    org.muilab.notigpt.ui.preference.component.PreferenceQuickSyncReviewDialog(preferenceViewModel = preferenceViewModel)

    // ── Navigation helpers ──
    fun clearSearch() {
        drawerViewModel.updateQueryString("")
        isSearchExpanded = false
    }

    val openMenuScreen: (AppMenuScreen) -> Unit = { screen ->
        menuScreen = screen
        clearSearch()
        scope.launch { drawerState.close() }
    }
    val resetToHome: () -> Unit = {
        menuScreen = null
        backStack.clear()
        backStack.add(HomeDestination.Home)
        clearSearch()
        scope.launch { drawerState.close() }
    }
    val pushHome: (HomeDestination) -> Unit = { dest ->
        menuScreen = null
        backStack.add(dest)
        clearSearch()
    }
    val openSavedListFromDrawer: (SavedListFilter) -> Unit = { filter ->
        pushHome(HomeDestination.SavedList(filter))
        scope.launch { drawerState.close() }
    }
    val popBack: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    // Predictive back: scrub a subtle scale on the content during the gesture, commit the pop on
    // completion (which then plays the AnimatedContent pop), and spring back on cancel.
    var backProgress by remember { mutableStateOf(0f) }
    PredictiveBackHandler(enabled = menuScreen != null || backStack.size > 1) { events ->
        try {
            events.collect { event -> backProgress = event.progress }
            backProgress = 0f
            if (menuScreen != null) menuScreen = null else popBack()
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }

    // ── Top-bar configuration for the current destination ──
    val screenTitle: String? = when {
        menuScreen != null -> menuScreenTitle(menuScreen!!)
        else -> homeDestinationTitle(currentDest)
    }
    val showBack = menuScreen != null || currentDest !is HomeDestination.Home
    val showSearch = when {
        menuScreen == AppMenuScreen.History || menuScreen == AppMenuScreen.Reminders -> true
        menuScreen != null -> false
        currentDest is HomeDestination.SavedList || currentDest is HomeDestination.NotiList -> true
        else -> false
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the hamburger icon; swipe-to-open steals horizontal card swipes.
        // Keep gestures on while open so swipe / scrim tap still close the drawer.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawerContent(
                isHome = atHomeRoot,
                accountEmail = remember(accountRevision) {
                    org.muilab.notigpt.data.remote.auth.GoogleAuthManager.currentUser()?.email
                },
                unresolvedConflictCount = unresolvedConflicts.size,
                dueUnseenReminderCount = dueUnseenReminderCount,
                activeTaskCount = activeTaskCount,
                activeKeepCount = activeKeepCount,
                onHomeSelected = resetToHome,
                onSavedListSelected = openSavedListFromDrawer,
                onMenuScreenSelected = openMenuScreen,
            )
        },
    ) {
        Scaffold(
            modifier = if (topBarScrollBehavior != null) {
                Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection)
            } else Modifier,
            topBar = {
                AnimatedVisibility(
                    visible = !detailOpen,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                ) {
                    AppTopBar(
                        drawerViewModel = drawerViewModel,
                        isSearchExpanded = isSearchExpanded,
                        onSearchToggled = { isSearchExpanded = it },
                        onMenuClicked = { scope.launch { drawerState.open() } },
                        screenTitle = screenTitle,
                        onNavigateBack = if (showBack) {
                            { if (menuScreen != null) menuScreen = null else popBack() }
                        } else null,
                        showSearch = showSearch,
                        searchQuery = appSearchQuery,
                        onSearchQueryChange = drawerViewModel::updateQueryString,
                        scrollBehavior = topBarScrollBehavior,
                        large = atHomeRoot,
                        actions = {
                            // Clear-all bin on the notification category (Communication/Content) pages.
                            // Scoped to the visible list: the current category + time window, excluding pinned.
                            if (menuScreen == null && currentDest is HomeDestination.NotiList) {
                                val category = (currentDest as HomeDestination.NotiList).category
                                val clearableCounts by drawerViewModel.clearableVisibleCountByCategory.collectAsState()
                                val clearableCount = clearableCounts[category] ?: 0
                                val enabled = clearableCount > 0
                                IconButton(
                                    enabled = enabled,
                                    onClick = {
                                        if (org.muilab.notigpt.util.SharedPreferencesManager.skipClearAllConfirm) {
                                            drawerViewModel.clearVisibleNotis(category)
                                        } else {
                                            clearConfirmDontShowAgain = false
                                            clearConfirmCategory = category
                                        }
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = stringResource(R.string.ui_user_clear_all),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else 0.38f),
                                    )
                                }
                            }
                        },
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            // Canvas: the lightest surface; cards (surfaceContainerLow / surfaceBright) provide
            // subtle separation. See the surface-role note in Color.kt.
            containerColor = MaterialTheme.colorScheme.surface,
        ) { paddingValues ->
            val navDepth = backStack.size + (if (menuScreen != null) 1 else 0)
            val navSnapshot = NavSnapshot(menuScreen, currentDest, navDepth)
            AnimatedContent(
                targetState = navSnapshot,
                modifier = Modifier
                    .padding(paddingValues)
                    .graphicsLayer {
                        val s = 1f - 0.06f * backProgress.coerceIn(0f, 1f)
                        scaleX = s
                        scaleY = s
                    },
                transitionSpec = {
                    val ms = MotionSpecs.ScreenTransitionMs
                    val menuInvolved = initialState.menuScreen != null || targetState.menuScreen != null
                    when {
                        menuInvolved ->
                            fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
                        targetState.depth >= initialState.depth ->
                            (slideInHorizontally(tween(ms)) { it / 4 } + fadeIn(tween(ms))) togetherWith
                                (slideOutHorizontally(tween(ms)) { -it / 6 } + fadeOut(tween(ms)))
                        else ->
                            (slideInHorizontally(tween(ms)) { -it / 6 } + fadeIn(tween(ms))) togetherWith
                                (slideOutHorizontally(tween(ms)) { it / 4 } + fadeOut(tween(ms)))
                    }
                },
                label = "screen",
            ) { snap ->
                Box(Modifier.fillMaxSize()) {
                    when (snap.menuScreen) {
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
                        AppMenuScreen.Settings -> SettingsScreen(
                            onSignedOut = onSignedOut,
                            onAccountChanged = { accountRevision += 1 },
                        )
                        null -> when (val dest = snap.dest) {
                            HomeDestination.Home -> HomeScreen(
                                drawerViewModel = drawerViewModel,
                                onOpenReview = { pushHome(HomeDestination.Review) },
                                onOpenNotiCategory = { category -> pushHome(HomeDestination.NotiList(category)) },
                                onOpenSavedList = { filter -> pushHome(HomeDestination.SavedList(filter)) },
                                dueReminderCount = dueUnseenReminderCount,
                                onOpenReminders = { openMenuScreen(AppMenuScreen.Reminders) },
                            )
                            HomeDestination.Review -> ReviewScreen(
                                drawerViewModel = drawerViewModel,
                                reminderViewModel = reminderViewModel,
                                preferenceViewModel = preferenceViewModel,
                                onBack = popBack,
                                onOpenUndetermined = {
                                    popBack()
                                    pushHome(HomeDestination.SavedList(SavedListFilter.Undetermined))
                                },
                                onDetailOpenChange = { detailOpen = it },
                            )
                            is HomeDestination.NotiList -> NotiCategoryScreen(
                                category = dest.category,
                                drawerViewModel = drawerViewModel,
                                scheduledReminderViewModel = scheduledReminderViewModel,
                                searchQuery = appSearchQuery,
                                onOpenSavedItem = { item ->
                                    pushHome(
                                        HomeDestination.SavedList(
                                            filter = if (item.isTask) SavedListFilter.Tasks else SavedListFilter.Keep,
                                            focusItemId = item.savedItemId,
                                        )
                                    )
                                },
                            )
                            is HomeDestination.SavedList -> {
                                val listMode = when (dest.filter) {
                                    SavedListFilter.Tasks -> ReminderViewModel.ListMode.Tasks
                                    SavedListFilter.Keep -> ReminderViewModel.ListMode.Keep
                                    else -> ReminderViewModel.ListMode.All
                                }
                                val smart = when (dest.filter) {
                                    SavedListFilter.Tasks, SavedListFilter.Keep -> null
                                    else -> dest.filter
                                }
                                RemindersScreen(
                                    drawerViewModel = drawerViewModel,
                                    reminderViewModel = reminderViewModel,
                                    scheduledReminderViewModel = scheduledReminderViewModel,
                                    preferenceViewModel = preferenceViewModel,
                                    listMode = listMode,
                                    smartFilter = smart,
                                    initialDetailItemId = dest.focusItemId,
                                    onDetailOpenChange = { detailOpen = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    clearConfirmCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { clearConfirmCategory = null },
            title = { Text(stringResource(R.string.noti_clear_all_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.noti_clear_all_confirm_message))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clickable { clearConfirmDontShowAgain = !clearConfirmDontShowAgain },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = clearConfirmDontShowAgain,
                            onCheckedChange = { clearConfirmDontShowAgain = it },
                        )
                        Text(stringResource(R.string.noti_clear_all_dont_show_again))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (clearConfirmDontShowAgain) {
                        org.muilab.notigpt.util.SharedPreferencesManager.skipClearAllConfirm = true
                    }
                    drawerViewModel.clearVisibleNotis(category)
                    clearConfirmCategory = null
                }) {
                    Text(
                        stringResource(R.string.ui_user_clear_all),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmCategory = null }) {
                    Text(stringResource(R.string.ui_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun menuScreenTitle(screen: AppMenuScreen): String = when (screen) {
    AppMenuScreen.Reminders -> stringResource(R.string.menu_reminders)
    AppMenuScreen.Preferences -> stringResource(R.string.tab_preferences)
    AppMenuScreen.History -> stringResource(R.string.menu_history)
    AppMenuScreen.Settings -> stringResource(R.string.menu_settings)
}

@Composable
private fun homeDestinationTitle(dest: HomeDestination): String? = when (dest) {
    HomeDestination.Home -> null
    HomeDestination.Review -> stringResource(R.string.review_title)
    is HomeDestination.NotiList -> when (dest.category) {
        NotiCategory.Communication -> stringResource(R.string.home_noti_communication)
        NotiCategory.Content -> stringResource(R.string.home_noti_content)
        else -> dest.category
    }
    is HomeDestination.SavedList -> when (dest.filter) {
        SavedListFilter.TodayEarlier -> stringResource(R.string.home_filter_today)
        SavedListFilter.Upcoming -> stringResource(R.string.home_filter_upcoming)
        SavedListFilter.Someday -> stringResource(R.string.home_filter_someday)
        SavedListFilter.Undetermined -> stringResource(R.string.home_filter_undetermined)
        SavedListFilter.Starred -> stringResource(R.string.home_filter_starred)
        SavedListFilter.Tasks -> stringResource(R.string.home_collection_tasks)
        SavedListFilter.Keep -> stringResource(R.string.home_collection_keep)
    }
}
