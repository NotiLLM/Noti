package org.muilab.notigpt.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.muilab.notigpt.model.notifications.NotiDrawerItem
import org.muilab.notigpt.model.notifications.NotiGroupItem
import org.muilab.notigpt.model.notifications.NotiItem
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.ui.component.notification.card.groupcard.GroupCard
import org.muilab.notigpt.ui.component.notification.card.noticard.NotiCard
import org.muilab.notigpt.ui.component.notification.card.notirecord.NotiRecordContextCard
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel

private const val HISTORY_PAGE_SIZE = 20

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NotificationsScreen(
    drawerViewModel: DrawerViewModel,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var viewportBounds by remember { mutableStateOf<Rect?>(null) }

    val activeItems by drawerViewModel.groupedNotifications.collectAsState()
    val activeCount by drawerViewModel.activeNotDismissedCount.collectAsState()

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
                // Trigger when user scrolls near the end.
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0 && lastVisibleIndex >= total - 6) {
                    scope.launch { loadMore() }
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .onGloballyPositioned { viewportBounds = it.boundsInWindow() },
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Text(
                text = "New Notifications (${activeCount})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        items(activeItems, key = { it.id }) { item: NotiDrawerItem ->
            when (item) {
                is NotiItem -> {
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
                    )
                }
                is NotiGroupItem -> {
                    GroupCard(
                        context = context,
                        groupItem = item,
                        drawerViewModel = drawerViewModel,
                        isMergeTarget = false,
                        isSortingMode = false,
                        parentViewport = viewportBounds,
                    )
                }
            }
        }

        item {
            Text(
                text = "All Notifications",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
