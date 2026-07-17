package org.muilab.notigpt.ui.common.component

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.common.feedback.AppSnackbar
import org.muilab.notigpt.ui.notification.component.AutoControlBar
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.work.ExtractionPeriodicWork

/**
 * Developer-only control panel for invoking notification/debug operations from the UI.
 *
 * Keep this component separated from normal user flows. Any operation that changes durable app data should still
 * route through ViewModel/repository methods.
 */
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
                    stringResource(R.string.ui_dev_open_control_panel)
                } else {
                    stringResource(R.string.ui_dev_close_control_panel)
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

                AutoControlBar()
                Button(onClick = {
                    AppSnackbar.show(context.getString(R.string.ui_dev_sync_noti_status_toast))
                }) {
                    Text(stringResource(R.string.ui_dev_sync_noti_status))
                }
                Button(onClick = {
                    AppSnackbar.show(context.getString(R.string.ui_dev_work_in_progress))
                }) {
                    Text(stringResource(R.string.ui_dev_update_user))
                }
                Button(onClick = {
                    drawerViewModel.exportAllData(true, true)
                }) {
                    Text(stringResource(R.string.ui_dev_export_data))
                }
                Button(onClick = {
                    drawerViewModel.exportPostContent(true, false)
                }) {
                    Text(stringResource(R.string.ui_dev_copy_data_with_history))
                }
                Button(onClick = {
                    drawerViewModel.exportPostContent(false, false)
                }) {
                    Text(stringResource(R.string.ui_dev_copy_data))
                }
                Button(onClick = {
                    try {
                        SharedPreferencesManager.init(context.applicationContext)
                        ExtractionPeriodicWork.logStatus(context.applicationContext)
                        val t = SharedPreferencesManager.lastExtractionPeriodicRunTime
                        AppSnackbar.show(
                            if (t == 0L) "Periodic worker hasn't run yet (or prefs cleared)" else "Last periodic run: $t"
                        )
                    } catch (e: Exception) {
                        AppSnackbar.show("Failed to query periodic work: ${e.message}")
                    }
                }) {
                    Text("Debug: periodic work status")
                }
            }
        }
    }
}
