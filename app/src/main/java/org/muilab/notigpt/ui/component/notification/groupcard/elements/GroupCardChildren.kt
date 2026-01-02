package org.muilab.notigpt.ui.component.notification.groupcard.elements

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.ui.component.notification.noticard.NotiCard
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
internal fun GroupCardChildren(
    context: Context,
    itemsToShow: List<NotiDisplayUnit>,
    drawerViewModel: DrawerViewModel,
    isMergeTarget: Boolean,
    parentViewport: Rect?,
    containerColor: Color,
    enableChildSwipe: Boolean,
    onBoundsInParent: (Rect) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, RoundedCornerShape(12.dp))
            .padding(8.dp)
            .onGloballyPositioned { coords ->
                onBoundsInParent(coords.boundsInParent())
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsToShow.forEach { unit ->
            NotiCard(
                context = context,
                notiDisplayUnit = unit,
                isDragging = false,
                drawerViewModel = drawerViewModel,
                isCardVisible = true,
                parentViewport = parentViewport,
                category = unit.category,
                appCategory = unit.appCategory,
                isMergeTarget = isMergeTarget,
                isInGroup = true,
                swipeEnabled = enableChildSwipe,
            )
        }
    }
}
