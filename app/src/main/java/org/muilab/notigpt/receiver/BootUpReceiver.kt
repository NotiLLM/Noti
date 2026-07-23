package org.muilab.notigpt.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.repository.reminder.ScheduledReminderRepository
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.work.ExtractionPeriodicWork

/**
 * Broadcast receiver that restarts reminder/background scheduling after device boot.
 *
 * Keep this receiver limited to idempotent scheduling. Long-running work should be delegated to WorkManager or
 * app services so boot handling returns quickly.
 */
class BootUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            Log.i("BootUpReceiver", "BOOT_COMPLETED received; ensuring periodic work is scheduled")

            // On modern Android versions, starting background services from boot is heavily restricted
            // and can cause the receiver to fail early.

            // A fresh boot may deliver this broadcast before any Activity has run, so
            // SharedPreferencesManager (which enqueueIfEntitled reads hasAccess from) may not be
            // initialized yet in this process.
            SharedPreferencesManager.init(context.applicationContext)

            // Ensure periodic scan/extract restarts after reboot, only if already entitled.
            ExtractionPeriodicWork.enqueueIfEntitled(context.applicationContext)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context.applicationContext)
                    ScheduledReminderRepository(context.applicationContext, db.reminderDao())
                        .scheduleExistingFutureReminders()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
