package org.muilab.notigpt.ui.notification.component.card.noticard.elements

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel

/**
 * Options for one notification card, shown as a Material bottom sheet of list items (was an
 * AlertDialog of text buttons). Long-press on the card opens it. Every action the card exposes
 * elsewhere is reachable here: pin/unpin, extract task, and — when available — create a scheduled
 * reminder. Persisted actions flow through callbacks owned by the screen/ViewModel.
 */
data class NotiCardOptionsState(
    val isPinned: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NotiCardOptionsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    drawerViewModel: DrawerViewModel,
    notiKey: String,
    state: NotiCardOptionsState,
    onCreateReminder: (() -> Unit)? = null,
) {
    if (!show) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding()) {
            OptionRow(
                iconRes = if (state.isPinned) R.drawable.pin_yes else R.drawable.pin_no,
                label = stringResource(if (state.isPinned) R.string.ui_noti_action_unpin else R.string.ui_noti_action_pin),
                onClick = {
                    drawerViewModel.actOnNoti(notiKey, if (state.isPinned) "unpin" else "pin")
                    onDismiss()
                },
            )
            OptionRow(
                iconRes = R.drawable.task,
                label = stringResource(R.string.ui_action_extract_reminder),
                onClick = {
                    drawerViewModel.actOnNoti(notiKey, "extract_reminder")
                    onDismiss()
                },
            )
            if (onCreateReminder != null) {
                OptionRow(
                    iconRes = R.drawable.schedule,
                    label = stringResource(R.string.ui_reminders_create_button),
                    onClick = {
                        onCreateReminder()
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun OptionRow(iconRes: Int, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
