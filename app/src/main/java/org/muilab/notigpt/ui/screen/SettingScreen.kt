package org.muilab.notigpt.ui.screen

import android.app.TimePickerDialog
import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import java.util.Locale
import org.muilab.notigpt.R
import org.muilab.notigpt.repository.NotiRepositoryProvider
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.viewmodel.DrawerViewModelFactory
import org.muilab.notigpt.util.SharedPreferencesManager

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    // Get or create DrawerViewModel for export functionality
    val drawerViewModel: DrawerViewModel = viewModel(
        factory = DrawerViewModelFactory(
            application = context.applicationContext as Application,
            notiRepository = NotiRepositoryProvider.provideNotiRepository(context)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
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
            return String.format(Locale.getDefault(), "%02d:%02d", h, m)
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

        Spacer(modifier = Modifier.size(24.dp))

        // --- Export Data Section ---
        var showExportOptions by remember { mutableStateOf(false) }
        var includeContext by remember { mutableStateOf(true) }
        var includeDismissed by remember { mutableStateOf(false) }

        Text(
            stringResource(R.string.ui_settings_export_data),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
        )

        Text(
            stringResource(R.string.ui_settings_export_description),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )

        // Export options
        if (!showExportOptions) {
            Button(
                onClick = { showExportOptions = true },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                Text(stringResource(R.string.ui_settings_export_data))
            }
        } else {
            // Show options when expanded
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeContext,
                    onCheckedChange = { includeContext = it }
                )
                Text(
                    stringResource(R.string.ui_settings_export_include_context),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeDismissed,
                    onCheckedChange = { includeDismissed = it }
                )
                Text(
                    stringResource(R.string.ui_settings_export_include_dismissed),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        drawerViewModel.exportAllData(includeContext, includeDismissed)
                        showExportOptions = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.ui_action_ok))
                }
                TextButton(
                    onClick = { showExportOptions = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.ui_action_cancel))
                }
            }
        }
    }
}

