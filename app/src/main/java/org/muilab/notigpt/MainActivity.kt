package org.muilab.notigpt

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import org.muilab.notigpt.repository.NotiRepositoryProvider
import org.muilab.notigpt.service.NotiListenerService
import org.muilab.notigpt.ui.theme.NotiTheme
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.ui.component.AppScaffold
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.viewmodel.DrawerViewModelFactory
import org.muilab.notigpt.work.ReminderPeriodicWork

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NOTE: This used to cancel all WorkManager jobs, which breaks scheduled ESM delivery.
        // WorkManager.getInstance(applicationContext).cancelAllWork()

        SharedPreferencesManager.init(this)
        SharedPreferencesManager.userId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // Periodic safety-net for reminder scan/extraction.
        ReminderPeriodicWork.enqueue(applicationContext)

        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            val intent = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        }

        if (!isBatteryOptimizationsIgnored()) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                if (intent.resolveActivity(packageManager) != null)
                    startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


        if (isNotiListenerEnabled()) {
            val notiListenerIntent = Intent(this@MainActivity, NotiListenerService::class.java)
            startService(notiListenerIntent)
        } else {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        setContent {
            NotiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScaffold(
                        drawerViewModel = drawerViewModel,
                    )
                }
            }
        }
    }

    private val drawerViewModel: DrawerViewModel by viewModels {
        DrawerViewModelFactory(this.application, NotiRepositoryProvider.provideNotiRepository(applicationContext))
    }


    private fun isNotiListenerEnabled(): Boolean {
        val cn = ComponentName(this, NotiListenerService::class.java)
        val flat: String? =
            Settings.Secure.getString(this.contentResolver, "enabled_notification_listeners")
        return (flat != null) && (cn.flattenToString() in flat)
    }

    fun isBatteryOptimizationsIgnored(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val services = activityManager.getRunningServices(Integer.MAX_VALUE)
        for (serviceInfo in services)
            if (serviceClass.name == serviceInfo.service.className)
                return true
        return false
    }

    override fun onResume() {
        SharedPreferencesManager.lastAppResumeTime = System.currentTimeMillis()
        super.onResume()

        // Opportunistic wake-up: if WorkManager got delayed in doze, kick once when user opens app.
        // Rate limit to avoid spamming when user switches apps quickly.
        try {
            SharedPreferencesManager.init(this)
            ReminderPeriodicWork.enqueue(applicationContext)
            val now = System.currentTimeMillis()
            val last = SharedPreferencesManager.lastReminderPeriodicRunTime
            val shouldKick = (last == 0L) || (now - last) >= (5 * 60 * 1000L)

            if (shouldKick) {
                Log.i("MainActivity", "Kicking reminder periodic worker; lastRun=$last")
                ReminderPeriodicWork.kickNow(applicationContext)
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to enqueue/kick periodic reminder work", e)
        }
    }
    
}