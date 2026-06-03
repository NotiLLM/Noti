package org.muilab.notigpt.ui.notification.component.card.noticard.elements

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel

/**
 * Options dialog and transient dialog state for one notification card.
 *
 * Keep dialog-local visibility and text input here. Persisted actions such as pinning, extraction, or feedback
 * should continue flowing through callbacks owned by the screen/ViewModel.
 */
data class NotiCardOptionsState(
    val isPinned: Boolean,
)

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun NotiCardOptionsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    drawerViewModel: DrawerViewModel,
    notiKey: String,
    state: NotiCardOptionsState,
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_noti_options_title)) },
        text = {
            Column {
                TextButton(
                    onClick = {
                        drawerViewModel.actOnNoti(
                            notiKey,
                            if (state.isPinned) "unpin" else "pin",
                        )
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(if (state.isPinned) R.drawable.pin_yes else R.drawable.pin_no),
                            contentDescription = stringResource(if (state.isPinned) R.string.ui_noti_action_unpin else R.string.ui_noti_action_pin),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(if (state.isPinned) R.string.ui_noti_action_unpin else R.string.ui_noti_action_pin))
                    }
                }
//                TextButton(
//                    onClick = {
//                        drawerViewModel.actOnNoti(notiKey, "dismiss")
//                        onDismiss()
//                    },
//                    modifier = Modifier.fillMaxWidth(),
//                ) {
//                    Row(
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.Start,
//                        modifier = Modifier.fillMaxWidth(),
//                    ) {
//                        Icon(
//                            painter = painterResource(R.drawable.close),
//                            contentDescription = stringResource(R.string.ui_noti_action_dismiss),
//                            modifier = Modifier.size(24.dp),
//                        )
//                        Spacer(Modifier.width(12.dp))
//                        Text(stringResource(R.string.ui_noti_action_dismiss))
//                    }
//                }

                TextButton(
                    onClick = {
                        drawerViewModel.actOnNoti(notiKey, "extract_reminder")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.task_yes),
                            contentDescription = stringResource(R.string.ui_action_extract_reminder),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.ui_action_extract_reminder))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_action_close)) }
        },
    )
}
