package org.muilab.notigpt.ui.component.notification.action

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.muilab.notigpt.R
import org.muilab.notigpt.database.server.enqueueNotificationAction
import org.muilab.notigpt.database.server.enqueueUpdateNotification
import org.muilab.notigpt.model.notifications.NotiUnit

@Composable
fun NotiFeedbackDropdown(context: Context, notiUnit: NotiUnit, isDropdownMenuExpanded: MutableState<Boolean>) {

    val scrollState = rememberScrollState()

    DropdownMenu(
        expanded = isDropdownMenuExpanded.value,
        onDismissRequest = { isDropdownMenuExpanded.value = false },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row {
                Button(onClick = {
                    Toast.makeText(context, "Start Updating Notification", Toast.LENGTH_SHORT).show()
                    enqueueUpdateNotification(context, notiUnit.notiKey)
                    isDropdownMenuExpanded.value = false
                }) {
                    Text("Update Notification")
                }
            }
            Box(
                modifier = Modifier
                    .height(150.dp) // Set fixed height to demonstrate overflow
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                Text(
                    text = notiUnit.explanation,
                    fontSize = 10.sp
                )
            }
        }
    }
}