package org.muilab.notigpt.view.component.notification.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension

@Composable
fun ExpandedNotiInfo(
    notiTitle: String,
    notiTime: Long,
    notiContent: String,
    notiSeen: Boolean,
    showTitle: Boolean,
    infoTimeColor: Color,
    viewedInfos: MutableSet<Long>
) {

    var infoTopViewed by remember { mutableStateOf(false) }
    var infoBottomViewed by remember { mutableStateOf(false) }

    Column(
        Modifier
            .onGloballyPositioned { coordinates ->
                val windowBounds = coordinates.boundsInWindow()
                infoTopViewed =
                    infoTopViewed || windowBounds.top >= 0 && windowBounds.top < windowBounds.height
            }
            .onGloballyPositioned { coordinates ->
                val windowBounds = coordinates.boundsInWindow()
                infoBottomViewed =
                    infoBottomViewed || windowBounds.bottom > 0 && windowBounds.bottom <= windowBounds.height
            }
    ) {
        ConstraintLayout (Modifier.fillMaxWidth()) {
            val (leftText, rightText) = createRefs()
            val leftTextModifier = Modifier
                .constrainAs(leftText) {
                    start.linkTo(parent.start)
                    end.linkTo(rightText.start)
                    top.linkTo(parent.top)
                    width = Dimension.fillToConstraints
                }
            val rightTextModifier = Modifier
                .constrainAs(rightText) {
                    end.linkTo(parent.end)
                    top.linkTo(parent.top)
                }
            if (showTitle)
                NotiInfoTitle(notiTitle, leftTextModifier)
            else
                NotiInfoContent(notiContent, leftTextModifier)
            NotiInfoTime(notiTime, infoTimeColor, rightTextModifier)
        }
        if (showTitle)
            NotiInfoContent(notiContent)
    }

    LaunchedEffect(infoTopViewed, infoBottomViewed) {
        if (infoTopViewed && infoBottomViewed && !notiSeen) {
            viewedInfos.add(notiTime)  // Mark the item as viewed once fully revealed
        }
    }
}