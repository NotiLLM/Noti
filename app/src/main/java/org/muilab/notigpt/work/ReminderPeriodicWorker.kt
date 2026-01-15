package org.muilab.notigpt.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.database.server.enqueueTaskExtraction
import org.muilab.notigpt.database.server.enqueueTaskScan

/**
 * Periodic safety-net for reminder scan + extraction.
 *
 * Motivation: scan/extraction was previously only triggered by new notifications hitting counters.
 * This periodically checks the DB so reminders still get extracted even in quiet periods.
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

        // === Extraction pass ===
        val extractKeys = try {
            drawerDao.getAllActive().filter { it.shouldExtractReminder }.map { it.notiKey }
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
    }
}
