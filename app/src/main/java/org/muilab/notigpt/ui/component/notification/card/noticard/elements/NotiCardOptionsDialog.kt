package org.muilab.notigpt.ui.component.notification.card.noticard.elements

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
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel

data class NotiCardOptionsState(
    val isInGroup: Boolean,
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
        title = { Text("Options") },
        text = {
            Column {
                if (state.isInGroup) {
                    TextButton(
                        onClick = {
                            drawerViewModel.removeFromGroup(notiKey)
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
                                painter = painterResource(R.drawable.leave_group),
                                contentDescription = "Leave Group",
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Remove from Group")
                        }
                    }
                }

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
                            contentDescription = "Pin",
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(if (state.isPinned) "Unpin" else "Pin")
                    }
                }

                TextButton(
                    onClick = {
                        drawerViewModel.actOnNoti(notiKey, "to_top")
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
                            painter = painterResource(R.drawable.totop),
                            contentDescription = "To Top",
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("To Top")
                    }
                }

                TextButton(
                    onClick = {
                        drawerViewModel.actOnNoti(notiKey, "dismiss")
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
                            painter = painterResource(R.drawable.close),
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Dismiss")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
