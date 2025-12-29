package org.muilab.notigpt.view.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.util.SharedPreferencesManager

@Composable
fun SettingsScreen() {
    Column(Modifier.fillMaxSize()) {
        var isLeftSwipe by remember { mutableStateOf(SharedPreferencesManager.swipeDeleteLeft) }

        // Simple radio row to choose swipe-delete direction
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Delete on swipe: ", modifier = Modifier.padding(end = 8.dp))

            // Left Option
            Row(modifier = Modifier.selectable(
                selected = isLeftSwipe,
                onClick = {
                    isLeftSwipe = true
                    SharedPreferencesManager.swipeDeleteLeft = true
                },
                role = Role.RadioButton
            )) {
                RadioButton(
                    selected = isLeftSwipe,
                    onClick = {
                        isLeftSwipe = true
                        SharedPreferencesManager.swipeDeleteLeft = true
                    }
                )
                Text("Left", modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            }

            Spacer(modifier = Modifier.size(16.dp))

            // Right Option
            Row(modifier = Modifier.selectable(
                selected = !isLeftSwipe,
                onClick = {
                    isLeftSwipe = false
                    SharedPreferencesManager.swipeDeleteLeft = false
                },
                role = Role.RadioButton
            )) {
                RadioButton(
                    selected = !isLeftSwipe,
                    onClick = {
                        isLeftSwipe = false
                        SharedPreferencesManager.swipeDeleteLeft = false
                    }
                )
                Text("Right", modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            }
        }

        var isTaskExpandedDefault by remember { mutableStateOf(SharedPreferencesManager.taskListDefaultExpanded) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Expand Task List on startup", modifier = Modifier.weight(1f))
            Switch(
                checked = isTaskExpandedDefault,
                onCheckedChange = {
                    isTaskExpandedDefault = it
                    SharedPreferencesManager.taskListDefaultExpanded = it
                }
            )
        }
    }
}