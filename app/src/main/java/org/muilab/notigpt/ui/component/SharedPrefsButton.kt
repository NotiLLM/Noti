package org.muilab.notigpt.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import org.muilab.notigpt.R
import org.muilab.notigpt.data.remote.n8n.N8nAPIClient
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_HISTORY_NOTI_COUNT_THRESHOLD
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_HISTORY_NOTI_HOURS_THRESHOLD
import org.muilab.notigpt.util.SharedPreferencesManager.KEY_SERVER_URL

/**
 * Settings UI for inspecting and editing shared-preference-backed configuration.
 *
 * Keep validation and preference writes explicit here because these values affect remote endpoints and app-wide
 * behavior. If settings grow, split each setting group into a dedicated component.
 */
@Composable
fun SharedPrefsButton(
    buttonText: String,
    prefKey: String
) {
    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Pre-compute localized strings so we don't call stringResource() inside non-composable lambdas.
    val invalidIntegerMsg = stringResource(R.string.ui_debug_invalid_integer)
    val invalidIpMsg = stringResource(R.string.ui_debug_invalid_ip)

    Button(onClick = { showDialog = true }) {
        Text(buttonText)
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface {
                Column {
                    Text(stringResource(R.string.ui_debug_enter_integer_for, prefKey))
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text(stringResource(R.string.ui_debug_enter_value)) }
                    )
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(onClick = { showDialog = false }) {
                            Text(stringResource(R.string.ui_action_cancel))
                        }
                        Button(onClick = {
                            when (prefKey) {
                                KEY_HISTORY_NOTI_COUNT_THRESHOLD -> {
                                    val intValue = inputText.toIntOrNull()
                                    if (intValue != null) {
                                        SharedPreferencesManager.historyNotiCountThreshold = intValue
                                        showDialog = false
                                    } else {
                                        errorMessage = invalidIntegerMsg
                                    }
                                }
                                KEY_HISTORY_NOTI_HOURS_THRESHOLD -> {
                                    val intValue = inputText.toIntOrNull()
                                    if (intValue != null) {
                                        SharedPreferencesManager.historyNotiHoursThreshold = intValue
                                        showDialog = false
                                    } else {
                                        errorMessage = invalidIntegerMsg
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
                                        errorMessage = invalidIpMsg
                                    }
                                }
                                else -> {}
                            }
                        }) {
                            Text(stringResource(R.string.ui_action_save))
                        }
                    }
                }
            }
        }
    }
}
