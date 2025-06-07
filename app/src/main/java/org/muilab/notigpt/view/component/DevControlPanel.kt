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
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.muilab.notigpt.database.server.DifyAPIClient
import org.muilab.notigpt.database.server.enqueueUpdateNotification
import org.muilab.notigpt.repository.NotiRepositoryProvider
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_HISTORY_NOTI_COUNT_THRESHOLD
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_HISTORY_NOTI_HOURS_THRESHOLD
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_SERVER_IP
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
                    .wrapContentHeight()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(onClick = {
                    Toast.makeText(context, "Start Updating Notifications", Toast.LENGTH_SHORT).show()
                    CoroutineScope(Dispatchers.IO).launch {
                        val notiRepository = NotiRepositoryProvider.provideNotiRepository(context)
                        val notiKeys = notiRepository.getNotificationKeys()
                        notiKeys.forEach { notiKey ->
                            enqueueUpdateNotification(context, notiKey)
                            delay(30 * 1000)
                        }
                    }
                }) {
                    Text("Sync Noti Status")
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
                SharedPrefsButton("Set History Count", KEY_HISTORY_NOTI_COUNT_THRESHOLD)
                SharedPrefsButton("Set History Time", KEY_HISTORY_NOTI_HOURS_THRESHOLD)
                SharedPrefsButton("Set IP", KEY_SERVER_IP)
            }
        }
    }
}

@Composable
fun SharedPrefsButton(
    buttonText: String,
    prefKey: String
) {
    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Button to trigger the dialog
    Button(onClick = { showDialog = true }) {
        Text(buttonText)
    }

    // Dialog logic
    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface {
                Column {
                    Text("Enter an Integer for $prefKey")

                    // Input Field
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Enter value") }
                    )

                    // Error Message
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }

                    // Buttons
                    Row {
                        TextButton(onClick = { showDialog = false }) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            when (prefKey) {
                                KEY_HISTORY_NOTI_COUNT_THRESHOLD -> {
                                    val intValue = inputText.toIntOrNull()
                                    if (intValue != null) {
                                        SharedPreferencesManager.historyNotiCountThreshold = intValue
                                        showDialog = false
                                    } else {
                                        errorMessage = "Invalid integer!"
                                    }
                                }
                                KEY_HISTORY_NOTI_HOURS_THRESHOLD -> {
                                    val intValue = inputText.toIntOrNull()
                                    if (intValue != null) {
                                        SharedPreferencesManager.historyNotiHoursThreshold = intValue
                                        showDialog = false
                                    } else {
                                        errorMessage = "Invalid integer!"
                                    }
                                }
                                KEY_SERVER_IP -> {

                                    fun isValidIpAddress(ip: String): Boolean {
                                        val ipv4Pattern = Regex("^((25[0-5]|2[0-4][0-9]|1?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|1?[0-9][0-9]?)$")

                                        val ipv6Pattern = Regex("^[0-9a-fA-F:]+$") // Loose check for IPv6 format
                                        val validIpv6 = ip.contains(":") && ip.split(":").size in 3..8

                                        return ipv4Pattern.matches(ip) || (ipv6Pattern.matches(ip) && validIpv6)
                                    }

                                    if (isValidIpAddress(inputText)) {
                                        SharedPreferencesManager.serverIP = inputText
                                        DifyAPIClient.updateBaseUrl(SharedPreferencesManager.serverIP)
                                        showDialog = false
                                    } else {
                                        errorMessage = "Invalid IP!"
                                    }
                                }
                                else -> {

                                }
                            }
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
