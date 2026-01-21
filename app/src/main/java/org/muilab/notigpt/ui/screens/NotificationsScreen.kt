package org.muilab.notigpt.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.muilab.notigpt.R
import org.muilab.notigpt.debug.DummyData
import org.muilab.notigpt.debug.ScreenshotMode
import org.muilab.notigpt.model.notifications.NotiDrawerItem
import org.muilab.notigpt.model.notifications.NotiGroupItem
import org.muilab.notigpt.model.notifications.NotiItem
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.ui.component.drawer.drag.DragState
import org.muilab.notigpt.ui.component.notification.card.groupcard.GroupCard
import org.muilab.notigpt.ui.component.notification.card.noticard.NotiCard
import org.muilab.notigpt.ui.component.notification.card.notirecord.NotiRecordContextCard
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val HISTORY_PAGE_SIZE = 20

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationsScreen(
    drawerViewModel: DrawerViewModel,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val screenshotModeEnabled by ScreenshotMode.enabled.collectAsState()
    if (screenshotModeEnabled) {
        val dummyItems = remember(screenshotModeEnabled) { DummyData.Notifications.buildDrawerItems(context) }
        val activeCount = remember(dummyItems) { dummyItems.count { !it.displayUnit.notiUnit.isDismissed } }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Transparent)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.ui_notifications_new, activeCount),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(dummyItems, key = { it.id }) { item ->
                    NotiCard(
                        context = context,
                        notiDisplayUnit = item.displayUnit,
                        isDragging = false,
                        drawerViewModel = drawerViewModel,
                        isCardVisible = true,
                        parentViewport = null,
                        isMergeTarget = false,
                        isInGroup = false,
                        swipeEnabled = true,
                        reorderEnabled = false,
                        reorderScope = null,
                        revealActions = item.id == dummyItems.firstOrNull()?.id,
                    )
                }
            }
        }
        return
    }

    // --- real data path ---
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var viewportBounds by remember { mutableStateOf<Rect?>(null) }

    val activeItems by drawerViewModel.groupedNotifications.collectAsState()
    val activeCount by drawerViewModel.activeNotDismissedCount.collectAsState()
    val isSortingMode by drawerViewModel.isSortingMode.collectAsState()

    // Drag state (loose items only)
    val dragState = remember { DragState() }

    // Keep a local optimistic order for loose items while sorting.
    // When not sorting, we just use activeItems as-is.
    var looseOrder by remember { mutableStateOf<List<String>>(emptyList()) }

    // Hold the last sorting order briefly when exiting sorting mode to prevent a one-frame reordering flash.
    var holdOrderOnExit by remember { mutableStateOf(false) }

    // Start/refresh manual sort session when entering sorting mode.
    LaunchedEffect(isSortingMode) {
        if (isSortingMode) {
            holdOrderOnExit = false
            drawerViewModel.startManualSortSession()
            looseOrder = activeItems.filterIsInstance<NotiItem>().map { it.displayUnit.notiKey }
        } else {
            // If we just exited sort mode, keep the last looseOrder for a short moment.
            // This prevents LazyColumn from briefly re-rendering the DB order before it updates.
            if (looseOrder.isNotEmpty()) {
                holdOrderOnExit = true
                kotlinx.coroutines.delay(80)
                holdOrderOnExit = false
            }
            dragState.clear()
        }
    }

    // Keep local loose order in sync if data changes while sorting and we aren't dragging.
    LaunchedEffect(activeItems, isSortingMode) {
        if (!isSortingMode) return@LaunchedEffect
        if (dragState.draggingId != null) return@LaunchedEffect
        val latestLoose = activeItems.filterIsInstance<NotiItem>().map { it.displayUnit.notiKey }
        // Only refresh if keys changed (avoid jank).
        if (latestLoose.toSet() != looseOrder.toSet()) {
            looseOrder = latestLoose
        }
    }

    fun moveLooseOptimistically(key: String, from: Int, to: Int) {
        if (from == to) return
        val current = looseOrder
        if (current.isEmpty()) return
        val fromClamped = from.coerceIn(0, current.lastIndex)
        val toClamped = to.coerceIn(0, current.lastIndex)
        if (fromClamped == toClamped) return
        if (current.getOrNull(fromClamped) != key) {
            val idx = current.indexOf(key)
            if (idx == -1) return
            return moveLooseOptimistically(key, idx, toClamped)
        }

        looseOrder = current.toMutableList().apply {
            add(toClamped, removeAt(fromClamped))
        }
        drawerViewModel.moveLooseItem(key, fromClamped, toClamped)
    }

    val history = remember { mutableStateListOf<NotiRecord>() }
    var isLoadingMore by remember { mutableStateOf(false) }
    var reachedEnd by remember { mutableStateOf(false) }

    suspend fun loadInitial() {
        isLoadingMore = true
        reachedEnd = false
        history.clear()
        val recs = withContext(Dispatchers.IO) {
            drawerViewModel.getLatestRecordsForHistory(HISTORY_PAGE_SIZE)
        }
        history.addAll(recs)
        isLoadingMore = false
        if (recs.size < HISTORY_PAGE_SIZE) reachedEnd = true
    }

    suspend fun loadMore() {
        if (isLoadingMore || reachedEnd) return
        val pivot = history.lastOrNull()?.time ?: return
        isLoadingMore = true
        val recs = withContext(Dispatchers.IO) {
            drawerViewModel.getRecordsBeforeForHistory(pivot, HISTORY_PAGE_SIZE)
        }
        if (recs.isEmpty()) {
            reachedEnd = true
        } else {
            history.addAll(recs)
            if (recs.size < HISTORY_PAGE_SIZE) reachedEnd = true
        }
        isLoadingMore = false
    }

    LaunchedEffect(Unit) {
        loadInitial()
    }

    // Infinite scroll trigger
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex == null) return@collect
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0 && lastVisibleIndex >= total - 6) {
                    scope.launch { loadMore() }
                }
            }
    }

    // Build a displayed list where loose items are reordered by looseOrder during sorting.
    val displayedItems: List<NotiDrawerItem> = remember(activeItems, looseOrder, isSortingMode, holdOrderOnExit) {
        if (!isSortingMode && !holdOrderOnExit) return@remember activeItems

        val looseMap = activeItems.filterIsInstance<NotiItem>().associateBy { it.displayUnit.notiKey }
        val orderedLoose = looseOrder.mapNotNull { looseMap[it] }
        val inactiveLooseKeys = looseMap.keys - orderedLoose.map { it.displayUnit.notiKey }.toSet()
        val fallbackLoose = inactiveLooseKeys.mapNotNull { looseMap[it] }

        val groups = activeItems.filterIsInstance<NotiGroupItem>()
        orderedLoose + fallbackLoose + groups
    }

    // --- Reorderable (loose items only) ---
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        // Only allow reordering between loose items. Ignore if either side isn't a loose item.
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        if (fromKey !in looseOrder || toKey !in looseOrder) return@rememberReorderableLazyListState

        // Use current indices from the optimistic order (not LazyList indices, which include headers/groups).
        val fromIdx = looseOrder.indexOf(fromKey)
        val toIdx = looseOrder.indexOf(toKey)
        if (fromIdx == -1 || toIdx == -1 || fromIdx == toIdx) return@rememberReorderableLazyListState

        moveLooseOptimistically(fromKey, fromIdx, toIdx)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Transparent)
            .onGloballyPositioned { coords ->
                viewportBounds = coords.boundsInWindow()
                dragState.boxTopLeftInRoot = coords.boundsInWindow().topLeft
                dragState.boxSize = coords.size
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.ui_notifications_new, activeCount),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(displayedItems, key = { it.id }) { item: NotiDrawerItem ->
                when (item) {
                    is NotiItem -> {
                        val id = item.displayUnit.notiKey
                        val isLoose = id in looseOrder

                        // Only loose items participate in reorder.
                        if (isLoose) {
                            ReorderableItem(
                                state = reorderableState,
                                key = id,
                                enabled = isSortingMode,
                            ) { isDragging ->
                                NotiCard(
                                    context = context,
                                    notiDisplayUnit = item.displayUnit,
                                    isDragging = isDragging,
                                    drawerViewModel = drawerViewModel,
                                    isCardVisible = true,
                                    parentViewport = viewportBounds,
                                    isMergeTarget = false,
                                    isInGroup = false,
                                    swipeEnabled = true,
                                    reorderEnabled = isSortingMode,
                                    reorderScope = this,
                                )
                            }
                        } else {
                            // Non-loose (e.g., orphaned/filtered) items render normally.
                            NotiCard(
                                context = context,
                                notiDisplayUnit = item.displayUnit,
                                isDragging = false,
                                drawerViewModel = drawerViewModel,
                                isCardVisible = true,
                                parentViewport = viewportBounds,
                                isMergeTarget = false,
                                isInGroup = false,
                                swipeEnabled = true,
                                reorderEnabled = false,
                            )
                        }
                    }

                    is NotiGroupItem -> {
                        GroupCard(
                            context = context,
                            groupItem = item,
                            drawerViewModel = drawerViewModel,
                            isMergeTarget = false,
                            isSortingMode = isSortingMode,
                            parentViewport = viewportBounds,
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.ui_notifications_all),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(history, key = { it.notiRecordId }) { rec ->
                NotiRecordContextCard(
                    notiKey = rec.notiKey,
                    records = listOf(rec),
                    drawerViewModel = drawerViewModel,
                )
            }
        }
    }
}
