package org.muilab.notigpt.ui.notification.component.card.noticard

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.ui.notification.component.card.noticard.elements.NOTI_CARD_COLLAPSE_THRESHOLD_PX_DEFAULT
import org.muilab.notigpt.ui.notification.component.card.noticard.elements.NotiCardBackgroundActions
import org.muilab.notigpt.ui.notification.component.card.noticard.elements.NotiCardExpandedRecords
import org.muilab.notigpt.ui.notification.component.card.noticard.elements.NotiCardHeaderContent
import org.muilab.notigpt.ui.notification.component.card.noticard.elements.NotiCardOptionsDialog
import org.muilab.notigpt.ui.notification.component.card.noticard.elements.NotiCardOptionsState
import org.muilab.notigpt.ui.notification.component.card.noticard.elements.notiCardExpansionFlingBehavior
import org.muilab.notigpt.ui.notification.component.card.noticard.elements.notiCardSwipeHandler
import org.muilab.notigpt.ui.notification.component.card.noticard.elements.rememberNotiCardExpansionState
import org.muilab.notigpt.ui.notification.state.NotiExpandState
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.util.SharedPreferencesManager
import sh.calvin.reorderable.ReorderableCollectionItemScope
import kotlin.math.max

/**
 * Renders one live drawer notification and exposes all user actions as callbacks.
 *
 * This component assembles header, actions, expansion, swipe, and overlay controls. The card may manage
 * gesture/animation state, but durable changes should stay outside the composable.
 */
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
    swipeEnabled: Boolean = true,
    // Reorder: handle-only (long-press) callbacks. No-op defaults keep API stable for other call sites.
    reorderEnabled: Boolean = false,
    // Optional when rendered inside ReorderableItem; enables native handle dragging.
    reorderScope: ReorderableCollectionItemScope? = null,
    onStartReorderDrag: (Offset) -> Unit = {},
    onReorderDrag: (Offset) -> Unit = {},
    onStopReorderDrag: () -> Unit = {},
    onCreateReminder: ((String, List<NotiRecord>) -> Unit)? = null,
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
    val notiRecords = remember(notiDisplayUnit.notiRecords) { notiDisplayUnit.notiRecords.sortedBy { it.time } }
    val notiKey = notiUnit.notiKey

    val isPinned = notiUnit.isPinned

    val summary = notiUnit.summary
    val hasSummary = summary.isNotEmpty()

    val backgroundColor = MaterialTheme.colorScheme.surfaceBright

    // A single hairline; a pinned/sorted card is marked by a primary-tinted 1.5dp edge instead of a
    // heavy outline. (Previously an outline border plus a second rim border stacked on the card.)
    val isHighlighted = notiUnit.sortPosition != -1
    val borderColor = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isHighlighted) 1.5.dp else 1.dp

    // Expand state
    val maxHeightDp = 200.dp
    val sampleLimit = 8
    val density = LocalDensity.current

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

    val firstRecord = notiRecords.firstOrNull()
    val lastRecord = notiRecords.lastOrNull()
    val titleRecord by remember(anchored, firstRecord, lastRecord, collapseThreshold) {
        derivedStateOf {
            val offset = anchored.offset
            if (!offset.isNaN() && offset < collapseThreshold) {
                lastRecord
            } else {
                firstRecord
            }
        }
    }
    // Capture the delegated value into a local so it can be smart-cast in the when-branches below.
    val tr = titleRecord
    val notiOverallTitle = when {
        tr != null && tr.extraConversationTitle != "null" -> tr.extraConversationTitle
        notiDisplayUnit.title != "null" -> notiDisplayUnit.title
        tr != null && tr.extraSubText != "null" -> tr.extraSubText
        else -> ""
    }
    val notiSecondOverallTitle = when {
        tr != null && tr.extraConversationTitle != "null" && notiDisplayUnit.title != "null" -> notiDisplayUnit.title
        tr != null && tr.extraConversationTitle == "null" && notiDisplayUnit.title != "null" && tr.extraSubText != "null" -> tr.extraSubText
        tr != null && tr.extraConversationTitle == "null" && notiDisplayUnit.title != "null" -> ""
        else -> ""
    }

    val hasSecondTitle = notiSecondOverallTitle.isNotBlank() && notiSecondOverallTitle != notiOverallTitle
    val isPeople = notiUnit.isPeople

    var requiresExpansion by remember(notiRecords, summary, notiOverallTitle, isPeople) {
        mutableStateOf(
            notiRecords.size > 1 || hasSummary ||
                    (notiRecords.size == 1 && notiRecords[0].getDisplayedTitle(isPeople)
                        .let { notiOverallTitle.isNotBlank() && notiOverallTitle != it })
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val horizontalOffsetX = remember { Animatable(0f) }
    var endActionsWidth by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableFloatStateOf(0f) }

    var surfaceBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    var excludedBoundsRelativeToSurface by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var isSwipeActive by remember { mutableStateOf(false) }

    val swipeModifier = Modifier.notiCardSwipeHandler(
        enabled = swipeEnabled && !isDragging && !isSortingMode,
        endActionsWidth = endActionsWidth,
        cardWidth = cardWidth,
        swipeDeleteLeft = swipeDeleteLeft,
        excludedBounds = excludedBoundsRelativeToSurface,
        horizontalOffsetX = horizontalOffsetX,
        onDismiss = {
            org.muilab.notigpt.ui.common.feedback.Haptics.commit(haptic)
            drawerViewModel.archiveNewNotificationCard(notiKey)
        },
        scope = coroutineScope,
        onSwipeActiveChanged = { isSwipeActive = it },
    )

    val scaleValue by animateFloatAsState(if (isDragging) 1.02f else 1f)

    val targetLift = 0.dp
    val lift by animateDpAsState(targetLift, label = "lift")

    // TalkBack custom actions mirror every gesture affordance (swipe/drag/long-press) so the card is
    // fully operable without them. Labels resolved here since the semantics block is not composable.
    val a11yArchive = stringResource(R.string.a11y_archive)
    val a11yPin = stringResource(R.string.ui_noti_action_pin)
    val a11yUnpin = stringResource(R.string.ui_noti_action_unpin)
    val a11yExpand = stringResource(R.string.a11y_expand)
    val a11yCollapse = stringResource(R.string.a11y_collapse)
    val a11yExtract = stringResource(R.string.ui_action_extract_reminder)
    val a11yCreateReminder = stringResource(R.string.ui_reminders_create_button)
    val cardActions = buildList {
        add(androidx.compose.ui.semantics.CustomAccessibilityAction(a11yArchive) {
            org.muilab.notigpt.ui.common.feedback.Haptics.commit(haptic)
            drawerViewModel.archiveNewNotificationCard(notiKey); true
        })
        add(androidx.compose.ui.semantics.CustomAccessibilityAction(if (isPinned) a11yUnpin else a11yPin) {
            drawerViewModel.actOnNoti(notiKey, if (isPinned) "unpin" else "pin"); true
        })
        if (requiresExpansion) add(
            androidx.compose.ui.semantics.CustomAccessibilityAction(
                if (anchored.offset < collapseThreshold) a11yExpand else a11yCollapse
            ) {
                coroutineScope.launch {
                    if (anchored.offset < 20f) anchored.animateTo(NotiExpandState.Opened)
                    else anchored.animateTo(NotiExpandState.Collapsed)
                }; true
            }
        )
        add(androidx.compose.ui.semantics.CustomAccessibilityAction(a11yExtract) {
            drawerViewModel.actOnNoti(notiKey, "extract_reminder"); true
        })
        onCreateReminder?.let { cb ->
            add(androidx.compose.ui.semantics.CustomAccessibilityAction(a11yCreateReminder) {
                cb(notiOverallTitle, notiRecords); true
            })
        }
    }

    Box(
        modifier = Modifier
            .padding(vertical = 3.dp, horizontal = org.muilab.notigpt.ui.theme.Dimens.screenH)
            .graphicsLayer {
                scaleX = scaleValue
                scaleY = scaleValue
            }
            .onSizeChanged {
                // Keep capturing this for future tuning/thresholds; currently only used implicitly
                // by the swipe handler via `cardWidth` state updates.
                cardWidth = it.width.toFloat()
            }
            .clip(MaterialTheme.shapes.large),
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
                        drawerViewModel.accessAndDismissNotification(notiUnit)
                    },
                    onLongClick = {
                        if (!isSortingMode) {
                            org.muilab.notigpt.ui.common.feedback.Haptics.longPress(haptic)
                            showOptionsDialog = true
                        }
                    },
                )
                // Swipe handler lives at the Surface level so the whole card is swipeable (except the
                // expand/pin buttons, which report their bounds via excludedBounds). down.position is
                // then in the same coordinate space as surfaceBoundsInWindow.
                .then(if (isSortingMode) Modifier else swipeModifier)
                .onGloballyPositioned { coords -> surfaceBoundsInWindow = coords.boundsInWindow() }
                .semantics { customActions = cardActions },
            shape = MaterialTheme.shapes.large,
            shadowElevation = 0.dp,
            color = backgroundColor,
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Column(Modifier.fillMaxWidth()) {

                    // Header layer: main content + overlay buttons stacked.
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NotiCardHeaderContent(
                                modifier = Modifier.weight(1f),
                                notiDisplayUnit = NotiDisplayUnit(notiUnit, notiRecords),
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
                            org.muilab.notigpt.ui.notification.component.card.noticard.elements.NotiCardOverlayButtons(
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
                                onExcludedBoundsChange = { windowRects ->
                                    if (isSwipeActive) return@NotiCardOverlayButtons

                                    val surfaceWindow = surfaceBoundsInWindow ?: return@NotiCardOverlayButtons
                                    excludedBoundsRelativeToSurface = windowRects.map { r ->
                                        Rect(
                                            r.left - surfaceWindow.left,
                                            r.top - surfaceWindow.top,
                                            r.right - surfaceWindow.left,
                                            r.bottom - surfaceWindow.top,
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

    NotiCardOptionsDialog(
        show = showOptionsDialog,
        onDismiss = { showOptionsDialog = false },
        drawerViewModel = drawerViewModel,
        notiKey = notiKey,
        state = NotiCardOptionsState(
            isPinned = isPinned,
        ),
        onCreateReminder = onCreateReminder?.let { callback ->
            { callback(notiOverallTitle, notiRecords) }
        },
    )
}
