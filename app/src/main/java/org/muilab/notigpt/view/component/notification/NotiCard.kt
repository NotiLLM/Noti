package org.muilab.notigpt.view.component.notification

import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.database.server.enqueueNotificationAction
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.service.NotiListenerService
import org.muilab.notigpt.util.Constants
import org.muilab.notigpt.util.hasTransparentPixels
import org.muilab.notigpt.util.replaceChars
import org.muilab.notigpt.view.component.notification.action.NotiActionIconButton
import org.muilab.notigpt.view.component.notification.info.ExpandedNotiRecord
import org.muilab.notigpt.view.utils.NotiExpandState
import org.muilab.notigpt.viewModel.DrawerViewModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

private enum class DragDirection {
    HORIZONTAL, VERTICAL
}

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun ReorderableCollectionItemScope.NotiCard(
    context: Context,
    notiDisplayUnit: NotiDisplayUnit,
    isDragging: Boolean,
    drawerViewModel: DrawerViewModel,
    onNotiCardRead: () -> Unit,
    onNotiRecordRead: (recordId: String) -> Unit
) {

    val isSortingMode by drawerViewModel.isSortingMode.collectAsState()

    var notiTopViewed by remember { mutableStateOf(false) }
    var notiBottomViewed by remember { mutableStateOf(false) }
    val readRecordIdsInCard = remember { mutableSetOf<String>() }
    // Get screen height for a more reliable visibility check
    val screenHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    val notiUnit = notiDisplayUnit.notiUnit
    val notiRecords = notiDisplayUnit.notiRecords

    val notiKey = notiUnit.notiKey
    val isPinned = notiUnit.isPinned
    val isCompletelyRead = notiUnit.isCompletelyRead
    val notiOverallTitle = notiDisplayUnit.title
    val isPeople = notiUnit.isPeople
    val appName = notiUnit.appName
    val bitmap = notiUnit.bitmap
    val largeBitmap = notiUnit.largeBitmap

    val isAppCategoryView = drawerViewModel.isAppCategoryView.collectAsState()
    val sortPosition = if (isAppCategoryView.value) notiUnit.appCategorySortPosition else notiUnit.sortPosition

    val summary = notiUnit.summary
    val hasSummary = summary.isNotEmpty()

    val backgroundColor = when {
        (sortPosition != -1) -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val timeColor = when {
        !isCompletelyRead -> MaterialTheme.colorScheme.error
        else -> backgroundColor
    }

    var requiresExpansion by remember {
        mutableStateOf(
            notiRecords.size > 1 || hasSummary ||
                    (notiRecords.size == 1 && notiRecords[0].getDisplayedTitle(isPeople)
                        .let { notiOverallTitle.isNotBlank() && notiOverallTitle != it })
        )
    }

    var contentHeightPx by remember { mutableIntStateOf(0) }
    var latestMessageHeightPx by remember { mutableIntStateOf(0) }
    var maxContentHeightPx by remember { mutableFloatStateOf(0f) }
    val maxHeightDp = 200.dp
    val notiInfoGapDp = 4.dp

    val expansionProgress: (Float, Float) -> Float = { offset, maxHeight ->
        offset.coerceIn(0F, maxHeight) / maxOf(maxHeight, 1F)
    }
    val COLLAPSE_THRESHOLD = 20f

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val horizontalOffsetX = remember { Animatable(0f) }
    var endActionsWidth by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }

    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = NotiExpandState.Collapsed,
            anchors = DraggableAnchors {
                NotiExpandState.Collapsed at 0f
                NotiExpandState.Opened at maxContentHeightPx
            },
            positionalThreshold = { distance: Float -> distance * 0.5f },
            snapAnimationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            decayAnimationSpec = exponentialDecay(),
            velocityThreshold = { with(density) { 80.dp.toPx() } }
        )
    }

    var dragDirection by remember { mutableStateOf<DragDirection?>(null) }
    val combinedDragHandler = if (isDragging) Modifier else Modifier.pointerInput(endActionsWidth, cardWidth, requiresExpansion) {
        detectDragGestures(
            onDragStart = {
                dragDirection = null
            },
            onDrag = { change, dragAmount ->
                change.consume()
                if (dragDirection == null) {
                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                        dragDirection = DragDirection.HORIZONTAL
                    } else if (requiresExpansion) { // Only allow vertical drag if expandable
                        dragDirection = DragDirection.VERTICAL
                    }
                }
                when (dragDirection) {
                    DragDirection.HORIZONTAL -> coroutineScope.launch {
                        val newOffset = horizontalOffsetX.targetValue + dragAmount.x
                        // Allow swiping left to -cardWidth and right to endActionsWidth
                        horizontalOffsetX.snapTo(newOffset.coerceIn(-cardWidth, endActionsWidth))
                    }
                    DragDirection.VERTICAL -> if (requiresExpansion) {
                        anchoredDraggableState.dispatchRawDelta(dragAmount.y)
                    }
                    null -> { /* no-op */ }
                }
            },
            onDragEnd = {
                coroutineScope.launch {
                    when (dragDirection) {
                        DragDirection.HORIZONTAL -> {
                            val swipeThresholdPx = cardWidth * 0.4f
                            when {
                                // Swipe left to dismiss
                                horizontalOffsetX.value < -swipeThresholdPx -> {
                                    horizontalOffsetX.animateTo(-cardWidth, tween(300))
                                    if (!isPinned)
                                        drawerViewModel.actOnNoti(notiKey, "dismiss_swipe")
                                    horizontalOffsetX.snapTo(0f)
                                }
                                // Swipe right to reveal actions
                                horizontalOffsetX.value > swipeThresholdPx -> {
                                    horizontalOffsetX.animateTo(endActionsWidth)
                                }
                                // Snap back to original position
                                else -> horizontalOffsetX.animateTo(0f)
                            }
                        }
                        DragDirection.VERTICAL -> if (requiresExpansion) {
                            if (anchoredDraggableState.requireOffset().isFinite()) {
                                anchoredDraggableState.animateTo(
                                    if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD) NotiExpandState.Opened
                                    else NotiExpandState.Collapsed
                                )
                            }
                        }
                        null -> { /* no-op */ }
                    }
                }
            }
        )
    }

    val showSummary = {
        anchoredDraggableState.offset < COLLAPSE_THRESHOLD && hasSummary
    }

    val elevation by animateDpAsState(if (isDragging) 12.dp else 1.dp, label = "elevation")
    val scale by animateFloatAsState(if (isDragging) 1.05f else 1.0f, label = "scale")

    val collapse: suspend () -> Unit = {
        horizontalOffsetX.animateTo(0f)
    }

    Box(
        modifier = Modifier
            .padding(vertical = 1.dp, horizontal = 20.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onSizeChanged { cardWidth = it.width.toFloat() }
            .clip(MaterialTheme.shapes.large)
    ) {
        val surfaceBaseModifier = if (isSortingMode) {
            Modifier.longPressDraggableHandle()
        } else {
            Modifier.then(combinedDragHandler)
        }

        Surface(
            modifier = surfaceBaseModifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = horizontalOffsetX.value
                    // keep your existing scaleX and scaleY
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    onClick = {
                        val contentIntent = NotiListenerService.getContentIntent(context, notiUnit)
                        if (contentIntent != null) {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    val options = ActivityOptions.makeBasic().apply {
                                        pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                    }
                                    contentIntent.send(context, 0, null, null, null, null, options.toBundle())
                                } else {
                                    contentIntent.send()
                                }
                            } catch (e: Exception) {
                                Log.e("AccessNotification", "PendingIntent send failed", e)
                            }
                        }
                        Log.d("NotiListenerService", "Sent intent")
                        if (!isPinned)
                            drawerViewModel.actOnNoti(notiKey, "access_click")
                    }
                )
                .onGloballyPositioned { coordinates ->
                    val windowBounds = coordinates.boundsInWindow()
                    val top = windowBounds.top
                    val bottom = windowBounds.bottom

                    // --- DEBUG LOG ---
                    // Log.d("VisibilityCheck", "Card Key: ${notiUnit.notiKey} | Top: $top, Bottom: $bottom, ScreenHeight: $screenHeightPx")

                    if (!notiTopViewed && top >= 0 && top < screenHeightPx) {
                        notiTopViewed = true
//                        Log.d("VisibilitySet", "Card Key: ${notiUnit.notiKey} -> cardTopVisible = TRUE")
                    }
                    if (!notiBottomViewed && bottom > 0 && bottom <= screenHeightPx) {
                        notiBottomViewed = true
//                        Log.d("VisibilitySet", "Card Key: ${notiUnit.notiKey} -> cardBottomVisible = TRUE")
                    }
                },
            shape = MaterialTheme.shapes.large,
            shadowElevation = elevation,
            color = backgroundColor
        ) {

            val progress = expansionProgress(anchoredDraggableState.offset, maxContentHeightPx)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {

                Column(
                    Modifier.padding(start = 3.dp, end = 3.dp),
                ) {
                    val imageToDisplay = remember(bitmap, largeBitmap, anchoredDraggableState.offset) {
                        if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD && largeBitmap != null) {
                            largeBitmap.asImageBitmap()
                        } else {
                            bitmap?.asImageBitmap()
                        }
                    }

                    val hasTransparency = remember(bitmap) {
                        bitmap != null && hasTransparentPixels(bitmap, 0.1f)
                    }

                    if (showSummary())
                        Spacer(Modifier.size(3.dp))

                    if (imageToDisplay != null) {
                        if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD && largeBitmap != null) {
                            Image(bitmap = imageToDisplay, "Notification Icon",
                                Modifier
                                    .size((40 + 15 * progress).dp)
                                    .padding(vertical = 3.dp, horizontal = 6.dp)
                            )
                        } else {
                            if (hasTransparency) {
                                Icon(bitmap = imageToDisplay, "Notification Icon",
                                    Modifier
                                        .size((40 + 15 * progress).dp)
                                        .padding(vertical = 3.dp, horizontal = 6.dp)
                                )
                            } else {
                                Image(bitmap = imageToDisplay, "Notification Icon",
                                    Modifier
                                        .size((40 + 15 * progress).dp)
                                        .padding(vertical = 3.dp, horizontal = 6.dp),
                                )
                            }
                        }
                    }

                }

                Column (Modifier.align(Alignment.TopEnd)) {

                    Row(
                        Modifier
                            .wrapContentHeight()
                            .padding(start = 35.dp)
                    ) {
                        if (showSummary()) {
                            Text(
                                summary,
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 5.dp)
                                    .align(Alignment.CenterVertically),
                                fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Column(
                                Modifier
                                    .padding(start = (20 + 15 * progress).dp, end = 5.dp)
                                    .weight(1f)
                            ) {
                                Row(Modifier.fillMaxWidth()) {
                                    if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD) {
                                        Text(appName, fontSize = (12 + progress * 4).sp)
                                        Spacer(Modifier.weight(1F))
                                    } else {
                                        Text(
                                            modifier = Modifier.weight(1F),
                                            text = if (notiOverallTitle == "null") appName else notiOverallTitle,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 14.sp,
                                            onTextLayout = { textLayoutResult -> if (textLayoutResult.hasVisualOverflow) requiresExpansion = true }
                                        )
                                    }
                                    if (requiresExpansion) {
                                        Icon(
                                            painter = if (progress < 0.5f) painterResource(R.drawable.expand_circle_down)
                                            else painterResource(R.drawable.expand_circle_up),
                                            "Expand",
                                            Modifier
                                                .size(25.dp)
                                                .align(Alignment.CenterVertically)
                                                .clickable(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            if (anchoredDraggableState.offset < COLLAPSE_THRESHOLD) {
                                                                Log.d("Expand", "Open on Click")
                                                                anchoredDraggableState.animateTo(NotiExpandState.Opened)
                                                            } else {
                                                                Log.d("Expand", "Close on Click")
                                                                anchoredDraggableState.animateTo(NotiExpandState.Collapsed)
                                                            }
                                                        }
                                                    }
                                                )
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.background(timeColor, RoundedCornerShape(16.dp))
                                    ) {
                                        Text(
                                            modifier = Modifier.padding(horizontal = 5.dp),
                                            text = notiDisplayUnit.latestUpdateRelTimeStr,
                                            maxLines = 1,
                                            fontSize = 12.sp,
                                            fontStyle = FontStyle.Italic,
                                            color = contentColorFor(timeColor)
                                        )
                                    }
                                }

                                Row {
                                    if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD) {
                                        Text(
                                            text = if (notiOverallTitle == "null") "" else notiOverallTitle,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            maxLines = if (anchoredDraggableState.offset < COLLAPSE_THRESHOLD) 1 else Int.MAX_VALUE,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = (18 + progress * 3).sp,
                                            onTextLayout = { textLayoutResult -> if (textLayoutResult.hasVisualOverflow) requiresExpansion = true }
                                        )
                                    } else {
                                        val notiContent = notiRecords.last().content
                                        Text(
                                            text = if (notiContent == "null") "" else replaceChars(notiContent),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            onTextLayout = { textLayoutResult -> if (textLayoutResult.hasVisualOverflow) requiresExpansion = true },
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (isSortingMode) {
                            Icon(
                                painterResource(R.drawable.drag_handle),
                                contentDescription = "Drag to reorder",
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                            )
                        } else {
                            NotiActionIconButton(
                                iconRes = if (isPinned) R.drawable.pin_yes else R.drawable.pin_no,
                                contentDescription = "Pin",
                                backgroundColor = backgroundColor,
                                onClick = { if (isPinned) drawerViewModel.actOnNoti(notiKey, "unpin") else drawerViewModel.actOnNoti(notiKey, "pin") },
                                hasBorder = false,
                                color = if (isPinned) Color(76, 139, 245) else Color.Unspecified
                            )
                        }

                        Spacer(modifier = Modifier.padding(5.dp))
                    }

                    if (requiresExpansion) {
                        LaunchedEffect(contentHeightPx) {
                            val maxHeightPx = with(density) { maxHeightDp.toPx() }
                            maxContentHeightPx = minOf(contentHeightPx.toFloat(), maxHeightPx)
                            anchoredDraggableState.updateAnchors(
                                DraggableAnchors {
                                    NotiExpandState.Collapsed at 0f
                                    NotiExpandState.Opened at maxContentHeightPx
                                }
                            )
                        }

                        val currentHeightPx = anchoredDraggableState.offset.coerceIn(0f, maxContentHeightPx)

                        Column(
                            modifier = Modifier
                                .height(with(density) { currentHeightPx.toDp() })
                                .clipToBounds()
                        ) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = Color.White)
                            val scrollState = rememberScrollState()
                            val isGroup = (listOf(notiOverallTitle) + notiRecords.map { it.getDisplayedTitle(isPeople) })
                                .filter { it.isNotBlank() }.toSet().size > 1

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .onSizeChanged { size -> contentHeightPx = size.height }
                            ) {
                                notiRecords.forEachIndexed { idx, notiRecord ->
                                    val notiTitle = notiRecord.getDisplayedTitle(isPeople)
                                    val prevTitle = if (idx == 0) notiOverallTitle else notiRecords[idx - 1].getDisplayedTitle(isPeople)
                                    val newTitle = (notiTitle != prevTitle && notiTitle.isNotBlank() && prevTitle.isNotBlank())
                                    val showTitle = isGroup && newTitle
                                    val infoTimeColor = when {
                                        !notiRecord.isRead -> MaterialTheme.colorScheme.error
                                        else -> backgroundColor
                                    }
                                    if (showTitle) Spacer(modifier = Modifier.height(notiInfoGapDp))
                                    if (idx == notiRecords.size - 1) {
                                        Box(modifier = Modifier.onSizeChanged { size -> latestMessageHeightPx = size.height }) {
                                            ExpandedNotiRecord(
                                                notiTitle = notiTitle,
                                                notiTime = notiRecord.time,
                                                notiContent = notiRecord.content,
                                                showTitle = showTitle,
                                                infoTimeColor = infoTimeColor,
                                                notiSeen = notiRecord.isRead,
                                                onRecordRead = {
                                                    // This is the callback from ExpandedNotiRecord
                                                    // It now correctly communicates back to the NotiCard
                                                    if (!notiRecord.isRead) {
                                                        onNotiRecordRead(notiRecord.notiRecordId)
                                                        readRecordIdsInCard.add(notiRecord.notiRecordId)
                                                    }
                                                }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(notiInfoGapDp))
                                    } else {
                                        ExpandedNotiRecord(
                                            notiTitle = notiTitle,
                                            notiTime = notiRecord.time,
                                            notiContent = notiRecord.content,
                                            showTitle = showTitle,
                                            infoTimeColor = infoTimeColor,
                                            notiSeen = notiRecord.isRead,
                                            onRecordRead = {
                                                // This is the callback from ExpandedNotiRecord
                                                // It now correctly communicates back to the NotiCard
                                                if (!notiRecord.isRead) {
                                                    onNotiRecordRead(notiRecord.notiRecordId)
                                                    readRecordIdsInCard.add(notiRecord.notiRecordId)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            LaunchedEffect(contentHeightPx, maxContentHeightPx, latestMessageHeightPx) {
                                val maxHeightPx = with(density) { maxHeightDp.toPx() }
                                val notiInfoGapPx = with(density) { notiInfoGapDp.toPx() }
                                if (contentHeightPx > maxHeightPx) {
                                    val targetScroll = if (latestMessageHeightPx >= maxHeightPx) {
                                        contentHeightPx - latestMessageHeightPx - notiInfoGapPx
                                    } else {
                                        contentHeightPx - maxHeightPx - notiInfoGapPx
                                    }
                                    scrollState.scrollTo(maxOf(targetScroll.toInt(), 0))
                                } else {
                                    scrollState.scrollTo(0)
                                }
                            }
                        }
                    }
                }
            }

        }

        // Background Actions (now on the left, revealed by swiping right)
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart) // Changed from CenterEnd
                .onSizeChanged { endActionsWidth = it.width.toFloat() }
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    alpha = ((horizontalOffsetX.value / endActionsWidth).coerceIn(0f, 1f)).pow(2)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // New "Collapse" button, appears on the far right of the actions
            NotiActionIconButton(
                iconRes = R.drawable.close, // Using pin icon as requested placeholder
                contentDescription = "Hide Actions",
                backgroundColor = Color.Black,
                onClick = {
                    coroutineScope.launch { collapse() }
                }
            )
            // Action buttons order is reversed to appear right-to-left
            NotiActionIconButton(
                iconRes = R.drawable.thumb_down,
                contentDescription = "Dislike",
                backgroundColor = Color.Black,
                onClick = {
                    enqueueNotificationAction(context, notiKey, "disliked")
                    coroutineScope.launch { collapse() }
                },
                color = Color.Red
            )
            NotiActionIconButton(
                iconRes = R.drawable.thumb_up,
                contentDescription = "Like",
                backgroundColor = Color.Black,
                onClick = {
                    enqueueNotificationAction(context, notiKey, "liked")
                    coroutineScope.launch { collapse() }
                },
                color = Color.Green
            )
            NotiActionIconButton(
                iconRes = if (notiUnit.category == Constants.NOTI_CATEGORY_ARCHIVE) R.drawable.archive_yes else R.drawable.archive_no,
                contentDescription = "Archive",
                backgroundColor = Color.Black,
                onClick = {
                    if (notiUnit.category == Constants.NOTI_CATEGORY_ARCHIVE) drawerViewModel.actOnNoti(notiKey, "unarchive")
                    else drawerViewModel.actOnNoti(notiKey, "archive")
                    coroutineScope.launch { collapse() }
                }
            )
            NotiActionIconButton(
                iconRes = if (notiUnit.category == Constants.NOTI_CATEGORY_MAKETASK) R.drawable.task_yes else R.drawable.task_no,
                contentDescription = "Make-Task",
                backgroundColor = Color.Black,
                onClick = {
                    if (notiUnit.category == Constants.NOTI_CATEGORY_MAKETASK) drawerViewModel.actOnNoti(notiKey, "dismiss_task")
                    else drawerViewModel.actOnNoti(notiKey, "make_task")
                    coroutineScope.launch { collapse() }
                }
            )
        }
    }

    // --- LOGIC FOR SIMPLE CARDS ---
    LaunchedEffect(notiTopViewed, notiBottomViewed, notiUnit.isCompletelyRead, requiresExpansion) {
//        Log.d("EffectCheck", "Card Key: ${notiUnit.notiKey} [SIMPLE] | Effect Running | TopSeen: $notiTopViewed, BottomSeen: $notiBottomViewed, Read: ${notiUnit.isCompletelyRead}, Expands: $requiresExpansion")
        if (notiTopViewed && notiBottomViewed && !notiUnit.isCompletelyRead && !requiresExpansion) {
//            Log.d("CallbackFire", "Card Key: ${notiUnit.notiKey} [SIMPLE] -> Firing onNotiCardRead()!")
            onNotiCardRead()
        }
    }

    // --- LOGIC FOR COMPLEX CARDS ---
    LaunchedEffect(notiTopViewed, notiBottomViewed, readRecordIdsInCard.size) {
        val unreadRecordsInThisUnit = notiRecords.filter { !it.isRead }.map { it.notiRecordId }.toSet()
//        Log.d("EffectCheck", "Card Key: ${notiUnit.notiKey} [COMPLEX] | Effect Running | Read in card: ${readRecordIdsInCard.size}, Total unread: ${unreadRecordsInThisUnit.size}")
        if (requiresExpansion && !notiUnit.isCompletelyRead && (unreadRecordsInThisUnit.isNotEmpty() && readRecordIdsInCard.containsAll(unreadRecordsInThisUnit) || notiRecords.all { it.isRead })) {
//            Log.d("CallbackFire", "Card Key: ${notiUnit.notiKey} [COMPLEX] -> Firing onNotiCardRead()!")
            onNotiCardRead()
        }
    }
}