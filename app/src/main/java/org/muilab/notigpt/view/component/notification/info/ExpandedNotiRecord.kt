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
import androidx.compose.ui.geometry.Rect
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
    isCardVisible: Boolean, // Is the parent NotiCard fully visible?
    recordsViewport: Rect?, // What is the visible area of the records list?
    onRecordRead: () -> Unit
) {

    var isFullyVisible by remember { mutableStateOf(false) }

    // Add local state to prevent the callback from firing multiple times
    var hasBeenTriggered by remember { mutableStateOf(false) }

    Column(
        Modifier
            // Use a single onGloballyPositioned for efficiency
            .onGloballyPositioned { coordinates ->
                if (recordsViewport != null) {

                    // If the size of coordinates and viewport is not zero, we can proceed
                    if (coordinates.size.width == 0 || coordinates.size.height == 0 ||
                        recordsViewport.width == 0f || recordsViewport.height == 0f) {
                        isFullyVisible = false
                        return@onGloballyPositioned
                    }

                    val recordBounds = coordinates.boundsInWindow()

                    // --- VVV THE FIX VVV ---
                    val tolerance = 1.0f // A 1px tolerance for floating point inaccuracies

                    // Check if the record's top is at or below the viewport's top (with tolerance)
                    val topIsVisible = recordBounds.top >= recordsViewport.top - tolerance

                    // Check if the record's bottom is at or above the viewport's bottom (with tolerance)
                    val bottomIsVisible = recordBounds.bottom <= recordsViewport.bottom + tolerance

                    // Check if the horizontal bounds are also within the viewport
                    val leftIsVisible = recordBounds.left >= recordsViewport.left - tolerance
                    val rightIsVisible = recordBounds.right <= recordsViewport.right + tolerance

                    val newVisibility = topIsVisible && bottomIsVisible && leftIsVisible && rightIsVisible
                    // --- ^^^ THE FIX ^^^ ---

                    if (newVisibility != isFullyVisible) {
                        isFullyVisible = newVisibility
                        // Optional: Add a log here to see the result of the new check
                        // Log.d("VisibilityCheck", "Record: $notiTitle, isVisible: $newVisibility, TopOK: $topIsVisible, BottomOK: $bottomIsVisible")
                    }
                } else {
                    if (isFullyVisible) isFullyVisible = false
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

    // --- MODIFIED: The effect that triggers the read callback ---
    LaunchedEffect(isCardVisible, isFullyVisible, notiSeen) {
        // Log.d("VisibilityCheck", "Record: $notiTitle | CardVisible: $isCardVisible, RecordFullyVisible: $isFullyVisible, Seen: $notiSeen")
        if (isCardVisible && isFullyVisible && !notiSeen && !hasBeenTriggered) {
            Log.d("CallbackFire", "Record: '$notiTitle' -> Firing onRecordRead()!")
            onRecordRead()
            hasBeenTriggered = true // Prevent multiple triggers
        }
    }
}