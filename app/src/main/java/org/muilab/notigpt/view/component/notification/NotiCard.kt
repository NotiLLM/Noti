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
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.layout.fillMaxHeight
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
import sh.calvin.reorderable.ReorderableCollectionItemScope
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_IMPORT") // parameters and some temporaries may be unused in certain builds
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

    // --- observe swipe-delete direction preference ---
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


    Log.d("NotiCard", "Title: $notiOverallTitle, Last Update Time: ${notiDisplayUnit.lastUpdateTime}")

    val hasSecondTitle = notiSecondOverallTitle.isNotBlank() && notiSecondOverallTitle != notiOverallTitle
    val isPeople = notiUnit.isPeople
    val appName = notiUnit.appName
    val bitmap = notiUnit.bitmap
    val largeBitmap = notiUnit.largeBitmap

    val isAppCategoryView = drawerViewModel.isAppCategoryView.collectAsState()
    val isTask = notiUnit.category == NOTI_CATEGORY_MAKETASK && !hideComplexVisuals

    val summary = notiUnit.summary
    val hasSummary = summary.isNotEmpty()

    // Use the normal surfaceVariant for the background so color doesn't communicate
    // manual-sort state. Instead, we'll use elevation/shadow to indicate a "floaty"
    // item when sortPosition == -1 (not manually sorted). Items with a manual
    // sort position will be flat (no 3D effect) so they look "stemmed" into the
    // background.
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

    // If sortPosition == -1, the item is not manually sorted and should appear
    // slightly floaty; otherwise keep it flat.
    // Disable all sortPosition-based visual tweaks (alpha, rim, lift) by
    // forcing isFloating=false. This is the minimal change to remove the
    // floating/fixed visual distinctions requested.
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

    // Create the anchored draggable state using the non-deprecated form and
    // provide fling/threshold configuration via AnchoredDraggableDefaults.flingBehavior
    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = NotiExpandState.Collapsed,
            anchors = DraggableAnchors {
                NotiExpandState.Collapsed at 0f
                // use initialEstimatePx so initial anchors are non-zero
                NotiExpandState.Opened at initialEstimatePx
            }
        )
    }

    // Use current API: flingBehavior(state, positionalThreshold, animationSpec)
    val anchoredFlingBehavior = AnchoredDraggableDefaults.flingBehavior(
        anchoredDraggableState,
        { distance: Float -> distance * 0.5f },
        spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // Helper: compute a conservative measured expanded height and update anchors.
    // Placed after anchoredDraggableState so it can reference the state variable.
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

    // Current expansion progress (0..1) used by overlay controls and expansion icon
    val progress = expansionProgress(anchoredDraggableState.offset, maxContentHeightPx)

    val coroutineScope = rememberCoroutineScope()

    val horizontalOffsetX = remember { Animatable(0f) }
    var endActionsWidth by remember { mutableStateOf(0f) }
    var cardWidth by remember { mutableStateOf(0f) }

    // Observed offset used to drive UI recompositions while dragging.
    val observedOffset = remember { mutableFloatStateOf(anchoredDraggableState.offset.coerceAtLeast(0f)) }

    LaunchedEffect(anchoredDraggableState) {
        snapshotFlow { anchoredDraggableState.offset }
            .collect { value ->
                observedOffset.floatValue = (value.coerceAtLeast(0f))
            }
    }

    // touch slop for this device / compose configuration
    val viewTouchSlop = LocalViewConfiguration.current.touchSlop

    // Track surface bounds and overlay bounds so the combined drag handler can ignore overlay touches
    var surfaceBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    var overlayBoundsRelativeToSurface by remember { mutableStateOf<Rect?>(null) }

    val combinedDragHandler = if (isDragging) Modifier else Modifier.pointerInput(endActionsWidth, cardWidth) {
        // Make horizontal detection a bit stricter than the very-permissive setting so
        // vertical scrolling isn't as easily misclassified as a horizontal swipe.
        val horizontalBiasFactor = 0.45f
        val minHorizontalPx = viewTouchSlop * 0.45f
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            // overlay controls are on top, so no need to special-case pin hit-tests here
            // If the down started inside the overlay controls, bail out so the overlay handles the event
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
                // handle horizontal drag with velocity tracking
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
                        // Reduce accidental fling-based dismisses by requiring a stronger fling
                        // and making swipe threshold slightly easier to reach.
                        val flingThreshold = 800f
                        val swipeThresholdPx = cardWidth * 0.20f

                        if (isHorizontal) {
                            val currentOffsetVal = horizontalOffsetX.value
                            if (abs(flingVelocityX) > flingThreshold) {
                                // Only treat a quick fling as a dismiss if the fling direction
                                // matches the current offset direction (prevents overshooting
                                // to the opposite side) and the offset has passed a small
                                // minimum (half the swipe threshold).
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
                                } else {
                                    // Fling not aligned with current offset or offset too small: fall
                                    // back to regular threshold behavior below.
                                    // (Do nothing here; the code below will run.)
                                }
                            }

                            // Regular snap behavior when no qualifying fling dismiss happened
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
                                // If a fling was greater than threshold but didn't meet the
                                // directional/offset requirement, snap back to neutral or action
                                // reveal based on current offset.
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

    val showSummary = {
        anchoredDraggableState.offset < COLLAPSE_THRESHOLD && hasSummary
    }

    // Animate elevation: when dragging we still raise the card; otherwise only
    // unsorted (floating) cards get a small elevation to appear 3D. Sorted cards
    // remain flat.
    val targetElevationDp = if (isDragging) 12.dp else if (isFloating) 6.dp else 0.dp
    val elevation by animateDpAsState(targetElevationDp, label = "elevation")

    // Small scale animation: slightly enlarge while dragging to emphasize elevation.
    val scaleValue by animateFloatAsState(if (isDragging) 1.02f else 1f)

    // Helper to collapse any revealed action buttons (snap horizontal offset back to 0)
    val collapse: suspend () -> Unit = {
        try {
            horizontalOffsetX.animateTo(0f)
        } catch (_: Throwable) {}
    }

    // Choose a shadow tint that reads in both light and dark themes.
    val isDarkTheme = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .padding(vertical = 1.dp, horizontal = 20.dp)
            .graphicsLayer {
                // keep only the scale transforms here; remove shadowElevation
                // so the layer doesn't draw a rectangular shadow. Surface's
                // shadowElevation (set below) will be used instead and will
                // respect the rounded shape.
                scaleX = scaleValue
                scaleY = scaleValue
            }
            .onSizeChanged {
                cardWidth = it.width.toFloat()
                // Reveal buttons when swiping right to 80% of card width
                endActionsWidth = cardWidth * 0.8f
            }
            .then(if (isSortingMode) Modifier else combinedDragHandler)
            .clip(MaterialTheme.shapes.large)
    ) {
        val surfaceBaseModifier = if (isSortingMode) {
            Modifier.longPressDraggableHandle()
        } else {
            Modifier
        }
        // Background Actions (rendered beneath the Surface so they stay stationary while Surface translates)
        Row(
            modifier = Modifier
                .align(if (swipeDeleteLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .onSizeChanged { endActionsWidth = it.width.toFloat() }
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                // Guard the actions row from any inherited translation by forcing translationX=0
                .graphicsLayer {
                    translationX = 0f
                    // compute reveal progress and square it for a nicer ease-in effect
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
            NotiActionIconButton(
                iconRes = R.drawable.close,
                contentDescription = "Hide Actions",
                backgroundColor = Color.Black,
                onClick = {
                    if (abs(horizontalOffsetX.value) == endActionsWidth)
                        coroutineScope.launch { collapse() }
                }
            )
            NotiActionIconButton(
                iconRes = if (notiUnit.category == NOTI_CATEGORY_MAKETASK) R.drawable.task_yes else R.drawable.task_no,
                contentDescription = "Make-Task",
                backgroundColor = Color.Black,
                onClick = {
                    if (abs(horizontalOffsetX.value) == endActionsWidth) {
                        if (notiUnit.category == NOTI_CATEGORY_MAKETASK)
                            drawerViewModel.actOnNoti(notiKey, "dismiss_task")
                        else drawerViewModel.actOnNoti(notiKey, "make_task")
                        coroutineScope.launch { collapse() }
                    }
                }
            )
            NotiActionIconButton(
                iconRes = if (notiUnit.category == NOTI_CATEGORY_SAVE) R.drawable.save_yes else R.drawable.save_no,
                contentDescription = "Save",
                backgroundColor = Color.Black,
                onClick = {
                    if (abs(horizontalOffsetX.value) == endActionsWidth) {
                        if (notiUnit.category == NOTI_CATEGORY_SAVE)
                            drawerViewModel.actOnNoti(notiKey, "unsave")
                        else drawerViewModel.actOnNoti(notiKey, "save")
                        coroutineScope.launch { collapse() }
                    }
                }
            )
            NotiActionIconButton(
                iconRes = if (notiUnit.category == NOTI_CATEGORY_ARCHIVE) R.drawable.archive_yes else R.drawable.archive_no,
                contentDescription = "Archive",
                backgroundColor = Color.Black,
                onClick = {
                    if (abs(horizontalOffsetX.value) == endActionsWidth) {
                        if (notiUnit.category == NOTI_CATEGORY_ARCHIVE)
                            drawerViewModel.actOnNoti(notiKey, "unarchive")
                        else drawerViewModel.actOnNoti(notiKey, "archive")
                        coroutineScope.launch { collapse() }
                    }
                }
            )
//            NotiActionIconButton(
//                iconRes = R.drawable.reset_sort,
//                contentDescription = "Reset Sort",
//                backgroundColor = Color.Black,
//                onClick = {
//                    if (horizontalOffsetX.value == endActionsWidth) {
//                        drawerViewModel.resetManualSortOrder(notiKey)
//                        coroutineScope.launch { collapse() }
//                    }
//                }
//            )
        }

        // When floating on a pure-black background, shadows can be hard to see.
        // Add a subtle rounded border in dark mode and a small highlight so the
        // 3D effect remains visible while preserving rounded corners.
        val rimColor = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
        val surfaceBorderModifier = if (isFloating) {
            // use the width/color overload to avoid referencing BorderStroke directly
            Modifier.border(1.dp, rimColor, shape = MaterialTheme.shapes.large)
        } else Modifier

        // Small lift animation (move slightly upward when floating) to help the 3D
        // perception. Negative dp moves the Surface up.
        val targetLift = if (isFloating) (-4).dp else 0.dp
        val lift by animateDpAsState(targetLift, label = "lift")

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(0f)
                .graphicsLayer {
                    translationX = horizontalOffsetX.value
                    translationY = lift.toPx()
                    // keep your existing scaleX and scaleY
                    scaleX = scaleValue
                    scaleY = scaleValue
                    // De-emphasize movable (floating) items by reducing alpha slightly.
                    // Fixed (non-floating) items remain at full opacity.
                    alpha = if (isFloating) 0.88f else 1f
                }
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
                        Log.d("NotiListenerService", "Sent intent")
                        drawerViewModel.actOnNoti(notiKey, "access_click_dismiss")
                    }
                )
                // NOTE: anchoredDraggable was intentionally moved from the Surface to the expand icon
                .onGloballyPositioned { coords ->
                    // capture surface bounds in window coordinates so we can compute relative pin bounds
                    surfaceBoundsInWindow = coords.boundsInWindow()
                }
                .then(surfaceBorderModifier)
                .then(surfaceBaseModifier),
            shape = MaterialTheme.shapes.large,
            // Disable platform elevation shadow which can draw a rectangular shadow
            // and rely on our rounded layered shadow approximation instead.
            shadowElevation = 0.dp,
            color = backgroundColor
        ) {

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
                                // quick heuristic for icon-like: few distinct colours
                                val unique = pixels.map { it and 0xFFFFFF }.toSet().size
                                unique < 12
                            }
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
                        // expand/pin controls handled by the overlay Row; keep layout stable
                        Spacer(modifier = Modifier.width(100.dp))
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
                            measuredContentHeightPx.floatValue = sampleTotalHeight.toFloat()

                            // Don't occupy space
                            layout(constraints.maxWidth, 0) { }
                        }

                        LaunchedEffect(measuredContentHeightPx.floatValue, showingRecords.size) {
                            val maxHeightPx = with(density) { maxHeightDp.toPx() }
                            val samplePx = if (measuredContentHeightPx.floatValue > 0f) measuredContentHeightPx.floatValue else with(density) { (56.dp * sampleCount).toPx() }
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
                            Log.d("NotiCardMeas", "SamplePx=${measuredContentHeightPx.floatValue}, sampleCount=$sampleCount, total=${showingRecords.size}, set anchor=${maxContentHeightPx} for key=$notiKey")
                        }

                        // Render the showingRecords in a LazyColumn whose height matches the current
                        // draggable offset (clamped to the computed maxContentHeightPx). This ensures
                        // users can actually see records when they expand the card.
                        val currentHeightPx = observedOffset.floatValue.coerceIn(0f, maxContentHeightPx)
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
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.White, thickness = 1.dp)
                            }

                            if (fullRecords.isEmpty() && anchoredDraggableState.currentValue == NotiExpandState.Opened) {
                                item {
                                    CircularProgressIndicator(
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

        // Overlay: controls rendered in a horizontal Row aligned to the top-end.
        // Apply the same horizontal translation so the overlay moves with the card while
        // still being visually above it (zIndex) — this keeps the buttons aligned to the
        // moving Surface and avoids perceived desync when swiping.
        Box(modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = horizontalOffsetX.value }
            .zIndex(3f)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 8.dp)
                     .onGloballyPositioned { coords ->
                        // compute overlay bounds from the actual Row (buttons) so only the
                        // interactive area of the buttons is excluded from the combined drag handler.
                        val overlayWindow = coords.boundsInWindow()
                        val surfaceWindow = surfaceBoundsInWindow
                        if (surfaceWindow != null) {
                            overlayBoundsRelativeToSurface = Rect(
                                left = overlayWindow.left - surfaceWindow.left,
                                top = overlayWindow.top - surfaceWindow.top,
                                right = overlayWindow.right - surfaceWindow.left,
                                bottom = overlayWindow.bottom - surfaceWindow.top
                            )
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (requiresExpansion) {
                    val expandPainter = if (progress < 0.5f) painterResource(R.drawable.expand_circle_down) else painterResource(R.drawable.expand_circle_up)
                    Icon(
                        painter = expandPainter,
                        contentDescription = "Expand",
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(30.dp)
                            .anchoredDraggable(state = anchoredDraggableState, orientation = Orientation.Vertical, enabled = requiresExpansion, flingBehavior = anchoredFlingBehavior)
                            .clickable {
                                coroutineScope.launch {
                                    // reuse helper to compute measured anchors
                                    updateMeasuredAnchors()
                                     if (anchoredDraggableState.offset < COLLAPSE_THRESHOLD) anchoredDraggableState.animateTo(NotiExpandState.Opened) else anchoredDraggableState.animateTo(NotiExpandState.Collapsed)
                                }
                            }
                    )
                }

                if (isSortingMode) {
                    Icon(painterResource(R.drawable.drag_handle), contentDescription = "Drag to reorder", modifier = Modifier.minimumInteractiveComponentSize())
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
            }
        }
    } // end outer Box
} // end NotiCard
