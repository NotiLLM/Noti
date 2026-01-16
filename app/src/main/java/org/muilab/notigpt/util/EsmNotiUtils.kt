package org.muilab.notigpt.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.MainActivity
import org.muilab.notigpt.database.room.AppDatabase

private const val ESM_CHANNEL_ID = "notigpt_esm"
private const val ESM_NOTIFICATION_ID = 45
private const val ESM_GROUP_KEY = "notigpt_esm_group"

fun createEsmNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
        ESM_CHANNEL_ID,
        "NotiGPT ESM",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Experience sampling questionnaire availability"
        setShowBadge(true)
        setSound(null, null)
        enableVibration(false)
    }
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(channel)
}

/**
 * Posts a separate indicator notification if any ESM is AVAILABLE.
 * Clears it when none are available.
 */
fun postEsmIndicatorNotification(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
        createEsmNotificationChannel(context)
        val db = AppDatabase.getInstance(context)
        val esmDao = db.esmDao()
        val currentTime = System.currentTimeMillis()
        val availableCount = esmDao.getUnexpiredAvailable(currentTime).size

        val nm = ContextCompat.getSystemService(context, NotificationManager::class.java) as NotificationManager

        if (availableCount <= 0) {
            nm.cancel(ESM_NOTIFICATION_ID)
            return@launch
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_esm", true)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            4545,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, ESM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("NotiGPT questionnaire")
            .setContentText("You have $availableCount questionnaire(s) to answer")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSortKey("2_esm")
            .setGroup(ESM_GROUP_KEY)
            .setGroupSummary(false)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(ESM_NOTIFICATION_ID, notification)
    }
}
