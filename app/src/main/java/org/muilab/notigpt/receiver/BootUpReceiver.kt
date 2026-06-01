package org.muilab.notigpt.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.muilab.notigpt.work.ReminderPeriodicWork

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

            // Ensure periodic scan/extract restarts after reboot.
            ReminderPeriodicWork.enqueue(context.applicationContext)
        }
    }
}
