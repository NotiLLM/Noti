package org.muilab.notigpt.view.component

import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.muilab.notigpt.database.server.N8nAPIClient
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_HISTORY_NOTI_COUNT_THRESHOLD
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_HISTORY_NOTI_HOURS_THRESHOLD
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_SERVER_URL

@Composable
fun SharedPrefsButton(
    buttonText: String,
    prefKey: String
) {
    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Button(onClick = { showDialog = true }) {
        Text(buttonText)
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface {
                Column {
                    Text("Enter an Integer for $prefKey")
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Enter value") }
                    )
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }
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
                                KEY_SERVER_URL -> {
                                    fun isValidIpAddress(ip: String): Boolean {
                                        val ipv4Pattern = Regex("^((25[0-5]|2[0-4][0-9]|1?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|1?[0-9][0-9]?)$")
                                        val ipv6Pattern = Regex("^[0-9a-fA-F:]+$")
                                        val validIpv6 = ip.contains(":") && ip.split(":").size in 3..8
                                        return ipv4Pattern.matches(ip) || (ipv6Pattern.matches(ip) && validIpv6)
                                    }
                                    if (isValidIpAddress(inputText)) {
                                        SharedPreferencesManager.serverIP = inputText
                                        N8nAPIClient.updateBaseUrl(SharedPreferencesManager.serverIP)
                                        showDialog = false
                                    } else {
                                        errorMessage = "Invalid IP!"
                                    }
                                }
                                else -> {}
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

