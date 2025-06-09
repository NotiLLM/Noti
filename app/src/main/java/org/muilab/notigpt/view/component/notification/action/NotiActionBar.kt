package org.muilab.notigpt.view.component.notification.action

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.viewModel.DrawerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NotiActionBar(notiUnit: NotiUnit, drawerViewModel: DrawerViewModel) {

    val isPinned = notiUnit.isPinned
    val notiKey = notiUnit.notiKey

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NotiActionIconButton(
            iconRes = if (isPinned) R.drawable.pin_yes else R.drawable.pin_no,
            contentDescription = "Pin",
            onClick = { if (isPinned) drawerViewModel.actOnNoti(notiKey, "unpin") else drawerViewModel.actOnNoti(notiKey, "pin") }
        )

        NotiActionIconButton(
            iconRes = if (notiUnit.category == NOTI_CATEGORY_ARCHIVE) R.drawable.archive_yes else R.drawable.archive_no,
            contentDescription = "Archive",
            onClick = {
                if (notiUnit.category == NOTI_CATEGORY_ARCHIVE) {
                    drawerViewModel.actOnNoti(notiKey, "unarchive")
                } else {
                    drawerViewModel.actOnNoti(notiKey, "archive")
                }
            }
        )

        NotiActionIconButton(
            iconRes = if (notiUnit.category == NOTI_CATEGORY_MAKETASK) R.drawable.task_yes else R.drawable.task_no,
            contentDescription = "Make-Task",
            onClick = {
                if (notiUnit.category == NOTI_CATEGORY_MAKETASK) {
                    drawerViewModel.actOnNoti(notiKey, "dismiss_task")
                } else {
                    drawerViewModel.actOnNoti(notiKey, "make_task")
                }
            }
        )

//        NotiActionIconButton(
//            iconRes = R.drawable.schedule,
//            contentDescription = "Schedule",
//            onClick = {  }
//        )

        NotiActionIconButton(
            iconRes = R.drawable.delete,
            contentDescription = "Delete",
            onClick = {
                if (!isPinned)
                    drawerViewModel.actOnNoti(notiKey, "dismiss_click")
            }
        )
    }
}