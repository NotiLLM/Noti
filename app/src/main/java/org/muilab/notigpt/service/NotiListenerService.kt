package org.muilab.notigpt.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.collection.LruCache
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.muilab.notigpt.domain.notification.NotificationFilter
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.receiver.NotiListenerRestartReceiver
import org.muilab.notigpt.data.repository.notification.NotiRepository
import org.muilab.notigpt.util.Constants.Companion.NOTI_REMOVE_DELAY
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.createNotificationChannel
import org.muilab.notigpt.util.postOngoingNotification

/**
 * Android notification-listener boundary for capturing, filtering, storing, and lightly cancelling notifications.
 *
 * Keep framework callbacks and PendingIntent caching here. Business rules should live in repositories or
 * domain helpers so this service remains a thin adapter around Android's listener lifecycle.
 */
@AndroidEntryPoint
class NotiListenerService: NotificationListenerService() {

    private var componentName: ComponentName? = null

    companion object {
        private const val REBIND_DELAY_MS = 2_000L
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSize = maxMemory / 16 // Example: Use 1/16th of available heap for the cache size (in KB)
        val contentIntentCache = LruCache<String, PendingIntent>(cacheSize)
        val deleteIntentCache = LruCache<String, PendingIntent>(cacheSize)

        /**
         * Returns a launch PendingIntent for opening the app behind a notification key.
         *
         * The cache is best-effort framework state; Room should store only the metadata needed to request it.
         */
        fun getContentIntent(context: Context, notiUnit: NotiUnit): PendingIntent? {

            val notiKey = notiUnit.notiKey
            val packageName = notiUnit.metadata.pkgName

            val cachedIntent = contentIntentCache.get(notiKey)
            if (cachedIntent != null) {
                Log.d("NotiListenerService", "Using cached intent")
                return cachedIntent
            }

            return try {
                // A package-scoped launch intent can be sent without broad QUERY_ALL_PACKAGES
                // visibility. Resolution happens in the system when the PendingIntent is used.
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                Log.d("NotiListenerService", "Creating fallback intent for package: $packageName")
                PendingIntent.getActivity(
                    context,
                    notiKey.hashCode(),
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }  catch (_: Exception) {
                null
            }
        }

        fun getDeleteIntent(notiKey: String): PendingIntent? {
            return deleteIntentCache.get(notiKey)
        }

        fun removeIntents(notiKey: String) {
            contentIntentCache.remove(notiKey)
            deleteIntentCache.remove(notiKey)
        }
    }

    @Inject lateinit var notiRepository: NotiRepository
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(NotiListenerRestartReceiver.pendingIntent(this))
        SharedPreferencesManager.init(applicationContext)
        activeNotifications.forEach {
            addNotification(it, true)
        }
        createNotificationChannel(applicationContext)
    }

    override fun onListenerDisconnected() {

        if (componentName == null) {
            componentName = ComponentName(this, this::class.java)
        }

        componentName?.let { requestRebind(it) }

        super.onListenerDisconnected()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        scheduleRebindFallback()
        super.onDestroy()
    }

    /**
     * Schedules a short, inexact recovery alarm. The receiver requests a framework rebind; it does
     * not start this service directly, which would be unreliable under background-start limits.
     */
    private fun scheduleRebindFallback() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + REBIND_DELAY_MS,
            NotiListenerRestartReceiver.pendingIntent(this),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if(componentName == null) {
            componentName = ComponentName(this, this::class.java)
        }

        componentName?.let {
            requestRebind(it)
            toggleNotificationListenerService(it)
        }
        return START_REDELIVER_INTENT
    }

    private fun toggleNotificationListenerService(componentName: ComponentName) {
        val pm = packageManager
        pm.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        addNotification(sbn, false)
    }

    /**
     * Applies capture policy for one posted or initially discovered notification.
     *
     * Accepted notifications update the current drawer row, append a record, cache actions, and schedule the
     * lightweight cancel behavior that keeps Android's shade from duplicating the app drawer.
     */
    private fun addNotification(sbn: StatusBarNotification, isInit: Boolean) {

        val ignoreReason = NotificationFilter.ignoreReason(sbn, appPackageName = packageName)
        if (ignoreReason != null) {
            // Preserve behavior: only group summary triggers delayed cancel.
            if (ignoreReason == NotificationFilter.IgnoreReason.GROUP_SUMMARY) {
                serviceScope.launch {
                    delay(NOTI_REMOVE_DELAY)
                    cancelNotification(sbn.key)
                }
            }
            return
        }

        // Store notification to DB
        serviceScope.launch {
            notiRepository.upsertNotiUnit(applicationContext, sbn, isInit)
            notiRepository.insertNotiRecord(sbn)

            // Debug: log visible record count and sample ids for this key
            try {
                val count = notiRepository.getActiveRecordsCountForKey(sbn.key)
                val sampleIds = notiRepository.getActiveRecordIdsForKey(sbn.key, limit = 5)
                Log.d("NotiListenerService", "Inserted record for key=${sbn.key}; activeCount=$count; sampleIds=${sampleIds.joinToString(separator = ",")}")
            } catch (e: Exception) {
                Log.d("NotiListenerService", "Debug logging failed for key=${sbn.key}", e)
            }
        }

        val notification = sbn.notification
        if (notification != null) {
            notification.contentIntent?.let {
                Log.d("NotiListenerService", "Caching content intent")
                contentIntentCache.put(sbn.key, it)
            }
            notification.deleteIntent?.let {
                Log.d("NotiListenerService", "Caching delete intent")
                deleteIntentCache.put(sbn.key, it)
            }
//            enqueueUpdateNotification(applicationContext, sbn.key)
            postOngoingNotification(applicationContext)
        }

        if (!isInit) {
            serviceScope.launch {
                delay(NOTI_REMOVE_DELAY)
                cancelNotification(sbn.key)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (reason in setOf(REASON_LISTENER_CANCEL, REASON_GROUP_SUMMARY_CANCELED, REASON_GROUP_OPTIMIZATION))
            return

//        serviceScope.launch {
//            notiRepository.removeNotiUnit(sbn.key)
//            postOngoingNotification(applicationContext)
//        }
    }
}
