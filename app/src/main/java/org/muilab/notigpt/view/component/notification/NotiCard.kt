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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.scale
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.service.NotiListenerService
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_SAVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_TASK_STATE_COMPLETED
import org.muilab.notigpt.util.Constants.Companion.NOTI_TASK_STATE_IN_PROGRESS
import org.muilab.notigpt.util.Constants.Companion.NOTI_TASK_STATE_NOT_STARTED
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.replaceChars
import org.muilab.notigpt.view.component.notification.action.NotiActionIconButton
import org.muilab.notigpt.view.component.notification.info.ExpandedNotiRecord
import org.muilab.notigpt.view.utils.NotiExpandState
import org.muilab.notigpt.viewModel.DrawerViewModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NotiCard( // REMOVED RECEIVER HERE
    context: Context,
    notiDisplayUnit: NotiDisplayUnit,
    isDragging: Boolean,
    drawerViewModel: DrawerViewModel,
    isCardVisible: Boolean,
    onNotiCardRead: (Boolean) -> Unit,
    onNotiRecordRead: (recordId: String) -> Unit,
    category: String,
    appCategory: String,
    isMergeTarget: Boolean = false,
    isInGroup: Boolean = false
) {

    val hideComplexVisuals by SharedPreferencesManager.hideComplexVisualsFlow.collectAsState()
    val swipeDeleteLeft by SharedPreferencesManager.swipeDeleteLeftFlow.collectAsState()
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

    val hasSecondTitle = notiSecondOverallTitle.isNotBlank() && notiSecondOverallTitle != notiOverallTitle
    val isPeople = notiUnit.isPeople
    val appName = notiUnit.appName
    val bitmap = notiUnit.bitmap
    val largeBitmap = notiUnit.largeBitmap
    val isTask = notiUnit.category == NOTI_CATEGORY_MAKETASK && !hideComplexVisuals
    val summary = notiUnit.summary
    val hasSummary = summary.isNotEmpty()

    val backgroundColor = when {
        isMergeTarget -> MaterialTheme.colorScheme.primaryContainer
        // 讓通知卡片成為全螢幕最亮的元件
        else -> MaterialTheme.colorScheme.surfaceBright
    }

    // Floating visualization logic (simplified)
    val isFloating = false

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

    val maxHeightDp = 200.dp
    val SAMPLE_LIMIT = 8
    val density = LocalDensity.current

    val initialEstimatePx = remember(notiRecords.size, density) {
        val perItemPx = with(density) { 56.dp.toPx() }
        val sample = minOf(SAMPLE_LIMIT, notiRecords.size)
        val maxHeightPx = with(density) { maxHeightDp.toPx() }
        val minOpenPx = with(density) { 80.dp.toPx() }
        max(minOpenPx, minOf(perItemPx * sample, maxHeightPx))
    }

    var maxContentHeightPx by remember { mutableFloatStateOf(initialEstimatePx) }
    val measuredContentHeightPx = remember { mutableFloatStateOf(0f) }

    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = NotiExpandState.Collapsed,
            anchors = DraggableAnchors {
                NotiExpandState.Collapsed at 0f
                NotiExpandState.Opened at initialEstimatePx
            }
        )
    }

    val anchoredFlingBehavior = AnchoredDraggableDefaults.flingBehavior(
        anchoredDraggableState,
        { distance: Float -> distance * 0.5f },
        spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val updateMeasuredAnchors = {
        val maxHeightPx = with(density) { maxHeightDp.toPx() }
        val measured = if (abs(measuredContentHeightPx.floatValue) > 0.001f)
            measuredContentHeightPx.floatValue
        else
            with(density) { (56.dp * notiRecords.size + 20.dp).toPx() }
        maxContentHeightPx = minOf(measured, maxHeightPx)
        anchoredDraggableState.updateAnchors(
            DraggableAnchors {
                NotiExpandState.Collapsed at 0f
                NotiExpandState.Opened at maxContentHeightPx
            }
        )
    }

    val expansionProgress: (Float, Float) -> Float = { offset, maxHeight ->
        offset.coerceIn(0F, maxHeight) / maxOf(maxHeight, 1F)
    }
    val COLLAPSE_THRESHOLD = 20f
    val progress = expansionProgress(anchoredDraggableState.offset, maxContentHeightPx)

    val coroutineScope = rememberCoroutineScope()
    val horizontalOffsetX = remember { Animatable(0f) }
    var endActionsWidth by remember { mutableStateOf(0f) }
    var cardWidth by remember { mutableStateOf(0f) }
    val observedOffset = remember { mutableFloatStateOf(anchoredDraggableState.offset.coerceAtLeast(0f)) }

    LaunchedEffect(anchoredDraggableState) {
        snapshotFlow { anchoredDraggableState.offset }
            .collect { value -> observedOffset.floatValue = (value.coerceAtLeast(0f)) }
    }

    val viewTouchSlop = LocalViewConfiguration.current.touchSlop
    var surfaceBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    var overlayBoundsRelativeToSurface by remember { mutableStateOf<Rect?>(null) }

    val combinedDragHandler = if (isDragging) Modifier else Modifier.pointerInput(endActionsWidth, cardWidth) {
        val horizontalBiasFactor = 0.45f
        val minHorizontalPx = viewTouchSlop * 0.45f
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val overlayRect = overlayBoundsRelativeToSurface
            if (overlayRect != null && overlayRect.contains(down.position)) return@awaitEachGesture

            var isHorizontal = false
            val slopResult = awaitTouchSlopOrCancellation(down.id) { change, over ->
                val absX = abs(over.x)
                val absY = abs(over.y)
                if (absX > max(minHorizontalPx, absY * horizontalBiasFactor)) {
                    isHorizontal = true
                    change.consume()
                }
            }

            if (slopResult != null && isHorizontal) {
                val velocityTracker = VelocityTracker()
                try {
                    drag(down.id) { change ->
                        val delta = change.positionChange()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (delta.x != 0f) {
                            change.consume()
                            val newOffset = horizontalOffsetX.targetValue + delta.x
                            coroutineScope.launch { horizontalOffsetX.snapTo(newOffset.coerceIn(-cardWidth, endActionsWidth)) }
                        }
                    }
                } finally {
                    coroutineScope.launch {
                        val vel = try { velocityTracker.calculateVelocity() } catch (_: Throwable) { androidx.compose.ui.unit.Velocity.Zero }
                        val flingVelocityX = vel.x
                        val flingThreshold = 800f
                        val swipeThresholdPx = cardWidth * 0.20f

                        if (isHorizontal) {
                            val currentOffsetVal = horizontalOffsetX.value
                            if (abs(flingVelocityX) > flingThreshold) {
                                val flingDir = if (flingVelocityX < 0f) -1 else 1
                                val offsetDir = when {
                                    currentOffsetVal < 0f -> -1
                                    currentOffsetVal > 0f -> 1
                                    else -> flingDir
                                }
                                val minOffsetForFling = swipeThresholdPx * 0.5f
                                if (flingDir == offsetDir && abs(currentOffsetVal) > minOffsetForFling) {
                                    if (flingDir < 0) {
                                        if (swipeDeleteLeft) {
                                            horizontalOffsetX.animateTo(-cardWidth, tween(300))
                                            drawerViewModel.actOnNoti(notiKey, "dismiss_swipe")
                                            horizontalOffsetX.snapTo(0f)
                                        } else {
                                            horizontalOffsetX.animateTo(-endActionsWidth)
                                        }
                                    } else {
                                        if (swipeDeleteLeft) {
                                            horizontalOffsetX.animateTo(endActionsWidth)
                                        } else {
                                            horizontalOffsetX.animateTo(cardWidth, tween(300))
                                            drawerViewModel.actOnNoti(notiKey, "dismiss_swipe")
                                            horizontalOffsetX.snapTo(0f)
                                        }
                                    }
                                }
                            }

                            if (abs(flingVelocityX) <= flingThreshold) {
                                if (swipeDeleteLeft) {
                                    when {
                                        horizontalOffsetX.value < -swipeThresholdPx -> {
                                            horizontalOffsetX.animateTo(-cardWidth, tween(300))
                                            drawerViewModel.actOnNoti(notiKey, "dismiss_swipe")
                                            horizontalOffsetX.snapTo(0f)
                                        }
                                        horizontalOffsetX.value > swipeThresholdPx -> horizontalOffsetX.animateTo(endActionsWidth)
                                        else -> horizontalOffsetX.animateTo(0f)
                                    }
                                } else {
                                    when {
                                        horizontalOffsetX.value > swipeThresholdPx -> {
                                            horizontalOffsetX.animateTo(cardWidth, tween(300))
                                            drawerViewModel.actOnNoti(notiKey, "dismiss_swipe")
                                            horizontalOffsetX.snapTo(0f)
                                        }
                                        horizontalOffsetX.value < -swipeThresholdPx -> horizontalOffsetX.animateTo(-endActionsWidth)
                                        else -> horizontalOffsetX.animateTo(0f)
                                    }
                                }
                            } else {
                                if (swipeDeleteLeft) {
                                    when {
                                        horizontalOffsetX.value < -swipeThresholdPx -> {
                                            horizontalOffsetX.animateTo(-cardWidth, tween(300))
                                            drawerViewModel.actOnNoti(notiKey, "dismiss_swipe")
                                            horizontalOffsetX.snapTo(0f)
                                        }
                                        horizontalOffsetX.value > swipeThresholdPx -> horizontalOffsetX.animateTo(endActionsWidth)
                                        else -> horizontalOffsetX.animateTo(0f)
                                    }
                                } else {
                                    when {
                                        horizontalOffsetX.value > swipeThresholdPx -> {
                                            horizontalOffsetX.animateTo(cardWidth, tween(300))
                                            drawerViewModel.actOnNoti(notiKey, "dismiss_swipe")
                                            horizontalOffsetX.snapTo(0f)
                                        }
                                        horizontalOffsetX.value < -swipeThresholdPx -> horizontalOffsetX.animateTo(-endActionsWidth)
                                        else -> horizontalOffsetX.animateTo(0f)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                return@awaitEachGesture
            }
        }
    }

    val showSummary = { anchoredDraggableState.offset < COLLAPSE_THRESHOLD && hasSummary }
    val targetElevationDp = if (isDragging) 12.dp else if (isFloating) 6.dp else 0.dp
    val elevation by animateDpAsState(targetElevationDp, label = "elevation")
    val scaleValue by animateFloatAsState(if (isDragging) 1.02f else 1f)
    val collapse: suspend () -> Unit = { try { horizontalOffsetX.animateTo(0f) } catch (_: Throwable) {} }
    val isDarkTheme = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .padding(vertical = 1.dp, horizontal = if (isInGroup) 5.dp else 20.dp)
            .graphicsLayer {
                scaleX = scaleValue
                scaleY = scaleValue
            }
            .onSizeChanged {
                cardWidth = it.width.toFloat()
                endActionsWidth = cardWidth * 0.8f
            }
            .then(if (isSortingMode) Modifier else combinedDragHandler) // If sorting mode, DragGestures in NotiDrawer handles it
            .clip(MaterialTheme.shapes.large)
    ) {
        val rimColor = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
        val surfaceBorderModifier = if (isFloating) Modifier.border(1.dp, rimColor, shape = MaterialTheme.shapes.large) else Modifier
        val targetLift = if (isFloating) (-4).dp else 0.dp
        val lift by animateDpAsState(targetLift, label = "lift")

        // Background Actions
        Row(
            modifier = Modifier
                .align(if (swipeDeleteLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .onSizeChanged { endActionsWidth = it.width.toFloat() }
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    translationX = 0f
                    val safeWidth = maxOf(1f, endActionsWidth)
                    val t = if (swipeDeleteLeft) {
                        (horizontalOffsetX.value / safeWidth).coerceIn(0f, 1f)
                    } else {
                        ((-horizontalOffsetX.value) / safeWidth).coerceIn(0f, 1f)
                    }
                    alpha = t * t
                }
                .zIndex(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotiActionIconButton(R.drawable.close, "Hide Actions", { if (abs(horizontalOffsetX.value) == endActionsWidth) coroutineScope.launch { collapse() } }, Color.Black)
            if (isInGroup) {
                NotiActionIconButton(R.drawable.leave_group, "Remove from Group", {
                    if (abs(horizontalOffsetX.value) == endActionsWidth) {
                        drawerViewModel.removeFromGroup(notiKey)
                        coroutineScope.launch { collapse() }
                    }
                }, Color.Black)
            } else {
                NotiActionIconButton(
                    if (notiUnit.category == NOTI_CATEGORY_MAKETASK) R.drawable.task_yes else R.drawable.task_no,
                    "Make-Task",
                    {
                        if (abs(horizontalOffsetX.value) == endActionsWidth) {
                            if (notiUnit.category == NOTI_CATEGORY_MAKETASK) drawerViewModel.actOnNoti(
                                notiKey,
                                "dismiss_task"
                            ) else drawerViewModel.actOnNoti(notiKey, "make_task")
                            coroutineScope.launch { collapse() }
                        }
                    },
                    Color.Black
                )
                NotiActionIconButton(
                    if (notiUnit.category == NOTI_CATEGORY_SAVE) R.drawable.save_yes else R.drawable.save_no,
                    "Save",
                    {
                        if (abs(horizontalOffsetX.value) == endActionsWidth) {
                            if (notiUnit.category == NOTI_CATEGORY_SAVE) drawerViewModel.actOnNoti(
                                notiKey,
                                "unsave"
                            ) else drawerViewModel.actOnNoti(notiKey, "save")
                            coroutineScope.launch { collapse() }
                        }
                    },
                    Color.Black
                )
                NotiActionIconButton(
                    if (notiUnit.category == NOTI_CATEGORY_ARCHIVE) R.drawable.archive_yes else R.drawable.archive_no,
                    "Archive",
                    {
                        if (abs(horizontalOffsetX.value) == endActionsWidth) {
                            if (notiUnit.category == NOTI_CATEGORY_ARCHIVE) drawerViewModel.actOnNoti(
                                notiKey,
                                "unarchive"
                            ) else drawerViewModel.actOnNoti(notiKey, "archive")
                            coroutineScope.launch { collapse() }
                        }
                    },
                    Color.Black
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(0f)
                .graphicsLayer {
                    translationX = horizontalOffsetX.value
                    translationY = lift.toPx()
                    scaleX = scaleValue
                    scaleY = scaleValue
                    alpha = if (isFloating) 0.88f else 1f
                }
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.large
                )
                .clickable(
                    onClick = {
                        val contentIntent = NotiListenerService.getContentIntent(context, notiUnit)
                        if (contentIntent != null) {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    val options = ActivityOptions.makeBasic()
                                    contentIntent.send(context, 0, null, null, null, null, options.toBundle())
                                } else {
                                    contentIntent.send()
                                }
                            } catch (e: Exception) {
                                Log.e("AccessNotification", "PendingIntent send failed", e)
                            }
                        }
                        drawerViewModel.actOnNoti(notiKey, "access_click_dismiss")
                    }
                )
                .onGloballyPositioned { coords -> surfaceBoundsInWindow = coords.boundsInWindow() }
                .then(surfaceBorderModifier),
            // REMOVED longPressDraggableHandle
            shape = MaterialTheme.shapes.large,
            shadowElevation = 0.dp,
            color = backgroundColor
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(Modifier.padding(start = 6.dp, end = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    val imageToDisplay = remember(bitmap, largeBitmap, anchoredDraggableState.offset) {
                        if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD && largeBitmap != null) largeBitmap.asImageBitmap() else bitmap?.asImageBitmap()
                    }
                    val hasTransparency = remember(bitmap) {
                        if (bitmap == null) false else {
                            val w = minOf(bitmap.width, 16)
                            val h = minOf(bitmap.height, 16)
                            val scaled = if (bitmap.width > w || bitmap.height > h) bitmap.scale(w, h) else bitmap
                            val pixels = IntArray(w * h)
                            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
                            if (bitmap.hasAlpha()) pixels.count { ((it ushr 24) and 0xFF) < 250 } / pixels.size.toFloat() > 0.1f else pixels.map { it and 0xFFFFFF }.toSet().size < 12
                        }
                    }

                    if (showSummary()) Spacer(Modifier.size(3.dp))

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
                        NotiActionIconButton(iconRes, "Task Checkbox", { drawerViewModel.actOnNoti(notiKey, action) }, backgroundColor, false, color)
                    }

                    if (imageToDisplay != null) {
                        val iconModifier = Modifier.size(35.dp).padding(vertical = 3.dp, horizontal = if (isTask) 0.dp else 3.dp)
                        if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD && largeBitmap != null) {
                            Image(bitmap = imageToDisplay, contentDescription = "Notification Icon", modifier = iconModifier)
                        } else {
                            if (hasTransparency) Icon(bitmap = imageToDisplay, contentDescription = "Notification Icon", modifier = iconModifier, tint = contentColorFor(backgroundColor)) else Image(bitmap = imageToDisplay, contentDescription = "Notification Icon", modifier = iconModifier)
                        }
                    }
                }

                Column(Modifier.align(Alignment.TopEnd)) {
                    Row(Modifier.wrapContentHeight().padding(start = if (isTask) 80.dp else 35.dp)) {
                        if (showSummary()) {
                            Text(summary, Modifier.weight(1f).padding(horizontal = 5.dp).align(Alignment.CenterVertically), fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        } else {
                            Column(Modifier.padding(start = 20.dp, end = 5.dp).weight(1f)) {
                                Row(Modifier.fillMaxWidth()) {
                                    if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD) {
                                        Text(appName, fontSize = 12.sp)
                                        Spacer(Modifier.weight(1F))
                                    } else {
                                        Column(Modifier.wrapContentHeight().weight(1F)) {
                                            Text(if (notiOverallTitle.isBlank()) appName else notiOverallTitle, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, onTextLayout = { if (it.hasVisualOverflow) requiresExpansion = true })
                                            if (hasSecondTitle) Text(notiSecondOverallTitle, style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic), overflow = TextOverflow.Ellipsis, maxLines = 1, onTextLayout = { if (it.hasVisualOverflow) requiresExpansion = true })
                                        }
                                    }
                                    Box(modifier = Modifier.background(timeColor, RoundedCornerShape(16.dp))) {
                                        Text(notiDisplayUnit.latestUpdateRelTimeStr, Modifier.padding(horizontal = 5.dp), maxLines = 1, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = contentColorFor(timeColor))
                                    }
                                }
                                Row {
                                    if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD) {
                                        Column {
                                            Text(notiOverallTitle, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), maxLines = if (anchoredDraggableState.offset < COLLAPSE_THRESHOLD) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                            if (hasSecondTitle) Text(notiSecondOverallTitle, style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic), overflow = TextOverflow.Ellipsis)
                                        }
                                    } else {
                                        val notiContent = notiRecords.lastOrNull()?.content ?: ""
                                        Text(if (notiContent == "null") "" else replaceChars(notiContent), maxLines = 2, overflow = TextOverflow.Ellipsis, onTextLayout = { if (it.hasVisualOverflow) requiresExpansion = true }, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(100.dp))
                    }

                    if (requiresExpansion) {
                        LaunchedEffect(anchoredDraggableState.currentValue, notiKey) {
                            if (anchoredDraggableState.currentValue == NotiExpandState.Opened) drawerViewModel.loadFullRecordsForKey(notiKey)
                        }
                        val fullRecordsFlow = drawerViewModel.getFullRecordsFlow(notiKey)
                        val fullRecords by fullRecordsFlow.collectAsState()
                        val showingRecords = if (fullRecords.isNotEmpty()) fullRecords else notiRecords
                        val sampleCount = minOf(SAMPLE_LIMIT, showingRecords.size)

                        SubcomposeLayout(modifier = Modifier) { constraints ->
                            val measuringConstraints = constraints.copy(maxHeight = Int.MAX_VALUE)
                            val toMeasure = showingRecords.take(sampleCount)
                            val measPlaceables = subcompose("measurer") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    toMeasure.forEach { rec -> ExpandedNotiRecord(rec.getDisplayedTitle(isPeople), rec.time, rec.content, false, backgroundColor, rec.isRead, false, null, {}) }
                                }
                            }.map { it.measure(measuringConstraints) }
                            val sampleTotalHeight = measPlaceables.sumOf { it.height }
                            measuredContentHeightPx.floatValue = sampleTotalHeight.toFloat()
                            layout(constraints.maxWidth, 0) { }
                        }

                        LaunchedEffect(measuredContentHeightPx.floatValue, showingRecords.size) {
                            val maxHeightPx = with(density) { maxHeightDp.toPx() }
                            val samplePx = if (measuredContentHeightPx.floatValue > 0f) measuredContentHeightPx.floatValue else with(density) { (56.dp * sampleCount).toPx() }
                            val finalPx = if (showingRecords.size <= sampleCount) minOf(samplePx, maxHeightPx) else minOf((samplePx / maxOf(1, sampleCount)) * showingRecords.size, maxHeightPx)
                            maxContentHeightPx = finalPx
                            anchoredDraggableState.updateAnchors(DraggableAnchors {
                                NotiExpandState.Collapsed at 0f
                                NotiExpandState.Opened at maxContentHeightPx
                            })
                        }

                        val currentHeightPx = observedOffset.floatValue.coerceIn(0f, maxContentHeightPx)
                        val listState = rememberLazyListState()
                        LaunchedEffect(anchoredDraggableState.currentValue, showingRecords.size) {
                            if (anchoredDraggableState.currentValue == NotiExpandState.Opened && showingRecords.isNotEmpty()) listState.scrollToItem(maxOf(0, showingRecords.lastIndex))
                        }

                        LazyColumn(state = listState, modifier = Modifier.height(with(density) { currentHeightPx.toDp() }).onGloballyPositioned { recordsViewport = it.boundsInWindow() }) {
                            item { HorizontalDivider(Modifier.padding(horizontal = 16.dp), 1.dp, Color.White) }
                            if (fullRecords.isEmpty() && anchoredDraggableState.currentValue == NotiExpandState.Opened) item { CircularProgressIndicator(Modifier.padding(8.dp).size(24.dp)) }
                            items(showingRecords, key = { it.notiRecordId }) { notiRecord ->
                                val notiTitle = notiRecord.getDisplayedTitle(isPeople)
                                val infoTimeColor = when { !notiRecord.isRead && !hideComplexVisuals -> MaterialTheme.colorScheme.error else -> backgroundColor }
                                ExpandedNotiRecord(notiTitle, notiRecord.time, notiRecord.content, false, infoTimeColor, notiRecord.isRead, isCardVisible, recordsViewport, { if (!notiRecord.isRead) { onNotiRecordRead(notiRecord.notiRecordId); readRecordIdsInCard.add(notiRecord.notiRecordId) } })
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().graphicsLayer { translationX = horizontalOffsetX.value }.zIndex(3f)) {
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 8.dp).onGloballyPositioned { coords ->
                val overlayWindow = coords.boundsInWindow()
                val surfaceWindow = surfaceBoundsInWindow
                if (surfaceWindow != null) overlayBoundsRelativeToSurface = Rect(overlayWindow.left - surfaceWindow.left, overlayWindow.top - surfaceWindow.top, overlayWindow.right - surfaceWindow.left, overlayWindow.bottom - surfaceWindow.top)
            }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (requiresExpansion) {
                    val expandPainter = if (progress < 0.5f) painterResource(R.drawable.expand_circle_down) else painterResource(R.drawable.expand_circle_up)
                    Icon(painter = expandPainter, contentDescription = "Expand", modifier = Modifier.minimumInteractiveComponentSize().size(30.dp).anchoredDraggable(anchoredDraggableState, Orientation.Vertical, enabled = requiresExpansion, flingBehavior = anchoredFlingBehavior).clickable {
                        coroutineScope.launch { updateMeasuredAnchors(); if (anchoredDraggableState.offset < COLLAPSE_THRESHOLD) anchoredDraggableState.animateTo(NotiExpandState.Opened) else anchoredDraggableState.animateTo(NotiExpandState.Collapsed) }
                    })
                }
                if (isSortingMode) {
                    // Visual handle only - dragging logic handled by parent
                    Icon(painterResource(R.drawable.drag_handle), contentDescription = "Drag to reorder", modifier = Modifier.minimumInteractiveComponentSize())
                } else {
                    NotiActionIconButton(if (isPinned) R.drawable.pin_yes else R.drawable.pin_no, "Pin", { if (isPinned) drawerViewModel.actOnNoti(notiKey, "unpin") else drawerViewModel.actOnNoti(notiKey, "pin") }, backgroundColor, false, if (isPinned) Color(76, 139, 245) else Color.Unspecified)
                }
            }
        }
    }
}