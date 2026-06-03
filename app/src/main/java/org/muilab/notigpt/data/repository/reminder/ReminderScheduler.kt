package org.muilab.notigpt.data.repository.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.muilab.notigpt.receiver.ReminderAlarmReceiver
import org.muilab.notigpt.receiver.ReminderSeenReceiver

object ReminderScheduler {
    private const val REQUEST_CODE_BASE = 41000

    fun schedule(context: Context, reminderId: String, remindAtMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = alarmPendingIntent(context, reminderId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAtMs, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAtMs, pendingIntent)
    }

    fun cancel(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context, reminderId, PendingIntent.FLAG_NO_CREATE) ?: return)
    }

    fun seenPendingIntent(context: Context, reminderId: String): PendingIntent {
        val intent = Intent(context, ReminderSeenReceiver::class.java).apply {
            action = ReminderSeenReceiver.ACTION_MARK_SEEN
            putExtra(ReminderSeenReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(reminderId) + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmPendingIntent(context: Context, reminderId: String, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_FIRE_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(reminderId),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(reminderId: String): Int = REQUEST_CODE_BASE + reminderId.hashCode().and(0x3fffffff)
}
