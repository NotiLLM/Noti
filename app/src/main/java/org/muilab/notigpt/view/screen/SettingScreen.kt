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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.util.SharedPreferencesManager

@Composable
fun SettingsScreen() {
    Column(Modifier.fillMaxSize()) {
        // --- Observe swipe-delete preference and expose UI to change it ---
        val swipeDeleteLeft by SharedPreferencesManager.swipeDeleteLeftFlow.collectAsState()

        // Simple radio row to choose swipe-delete direction
        Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Delete on: ", modifier = Modifier.padding(end = 8.dp))
            Row(modifier = Modifier.selectable(selected = swipeDeleteLeft, onClick = { SharedPreferencesManager.swipeDeleteLeft = true }, role = Role.RadioButton)) {
                RadioButton(selected = swipeDeleteLeft, onClick = { SharedPreferencesManager.swipeDeleteLeft = true }, colors = RadioButtonDefaults.colors())
                Text("Left", modifier = Modifier.padding(start = 4.dp))
            }
            Spacer(modifier = Modifier.size(8.dp))
            Row(modifier = Modifier.selectable(selected = !swipeDeleteLeft, onClick = { SharedPreferencesManager.swipeDeleteLeft = false }, role = Role.RadioButton)) {
                RadioButton(selected = !swipeDeleteLeft, onClick = { SharedPreferencesManager.swipeDeleteLeft = false }, colors = RadioButtonDefaults.colors())
                Text("Right", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}