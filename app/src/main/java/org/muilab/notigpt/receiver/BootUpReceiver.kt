package org.muilab.notigpt.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.muilab.notigpt.work.EsmTriggerCWork
import org.muilab.notigpt.work.ReminderPeriodicWork

class BootUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            Log.i("BootUpReceiver", "BOOT_COMPLETED received; ensuring periodic work is scheduled")

            // On modern Android versions, starting background services from boot is heavily restricted
            // and can cause the receiver to fail early. The notification listener will be rebound by
            // the system once the user has enabled it.

            // Ensure periodic scan/extract restarts after reboot.
            ReminderPeriodicWork.enqueue(context.applicationContext)

            // Ensure periodic Trigger C check restarts after reboot.
            EsmTriggerCWork.enqueue(context.applicationContext)
        }
    }
}
