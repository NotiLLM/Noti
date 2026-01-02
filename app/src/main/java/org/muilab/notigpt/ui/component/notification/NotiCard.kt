package org.muilab.notigpt.ui.component.notification

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.replaceChars
import org.muilab.notigpt.ui.component.notification.action.NotiActionIconButton
import org.muilab.notigpt.ui.component.notification.info.ExpandedNotiRecord
import org.muilab.notigpt.ui.utils.NotiExpandState
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NotiCard( // REMOVED RECEIVER HERE
    context: Context,
    notiDisplayUnit: NotiDisplayUnit,
    isDragging: Boolean,
    drawerViewModel: DrawerViewModel,
    isCardVisible: Boolean,
    parentViewport: Rect?,
    category: String,
    appCategory: String,
    isMergeTarget: Boolean = false,
    isInGroup: Boolean = false
) {

    val swipeDeleteLeft = SharedPreferencesManager.swipeDeleteLeft
    val isSortingMode by drawerViewModel.isSortingMode.collectAsState()

    var recordsViewport: Rect? by remember { mutableStateOf(null) }

    var showOptionsDialog by remember { mutableStateOf(false) }

    val notiUnit = notiDisplayUnit.notiUnit
    val notiRecords = notiDisplayUnit.notiRecords
    val notiKey = notiUnit.notiKey
    val isPinned = notiUnit.isPinned
    val isRead = notiUnit.isRead

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

    val isTask = notiUnit.category == NOTI_CATEGORY_MAKETASK
    val isSave = notiUnit.category == NOTI_CATEGORY_SAVE
    val isArchive = notiUnit.category == NOTI_CATEGORY_ARCHIVE
    val isSetToTop = notiUnit.isSetToTop

    val hasSecondTitle = notiSecondOverallTitle.isNotBlank() && notiSecondOverallTitle != notiOverallTitle
    val isPeople = notiUnit.isPeople
    val appName = notiUnit.appName
    val bitmap = notiUnit.bitmap
    val largeBitmap = notiUnit.largeBitmap
    val summary = notiUnit.summary
    val hasSummary = summary.isNotEmpty()

    val backgroundColor = when {
        isMergeTarget -> MaterialTheme.colorScheme.primaryContainer
        isSetToTop -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceBright
    }

    // Border Color Logic
    val borderColor = when {
        isMergeTarget -> MaterialTheme.colorScheme.primary
        !isRead -> MaterialTheme.colorScheme.error
        isSetToTop -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }

    val borderWidth = if (isSetToTop || isMergeTarget) 2.dp else 0.5.dp

    // Floating visualization logic (simplified)
    val isFloating = false

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
    var endActionsWidth by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }
    val observedOffset = remember { mutableFloatStateOf(anchoredDraggableState.offset.coerceAtLeast(0f)) }

    LaunchedEffect(anchoredDraggableState) {
        snapshotFlow { anchoredDraggableState.offset }
            .collect { value -> observedOffset.floatValue = (value.coerceAtLeast(0f)) }
    }

    val viewTouchSlop = LocalViewConfiguration.current.touchSlop
    var surfaceBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    var overlayBoundsRelativeToSurface by remember { mutableStateOf<Rect?>(null) }

    val combinedDragHandler = if (isDragging) Modifier else Modifier.pointerInput(endActionsWidth, cardWidth) {
        // FIX: Increased bias to 0.5f to ensure diagonal movements don't accidentally trigger swipe
        val horizontalBiasFactor = 0.5f

        // FIX: Removed the (* 0.45f) multiplier.
        // Using the full viewTouchSlop ensures that micro-movements during a 'tap'
        // are ignored, allowing the click event to fire successfully.
        val minHorizontalPx = viewTouchSlop

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val overlayRect = overlayBoundsRelativeToSurface
            if (overlayRect != null && overlayRect.contains(down.position)) return@awaitEachGesture

            var isHorizontal = false
            val slopResult = awaitTouchSlopOrCancellation(down.id) { change, over ->
                val absX = abs(over.x)
                val absY = abs(over.y)

                // Logic: Only claim the gesture if X movement is significantly larger than Y
                // AND the total X movement exceeds the system's standard touch slop.
                if (absX > max(minHorizontalPx, absY * horizontalBiasFactor)) {
                    isHorizontal = true
                    change.consume() // Consuming this kills the click, so we only do it if we are SURE it's a swipe.
                }
            }

            if (slopResult != null && isHorizontal) {
                // ... (The rest of your existing VelocityTracker and drag logic remains exactly the same) ...
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
                    // ... (Your existing fling logic here) ...
                    // Copy the exact content of your finally block from the previous code
                    // logic for handling swipe delete left/right, dismissal, etc.
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
            .onGloballyPositioned { coordinates ->
                if (!isRead && parentViewport != null) {
                    val cardBounds = coordinates.boundsInWindow()

                    val tolerance = 1f

                    val isTopVisible = cardBounds.top >= (parentViewport.top - tolerance)
                    val isBottomVisible = cardBounds.bottom <= (parentViewport.bottom + tolerance)

                    // Optional: Check X axis if you have horizontal scrolling,
                    // but usually Y is sufficient for this specific Drawer design.
                    // If you want strict X as well:
                    // val isLeftVisible = cardBounds.left >= (parentViewport.left - tolerance)
                    // val isRightVisible = cardBounds.right <= (parentViewport.right + tolerance)

                    if (isTopVisible && isBottomVisible) {
                        drawerViewModel.markNotificationAsRead(notiKey)
                    }
                }
            }
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
                .zIndex(0f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotiActionIconButton(R.drawable.close, "Hide Actions", { if (abs(horizontalOffsetX.value) == endActionsWidth) coroutineScope.launch { collapse() } })

            if (isInGroup) {
                NotiActionIconButton(R.drawable.leave_group, "Remove from Group", {
                    if (abs(horizontalOffsetX.value) == endActionsWidth) {
                        drawerViewModel.removeFromGroup(notiKey)
                        coroutineScope.launch { collapse() }
                    }
                })
            } else {
                NotiActionIconButton(
                    if (isTask) R.drawable.task_yes else R.drawable.task_no,
                    "Make-Task",
                    {
                        if (abs(horizontalOffsetX.value) == endActionsWidth) {
                            if (isTask) drawerViewModel.actOnNoti(
                                notiKey,
                                "dismiss_task"
                            ) else drawerViewModel.actOnNoti(notiKey, "make_task")
                            coroutineScope.launch { collapse() }
                        }
                    }
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
                    }
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
                    }
                )
                NotiActionIconButton(
                    R.drawable.totop,
                    "To Top",
                    {
                        if (abs(horizontalOffsetX.value) == endActionsWidth) {
                            drawerViewModel.actOnNoti(notiKey, "to_top")
                            coroutineScope.launch { collapse() }
                        }
                    }
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .graphicsLayer {
                    translationX = horizontalOffsetX.value
                    translationY = lift.toPx()
                    scaleX = scaleValue
                    scaleY = scaleValue
                    alpha = if (isFloating) 0.88f else 1f
                }
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = MaterialTheme.shapes.large
                )
                .combinedClickable(
                    onClick = {
                        // 1. Try to get the intent (Cached or Fresh)
                        val contentIntent = NotiListenerService.getContentIntent(context, notiUnit)
                        if (contentIntent != null) {
                            try {
                                // 2. Version-specific ActivityOptions to allow Background Activity Launches
                                val optionsBundle: Bundle? = when {
                                    // Android 14 (API 34)+: STRICT requirement to allow background starts
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                                        val options = ActivityOptions.makeBasic()
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
                                            options.pendingIntentBackgroundActivityStartMode =
                                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                                        else
                                            options.pendingIntentBackgroundActivityStartMode =
                                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                        options.toBundle()
                                    }
                                    // Android 11 (API 30) - Android 13: Good practice to provide basic options
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                        ActivityOptions.makeBasic().toBundle()
                                    }
                                    // Older Android versions: null is usually sufficient
                                    else -> null
                                }

                                // 3. Attempt to send the PendingIntent
                                contentIntent.send(context, 0, null, null, null, null, optionsBundle)

                            } catch (e: PendingIntent.CanceledException) {
                                // 4. Handle "One Shot" expiration (CanceledException)
                                Log.w("AccessNotification", "PendingIntent canceled/expired. Trying Fallback.")
                                NotiListenerService.removeIntents(notiUnit.notiKey)

                                try {
                                    // Fallback: Launch the app's main page directly
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(notiUnit.metadata.pkgName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    }
                                } catch (e2: Exception) {
                                    Log.e("AccessNotification", "Fallback launch failed", e2)
                                }
                            } catch (e: Exception) {
                                // 5. Handle BAL Blocks (SecurityException) or other errors
                                Log.e("AccessNotification", "PendingIntent send failed", e)
                            }
                        } else {
                            Log.e("AccessNotification", "No content intent found for ${notiUnit.appName}")
                        }
                        // Perform your UI action
                        drawerViewModel.actOnNoti(notiKey, "access_click_dismiss")
                    },
                    onLongClick = {
                        if (!isSortingMode) {
                            showOptionsDialog = true
                        }
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

                    if (imageToDisplay != null) {
                        val iconModifier = Modifier.size(35.dp).padding(3.dp)
                        if (anchoredDraggableState.offset > COLLAPSE_THRESHOLD && largeBitmap != null) {
                            Image(bitmap = imageToDisplay, contentDescription = "Notification Icon", modifier = iconModifier)
                        } else {
                            if (hasTransparency) Icon(bitmap = imageToDisplay, contentDescription = "Notification Icon", modifier = iconModifier, tint = contentColorFor(backgroundColor)) else Image(bitmap = imageToDisplay, contentDescription = "Notification Icon", modifier = iconModifier)
                        }
                    }
                }

                Column(Modifier.align(Alignment.TopEnd)) {
                    Row(Modifier.wrapContentHeight().padding(start = 35.dp)) {
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
                                            Text(notiOverallTitle.ifBlank { appName }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, onTextLayout = { if (it.hasVisualOverflow) requiresExpansion = true })
                                            if (hasSecondTitle) Text(notiSecondOverallTitle, style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic), overflow = TextOverflow.Ellipsis, maxLines = 1, onTextLayout = { if (it.hasVisualOverflow) requiresExpansion = true })
                                        }
                                    }
                                    Text(notiDisplayUnit.latestUpdateRelTimeStr, Modifier.padding(horizontal = 5.dp), maxLines = 1, fontSize = 12.sp, fontStyle = FontStyle.Italic)
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
                        val showingRecords = fullRecords.ifEmpty { notiRecords }
                        val sampleCount = minOf(SAMPLE_LIMIT, showingRecords.size)

                        SubcomposeLayout(modifier = Modifier) { constraints ->
                            val measuringConstraints = constraints.copy(maxHeight = Int.MAX_VALUE)
                            val toMeasure = showingRecords.take(sampleCount)
                            val measPlaceables = subcompose("measurer") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    toMeasure.forEach { rec -> ExpandedNotiRecord(rec.getDisplayedTitle(isPeople), rec.time, rec.content, false) }
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
                            items(showingRecords.size, key = { showingRecords[it].notiRecordId }) { index ->
                                val notiRecord = showingRecords[index]
                                val currentTitle = notiRecord.getDisplayedTitle(isPeople)
                                val showTitle = if (index == 0) {
                                    currentTitle != notiOverallTitle
                                } else {
                                    val prevTitle = showingRecords[index - 1].getDisplayedTitle(isPeople)
                                    currentTitle != prevTitle
                                }

                                ExpandedNotiRecord(currentTitle, notiRecord.time, notiRecord.content, showTitle)
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd) // <--- ADD THIS
                .fillMaxHeight()
                .graphicsLayer { translationX = horizontalOffsetX.value }
                .zIndex(2f)
        ) {
            Row(
                // We keep onGloballyPositioned here.
                // Now 'overlayBoundsRelativeToSurface' will correctly represent ONLY the buttons,
                // allowing you to drag the rest of the header!
                modifier = Modifier
                    .padding(end = 12.dp, top = 8.dp)
                    .fillMaxHeight()
                    .onGloballyPositioned { coords ->
                        val overlayWindow = coords.boundsInWindow()
                        val surfaceWindow = surfaceBoundsInWindow
                        if (surfaceWindow != null) overlayBoundsRelativeToSurface = Rect(
                            overlayWindow.left - surfaceWindow.left,
                            overlayWindow.top - surfaceWindow.top,
                            overlayWindow.right - surfaceWindow.left,
                            overlayWindow.bottom - surfaceWindow.top
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Remove the Spacer entirely. You don't need it anymore.

                if (requiresExpansion) {
                    val expandPainter = if (progress < 0.5f) painterResource(R.drawable.expand_circle_down) else painterResource(R.drawable.expand_circle_up)
                    Icon(
                        painter = expandPainter,
                        contentDescription = "Expand",
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(30.dp)
                            .anchoredDraggable(anchoredDraggableState, Orientation.Vertical, enabled = requiresExpansion, flingBehavior = anchoredFlingBehavior)
                            .clickable {
                                coroutineScope.launch {
                                    updateMeasuredAnchors()
                                    if (anchoredDraggableState.offset < COLLAPSE_THRESHOLD) anchoredDraggableState.animateTo(NotiExpandState.Opened)
                                    else anchoredDraggableState.animateTo(NotiExpandState.Collapsed)
                                }
                            }
                    )
                }

                if (isSortingMode) {
                    if (!isInGroup)
                        Icon(painterResource(R.drawable.drag_handle), contentDescription = "Drag to reorder", modifier = Modifier.minimumInteractiveComponentSize())
                } else {
                    NotiActionIconButton(
                        if (isPinned) R.drawable.pin_yes else R.drawable.pin_no,
                        "Pin",
                        { if (isPinned) drawerViewModel.actOnNoti(notiKey, "unpin") else drawerViewModel.actOnNoti(notiKey, "pin") },
                        if (isPinned) Color(76, 139, 245) else Color.Unspecified
                    )
                }
            }
        }
    }

    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("Options") },
            text = {
                Column {

                    if (isInGroup) {
                        TextButton(
                            onClick = {
                                drawerViewModel.removeFromGroup(notiKey)
                                showOptionsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.leave_group),
                                    contentDescription = "Leave Group",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Remove from Group")
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            drawerViewModel.actOnNoti(notiKey, if (!isTask) "make_task" else "dismiss_task")
                            showOptionsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isTask) R.drawable.task_yes else R.drawable.task_no
                                ),
                                contentDescription = "To Task",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (isTask) "Remove from Tasks" else "Move to Tasks")
                        }
                    }

                    TextButton(
                        onClick = {
                            drawerViewModel.actOnNoti(notiKey, if (!isSave) "save" else "unsave")
                            showOptionsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isSave) R.drawable.save_yes else R.drawable.save_no
                                ),
                                contentDescription = "To Save",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (isSave) "Remove from Save" else "Move to Save")
                        }
                    }

                    TextButton(
                        onClick = {
                            drawerViewModel.actOnNoti(notiKey, if (!isArchive) "archive" else "unarchive")
                            showOptionsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isArchive) R.drawable.archive_yes else R.drawable.archive_no
                                ),
                                contentDescription = "To Archive",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (isArchive) "Remove from Archive" else "Move to Archive")
                        }
                    }


                    // To Top Button (Always visible)
                    TextButton(
                        onClick = {
                            drawerViewModel.actOnNoti(notiKey, "to_top")
                            showOptionsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.totop),
                                contentDescription = "To Top",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (isSetToTop) "Move to Top (Update Time)" else "Set To Top")
                        }
                    }

                    if (isSetToTop) {
                        TextButton(
                            onClick = {
                                drawerViewModel.actOnNoti(notiKey, "undo_to_top")
                                showOptionsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.undo_totop),
                                    contentDescription = "Undo To Top",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Undo To Top")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOptionsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}