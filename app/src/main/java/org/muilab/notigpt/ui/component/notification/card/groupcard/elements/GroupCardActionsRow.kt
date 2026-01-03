package org.muilab.notigpt.ui.component.notification.card.groupcard.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.component.notification.action.NotiActionIconButton
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_SAVE
import kotlin.math.max

@Composable
internal fun GroupCardActionsRow(
    modifier: Modifier,
    swipeDeleteLeft: Boolean,
    endActionsWidthPx: Float,
    horizontalOffsetX: Float,
    representativeCategory: String,
    isAnyChildTopped: Boolean,
    onHideActions: () -> Unit,
    onGroupTopToggle: () -> Unit,
    onMakeTaskToggle: () -> Unit,
    onSaveToggle: () -> Unit,
    onArchiveToggle: () -> Unit,
) {
    Row(
        modifier = modifier
            .wrapContentWidth(align = Alignment.CenterHorizontally)
            .fillMaxHeight()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                translationX = 0f
                val safeWidth = max(1f, endActionsWidthPx)
                val t = if (swipeDeleteLeft) (horizontalOffsetX / safeWidth) else (-horizontalOffsetX / safeWidth)
                alpha = t.coerceIn(0f, 1f)
            }
            .zIndex(0f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NotiActionIconButton(R.drawable.close, "Hide Actions", { onHideActions() })

        NotiActionIconButton(
            if (isAnyChildTopped) R.drawable.undo_totop else R.drawable.totop,
            if (isAnyChildTopped) "Undo Group Top" else "Group To Top",
            { onGroupTopToggle() }
        )

        NotiActionIconButton(
            if (representativeCategory == NOTI_CATEGORY_MAKETASK) R.drawable.task_yes else R.drawable.task_no,
            "Make-Task Group",
            { onMakeTaskToggle() }
        )

        NotiActionIconButton(
            if (representativeCategory == NOTI_CATEGORY_SAVE) R.drawable.save_yes else R.drawable.save_no,
            "Save Group",
            { onSaveToggle() }
        )

        NotiActionIconButton(
            if (representativeCategory == NOTI_CATEGORY_ARCHIVE) R.drawable.archive_yes else R.drawable.archive_no,
            "Archive Group",
            { onArchiveToggle() }
        )
    }
}
