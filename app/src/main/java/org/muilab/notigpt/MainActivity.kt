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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import org.muilab.notigpt.data.repository.notification.NotiRepositoryProvider
import org.muilab.notigpt.service.NotiListenerService
import org.muilab.notigpt.ui.theme.NotiTheme
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.ui.common.component.AppScaffold
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModelFactory
import org.muilab.notigpt.work.ReminderPeriodicWork

/**
 * Main Android entry point for the app shell and notification-permission/service bootstrap.
 *
 * Keep process-wide startup wiring here. Feature state should stay in ViewModels, repositories, or workers so
 * the activity remains a thin host for Compose and Android permission flows.
 */
class MainActivity : ComponentActivity() {
    /**
     * Initializes app-wide preferences, background work, and Compose content for the main shell.
     *
     * Keep permission prompts and service checks here because they depend on Android activity context; route
     * feature behavior through Compose screens and ViewModels.
     */
    private val requestLocalNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op: workers retry */ }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must precede super.onCreate: swaps the launch (splash) theme for the app theme.
        installSplashScreen()
        // Draw behind the system bars; enableEdgeToEdge also keeps status/nav icon contrast in sync
        // with the light/dark mode. Compose consumes the insets via Scaffold + WindowInsets.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // WorkManager.getInstance(applicationContext).cancelAllWork()

        SharedPreferencesManager.init(this)

        // Android 16+ (API 36) Local Network Protection: LAN/RFC-1918 hosts (e.g. the dev n8n server)
        // are unreachable until this runtime permission is granted, even though internet access works.
        maybeRequestLocalNetworkPermission()
        // Identity is always the signed-in Firebase UID. Keep it blank before sign-in; the device
        // ID is not an account identity and must never be used for Firestore or n8n payloads.
        SharedPreferencesManager.userId =
            org.muilab.notigpt.data.remote.auth.GoogleAuthManager.currentUser()?.uid.orEmpty()
        org.muilab.notigpt.data.remote.n8n.ExtractionStatusStore.restore()

        // Periodic safety-net for reminder scan/extraction — only once signed in.
        if (org.muilab.notigpt.data.remote.auth.GoogleAuthManager.isSignedIn()) {
            ReminderPeriodicWork.enqueue(applicationContext)
        }

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
                // Surface user-triggered extraction failures as a toast, wherever they were triggered from.
                val extractionStatus by org.muilab.notigpt.data.remote.n8n.ExtractionStatusStore.status.collectAsState()
                LaunchedEffect(extractionStatus.userTriggeredFailureTick) {
                    if (extractionStatus.userTriggeredFailureTick > 0L) {
                        org.muilab.notigpt.ui.common.feedback.AppSnackbar.show(
                            getString(R.string.extraction_failed_toast)
                        )
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Mandatory login: Firestore data is keyed by the Google account's UID.
                    var signedIn by remember {
                        androidx.compose.runtime.mutableStateOf(
                            org.muilab.notigpt.data.remote.auth.GoogleAuthManager.isSignedIn()
                        )
                    }
                    if (!signedIn) {
                        org.muilab.notigpt.ui.auth.SignInScreen(
                            onSignedIn = {
                                signedIn = true
                                ReminderPeriodicWork.enqueue(applicationContext)
                            },
                        )
                    } else {
                        AppScaffold(
                            drawerViewModel = drawerViewModel,
                        )
                    }
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

    /**
     * Requests ACCESS_LOCAL_NETWORK on Android 16+ so the app can reach LAN hosts (dev n8n server).
     * No-op on older versions where the permission does not exist and LAN access is unrestricted.
     */
    private fun maybeRequestLocalNetworkPermission() {
        if (Build.VERSION.SDK_INT < 36) return
        val perm = "android.permission.ACCESS_LOCAL_NETWORK"
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            requestLocalNetworkPermission.launch(perm)
        }
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
