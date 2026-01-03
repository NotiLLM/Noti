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
    val isTask: Boolean,
    val isSave: Boolean,
    val isArchive: Boolean,
    val isSetToTop: Boolean,
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
                } else {
                    TextButton(
                        onClick = {
                            drawerViewModel.actOnNoti(
                                notiKey,
                                if (!state.isTask) "make_task" else "dismiss_task",
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
                                painter = painterResource(if (state.isTask) R.drawable.task_yes else R.drawable.task_no),
                                contentDescription = "To Task",
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (state.isTask) "Remove from Tasks" else "Move to Tasks")
                        }
                    }

                    TextButton(
                        onClick = {
                            drawerViewModel.actOnNoti(notiKey, if (!state.isSave) "save" else "unsave")
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
                                painter = painterResource(if (state.isSave) R.drawable.save_yes else R.drawable.save_no),
                                contentDescription = "To Save",
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (state.isSave) "Remove from Save" else "Move to Save")
                        }
                    }

                    TextButton(
                        onClick = {
                            drawerViewModel.actOnNoti(notiKey, if (!state.isArchive) "archive" else "unarchive")
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
                                painter = painterResource(if (state.isArchive) R.drawable.archive_yes else R.drawable.archive_no),
                                contentDescription = "To Archive",
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (state.isArchive) "Remove from Archive" else "Move to Archive")
                        }
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
                        Text(if (state.isSetToTop) "Move to Top (Update Time)" else "Set To Top")
                    }
                }

                if (state.isSetToTop) {
                    TextButton(
                        onClick = {
                            drawerViewModel.actOnNoti(notiKey, "undo_to_top")
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
                                painter = painterResource(R.drawable.undo_totop),
                                contentDescription = "Undo To Top",
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Undo To Top")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
