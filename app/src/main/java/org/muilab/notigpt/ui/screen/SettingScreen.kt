package org.muilab.notigpt.ui.screen

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.util.Calendar
import org.muilab.notigpt.R
import org.muilab.notigpt.util.SharedPreferencesManager

@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        var isLeftSwipe by remember { mutableStateOf(SharedPreferencesManager.swipeDeleteLeft) }

        // Simple radio row to choose swipe-delete direction
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ui_settings_delete_on_swipe), modifier = Modifier.padding(end = 8.dp))

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
                Text(stringResource(R.string.ui_settings_left), modifier = Modifier.padding(start = 4.dp, top = 12.dp))
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
                Text(stringResource(R.string.ui_settings_right), modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            }
        }

        Spacer(modifier = Modifier.size(12.dp))

        // --- ESM receptive window ---
        var wakeMin by remember { mutableStateOf(SharedPreferencesManager.esmWakeupMinutes) }
        var bedMin by remember { mutableStateOf(SharedPreferencesManager.esmBedtimeMinutes) }

        fun fmt(mins: Int): String {
            val h = (mins / 60).coerceIn(0, 23)
            val m = (mins % 60).coerceIn(0, 59)
            return String.format("%02d:%02d", h, m)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.ui_settings_esm_wakeup), modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, wakeMin / 60)
                    set(Calendar.MINUTE, wakeMin % 60)
                }
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        wakeMin = hour * 60 + minute
                        SharedPreferencesManager.esmWakeupMinutes = wakeMin
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            }) {
                Text(fmt(wakeMin))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.ui_settings_esm_bedtime), modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, bedMin / 60)
                    set(Calendar.MINUTE, bedMin % 60)
                }
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        bedMin = hour * 60 + minute
                        SharedPreferencesManager.esmBedtimeMinutes = bedMin
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            }) {
                Text(fmt(bedMin))
            }
        }
    }
}