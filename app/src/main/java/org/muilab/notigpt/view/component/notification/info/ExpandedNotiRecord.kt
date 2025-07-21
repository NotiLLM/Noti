package org.muilab.notigpt.view.component.notification.info

import android.util.Log
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension

@Composable
fun ExpandedNotiRecord(
    // Parameters for displaying the UI
    notiTitle: String,
    notiTime: Long,
    notiContent: String,
    showTitle: Boolean,
    infoTimeColor: Color,
    // Parameters for the logic
    notiSeen: Boolean,
    onRecordRead: () -> Unit // REPLACES viewedInfos: MutableSet<String>
) {

    var infoTopViewed by remember { mutableStateOf(false) }
    var infoBottomViewed by remember { mutableStateOf(false) }
    // Add local state to prevent the callback from firing multiple times
    var hasBeenTriggered by remember { mutableStateOf(false) }

    val screenHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    Column(
        Modifier
            // Use a single onGloballyPositioned for efficiency
            .onGloballyPositioned { coordinates ->
                val windowBounds = coordinates.boundsInWindow()
                val top = windowBounds.top
                val bottom = windowBounds.bottom

                // --- DEBUG LOG ---
                // Log the raw values to see what we're working with.
                // Log.d("VisibilityCheck", "Record ID: $notiRecordId | Top: $top, Bottom: $bottom, ScreenHeight: $screenHeightPx")

                // Check and set top visibility flag
                if (!infoTopViewed && top >= 0 && top < screenHeightPx) {
                    infoTopViewed = true
//                    Log.d("VisibilitySet", "Record ID: $notiTitle -> infoTopViewed = TRUE")
                }
                // Check and set bottom visibility flag
                if (!infoBottomViewed && bottom > 0 && bottom <= screenHeightPx) {
                    infoBottomViewed = true
//                    Log.d("VisibilitySet", "Record ID: $notiTitle -> infoBottomViewed = TRUE")
                }
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

    LaunchedEffect(infoTopViewed, infoBottomViewed, notiSeen) {
//        Log.d("EffectCheck", "Record ID: $notiTitle | Effect Running | TopSeen: $infoTopViewed, BottomSeen: $infoBottomViewed, AlreadySeen: $notiSeen, Triggered: $hasBeenTriggered")
        if (infoTopViewed && infoBottomViewed && !notiSeen && !hasBeenTriggered) {
//            Log.d("CallbackFire", "Record ID: $notiTitle -> Firing onRecordRead()!")
            onRecordRead()
            hasBeenTriggered = true
        }
    }
}