package org.muilab.notigpt.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.remote.n8n.enqueueTaskExtraction
import org.muilab.notigpt.data.remote.n8n.enqueueTaskScan

/**
 * Periodic safety-net for reminder scan and extraction.
 *
 * This worker checks local notification state for unscanned records and extraction-ready keys, then enqueues the
 * same n8n jobs used by foreground flows so reminders still progress during quiet periods.
 */
class ReminderPeriodicWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Workers can run before any Activity is launched.
        try {
            org.muilab.notigpt.util.SharedPreferencesManager.init(applicationContext)
        } catch (_: Exception) {
            // Best-effort; still proceed.
        }

        org.muilab.notigpt.util.SharedPreferencesManager.lastReminderPeriodicRunTime = System.currentTimeMillis()
        Log.i(TAG, "doWork start; runAttemptCount=$runAttemptCount")

        // Limited retries to avoid thrashing. WorkManager will reschedule next period anyway.
        if (runAttemptCount >= MAX_RETRIES) {
            Log.w(TAG, "Giving up for this period after runAttemptCount=$runAttemptCount")
            return Result.success()
        }

        val db = AppDatabase.getInstance(applicationContext)
        val drawerDao = db.drawerDao()
        val recordDao = db.recordDao()

        // === Scan pass ===
        // Find active notifications that still have unscanned records.
        val activeKeys = drawerDao.getAllActiveKeys()
        val scanKeys = activeKeys.filter { key ->
            try {
                recordDao.getUnscannedRecordsByKey(key).isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }

        if (scanKeys.isNotEmpty()) {
            Log.d(TAG, "Periodic scan: enqueuing scan for ${scanKeys.size} keys")
            // Enqueue one scan per key; handler batches records per key.
            scanKeys.forEach { key ->
                enqueueTaskScan(applicationContext, key)
            }
        }

        // === Classification re-check pass (rare) ===
        // ~95% of threads never change category; re-classify only when a thread's record count
        // has doubled since it was last classified, or the classification has gone stale.
        val reclassKeys = try {
            val llmStateDao = db.notiLlmStateDao()
            val now = System.currentTimeMillis()
            activeKeys.filter { key ->
                if (key in scanKeys) return@filter false // a normal scan is already on its way
                val state = llmStateDao.getByKey(key) ?: return@filter false
                if (state.lastClassifiedAt <= 0L) return@filter false
                val total = recordDao.getRecordCountByKey(key)
                val grewTwofold = state.lastClassifiedRecordCount > 0 &&
                    total >= state.lastClassifiedRecordCount * 2
                val stale = now - state.lastClassifiedAt > RECLASSIFY_MAX_AGE_MS
                grewTwofold || stale
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (reclassKeys.isNotEmpty()) {
            Log.d(TAG, "Periodic reclassification: enqueuing for ${reclassKeys.size} keys")
            reclassKeys.forEach { key ->
                enqueueTaskScan(applicationContext, key, forceReclassify = true)
            }
        }

        // === Extraction pass ===
        val extractKeys = try {
            db.notiLlmStateDao().getActiveShouldExtractKeys()
        } catch (_: Exception) {
            emptyList()
        }

        if (extractKeys.isNotEmpty()) {
            Log.d(TAG, "Periodic extraction: enqueuing extraction for ${extractKeys.size} keys")
            // Use the normal non-user-triggered extraction flow.
            enqueueTaskExtraction(applicationContext, extractKeys, userTriggered = false)
        }

        Log.i(TAG, "doWork end; scannedKeys=${scanKeys.size} extractKeys=${extractKeys.size}")
        return Result.success()
    }

    companion object {
        private const val TAG = "ReminderPeriodicWorker"
        private const val MAX_RETRIES = 3

        /** Classification older than this is re-checked even without record growth. */
        private const val RECLASSIFY_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
    }
}
