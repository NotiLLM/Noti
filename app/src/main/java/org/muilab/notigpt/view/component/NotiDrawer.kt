package org.muilab.notigpt.view.component

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.view.component.notification.NotiCard
import org.muilab.notigpt.viewModel.DrawerViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotiDrawer(context: Context, drawerViewModel: DrawerViewModel) {

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val notifications by drawerViewModel.optimisticNotifications.collectAsState()
    val isAppCategoryView by drawerViewModel.isAppCategoryView.collectAsState()
    val category by drawerViewModel.category.collectAsState()
    val appCategory by drawerViewModel.appCategory.collectAsState()

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        drawerViewModel.onNotificationMoved(from.key.toString(), from.index, to.index)
        Log.d("NotiDrawer", "Moved from ${from.key} at ${from.index} to ${to.index}")
    }

    // --- State to hold the keys of fully visible cards ---
    var fullyVisibleCardKeys by remember { mutableStateOf(emptySet<String>()) }

    // --- The main visibility detection logic ---
    // This LaunchedEffect is the source of truth for card visibility.
    LaunchedEffect(lazyListState) {
        // Use snapshotFlow to observe changes to layoutInfo
        snapshotFlow { lazyListState.layoutInfo }
            .collectLatest { layoutInfo: LazyListLayoutInfo ->
                val viewportStartOffset = layoutInfo.viewportStartOffset
                val viewportEndOffset = layoutInfo.viewportEndOffset

                val visibleItems = layoutInfo.visibleItemsInfo

                val newVisibleKeys = visibleItems.filter { item ->
                    // Check if the item is fully contained within the viewport
                    val itemStart = item.offset
                    val itemEnd = item.offset + item.size
                    itemStart >= viewportStartOffset && itemEnd <= viewportEndOffset
                }.mapNotNull { it.key as? String } // Use mapNotNull for safety
                    .toSet()

                // Update the state only if the set of visible keys has changed
                if (newVisibleKeys != fullyVisibleCardKeys) {
                    // Log.d("Visibility", "Fully visible cards changed: $newVisibleKeys")
                    fullyVisibleCardKeys = newVisibleKeys
                }
            }
    }

    LaunchedEffect(category, appCategory) {
        // Scroll to top on category change
        lazyListState.animateScrollToItem(0)
    }

    // Wrap list in a Box so we can overlay a loading spinner when Paging is refreshing
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(notifications, key = { it.notiKey }) { notiDisplayUnit ->
                ReorderableItem(reorderableState, key = notiDisplayUnit.notiKey) { isDragging ->

                    // Debug: log the number of records we're about to render for this unit
                    Log.d("NotiDrawer", "Rendering NotiCard key=${notiDisplayUnit.notiKey} records=${notiDisplayUnit.notiRecords.size}")

                    NotiCard(
                        context = context,
                        notiDisplayUnit = notiDisplayUnit,
                        isDragging = isDragging,
                        drawerViewModel = drawerViewModel,
                        isCardVisible = fullyVisibleCardKeys.contains(notiDisplayUnit.notiKey),
                        // PASS a callback to mark the whole card as read
                        onNotiCardRead = { isManual ->
                            drawerViewModel.markNotificationAsRead(notiDisplayUnit.notiKey, isManual)
                        },
                        // PASS a callback to mark a single record as read
                        onNotiRecordRead = { recordId ->
                            drawerViewModel.markRecordAsRead(recordId)
                        },
                        category = category,
                        appCategory = appCategory
                    )
                }
            }
        }

        // Spinner is shown in AppScaffold; NotiDrawer no longer renders it.
    }

    val firstVisibleIndex by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex }
    }
    val scrollOffset by remember {
        derivedStateOf { lazyListState.firstVisibleItemScrollOffset }
    }
    if (firstVisibleIndex > 0) {
        val notiCount = firstVisibleIndex + if (scrollOffset > 0) 1 else 0

        val displayText = "$notiCount notifications above"

        ElevatedButton(
            onClick = {
                coroutineScope.launch {
                    lazyListState.scrollToItem(0)
                }
            },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.reach_top),
                "Reach Top",
                Modifier
                    .size(25.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                displayText,
                textAlign = TextAlign.Center
            )
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                drawerViewModel.syncManualSortOrder(isAppCategoryView)
                // Call the new ViewModel function
                drawerViewModel.persistReadStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            drawerViewModel.syncManualSortOrder(isAppCategoryView)
            // Call the new ViewModel function
            drawerViewModel.persistReadStatus()
        }
    }
}