package org.muilab.notigpt.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.remote.firestore.FirestoreRestoreRepository
import org.muilab.notigpt.data.remote.n8n.enqueueExtractionPipeline
import org.muilab.notigpt.data.remote.n8n.enqueueReflectionPipeline
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Periodic safety-net for the per-notiKey extraction pipeline and the reflection merge pass.
 *
 * Foreground record capture already kicks the pipeline per thread; this worker re-drives threads
 * with unprocessed records (past their fold watermark) in case a foreground trigger was missed or a
 * thread went quiet and needs compaction, and fires the cross-thread reflection merge once a day.
 */
@HiltWorker
class ExtractionPeriodicWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            SharedPreferencesManager.init(applicationContext)
        } catch (_: Exception) {
            // Best-effort; still proceed.
        }

        SharedPreferencesManager.lastExtractionPeriodicRunTime = System.currentTimeMillis()
        Log.i(TAG, "doWork start; runAttemptCount=$runAttemptCount")

        if (runAttemptCount >= MAX_RETRIES) {
            Log.w(TAG, "Giving up for this period after runAttemptCount=$runAttemptCount")
            return Result.success()
        }

        // Reconcile the cloud mirror before nudging extraction. This both backfills devices that
        // were offline before Firestore was added and replays failed writes on later runs.
        if (FirebaseAuth.getInstance().currentUser != null) {
            try {
                FirestoreRestoreRepository(applicationContext).reconcileAfterSignIn()
            } catch (t: Throwable) {
                Log.w(TAG, "Firestore reconciliation failed; will retry on a later run", t)
            }
        }

        val drawerDao = database.drawerDao()
        val recordDao = database.recordDao()
        val journalDao = database.extractionJournalDao()

        // === Extraction pass ===
        // Re-drive active threads that still have records past their fold watermark. The pipeline
        // itself decides whether to scan/extract/compact, so this is just a nudge.
        val activeKeys = drawerDao.getAllActiveKeys()
        var nudged = 0
        for (key in activeKeys) {
            val watermark = try {
                journalDao.getSummary(key)?.lastFoldedPostTime ?: 0L
            } catch (_: Exception) {
                0L
            }
            val pending = try {
                recordDao.getRecordCountByKeyAfter(key, watermark)
            } catch (_: Exception) {
                0
            }
            if (pending > 0) {
                enqueueExtractionPipeline(applicationContext, key)
                nudged++
            }
        }

        // === Reflection pass (daily) ===
        val now = System.currentTimeMillis()
        val sinceReflection = now - SharedPreferencesManager.lastReflectionRunTime
        if (sinceReflection >= REFLECTION_INTERVAL_MS) {
            SharedPreferencesManager.lastReflectionRunTime = now
            enqueueReflectionPipeline(applicationContext)
        }

        Log.i(TAG, "doWork end; nudgedKeys=$nudged")
        return Result.success()
    }

    companion object {
        private const val TAG = "ExtractionPeriodicWorker"
        private const val MAX_RETRIES = 3

        /** Reflection merge cadence: once a day. */
        private const val REFLECTION_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
