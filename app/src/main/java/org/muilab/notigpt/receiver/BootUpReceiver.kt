package org.muilab.notigpt.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.muilab.notigpt.service.NotiListenerService

class BootUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            val notiServiceIntent = Intent(context, NotiListenerService::class.java)
            context.startService(notiServiceIntent)
        }
    }
}

