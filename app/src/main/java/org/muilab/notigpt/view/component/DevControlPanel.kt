package org.muilab.notigpt.view.component

import org.muilab.notigpt.database.server.workers.ApiWorker
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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.muilab.notigpt.util.Constants.Companion.API_CLEAR_DB
import org.muilab.notigpt.util.Constants.Companion.API_EXPORT_DB
import org.muilab.notigpt.util.Constants.Companion.API_SORT_DRAWER
import org.muilab.notigpt.util.Constants.Companion.API_SYNC_DRAWER
import org.muilab.notigpt.util.Constants.Companion.API_UPDATE_USER
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_HISTORY_NOTI_COUNT_THRESHOLD
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_HISTORY_NOTI_HOURS_THRESHOLD
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
                    val inputData = Data.Builder()
                        .putString("api_type", API_SYNC_DRAWER)
                        .build()
                    val apiWorkerRequest = OneTimeWorkRequestBuilder<ApiWorker>()
                        .setInputData(inputData)
                        .build()
                    WorkManager.getInstance(context).enqueue(apiWorkerRequest)
                    Toast.makeText(context, "Start Syncing", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Sync Drawer")
                }
                Button(onClick = {
                    Toast.makeText(context, "Start Sorting", Toast.LENGTH_SHORT).show()
                    val inputData = Data.Builder()
                        .putString("api_type", API_SORT_DRAWER)
                        .putString("request_data", "")
                        .build()
                    val apiWorkerRequest = OneTimeWorkRequestBuilder<ApiWorker>()
                        .setInputData(inputData)
                        .build()
                    WorkManager.getInstance(context).enqueue(apiWorkerRequest)
                }) {
                    Text("Sort")
                }
                Button(onClick = {
                    Toast.makeText(context, "Update User", Toast.LENGTH_SHORT).show()
                    val inputData = Data.Builder()
                        .putString("api_type", API_UPDATE_USER)
                        .build()
                    val apiWorkerRequest = OneTimeWorkRequestBuilder<ApiWorker>()
                        .setInputData(inputData)
                        .build()
                    WorkManager.getInstance(context).enqueue(apiWorkerRequest)
                }) {
                    Text("Update User")
                }
                Button(onClick = {
                    Toast.makeText(context, "Exporting DB", Toast.LENGTH_SHORT).show()
                    val inputData = Data.Builder()
                        .putString("api_type", API_EXPORT_DB)
                        .build()
                    val apiWorkerRequest = OneTimeWorkRequestBuilder<ApiWorker>()
                        .setInputData(inputData)
                        .build()
                    WorkManager.getInstance(context).enqueue(apiWorkerRequest)
                }) {
                    Text("Export DB")
                }
                Button(onClick = {
                    Toast.makeText(context, "Clearing DB", Toast.LENGTH_SHORT).show()
                    val inputData = Data.Builder()
                        .putString("api_type", API_CLEAR_DB)
                        .build()
                    val apiWorkerRequest = OneTimeWorkRequestBuilder<ApiWorker>()
                        .setInputData(inputData)
                        .build()
                    WorkManager.getInstance(context).enqueue(apiWorkerRequest)
                }) {
                    Text("Clear DB")
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
                    drawerViewModel.exportPostContent(true)
                }) {
                    Text("Copy Data with History")
                }
                Button(onClick = {
                    drawerViewModel.exportPostContent(false)
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
                        placeholder = { Text("Enter integer") }
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
