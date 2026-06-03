package org.muilab.notigpt.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.R
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.repository.reminder.ReminderScheduler
import org.muilab.notigpt.model.features.ReminderStatus

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE_REMINDER) return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context.applicationContext)
                val dao = db.reminderDao()
                val reminder = dao.getById(reminderId) ?: return@launch
                if (reminder.status != ReminderStatus.Scheduled) return@launch
                dao.markDueUnseen(reminderId = reminderId, updatedAtMs = System.currentTimeMillis())
                postReminderNotification(context.applicationContext, reminderId, reminder.title, reminder.content)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postReminderNotification(context: Context, reminderId: String, title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.schedule)
            .setContentTitle(title.ifBlank { "Reminder" })
            .setContentText(content.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(ReminderScheduler.seenPendingIntent(context, reminderId))
            .build()

        NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification)
    }

    companion object {
        const val ACTION_FIRE_REMINDER = "org.muilab.notigpt.action.FIRE_REMINDER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        private const val CHANNEL_ID = "scheduled_reminders"
    }
}
