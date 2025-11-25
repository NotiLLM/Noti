package org.muilab.notigpt.view.component.notification

import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.util.Log

import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale

import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.viewModel.DrawerViewModel
import org.muilab.notigpt.util.Constants
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_TASK_STATE_COMPLETED
import org.muilab.notigpt.util.Constants.Companion.NOTI_TASK_STATE_IN_PROGRESS
import org.muilab.notigpt.util.Constants.Companion.NOTI_TASK_STATE_NOT_STARTED
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.replaceChars
import org.muilab.notigpt.view.component.notification.action.NotiActionIconButton
import org.muilab.notigpt.view.component.notification.info.ExpandedNotiRecord
import org.muilab.notigpt.view.utils.NotiExpandState
import sh.calvin.reorderable.ReorderableCollectionItemScope
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.max
import org.muilab.notigpt.database.server.enqueueUpdateNotification
import org.muilab.notigpt.service.NotiListenerService

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
    isCardVisible: Boolean,
    onNotiCardRead: (Boolean) -> Unit,
    onNotiRecordRead: (recordId: String) -> Unit,
    category: String,
    appCategory: String
) {

    val hideComplexVisuals by SharedPreferencesManager.hideComplexVisualsFlow.collectAsState()

    val isSortingMode by drawerViewModel.isSortingMode.collectAsState()

    val readRecordIdsInCard = remember { mutableSetOf<String>() }
    var recordsViewport: Rect? by remember { mutableStateOf<Rect?>(null) }

    val notiUnit = notiDisplayUnit.notiUnit
    val notiRecords = notiDisplayUnit.notiRecords

    val notiKey = notiUnit.notiKey
    val isPinned = notiUnit.isPinned
    val isCompletelyRead = notiUnit.isCompletelyRead

    val lastRecord = notiRecords.lastOrNull()
    val notiOverallTitle = when {
        lastRecord != null && lastRecord.extraConversationTitle != "null" -> lastRecord.extraConversationTitle
        notiDisplayUnit.title != "null" -> notiDisplayUnit.title
        lastRecord != null && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
        else -> ""
    }
    val notiSecondOverallTitle = when {
        lastRecord != null && lastRecord.extraConversationTitle != "null" && notiDisplayUnit.title != "null" -> notiDisplayUnit.title
        lastRecord != null && lastRecord.extraConversationTitle == "null" && notiDisplayUnit.title != "null" && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
        lastRecord != null && lastRecord.extraConversationTitle == "null" && notiDisplayUnit.title != "null" -> ""
        else -> ""
    }


    Log.d("NotiCard", "Title: $notiOverallTitle, Last Update Time: ${notiDisplayUnit.lastUpdateTime}")

    val hasSecondTitle = notiSecondOverallTitle.isNotBlank() && notiSecondOverallTitle != notiOverallTitle
    val isPeople = notiUnit.isPeople
    val appName = notiUnit.appName
    val bitmap = notiUnit.bitmap
    val largeBitmap = notiUnit.largeBitmap

    val isAppCategoryView = drawerViewModel.isAppCategoryView.collectAsState()
    val isTask = notiUnit.category == NOTI_CATEGORY_MAKETASK && !hideComplexVisuals
    val sortPosition = if (isAppCategoryView.value) notiUnit.appCategorySortPosition else notiUnit.sortPosition

    val summary = notiUnit.summary
    val hasSummary = summary.isNotEmpty()

    val backgroundColor = when {
        (sortPosition != -1) -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val timeColor = when {
        !isCompletelyRead && !hideComplexVisuals -> MaterialTheme.colorScheme.error
        else -> backgroundColor
    }

    var requiresExpansion by remember(notiRecords, summary, notiOverallTitle, isPeople) {
        mutableStateOf(
            notiRecords.size > 1 || hasSummary ||
                    (notiRecords.size == 1 && notiRecords[0].getDisplayedTitle(isPeople)
                        .let { notiOverallTitle.isNotBlank() && notiOverallTitle != it })
        )
    }

    // Debug: confirm UI sees the correct number of records and expansion state
    Log.d("NotiCardDebug", "Render key=$notiKey records=${notiRecords.size} requiresExpansion=$requiresExpansion")

    val maxHeightDp = 200.dp
    // Small sample limit used for measuring / estimating per-item height
    val SAMPLE_LIMIT = 8
    // Ensure we have density available before computing estimates
    val density = LocalDensity.current

    // Compute a conservative initial estimate for the opened anchor so AnchoredDraggableState
    // isn't created with a degenerate 0px range (which makes it stuck).
    val initialEstimatePx = remember(notiRecords.size, density) {
        val perItemPx = with(density) { 56.dp.toPx() }
        val sample = minOf(SAMPLE_LIMIT, notiRecords.size)
        val maxHeightPx = with(density) { maxHeightDp.toPx() }
        val minOpenPx = with(density) { 80.dp.toPx() }
        // Estimate = perItem * sample, clamped between minOpenPx and maxHeightPx
        max(minOpenPx, minOf(perItemPx * sample, maxHeightPx))
    }

    // Start the maxContentHeightPx with the initial estimate so the card has a usable opened anchor
    // before measurements refine it.
    var maxContentHeightPx by remember { mutableFloatStateOf(initialEstimatePx) }

    // Measured content height (in px) updated by the SubcomposeLayout measurement below.
    // Declared early so click handlers can reference the latest measured value.
    val measuredContentHeightPx = remember { mutableFloatStateOf(0f) }

    val expansionProgress: (Float, Float) -> Float = { offset, maxHeight ->
        offset.coerceIn(0F, maxHeight) / maxOf(maxHeight, 1F)
    }
    val COLLAPSE_THRESHOLD = 20f

    val coroutineScope = rememberCoroutineScope()

    val horizontalOffsetX = remember { Animatable(0f) }
    var endActionsWidth by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }

    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = NotiExpandState.Collapsed,
            anchors = DraggableAnchors {
                NotiExpandState.Collapsed at 0f
                // use initialEstimatePx so initial anchors are non-zero
                NotiExpandState.Opened at initialEstimatePx
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

    // Observed offset used to drive UI recompositions while dragging.
    val observedOffset = remember { mutableFloatStateOf(anchoredDraggableState.offset.coerceAtLeast(0f)) }

    LaunchedEffect(anchoredDraggableState) {
        snapshotFlow { anchoredDraggableState.offset }
            .collect { value ->
                observedOffset.value = (value.coerceAtLeast(0f))
            }
    }

    var dragDirection by remember { mutableStateOf<DragDirection?>(null) }
    // Pointer input handler now only handles horizontal swipes; vertical drags are handled here by dispatching to anchoredDraggableState.
    val combinedDragHandler = if (isDragging) Modifier else Modifier.pointerInput(endActionsWidth, cardWidth) {
        detectDragGestures(
            onDragStart = {
                dragDirection = null
            },
            onDrag = { change, dragAmount ->
                if (dragDirection == null) {
                    dragDirection = if (abs(dragAmount.x) > abs(dragAmount.y)) DragDirection.HORIZONTAL else DragDirection.VERTICAL
                }
                when (dragDirection) {
                    DragDirection.HORIZONTAL -> coroutineScope.launch {
                        // Only consume horizontal swipes here so they don't interfere with anchored draggable vertical gestures.
                        change.consume()
                        val newOffset = horizontalOffsetX.targetValue + dragAmount.x
                        horizontalOffsetX.snapTo(newOffset.coerceIn(-cardWidth, endActionsWidth))
                    }
                    DragDirection.VERTICAL -> if (requiresExpansion) {
                        // Consume vertical drags and forward to the anchored draggable state.
                        // Use positive dragAmount.y so dragging DOWN increases the offset (expands the card).
                        change.consume()
                        anchoredDraggableState.dispatchRawDelta(dragAmount.y)
                    }
                    else -> { /* no-op */ }
                }
            },
            onDragEnd = {
                coroutineScope.launch {
                    when (dragDirection) {
                        DragDirection.HORIZONTAL -> {
                            val swipeThresholdPx = cardWidth * 0.4f
                            when {
                                horizontalOffsetX.value < -swipeThresholdPx -> {
                                    horizontalOffsetX.animateTo(-cardWidth, tween(300))
                                    drawerViewModel.actOnNoti(notiKey, "dismiss_swipe")
                                    horizontalOffsetX.snapTo(0f)
                                }
                                horizontalOffsetX.value > swipeThresholdPx -> {
                                    horizontalOffsetX.animateTo(endActionsWidth)
                                }
                                else -> horizontalOffsetX.animateTo(0f)
                            }
                        }
                        DragDirection.VERTICAL -> {
                            // When vertical drag ends, snap to either fully opened or collapsed
                            if (requiresExpansion) {
                                // Use half of the computed maxContentHeightPx as threshold
                                val threshold = maxContentHeightPx * 0.5f
                                if (observedOffset.value > threshold) {
                                    anchoredDraggableState.animateTo(NotiExpandState.Opened)
                                } else {
                                    anchoredDraggableState.animateTo(NotiExpandState.Collapsed)
                                }
                            }
                        }
                        else -> { /* no-op */ }
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
            .onSizeChanged {
                cardWidth = it.width.toFloat()
                // Reveal buttons when swiping right to 80% of card width
                endActionsWidth = cardWidth * 0.8f
            }
            .clip(MaterialTheme.shapes.large)
    ) {
        val surfaceBaseModifier = if (isSortingMode) {
            Modifier.longPressDraggableHandle()
        } else {
            Modifier.then(combinedDragHandler)
        }

        // Background Actions (rendered beneath the Surface so they stay stationary while Surface translates)
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .onSizeChanged { endActionsWidth = it.width.toFloat() }
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                // Guard the actions row from any inherited translation by forcing translationX=0
                .graphicsLayer {
                    translationX = 0f
                    alpha = ((horizontalOffsetX.value / endActionsWidth).coerceIn(0f, 1f)).pow(2)
                }
                .zIndex(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotiActionIconButton(
                iconRes = R.drawable.close,
                contentDescription = "Hide Actions",
                backgroundColor = Color.Black,
                onClick = {
                    if (horizontalOffsetX.value == endActionsWidth)
                        coroutineScope.launch { collapse() }
                }
            )
            NotiActionIconButton(
                iconRes = if (notiUnit.category == Constants.NOTI_CATEGORY_MAKETASK) R.drawable.task_yes else R.drawable.task_no,
                contentDescription = "Make-Task",
                backgroundColor = Color.Black,
                onClick = {
                    if (horizontalOffsetX.value == endActionsWidth) {
                        if (notiUnit.category == Constants.NOTI_CATEGORY_MAKETASK)
                            drawerViewModel.actOnNoti(notiKey, "dismiss_task")
                        else drawerViewModel.actOnNoti(notiKey, "make_task")
                        coroutineScope.launch { collapse() }
                    }
                }
            )
            NotiActionIconButton(
                iconRes = if (notiUnit.category == Constants.NOTI_CATEGORY_ARCHIVE) R.drawable.archive_yes else R.drawable.archive_no,
                contentDescription = "Archive",
                backgroundColor = Color.Black,
                onClick = {
                    if (horizontalOffsetX.value == endActionsWidth) {
                        if (notiUnit.category == Constants.NOTI_CATEGORY_ARCHIVE)
                            drawerViewModel.actOnNoti(notiKey, "unarchive")
                        else drawerViewModel.actOnNoti(notiKey, "archive")
                        coroutineScope.launch { collapse() }
                    }
                }
            )
            NotiActionIconButton(
                iconRes = R.drawable.notifications,
                contentDescription = "Try Upload",
                backgroundColor = Color.Black,
                onClick = {
                    if (horizontalOffsetX.value == endActionsWidth) {
                        enqueueUpdateNotification(context, notiKey)
                        coroutineScope.launch { collapse() }
                    }
                }
            )
            NotiActionIconButton(
                iconRes = R.drawable.reset_sort,
                contentDescription = "Reset Sort",
                backgroundColor = Color.Black,
                onClick = {
                    if (horizontalOffsetX.value == endActionsWidth) {
                        drawerViewModel.resetManualSortOrder(notiKey)
                        coroutineScope.launch { collapse() }
                    }
                }
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(0f)
                // Attach anchoredDraggable first so it receives vertical motion before the pointerInput consumes it.
                .anchoredDraggable(
                    state = anchoredDraggableState,
                    orientation = Orientation.Vertical,
                    enabled = requiresExpansion
                )
                .then(surfaceBaseModifier)
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
                        drawerViewModel.actOnNoti(notiKey, "access_click_dismiss")
                    }
                ),
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

                Row(
                    Modifier.padding(start = 6.dp, end = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val imageToDisplay =
                        remember(bitmap, largeBitmap, anchoredDraggableState.offset) {
                            if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD && largeBitmap != null) {
                                largeBitmap.asImageBitmap()
                            } else {
                                bitmap?.asImageBitmap()
                            }
                        }

                    val hasTransparency = remember(bitmap) {
                        if (bitmap == null) false
                        else {
                            val w = minOf(bitmap.width, 16)
                            val h = minOf(bitmap.height, 16)
                            val scaled = if (bitmap.width > w || bitmap.height > h)
                                bitmap.scale(w, h)
                            else bitmap

                            val pixels = IntArray(w * h)
                            scaled.getPixels(pixels, 0, w, 0, 0, w, h)

                            if (bitmap.hasAlpha()) {
                                // original purpose: detect transparent background
                                val transparent = pixels.count { ((it ushr 24) and 0xFF) < 250 }
                                transparent / pixels.size.toFloat() > 0.1f
                            } else {
                                // quick heuristic for icon-like: few distinct colors
                                val unique = pixels.map { it and 0xFFFFFF }.toSet().size
                                unique < 12
                            }
                        }
                    }

                    if (showSummary())
                        Spacer(Modifier.size(3.dp))

                    if (isTask && !hideComplexVisuals) {

                        val taskState = notiUnit.taskState
                        val iconRes = when (taskState) {
                            NOTI_TASK_STATE_NOT_STARTED -> R.drawable.task_not_started
                            NOTI_TASK_STATE_IN_PROGRESS -> R.drawable.task_in_progress
                            NOTI_TASK_STATE_COMPLETED -> R.drawable.task_completed
                            else -> R.drawable.task_no
                        }
                        val color = when (taskState) {
                            NOTI_TASK_STATE_NOT_STARTED -> Color(234, 67, 53)
                            NOTI_TASK_STATE_IN_PROGRESS -> Color(251, 188, 5)
                            NOTI_TASK_STATE_COMPLETED -> Color(52, 168, 83)
                            else -> Color.Unspecified
                        }
                        val action = when (taskState) {
                            NOTI_TASK_STATE_NOT_STARTED -> "mark_task_in_progress"
                            NOTI_TASK_STATE_IN_PROGRESS -> "mark_task_completed"
                            NOTI_TASK_STATE_COMPLETED -> "mark_task_reset"
                            else -> "dismiss_task"
                        }

                        NotiActionIconButton(
                            iconRes = iconRes,
                            contentDescription = "Task Checkbox",
                            backgroundColor = backgroundColor,
                            onClick = { drawerViewModel.actOnNoti(notiKey, action) },
                            hasBorder = false,
                            color = color
                        )
                    }

                    if (imageToDisplay != null) {
                        val iconModifier = Modifier
                            .size(35.dp)
                            .padding(vertical = 3.dp, horizontal = if (isTask) 0.dp else 3.dp)
                        if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD && largeBitmap != null) {
                            Image(
                                bitmap = imageToDisplay, contentDescription = "Notification Icon",
                                modifier = iconModifier,
                            )
                        } else {
                            if (hasTransparency) {
                                Icon(
                                    bitmap = imageToDisplay,
                                    contentDescription = "Notification Icon",
                                    modifier = iconModifier,
                                    tint = contentColorFor(backgroundColor)
                                )
                            } else {
                                Image(
                                    bitmap = imageToDisplay, contentDescription = "Notification Icon",
                                    modifier = iconModifier
                                )
                            }
                        }
                    }

                }

                Column (Modifier.align(Alignment.TopEnd)) {

                    Row(
                        Modifier
                            .wrapContentHeight()
                            .padding(start = if (isTask) 80.dp else 35.dp)
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
                                    .padding(start = 20.dp, end = 5.dp)
                                    .weight(1f)
                            ) {
                                Row(Modifier.fillMaxWidth()) {
                                    if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD) {
                                        Text(appName, fontSize = 12.sp)
                                        Spacer(Modifier.weight(1F))
                                    } else {
                                        Column(Modifier.wrapContentHeight().weight(1F)) {
                                            Text(
                                                text = if (notiOverallTitle.isBlank()) appName else notiOverallTitle,
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 14.sp,
                                                onTextLayout = { textLayoutResult -> if (textLayoutResult.hasVisualOverflow) requiresExpansion = true }
                                            )
                                            if (hasSecondTitle) {
                                                Text(
                                                    text = notiSecondOverallTitle,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                                    overflow = TextOverflow.Ellipsis,
                                                    maxLines = 1,
                                                    onTextLayout = { textLayoutResult -> if (textLayoutResult.hasVisualOverflow) requiresExpansion = true }
                                                )
                                            }
                                        }
                                    }
                                    if (requiresExpansion) {
                                        val expandPainter = if (progress < 0.5f) painterResource(R.drawable.expand_circle_down)
                                        else painterResource(R.drawable.expand_circle_up)
                                        Icon(
                                            painter = expandPainter,
                                            contentDescription = "Expand",
                                            modifier = Modifier
                                                .size(25.dp)
                                                .align(Alignment.CenterVertically)
                                                .clickable {
                                                    coroutineScope.launch {
                                                        // Ensure anchors are up-to-date: prefer measured content height if available
                                                        val maxHeightPx = with(density) { maxHeightDp.toPx() }
                                                        val measured = if (kotlin.math.abs(measuredContentHeightPx.value) > 0.001f) measuredContentHeightPx.value else with(density) { (56.dp * notiRecords.size + 20.dp).toPx() }
                                                        maxContentHeightPx = minOf(measured, maxHeightPx)
                                                        anchoredDraggableState.updateAnchors(
                                                            DraggableAnchors {
                                                                NotiExpandState.Collapsed at 0f
                                                                NotiExpandState.Opened at maxContentHeightPx
                                                            }
                                                        )

                                                        Log.d("NotiCardMeas", "Icon click: measured=${measuredContentHeightPx.value}, usedAnchor=${maxContentHeightPx} for key=$notiKey")

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
                                        Column {
                                            Text(
                                                text = notiOverallTitle,
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                                maxLines = if (anchoredDraggableState.offset < COLLAPSE_THRESHOLD) 1 else Int.MAX_VALUE,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 14.sp
                                            )
                                            if (hasSecondTitle) {
                                                Text(
                                                    text = notiSecondOverallTitle,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    } else {
                                        val notiContent = notiRecords.lastOrNull()?.content ?: ""
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

                        // Trigger loading of full records when expanded for the first time
                        LaunchedEffect(anchoredDraggableState.currentValue, notiKey) {
                            if (anchoredDraggableState.currentValue == NotiExpandState.Opened) {
                                drawerViewModel.loadFullRecordsForKey(notiKey)
                            }
                        }

                        val fullRecordsFlow = drawerViewModel.getFullRecordsFlow(notiKey)
                        val fullRecords by fullRecordsFlow.collectAsState()

                        val showingRecords = if (fullRecords.isNotEmpty()) fullRecords else notiRecords

                        // Measure up to SAMPLE_LIMIT items to get an accurate per-item height sample,
                        // then use it directly for small lists or extrapolate for large lists.
                        val SAMPLE_LIMIT = 8
                        val sampleCount = minOf(SAMPLE_LIMIT, showingRecords.size)

                        SubcomposeLayout(modifier = Modifier) { constraints ->
                            val measuringConstraints = constraints.copy(maxHeight = Int.MAX_VALUE)
                            val toMeasure = showingRecords.take(sampleCount)
                            val measPlaceables = subcompose("measurer") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    toMeasure.forEach { rec ->
                                        ExpandedNotiRecord(
                                            notiTitle = rec.getDisplayedTitle(isPeople),
                                            notiTime = rec.time,
                                            notiContent = rec.content,
                                            showTitle = false,
                                            infoTimeColor = backgroundColor,
                                            notiSeen = rec.isRead,
                                            isCardVisible = false,
                                            recordsViewport = null,
                                            onRecordRead = {}
                                        )
                                    }
                                }
                            }.map { measurable -> measurable.measure(measuringConstraints) }

                            val sampleTotalHeight = measPlaceables.sumOf { it.height }
                            measuredContentHeightPx.value = sampleTotalHeight.toFloat()

                            // Don't occupy space
                            layout(constraints.maxWidth, 0) { }
                        }

                        LaunchedEffect(measuredContentHeightPx.value, showingRecords.size) {
                            val maxHeightPx = with(density) { maxHeightDp.toPx() }
                            val samplePx = if (measuredContentHeightPx.value > 0f) measuredContentHeightPx.value else with(density) { (56.dp * sampleCount).toPx() }
                            val finalPx = if (showingRecords.size <= sampleCount) {
                                // small list: use measured total
                                minOf(samplePx, maxHeightPx)
                            } else {
                                // large list: extrapolate average item height from sample
                                val avgItemPx = samplePx / maxOf(1, sampleCount)
                                minOf(avgItemPx * showingRecords.size, maxHeightPx)
                            }
                            maxContentHeightPx = finalPx
                            anchoredDraggableState.updateAnchors(
                                DraggableAnchors {
                                    NotiExpandState.Collapsed at 0f
                                    NotiExpandState.Opened at maxContentHeightPx
                                }
                            )
                            Log.d("NotiCardMeas", "SamplePx=${measuredContentHeightPx.value}, sampleCount=$sampleCount, total=${showingRecords.size}, set anchor=${maxContentHeightPx} for key=$notiKey")
                        }

                        // Render the showingRecords in a LazyColumn whose height matches the current
                        // draggable offset (clamped to the computed maxContentHeightPx). This ensures
                        // users can actually see records when they expand the card.
                        val currentHeightPx = observedOffset.value.coerceIn(0f, maxContentHeightPx)
                        // Keep a lazy list state so we can programmatically scroll to the latest record when opened.
                        val listState = rememberLazyListState()

                        // When the card is opened, scroll to the latest record so newest messages are visible.
                        LaunchedEffect(anchoredDraggableState.currentValue, showingRecords.size) {
                            if (anchoredDraggableState.currentValue == NotiExpandState.Opened && showingRecords.isNotEmpty()) {
                                // scroll to last index (latest message). Use immediate positioning.
                                listState.scrollToItem(maxOf(0, showingRecords.lastIndex))
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .height(with(density) { currentHeightPx.toDp() })
                                .onGloballyPositioned { layoutCoordinates -> recordsViewport = layoutCoordinates.boundsInWindow() }
                        ) {
                            item {
                                Divider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.White, thickness = 1.dp)
                            }

                            if (fullRecords.isEmpty() && anchoredDraggableState.currentValue == NotiExpandState.Opened) {
                                item {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(24.dp)
                                    )
                                }
                            }

                            items(showingRecords, key = { it.notiRecordId }) { notiRecord ->
                                val notiTitle = notiRecord.getDisplayedTitle(isPeople)
                                val infoTimeColor = when {
                                    !notiRecord.isRead && !hideComplexVisuals -> MaterialTheme.colorScheme.error
                                    else -> backgroundColor
                                }
                                ExpandedNotiRecord(
                                    notiTitle = notiTitle,
                                    notiTime = notiRecord.time,
                                    notiContent = notiRecord.content,
                                    showTitle = false,
                                    infoTimeColor = infoTimeColor,
                                    notiSeen = notiRecord.isRead,
                                    isCardVisible = isCardVisible,
                                    recordsViewport = recordsViewport,
                                    onRecordRead = {
                                        if (!notiRecord.isRead) {
                                            onNotiRecordRead(notiRecord.notiRecordId)
                                            readRecordIdsInCard.add(notiRecord.notiRecordId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                } // end Column (the content Column inside the Surface)
            } // end Box (content Box inside Surface)
        } // end Surface
    } // end outer Box
} // end NotiCard
