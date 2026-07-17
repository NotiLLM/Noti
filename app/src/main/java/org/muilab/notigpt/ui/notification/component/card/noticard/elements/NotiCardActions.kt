package org.muilab.notigpt.ui.notification.component.card.noticard.elements

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.ui.notification.component.action.NotiActionIconButton
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import kotlin.math.max

private const val NOTI_SWIPE_TAG = "NotiSwipe"

/**
 * Background and foreground action rows used by NotiCard gestures.
 *
 * Keep layout and button composition here. Action semantics should remain in callbacks so swipe/tap UI does not
 * duplicate repository or Android-notification behavior.
 */
@Composable
fun NotiCardBackgroundActions(
    modifier: Modifier = Modifier,
    endActionsWidthPx: Float,
    horizontalOffsetX: Float,
    swipeDeleteLeft: Boolean,
    notiUnit: NotiUnit,
    drawerViewModel: DrawerViewModel,
    onCollapseActions: () -> Unit,
    onMeasuredEndActionsWidthPx: (Float) -> Unit,
    actionsEnabled: Boolean = true,
) {
    val canClickActions = rememberUpdatedState(actionsEnabled)
    Box(
        modifier = modifier
            .wrapContentWidth(Alignment.CenterHorizontally)
            .onSizeChanged {
                val w = it.width.toFloat()
                Log.d(NOTI_SWIPE_TAG, "actions measured full width=$w")
                onMeasuredEndActionsWidthPx(w)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    val safeWidth = max(1f, endActionsWidthPx)
                    val t = if (swipeDeleteLeft) {
                        (horizontalOffsetX / safeWidth).coerceIn(0f, 1f)
                    } else {
                        ((-horizontalOffsetX) / safeWidth).coerceIn(0f, 1f)
                    }
                    alpha = t * t
                }
                .zIndex(0f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NotiActionIconButton(
                R.drawable.close,
                stringResource(R.string.ui_noti_action_hide_actions),
                {
                    if (canClickActions.value) onCollapseActions()
                },
            )

            NotiActionIconButton(
                R.drawable.task,
                stringResource(R.string.ui_action_extract_saved_item),
                {
                    if (canClickActions.value) {
                        drawerViewModel.actOnNoti(notiUnit.notiKey, "extract_reminder")
                        onCollapseActions()
                    }
                },
            )
        }
    }
}
