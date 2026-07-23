package org.muilab.notigpt.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import org.muilab.notigpt.util.SharedPreferencesManager
import java.util.concurrent.TimeUnit

/**
 * Scheduling helper for the notification extraction safety-net worker.
 *
 * Keep unique WorkManager names and enqueue policy here so the app can schedule or kick extraction from
 * multiple entry points without duplicating worker setup.
 */
object ExtractionPeriodicWork {

    private const val TAG = "ExtractionPeriodicWork"
    private const val UNIQUE_NAME = "extraction_periodic_scan"
    private const val UNIQUE_KICK_NAME = "extraction_periodic_scan_kick"
    private const val LEGACY_UNIQUE_NAME = "reminder_periodic_scan_extract"
    private const val LEGACY_UNIQUE_KICK_NAME = "reminder_periodic_scan_extract_kick"

    /** For diagnostics / UI. */
    fun uniqueName(): String = UNIQUE_NAME

    /**
     * Gated entry point every caller should use instead of [enqueue] directly.
     *
     * n8n itself never checks access (see plans/3-invitation-and-llm-usage.md's accepted-risk
     * decision), so this cached [SharedPreferencesManager.hasAccess] check is the only thing that
     * stops a signed-in-but-not-yet-invited account from silently accruing LLM cost in the
     * background before it ever reaches AppScaffold.
     */
    fun enqueueIfEntitled(context: Context) {
        if (SharedPreferencesManager.hasAccess) enqueue(context)
    }

    /** Gated entry point every caller should use instead of [kickNow] directly; see [enqueueIfEntitled]. */
    fun kickNowIfEntitled(context: Context) {
        if (SharedPreferencesManager.hasAccess) kickNow(context)
    }

    /**
     * Schedules a periodic "safety-net" run.
     *
     * Important: periodic WorkManager is inexact and can be delayed by Doze/App Standby.
     * We keep constraints minimal so it stays eligible as often as possible.
     */
    fun enqueue(context: Context) {
        val req = PeriodicWorkRequest.Builder(
            ExtractionPeriodicWorker::class.java,
            15,
            TimeUnit.MINUTES,
        )
            // Intentionally no network constraint: this worker mostly checks local DB and enqueues
            // downstream work that can apply its own network constraints/retries.
            //
            // Also no explicit backoff: periodic work will run again next period; retry/backoff can
            // easily push executions out by hours.
            .build()

        WorkManager.getInstance(context).apply {
            cancelUniqueWork(LEGACY_UNIQUE_NAME)
            enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        Log.i(TAG, "Enqueued unique periodic work name=$UNIQUE_NAME id=${req.id}")
    }

    /**
     * Trigger an immediate one-time run (kept unique + replace) to "wake" the pipeline when the
     * user opens the app.
     */
    fun kickNow(context: Context) {
        val req = OneTimeWorkRequest.Builder(ExtractionPeriodicWorker::class.java)
            .build()

        WorkManager.getInstance(context).apply {
            cancelUniqueWork(LEGACY_UNIQUE_KICK_NAME)
            enqueueUniqueWork(UNIQUE_KICK_NAME, ExistingWorkPolicy.REPLACE, req)
        }

        Log.i(TAG, "Enqueued one-time kick work name=$UNIQUE_KICK_NAME id=${req.id}")
    }

}
