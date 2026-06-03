package org.muilab.notigpt.ui.notification.screen

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
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.ui.notification.component.card.noticard.NotiCard
import org.muilab.notigpt.ui.notification.component.card.notirecord.NotiRecordContextCard
import org.muilab.notigpt.ui.notification.state.DragState
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val HISTORY_PAGE_SIZE = 20

/**
 * Main screen for browsing, searching, sorting, and acting on captured notifications.
 *
 * This screen renders active NotiDisplayUnit rows directly. Persistent mutations stay behind DrawerViewModel
 * callbacks and screen-local state is only for temporary UI concerns.
 */
@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationsScreen(
    drawerViewModel: DrawerViewModel,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var viewportBounds by remember { mutableStateOf<Rect?>(null) }

    val activeNotiUnits by drawerViewModel.activeNotiUnits.collectAsState()
    val activeCount by drawerViewModel.activeNotDismissedCount.collectAsState()
    val isSortingMode by drawerViewModel.isSortingMode.collectAsState()

    val dragState = remember { DragState() }
    var activeOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var holdOrderOnExit by remember { mutableStateOf(false) }

    LaunchedEffect(isSortingMode) {
        if (isSortingMode) {
            holdOrderOnExit = false
            drawerViewModel.startManualSortSession()
            activeOrder = activeNotiUnits.map { it.notiKey }
        } else {
            if (activeOrder.isNotEmpty()) {
                holdOrderOnExit = true
                kotlinx.coroutines.delay(80)
                holdOrderOnExit = false
            }
            dragState.clear()
        }
    }

    LaunchedEffect(activeNotiUnits, isSortingMode) {
        if (!isSortingMode) return@LaunchedEffect
        if (dragState.draggingId != null) return@LaunchedEffect
        val latestActiveOrder = activeNotiUnits.map { it.notiKey }
        if (latestActiveOrder.toSet() != activeOrder.toSet()) {
            activeOrder = latestActiveOrder
        }
    }

    fun moveActiveOptimistically(key: String, from: Int, to: Int) {
        if (from == to) return
        val current = activeOrder
        if (current.isEmpty()) return
        val fromClamped = from.coerceIn(0, current.lastIndex)
        val toClamped = to.coerceIn(0, current.lastIndex)
        if (fromClamped == toClamped) return
        if (current.getOrNull(fromClamped) != key) {
            val idx = current.indexOf(key)
            if (idx == -1) return
            return moveActiveOptimistically(key, idx, toClamped)
        }

        activeOrder = current.toMutableList().apply {
            add(toClamped, removeAt(fromClamped))
        }
        drawerViewModel.moveActiveNotiUnit(key, fromClamped, toClamped)
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

    val displayedNotiUnits: List<NotiDisplayUnit> = remember(activeNotiUnits, activeOrder, isSortingMode, holdOrderOnExit) {
        if (!isSortingMode && !holdOrderOnExit) return@remember activeNotiUnits

        val unitByKey = activeNotiUnits.associateBy { it.notiKey }
        val orderedUnits = activeOrder.mapNotNull { unitByKey[it] }
        val inactiveKeys = unitByKey.keys - orderedUnits.map { it.notiKey }.toSet()
        val fallbackUnits = inactiveKeys.mapNotNull { unitByKey[it] }
        orderedUnits + fallbackUnits
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        if (fromKey !in activeOrder || toKey !in activeOrder) return@rememberReorderableLazyListState

        val fromIdx = activeOrder.indexOf(fromKey)
        val toIdx = activeOrder.indexOf(toKey)
        if (fromIdx == -1 || toIdx == -1 || fromIdx == toIdx) return@rememberReorderableLazyListState

        moveActiveOptimistically(fromKey, fromIdx, toIdx)
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

            items(displayedNotiUnits, key = { it.notiKey }) { displayUnit ->
                ReorderableItem(
                    state = reorderableState,
                    key = displayUnit.notiKey,
                    enabled = isSortingMode,
                ) { isDragging ->
                    NotiCard(
                        context = context,
                        notiDisplayUnit = displayUnit,
                        isDragging = isDragging,
                        drawerViewModel = drawerViewModel,
                        isCardVisible = true,
                        parentViewport = viewportBounds,
                        swipeEnabled = true,
                        reorderEnabled = isSortingMode,
                        reorderScope = this,
                    )
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
