package org.muilab.notigpt.ui.component.notification.card.noticard.elements

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.ui.component.notification.action.NotiActionIconButton
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.util.Constants
import kotlin.math.abs
import kotlin.math.max

private const val NOTI_SWIPE_TAG = "NotiSwipe"

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NotiCardBackgroundActions(
    modifier: Modifier = Modifier,
    endActionsWidthPx: Float,
    horizontalOffsetX: Float,
    swipeDeleteLeft: Boolean,
    isInGroup: Boolean,
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
                "Hide Actions",
                {
                    if (canClickActions.value) onCollapseActions()
                },
            )

            if (isInGroup) {
                NotiActionIconButton(
                    R.drawable.leave_group,
                    "Remove from Group",
                    {
                        if (canClickActions.value) {
                            drawerViewModel.removeFromGroup(notiUnit.notiKey)
                            onCollapseActions()
                        }
                    },
                )
            } else {
                val isTask = notiUnit.category == Constants.NOTI_CATEGORY_MAKETASK
                val isSave = notiUnit.category == Constants.NOTI_CATEGORY_SAVE
                val isArchive = notiUnit.category == Constants.NOTI_CATEGORY_ARCHIVE

                NotiActionIconButton(
                    if (isTask) R.drawable.task_yes else R.drawable.task_no,
                    "Make-Task",
                    {
                        if (canClickActions.value) {
                            drawerViewModel.actOnNoti(
                                notiUnit.notiKey,
                                if (isTask) "dismiss_task" else "make_task",
                            )
                            onCollapseActions()
                        }
                    },
                )

                NotiActionIconButton(
                    if (isSave) R.drawable.save_yes else R.drawable.save_no,
                    "Save",
                    {
                        if (canClickActions.value) {
                            drawerViewModel.actOnNoti(
                                notiUnit.notiKey,
                                if (isSave) "unsave" else "save",
                            )
                            onCollapseActions()
                        }
                    },
                )

                NotiActionIconButton(
                    if (isArchive) R.drawable.archive_yes else R.drawable.archive_no,
                    "Archive",
                    {
                        if (canClickActions.value) {
                            drawerViewModel.actOnNoti(
                                notiUnit.notiKey,
                                if (isArchive) "unarchive" else "archive",
                            )
                            onCollapseActions()
                        }
                    },
                )

                NotiActionIconButton(
                    R.drawable.totop,
                    "To Top",
                    {
                        if (canClickActions.value) {
                            drawerViewModel.actOnNoti(notiUnit.notiKey, "to_top")
                            onCollapseActions()
                        }
                    },
                )
            }
        }
    }
}
