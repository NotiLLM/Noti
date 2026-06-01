package org.muilab.notigpt.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Scheduling helper for the reminder scan/extraction safety-net worker.
 *
 * Keep unique WorkManager names and enqueue policy here so the app can schedule or kick reminder processing from
 * multiple entry points without duplicating worker setup.
 */
object ReminderPeriodicWork {

    private const val TAG = "ReminderPeriodicWork"
    private const val UNIQUE_NAME = "reminder_periodic_scan_extract"
    private const val UNIQUE_KICK_NAME = "reminder_periodic_scan_extract_kick"

    /** For diagnostics / UI. */
    fun uniqueName(): String = UNIQUE_NAME

    /**
     * Schedules a periodic "safety-net" run.
     *
     * Important: periodic WorkManager is inexact and can be delayed by Doze/App Standby.
     * We keep constraints minimal so it stays eligible as often as possible.
     */
    fun enqueue(context: Context) {
        val req = PeriodicWorkRequest.Builder(
            ReminderPeriodicWorker::class.java,
            15,
            TimeUnit.MINUTES,
        )
            // Intentionally no network constraint: this worker mostly checks local DB and enqueues
            // downstream work that can apply its own network constraints/retries.
            //
            // Also no explicit backoff: periodic work will run again next period; retry/backoff can
            // easily push executions out by hours.
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, req)

        Log.i(TAG, "Enqueued unique periodic work name=$UNIQUE_NAME id=${req.id}")
    }

    /**
     * Trigger an immediate one-time run (kept unique + replace) to "wake" the pipeline when the
     * user opens the app.
     */
    fun kickNow(context: Context) {
        val req = OneTimeWorkRequest.Builder(ReminderPeriodicWorker::class.java)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_KICK_NAME, ExistingWorkPolicy.REPLACE, req)

        Log.i(TAG, "Enqueued one-time kick work name=$UNIQUE_KICK_NAME id=${req.id}")
    }

    /** Logs current state of the unique periodic work (useful when debugging 'not running'). */
    fun logStatus(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val infos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWorkFlow(UNIQUE_NAME)
                    .firstOrNull()
                    .orEmpty()

                if (infos.isEmpty()) {
                    Log.w(TAG, "No WorkInfos found for $UNIQUE_NAME (not scheduled?)")
                    return@launch
                }

                infos.forEach { info: WorkInfo ->
                    Log.i(
                        TAG,
                        "Status for $UNIQUE_NAME: id=${info.id} state=${info.state} runAttemptCount=${info.runAttemptCount}",
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to query WorkManager status for $UNIQUE_NAME", t)
            }
        }
    }
}
