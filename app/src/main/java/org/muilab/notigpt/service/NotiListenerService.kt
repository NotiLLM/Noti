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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.muilab.notigpt.database.room.DrawerDatabase
import org.muilab.notigpt.database.server.enqueueUpdateNotification
import org.muilab.notigpt.database.server.workers.DifyAPIWorker
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.util.Constants.Companion.NOTI_REMOVE_DELAY
import org.muilab.notigpt.util.Constants.Companion.DIFY_UPDATE_NOTIFICATION
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.createNotificationChannel
import org.muilab.notigpt.util.postOngoingNotification
import java.util.concurrent.TimeUnit

class NotiListenerService: NotificationListenerService() {

    companion object {
        val pendingIntents = mutableMapOf<String, PendingIntent>()

        fun getPendingIntent(context: Context, notiUnit: NotiUnit): PendingIntent? {
            val sbnKey = notiUnit.metadata.sbnKey
            if (sbnKey in pendingIntents)
                return pendingIntents[sbnKey]

            val launchIntent = context.packageManager.getLaunchIntentForPackage(notiUnit.metadata.pkgName)
            return if (launchIntent != null)
                PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)
            else
                null
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

            if (existingNoti.isEmpty()) {
                drawerDao.insert(newNoti)
            } else if (!isInit) {
                existingNoti[0].updateNoti(applicationContext, sbn)
                drawerDao.update(existingNoti[0])
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
            if (existingNoti.isNotEmpty()) {
                existingNoti[0].removeNoti()
                drawerDao.update(existingNoti[0])
            }
            postOngoingNotification(applicationContext)
        }
    }

}