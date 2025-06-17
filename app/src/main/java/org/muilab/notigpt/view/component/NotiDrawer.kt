package org.muilab.notigpt.view.component

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.repository.NotiRepository
import org.muilab.notigpt.repository.NotiRepositoryProvider
import org.muilab.notigpt.util.Constants
import org.muilab.notigpt.util.postOngoingNotification
import org.muilab.notigpt.view.component.notification.NotiCard
import org.muilab.notigpt.view.utils.LifecycleObserver
import org.muilab.notigpt.viewModel.DrawerViewModel
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotiDrawer(context: Context, drawerViewModel: DrawerViewModel) {

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val notifications by drawerViewModel.filteredByQuery.collectAsState()
    val seenNotis = remember { mutableSetOf<String>() }
    val seenInfos = remember { mutableMapOf<String, Set<String>>() }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, bottom = 8.dp),
    ) {
        items(notifications, key = { it.notiKey }) { notiDisplayUnit ->

            val notiKey = notiDisplayUnit.notiKey

            val notiViewed = remember { mutableStateOf(false) }
            val viewedInfos = remember { mutableSetOf<String>() }
            NotiCard(context, notiDisplayUnit, drawerViewModel, notiViewed, viewedInfos)

//
//            SwipeBox(
//                onEdit = {},
//                onDelete = {
//                    CoroutineScope(Dispatchers.IO).launch {
//                        drawerViewModel.actOnNoti(notiKey, "dismiss_swipe")
//                    }
//                }
//            ) {
//                val notiViewed = remember { mutableStateOf(false) }
//                val viewedInfos = remember { mutableSetOf<String>() }
//                NotiCard(context, notiDisplayUnit, drawerViewModel, notiViewed, viewedInfos)
//
//                LaunchedEffect(notiViewed) {
//                    seenNotis.add(notiKey)
//                }
//                LaunchedEffect(viewedInfos) {
//                    if (viewedInfos.isNotEmpty())
//                        seenInfos[notiKey] = viewedInfos
//                    else
//                        seenInfos.remove(notiKey)
//                }
//            }
        }
    }

    val firstVisibleIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    val scrollOffset by remember {
        derivedStateOf { listState.firstVisibleItemScrollOffset }
    }
    if (firstVisibleIndex > 0) {
        val notiCount = firstVisibleIndex + if (scrollOffset > 0) 1 else 0

        val displayText = "$notiCount notifications above"

        ElevatedButton(
            onClick = {
                coroutineScope.launch {
                    listState.scrollToItem(0)
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

    var isForeground by remember { mutableStateOf(false) }
    LifecycleObserver(
        onResume = { isForeground = true },
        onPause = { isForeground = false }
    )

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val leaveAppEvents = setOf(
            Lifecycle.Event.ON_PAUSE
        )
        val observer = LifecycleEventObserver { _, event ->
            if (event in leaveAppEvents) {
                updateSeenNotifications(context, seenNotis, seenInfos)
                postOngoingNotification(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            updateSeenNotifications(context, seenNotis, seenInfos)
            postOngoingNotification(context)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeBox(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    content: @Composable () -> Unit
) {
    val swipeState = rememberSwipeToDismissBoxState()

    // Define swipe visuals
    val (icon, alignment, color) = when (swipeState.dismissDirection) {
        SwipeToDismissBoxValue.EndToStart -> Triple(
            Icons.Outlined.Delete,
            Alignment.CenterEnd,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = swipeState.progress)
        )
        SwipeToDismissBoxValue.StartToEnd -> Triple(
            Icons.Outlined.Edit,
            Alignment.CenterStart,
            Color.Green.copy(alpha = swipeState.progress)
        )
        SwipeToDismissBoxValue.Settled -> Triple(
            Icons.Outlined.Delete,
            Alignment.CenterEnd,
            Color.Transparent
        )
    }

    Box(
    ) {
        SwipeToDismissBox(
            modifier = Modifier
                .animateContentSize(), // avoid re-animating the Box wrapper
            state = swipeState,
            backgroundContent = {
                Box(
                    contentAlignment = alignment,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                ) {
                    Icon(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .padding(16.dp),
                        imageVector = icon,
                        contentDescription = null
                    )
                }
            }
        ) {
            content()
        }
    }

    // Handle side effects on dismiss
    when (swipeState.currentValue) {
        SwipeToDismissBoxValue.EndToStart -> {
            LaunchedEffect(swipeState) {
                onDelete()
                swipeState.snapTo(SwipeToDismissBoxValue.Settled)
            }
        }
        SwipeToDismissBoxValue.StartToEnd -> {
            LaunchedEffect(swipeState) {
                onEdit()
                swipeState.snapTo(SwipeToDismissBoxValue.Settled)
            }
        }
        else -> Unit
    }
}

@RequiresApi(Build.VERSION_CODES.S)
fun updateSeenNotifications(context: Context, seenNotis: Set<String>, seenInfos: Map<String, Set<String>>) {
    CoroutineScope(Dispatchers.IO).launch {

        val seenInfosSet = seenInfos.values.flatten().toSet()
        val notiRepository = NotiRepositoryProvider.provideNotiRepository(context)
        notiRepository.updateSeenNotifications(seenNotis, seenInfosSet)
    }
}