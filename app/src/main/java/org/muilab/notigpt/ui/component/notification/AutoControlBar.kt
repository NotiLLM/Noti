package org.muilab.notigpt.ui.component.notification

import android.widget.ToggleButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Small control strip for automatic notification-processing actions.
 *
 * Keep this component presentation-only. If controls start coordinating worker scheduling or repository writes,
 * route those actions through a ViewModel instead of expanding this composable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoControlBar() {
    var autoArchive by remember { mutableStateOf(SharedPreferencesManager.autoArchive) }
    var autoDelete by remember { mutableStateOf(SharedPreferencesManager.autoDelete) }
    var trackPin by remember { mutableStateOf(SharedPreferencesManager.trackPin) }

    FlowRow (
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto Archive")
            Spacer(Modifier.size(20.dp))
            Switch(
                checked = autoArchive,
                onCheckedChange = {
                    autoArchive = it
                    SharedPreferencesManager.autoArchive = it
                }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto Delete")
            Spacer(Modifier.size(20.dp))
            Switch(
                checked = autoDelete,
                onCheckedChange = {
                    autoDelete = it
                    SharedPreferencesManager.autoDelete = it
                }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Track Pin")
            Spacer(Modifier.size(20.dp))
            Switch(
                checked = trackPin,
                onCheckedChange = {
                    trackPin = it
                    SharedPreferencesManager.trackPin = it
                }
            )
        }
    }
}