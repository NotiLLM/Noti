package org.muilab.notigpt.domain.esm

import android.app.ActivityManager
import android.content.Context

/**
 * Small helper for deciding when to enqueue ESM delivery.
 *
 * Contract:
 * - Foreground means the app process is considered foreground by the system.
 * - This is best-effort (OEMs may behave differently), but is good enough for "enqueue now vs on next app open".
 */
object EsmTriggerPolicy {

    fun isAppInForeground(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val myPkg = context.packageName
        val procs = am.runningAppProcesses ?: return false
        val mine = procs.firstOrNull { it.processName == myPkg } ?: return false

        return mine.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            mine.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }
}

