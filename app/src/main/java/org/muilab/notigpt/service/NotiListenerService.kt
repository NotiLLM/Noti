package org.muilab.notigpt.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.collection.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.muilab.notigpt.database.server.enqueueUpdateNotification
import org.muilab.notigpt.domain.notification.NotificationFilter
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.repository.NotiRepository
import org.muilab.notigpt.repository.NotiRepositoryProvider
import org.muilab.notigpt.util.Constants.Companion.NOTI_REMOVE_DELAY
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.createNotificationChannel
import org.muilab.notigpt.util.postOngoingNotification

class NotiListenerService: NotificationListenerService() {

    private var componentName: ComponentName? = null

    companion object {
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSize = maxMemory / 16 // Example: Use 1/16th of available heap for the cache size (in KB)
        val contentIntentCache = LruCache<String, PendingIntent>(cacheSize)
        val deleteIntentCache = LruCache<String, PendingIntent>(cacheSize)

        fun getContentIntent(context: Context, notiUnit: NotiUnit): PendingIntent? {

            val notiKey = notiUnit.notiKey
            val packageName = notiUnit.metadata.pkgName

            val cachedIntent = contentIntentCache.get(notiKey)
            if (cachedIntent != null) {
                Log.d("NotiListenerService", "Using cached intent")
                return cachedIntent
            }

            return try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    Log.d("NotiListenerService", "Creating new intent for package: $packageName")
                    PendingIntent.getActivity(
                        context,
                        notiKey.hashCode(),
                        launchIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                } else {
                    null
                }
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

    private lateinit var notiRepository: NotiRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onListenerConnected() {
        super.onListenerConnected()
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
        notiRepository = NotiRepositoryProvider.provideNotiRepository(applicationContext)
    }

    override fun onDestroy() {
//        val restartServiceIntent = Intent(applicationContext, NotiListenerService::class.java).also {
//            it.setPackage(packageName)
//        }
//        val restartServicePendingIntent: PendingIntent = PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
//        getSystemService(ALARM_SERVICE)
//        val alarmService: AlarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
//        alarmService.set(AlarmManager.ELAPSED_REALTIME, System.currentTimeMillis() + 2000, restartServicePendingIntent)
        super.onDestroy()
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

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        addNotification(sbn, false)
    }

    @RequiresApi(Build.VERSION_CODES.S)
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
                val count = notiRepository.getVisibleRecordsCountForKey(sbn.key)
                val sampleIds = notiRepository.getVisibleRecordIdsForKey(sbn.key, limit = 5)
                Log.d("NotiListenerService", "Inserted record for key=${sbn.key}; visibleCount=$count; sampleIds=${sampleIds.joinToString(separator = ",")}")
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
            enqueueUpdateNotification(applicationContext, sbn.key)
            postOngoingNotification(applicationContext)
        }

        if (!isInit) {
            serviceScope.launch {
                delay(NOTI_REMOVE_DELAY)
                cancelNotification(sbn.key)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (reason in setOf(REASON_LISTENER_CANCEL, REASON_GROUP_SUMMARY_CANCELED, REASON_GROUP_OPTIMIZATION))
            return

//        serviceScope.launch {
//            notiRepository.removeNotiUnit(sbn.key)
//            postOngoingNotification(applicationContext)
//        }
    }
}