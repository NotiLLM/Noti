package org.muilab.notigpt.ui.component

import android.content.Context
import android.widget.Toast
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
import org.muilab.notigpt.ui.component.notification.AutoControlBar
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.work.ReminderPeriodicWork

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
                    Toast.makeText(context, context.getString(R.string.ui_dev_sync_noti_status_toast), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.ui_dev_sync_noti_status))
                }
                Button(onClick = {
                    Toast.makeText(context, context.getString(R.string.ui_dev_work_in_progress), Toast.LENGTH_SHORT).show()
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
                        ReminderPeriodicWork.logStatus(context.applicationContext)
                        val t = SharedPreferencesManager.lastReminderPeriodicRunTime
                        Toast.makeText(
                            context,
                            if (t == 0L) "Periodic worker hasn't run yet (or prefs cleared)" else "Last periodic run: $t",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to query periodic work: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("Debug: periodic work status")
                }
            }
        }
    }
}
