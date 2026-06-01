package org.muilab.notigpt.ui.component.notification.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension

/**
 * Compact row for one expanded notification record inside a notification card.
 *
 * Keep this as composition over title, content, and time subcomponents. If record rows gain actions or loading
 * state, move that behavior to a dedicated context-card component.
 */
@Composable
fun ExpandedNotiRecord(
    notiTitle: String,
    notiTime: Long,
    notiContent: String,
    showTitle: Boolean
) {
    Column(Modifier.fillMaxWidth()) {
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
            NotiInfoTime(notiTime, rightTextModifier)
        }
        if (showTitle)
            NotiInfoContent(notiContent)
    }
}