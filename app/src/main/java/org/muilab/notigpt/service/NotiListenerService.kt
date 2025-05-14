package org.muilab.notigpt.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.collection.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.muilab.notigpt.database.room.DrawerDatabase
import org.muilab.notigpt.database.server.enqueueUpdateNotification
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.util.Constants.Companion.NOTI_REMOVE_DELAY
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.createNotificationChannel
import org.muilab.notigpt.util.postOngoingNotification

class NotiListenerService: NotificationListenerService() {

    companion object {
        // Determine a suitable cache size. This example uses 1/16th of available app memory in KB
        // assuming each PendingIntent takes roughly 1KB (adjust as needed).
        // Or, more simply, set a fixed number like 250 or 500 based on expected usage.
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSize = maxMemory / 16 // Example: Use 1/16th of available heap for the cache size (in KB)
        // Alternatively, use a fixed count: private const val cacheSize = 250

        // Use LruCache instead of MutableMap
        val contentIntentCache = LruCache<String, PendingIntent>(cacheSize)
        val deleteIntentCache = LruCache<String, PendingIntent>(cacheSize)

        // --- Corrected getContentIntent ---
        // It should ONLY return the cached intent or null.
        // The fallback created the WRONG intent (generic launcher).
        fun getContentIntent(context: Context, notiUnit: NotiUnit): PendingIntent? {

            val notiKey = notiUnit.notiKey
            val packageName = notiUnit.metadata.pkgName

            // 1. Try getting the original intent from the cache
            val cachedIntent = contentIntentCache.get(notiKey)
            if (cachedIntent != null) {
                Log.d("NotiListenerService", "Using cached intent")
                return cachedIntent
            }

            // 2. If not cached and packageName is provided, try creating a fallback launch intent
            return try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    // Make sure the launch intent doesn't inherit flags that might cause issues
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    Log.d("NotiListenerService", "Creating new intent for package: $packageName")
                    PendingIntent.getActivity(
                        context,
                        notiKey.hashCode(), // Use key's hashcode for semi-unique request code
                        launchIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT // Update if package reinstalled/updated
                    )
                } else {
                    null // Package might be uninstalled or has no launch activity
                }
            }  catch (e: Exception) {
                null // Catch any potential errors
            }
        }

        // --- Corrected getDeleteIntent ---
        fun getDeleteIntent(notiKey: String): PendingIntent? {
            return deleteIntentCache.get(notiKey) // Get from cache or null if not found/evicted
        }

        // --- Method to explicitly remove intents when no longer needed ---
        // Call this from onNotificationRemoved or when user deletes from your archive UI
        fun removeIntents(notiKey: String) {
            contentIntentCache.remove(notiKey)
            deleteIntentCache.remove(notiKey)
            // remove other intents if you cache them (e.g., actions)
        }
    }

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
        requestRebind(ComponentName(this, NotiListenerService::class.java))
        try {

        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onListenerDisconnected()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        val restartServiceIntent = Intent(applicationContext, NotiListenerService::class.java).also {
            it.setPackage(packageName)
        }
        val restartServicePendingIntent: PendingIntent = PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
        getSystemService(ALARM_SERVICE)
        val alarmService: AlarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmService.set(AlarmManager.ELAPSED_REALTIME, System.currentTimeMillis() + 2000, restartServicePendingIntent)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        addNotification(sbn, false)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun addNotification(sbn: StatusBarNotification, isInit: Boolean) {

        if (sbn.packageName.equals(packageName) || sbn.isOngoing || !sbn.isClearable)
            return

        if ((sbn.notification?.flags as Int and Notification.FLAG_GROUP_SUMMARY) > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(NOTI_REMOVE_DELAY)
                cancelNotification(sbn.key)
            }
            return
        }

        val notiStyle = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEMPLATE)
        if (notiStyle == Notification.MediaStyle::class.java.canonicalName)
            return

        CoroutineScope(Dispatchers.IO).launch {
            val drawerDatabase = DrawerDatabase.getInstance(applicationContext)
            val drawerDao = drawerDatabase.drawerDao()
            val existingNoti = drawerDao.getBySbnKey(sbn.key)
            val newNoti = NotiUnit(applicationContext, sbn)

            if (existingNoti == null) {
                drawerDao.insert(newNoti)
            } else if (!isInit) {
                existingNoti.updateNoti(applicationContext, sbn)
                drawerDao.update(existingNoti)
            }

            // --- Cache the Intents ---
            val notification = sbn.notification
            if (notification != null) {
                notification.contentIntent?.let { // Use safe call ?.let
                    Log.d("NotiListenerService", "Caching content intent")
                    contentIntentCache.put(sbn.key, it)
                }
                notification.deleteIntent?.let { // Use safe call ?.let
                    Log.d("NotiListenerService", "Caching delete intent")
                    deleteIntentCache.put(sbn.key, it)
                }
                // Add caching for notification.actions[i].actionIntent if needed
            }

            enqueueUpdateNotification(applicationContext, sbn.key)

            postOngoingNotification(applicationContext)
            if (!isInit) {
                delay(NOTI_REMOVE_DELAY)
                cancelNotification(sbn.key)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (reason in setOf(REASON_LISTENER_CANCEL, REASON_GROUP_SUMMARY_CANCELED, REASON_GROUP_OPTIMIZATION))
            return
        CoroutineScope(Dispatchers.IO).launch {
            val drawerDatabase = DrawerDatabase.getInstance(applicationContext)
            val drawerDao = drawerDatabase.drawerDao()
            val existingNoti = drawerDao.getBySbnKey(sbn.key)
            if (existingNoti != null) {
                existingNoti.removeNoti()
                drawerDao.update(existingNoti)
            }
            postOngoingNotification(applicationContext)
        }
    }

}