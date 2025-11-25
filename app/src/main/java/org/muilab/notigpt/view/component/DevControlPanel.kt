package org.muilab.notigpt.view.component

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.muilab.notigpt.database.server.N8nAPIClient
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_HISTORY_NOTI_COUNT_THRESHOLD
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_HISTORY_NOTI_HOURS_THRESHOLD
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_SERVER_URL
import org.muilab.notigpt.view.component.notification.AutoControlBar
import org.muilab.notigpt.viewModel.DrawerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevControlPanel(context: Context, drawerViewModel: DrawerViewModel) {
    var showPanel by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Button (
            onClick = {
                showPanel = !showPanel
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 10.dp)
        ) {
            Text(
                if (!showPanel) {
                    "Open Control Panel"
                } else {
                    "Close Control Panel"
                },
                color = Color.Gray
            )
        }
        if (showPanel) {
            FlowRow (
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Button(onClick = {
                    // Example: extract 5 random tasks
                    drawerViewModel.extractRandomTasks(100)
                    Toast.makeText(context, "Requested extraction for 10 random notifications", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Extract")
                }

                AutoControlBar()
                Button(onClick = {
                    Toast.makeText(context, "Start Updating Notifications", Toast.LENGTH_SHORT).show()
//                    CoroutineScope(Dispatchers.IO).launch {
//                        val notiRepository = NotiRepositoryProvider.provideNotiRepository(context)
//                        val notiKeys = notiRepository.getNotificationKeys()
//                        notiKeys.forEach { notiKey ->
//                            enqueueUpdateNotification(context, notiKey)
//                            delay(30 * 1000)
//                        }
//                    }
                    drawerViewModel.syncAppCategory()
                }) {
                    Text("Sync Noti Status")
                }
                Button(onClick = {
                    drawerViewModel.resetAllManualSortOrders()
                }) {
                    Text("Reset Sort")
                }
                Button(onClick = {
                    Toast.makeText(context, "Work In Progress", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Update User")
                }
//            Button(onClick = {
//                Toast.makeText(context, "Start Categorizing", Toast.LENGTH_SHORT).show()
//                geminiViewModel.getCategories()
//            }) {
//                Text("Classify")
//            }
//            Button(onClick = {
//                Toast.makeText(context, "Work In Progress", Toast.LENGTH_SHORT).show()
//            }) {
//                Text("Extract Tasks")
//            }
                Button(onClick = {
                    drawerViewModel.exportPostContent(true, true)
                }) {
                    Text("Export Data")
                }
                Button(onClick = {
                    drawerViewModel.exportPostContent(true, false)
                }) {
                    Text("Copy Data with History")
                }
                Button(onClick = {
                    drawerViewModel.exportPostContent(false, false)
                }) {
                    Text("Copy Data")
                }
//            Button(onClick = {
//                drawerViewModel.resetGPTValues()
//                Toast.makeText(context, "GPT Values Reset", Toast.LENGTH_SHORT).show()
//            }) {
//                Text("Reset")
//            }
//                SharedPrefsButton("Set History Count", KEY_HISTORY_NOTI_COUNT_THRESHOLD)
//                SharedPrefsButton("Set History Time", KEY_HISTORY_NOTI_HOURS_THRESHOLD)
//                SharedPrefsButton("Set IP", KEY_SERVER_URL)
            }
        }
    }
}
