package org.muilab.notigpt.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.data.local.room.AppDatabase

class ReminderSeenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_SEEN) return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(context.applicationContext)
                    .reminderDao()
                    .setSeen(reminderId = reminderId, seenAtMs = System.currentTimeMillis())
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_SEEN = "org.muilab.notigpt.action.MARK_REMINDER_SEEN"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}
