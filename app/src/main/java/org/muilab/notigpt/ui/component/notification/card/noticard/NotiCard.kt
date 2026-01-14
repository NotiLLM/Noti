package org.muilab.notigpt.ui.component.notification.card.noticard

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.platform.NotificationLauncher
import org.muilab.notigpt.service.NotiListenerService
import org.muilab.notigpt.ui.component.notification.card.noticard.elements.NOTI_CARD_COLLAPSE_THRESHOLD_PX_DEFAULT
import org.muilab.notigpt.ui.component.notification.card.noticard.elements.NotiCardBackgroundActions
import org.muilab.notigpt.ui.component.notification.card.noticard.elements.NotiCardExpandedRecords
import org.muilab.notigpt.ui.component.notification.card.noticard.elements.NotiCardHeaderContent
import org.muilab.notigpt.ui.component.notification.card.noticard.elements.NotiCardOptionsDialog
import org.muilab.notigpt.ui.component.notification.card.noticard.elements.NotiCardOptionsState
import org.muilab.notigpt.ui.component.notification.card.noticard.elements.NotiCardOverlayButtons
import org.muilab.notigpt.ui.component.notification.card.noticard.elements.notiCardExpansionFlingBehavior
import org.muilab.notigpt.ui.component.notification.card.noticard.elements.notiCardSwipeHandler
import org.muilab.notigpt.ui.component.notification.card.noticard.elements.rememberNotiCardExpansionState
import org.muilab.notigpt.ui.utils.NotiExpandState
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.util.SharedPreferencesManager
import sh.calvin.reorderable.ReorderableCollectionItemScope
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NotiCard(
    context: Context,
    notiDisplayUnit: NotiDisplayUnit,
    isDragging: Boolean,
    drawerViewModel: DrawerViewModel,
    isCardVisible: Boolean,
    parentViewport: Rect?,
    isMergeTarget: Boolean = false,
    isInGroup: Boolean = false,
    swipeEnabled: Boolean = true,
    // Reorder: handle-only (long-press) callbacks. No-op defaults keep API stable for other call sites.
    reorderEnabled: Boolean = false,
    // Optional when rendered inside ReorderableItem; enables native handle dragging.
    reorderScope: ReorderableCollectionItemScope? = null,
    onStartReorderDrag: (Offset) -> Unit = {},
    onReorderDrag: (Offset) -> Unit = {},
    onStopReorderDrag: () -> Unit = {},
) {
    // These are part of the shared NotiCard API even if not used in this implementation yet.
    @Suppress("UNUSED_VARIABLE")
    val _unusedApiParams = isCardVisible

    val swipeDeleteLeft = SharedPreferencesManager.swipeDeleteLeft
    val isSortingMode by drawerViewModel.isSortingMode.collectAsState()

    @Suppress("UNUSED_VARIABLE")
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

    val hasSecondTitle = notiSecondOverallTitle.isNotBlank() && notiSecondOverallTitle != notiOverallTitle
    val isPeople = notiUnit.isPeople

    val summary = notiUnit.summary
    val hasSummary = summary.isNotEmpty()

    val backgroundColor = when {
        isMergeTarget -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceBright
    }

    val borderColor = when {
        isMergeTarget -> MaterialTheme.colorScheme.primary
        !isRead -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    val borderWidth = if (notiUnit.sortPosition != -1 || isMergeTarget) 3.dp else 1.dp

    // Expand state
    val maxHeightDp = 200.dp
    val sampleLimit = 8
    val density = LocalDensity.current

    var requiresExpansion by remember(notiRecords, summary, notiOverallTitle, isPeople) {
        mutableStateOf(
            notiRecords.size > 1 || hasSummary ||
                (notiRecords.size == 1 && notiRecords[0].getDisplayedTitle(isPeople)
                    .let { notiOverallTitle.isNotBlank() && notiOverallTitle != it })
        )
    }

    val initialEstimatePx = remember(notiRecords.size, density) {
        val perItemPx = with(density) { 56.dp.toPx() }
        val sample = minOf(sampleLimit, notiRecords.size)
        val maxHeightPx = with(density) { maxHeightDp.toPx() }
        val minOpenPx = with(density) { 80.dp.toPx() }
        max(minOpenPx, minOf(perItemPx * sample, maxHeightPx))
    }

    val measuredContentHeightPx = remember { mutableFloatStateOf(0f) }
    val maxContentHeightPxState = remember { mutableFloatStateOf(initialEstimatePx) }

    val anchored: AnchoredDraggableState<NotiExpandState> =
        rememberNotiCardExpansionState(initialEstimatePx)
    val anchoredFlingBehavior = notiCardExpansionFlingBehavior(anchored)

    // Track anchored offset
    val observedOffset = remember { mutableFloatStateOf(anchored.offset.coerceAtLeast(0f)) }
    LaunchedEffect(anchored) {
        snapshotFlow { anchored.offset }
            .collect { value -> observedOffset.floatValue = value.coerceAtLeast(0f) }
    }

    val collapseThreshold = NOTI_CARD_COLLAPSE_THRESHOLD_PX_DEFAULT
    val showSummary = { anchored.offset < collapseThreshold && hasSummary }

    val coroutineScope = rememberCoroutineScope()
    val horizontalOffsetX = remember { Animatable(0f) }
    var endActionsWidth by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }

    val viewTouchSlop = LocalViewConfiguration.current.touchSlop
    var surfaceBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    var overlayBoundsRelativeToSurface by remember { mutableStateOf<Rect?>(null) }
    var isSwipeActive by remember { mutableStateOf(false) }

    val swipeModifier = Modifier.notiCardSwipeHandler(
        enabled = swipeEnabled && !isDragging && !isSortingMode,
        endActionsWidth = endActionsWidth,
        cardWidth = cardWidth,
        viewTouchSlop = viewTouchSlop,
        swipeDeleteLeft = swipeDeleteLeft,
        overlayBoundsRelativeToSurface = overlayBoundsRelativeToSurface,
        horizontalOffsetX = horizontalOffsetX,
        onDismiss = { drawerViewModel.actOnNoti(notiKey, "dismiss_swipe") },
        scope = coroutineScope,
        onSwipeActiveChanged = { isSwipeActive = it },
    )

    val scaleValue by animateFloatAsState(if (isDragging) 1.02f else 1f)
    val isDarkTheme = isSystemInDarkTheme()

    val rimColor = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val surfaceBorderModifier = Modifier.border(1.dp, rimColor, shape = MaterialTheme.shapes.large)
    val targetLift = 0.dp
    val lift by animateDpAsState(targetLift, label = "lift")

    Box(
        modifier = Modifier
            .padding(vertical = 1.dp, horizontal = if (isInGroup) 5.dp else 20.dp)
            .graphicsLayer {
                scaleX = scaleValue
                scaleY = scaleValue
            }
            .onSizeChanged {
                // Keep capturing this for future tuning/thresholds; currently only used implicitly
                // by the swipe handler via `cardWidth` state updates.
                cardWidth = it.width.toFloat()
            }
            .then(if (isSortingMode) Modifier else swipeModifier)
            .clip(MaterialTheme.shapes.large)
            .onGloballyPositioned { coordinates ->
                if (!isRead && parentViewport != null) {
                    val cardBounds = coordinates.boundsInWindow()
                    val tolerance = 1f
                    val isTopVisible = cardBounds.top >= (parentViewport.top - tolerance)
                    val isBottomVisible = cardBounds.bottom <= (parentViewport.bottom + tolerance)
                    if (isTopVisible && isBottomVisible) {
                        drawerViewModel.markNotificationAsRead(notiKey)
                    }
                }
            },
    ) {
        // Background Actions
        NotiCardBackgroundActions(
            modifier = Modifier
                .align(if (swipeDeleteLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .zIndex(0f),
            endActionsWidthPx = endActionsWidth,
            horizontalOffsetX = horizontalOffsetX.value,
            swipeDeleteLeft = swipeDeleteLeft,
            isInGroup = isInGroup,
            notiUnit = notiUnit,
            drawerViewModel = drawerViewModel,
            onCollapseActions = {
                coroutineScope.launch {
                    horizontalOffsetX.animateTo(
                        0f,
                        tween(200)
                    )
                }
            },
            onMeasuredEndActionsWidthPx = { measured ->
                endActionsWidth = measured
            },
            actionsEnabled = !isSwipeActive,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .graphicsLayer {
                    translationX = horizontalOffsetX.value
                    translationY = lift.toPx()
                    scaleX = scaleValue
                    scaleY = scaleValue
                    alpha = 1f
                }
                .border(width = borderWidth, color = borderColor, shape = MaterialTheme.shapes.large)
                .combinedClickable(
                    onClick = {
                        val contentIntent = NotiListenerService.getContentIntent(context, notiUnit)
                        NotificationLauncher.launchPendingIntentOrFallback(
                            context = context,
                            pendingIntent = contentIntent,
                            packageName = notiUnit.metadata.pkgName,
                            logTag = "AccessNotification",
                        )
                        NotiListenerService.removeIntents(notiUnit.notiKey)
                        drawerViewModel.actOnNoti(notiKey, "access_click_dismiss")
                    },
                    onLongClick = {
                        if (!isSortingMode) showOptionsDialog = true
                    },
                )
                .onGloballyPositioned { coords -> surfaceBoundsInWindow = coords.boundsInWindow() }
                .then(surfaceBorderModifier),
            shape = MaterialTheme.shapes.large,
            shadowElevation = 0.dp,
            color = backgroundColor,
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.fillMaxWidth()) {

                    // Header layer: main content + overlay buttons stacked.
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NotiCardHeaderContent(
                                modifier = Modifier.weight(1f),
                                notiDisplayUnit = notiDisplayUnit,
                                notiOverallTitle = notiOverallTitle,
                                notiSecondOverallTitle = notiSecondOverallTitle,
                                hasSecondTitle = hasSecondTitle,
                                showSummary = showSummary(),
                                requiresExpansionSetter = { requiresExpansion = requiresExpansion || it },
                                collapseThreshold = collapseThreshold,
                                isExpandedOffset = anchored.offset,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        // Overlay buttons on top-right of the header.
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .fillMaxHeight()
                                .zIndex(2f),
                        ) {
                            org.muilab.notigpt.ui.component.notification.card.noticard.elements.NotiCardOverlayButtons(
                                translationX = 0f,
                                requiresExpansion = requiresExpansion,
                                progress = (
                                    anchored.offset.coerceIn(0f, maxContentHeightPxState.floatValue) /
                                        max(1f, maxContentHeightPxState.floatValue)
                                    ),
                                isSortingMode = isSortingMode,
                                isPinned = isPinned,
                                anchored = anchored,
                                anchoredFlingBehavior = anchoredFlingBehavior,
                                onUpdateMeasuredAnchors = {
                                    // update anchors based on measured
                                    // handled inside NotiCardExpandedRecords effect; keep no-op
                                },
                                notiKey = notiKey,
                                drawerViewModel = drawerViewModel,
                                onOverlayBoundsChange = { overlayWindow ->
                                    if (isSwipeActive) return@NotiCardOverlayButtons

                                    val surfaceWindow = surfaceBoundsInWindow
                                    if (overlayWindow != null && surfaceWindow != null) {
                                        overlayBoundsRelativeToSurface = Rect(
                                            overlayWindow.left - surfaceWindow.left,
                                            overlayWindow.top - surfaceWindow.top,
                                            overlayWindow.right - surfaceWindow.left,
                                            overlayWindow.bottom - surfaceWindow.top,
                                        )
                                    }
                                },
                                reorderEnabled = reorderEnabled,
                                reorderScope = reorderScope,
                                onStartReorderDrag = onStartReorderDrag,
                                onReorderDrag = onReorderDrag,
                                onStopReorderDrag = onStopReorderDrag,
                            )
                        }
                    }

                    // Expanded records below the header.
                    NotiCardExpandedRecords(
                        notiKey = notiKey,
                        isPeople = isPeople,
                        requiresExpansion = requiresExpansion,
                        anchored = anchored,
                        maxHeightDp = maxHeightDp,
                        sampleLimit = sampleLimit,
                        measuredContentHeightPx = measuredContentHeightPx,
                        maxContentHeightPxState = maxContentHeightPxState,
                        drawerViewModel = drawerViewModel,
                        onRecordsViewport = { recordsViewport = it },
                    )
                }
            }
        }
    }

    // Ensure overlay bounds state isn't optimized away by tooling; it's used by the swipe handler.
    @Suppress("UNUSED_VARIABLE")
    val _keepOverlayBoundsState = overlayBoundsRelativeToSurface

    NotiCardOptionsDialog(
        show = showOptionsDialog,
        onDismiss = { showOptionsDialog = false },
        drawerViewModel = drawerViewModel,
        notiKey = notiKey,
        state = NotiCardOptionsState(
            isInGroup = isInGroup,
            isPinned = isPinned,
        ),
    )
}
