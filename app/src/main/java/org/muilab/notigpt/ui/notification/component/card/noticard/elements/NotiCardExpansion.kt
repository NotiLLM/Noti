package org.muilab.notigpt.ui.notification.component.card.noticard.elements

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.ui.notification.component.info.ExpandedNotiRecord
import org.muilab.notigpt.ui.notification.state.NotiExpandState
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import kotlin.math.abs
import kotlin.math.max

const val NOTI_CARD_COLLAPSE_THRESHOLD_PX_DEFAULT = 20f

/**
 * Expansion-state helpers for NotiCard's contextual record list.
 *
 * Keep scroll/fling behavior here while data loading stays in the screen or ViewModel. If expansion rules become
 * shared across cards, this is the right place to consolidate them.
 */
@Composable
fun rememberNotiCardExpansionState(
    initialEstimatePx: Float,
): AnchoredDraggableState<NotiExpandState> {
    return remember {
        AnchoredDraggableState(
            initialValue = NotiExpandState.Collapsed,
            anchors = DraggableAnchors {
                NotiExpandState.Collapsed at 0f
                NotiExpandState.Opened at initialEstimatePx
            },
        )
    }
}

@Composable
fun notiCardExpansionFlingBehavior(
    anchored: AnchoredDraggableState<NotiExpandState>,
): FlingBehavior {
    return AnchoredDraggableDefaults.flingBehavior(
        anchored,
        { distance: Float -> distance * 0.5f },
        spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
    )
}

@Composable
fun NotiCardExpandedRecords(
    notiKey: String,
    isPeople: Boolean,
    requiresExpansion: Boolean,
    anchored: AnchoredDraggableState<NotiExpandState>,
    maxHeightDp: Dp,
    sampleLimit: Int,
    measuredContentHeightPx: MutableFloatState,
    maxContentHeightPxState: MutableFloatState,
    drawerViewModel: DrawerViewModel,
    onRecordsViewport: (Rect?) -> Unit,
) {
    if (!requiresExpansion) return

    LaunchedEffect(anchored.currentValue, notiKey) {
        if (anchored.currentValue == NotiExpandState.Opened) {
            drawerViewModel.loadFullRecordsForKey(notiKey)
        }
    }

    val fullRecordsFlow = drawerViewModel.getFullRecordsFlow(notiKey)
    val fullRecords by fullRecordsFlow.collectAsState()

    val showingRecords: List<NotiRecord> = remember(fullRecords) { fullRecords.sortedBy { it.time } }

    val density = LocalDensity.current

    // measure sample height
    val sampleCount = minOf(sampleLimit, showingRecords.size)
    SubcomposeLayout(modifier = Modifier) { constraints ->
        val measuringConstraints = constraints.copy(maxHeight = Int.MAX_VALUE)
        val toMeasure = showingRecords.take(sampleCount)
        val overallTitle = showingRecords.lastOrNull()?.getDisplayedTitle(isPeople).orEmpty()
        val measPlaceables = subcompose("measurer") {
            Column {
                toMeasure.forEachIndexed { index, rec ->
                    val currentTitle = rec.getDisplayedTitle(isPeople)
                    val prevTitle = if (index > 0) toMeasure[index - 1].getDisplayedTitle(isPeople) else null
                    val showTitle = when {
                        index == 0 -> currentTitle.isNotBlank() && currentTitle != overallTitle
                        else -> currentTitle.isNotBlank() && currentTitle != prevTitle
                    }
                    ExpandedNotiRecord(currentTitle, rec.time, rec.content, showTitle)
                }
            }
        }.map { it.measure(measuringConstraints) }

        val sampleTotalHeight = measPlaceables.sumOf { it.height }
        measuredContentHeightPx.floatValue = sampleTotalHeight.toFloat()
        layout(constraints.maxWidth, 0) { }
    }

    LaunchedEffect(measuredContentHeightPx.floatValue, showingRecords.size) {
        val maxHeightPx = with(density) { maxHeightDp.toPx() }
        val samplePx = if (abs(measuredContentHeightPx.floatValue) > 0.001f) {
            measuredContentHeightPx.floatValue
        } else {
            with(density) { (56.dp * sampleCount).toPx() }
        }

        val finalPx = if (showingRecords.size <= sampleCount) {
            minOf(samplePx, maxHeightPx)
        } else {
            minOf((samplePx / max(1, sampleCount)) * showingRecords.size, maxHeightPx)
        }

        maxContentHeightPxState.floatValue = finalPx
        anchored.updateAnchors(
            DraggableAnchors {
                NotiExpandState.Collapsed at 0f
                NotiExpandState.Opened at maxContentHeightPxState.floatValue
            },
        )
    }

    val currentHeightPx = anchored.offset.coerceIn(0f, maxContentHeightPxState.floatValue)
    val listState = rememberLazyListState()

    LaunchedEffect(anchored.currentValue, showingRecords.size) {
        if (anchored.currentValue == NotiExpandState.Opened && showingRecords.isNotEmpty()) {
            listState.scrollToItem(max(0, showingRecords.lastIndex))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .height(with(density) { currentHeightPx.toDp() })
            .onGloballyPositioned { onRecordsViewport(it.boundsInWindow()) },
    ) {
        item {
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (fullRecords.isEmpty() && anchored.currentValue == NotiExpandState.Opened) {
            item {
                CircularProgressIndicator(Modifier.padding(8.dp).size(24.dp))
            }
        }

        val overallTitle = showingRecords.lastOrNull()?.getDisplayedTitle(isPeople).orEmpty()

        itemsIndexed(showingRecords, key = { _, it -> it.notiRecordId }) { index, notiRecord ->
            val currentTitle = notiRecord.getDisplayedTitle(isPeople)
            val prevTitle = if (index > 0) showingRecords[index - 1].getDisplayedTitle(isPeople) else null
            val showTitle = when {
                index == 0 -> currentTitle.isNotBlank() && currentTitle != overallTitle
                else -> currentTitle.isNotBlank() && currentTitle != prevTitle
            }

            ExpandedNotiRecord(
                currentTitle,
                notiRecord.time,
                notiRecord.content,
                showTitle,
            )
        }
    }
}
