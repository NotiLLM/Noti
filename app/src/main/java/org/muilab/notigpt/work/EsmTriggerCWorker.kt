package org.muilab.notigpt.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.database.server.esm.enqueueEsmDelivery
import org.muilab.notigpt.domain.esm.EsmConfig
import org.muilab.notigpt.domain.esm.EsmScheduling
import org.muilab.notigpt.domain.esm.EsmTriggerPolicy
import org.muilab.notigpt.domain.esm.EsmTriggerTypes
import org.muilab.notigpt.repository.EsmRepository

/**
 * Periodic/timed check for Trigger C.
 *
 * Contract:
 * - If >= 2 hours since the last ESM was shown (AVAILABLE) or answered, create a Trigger C ESM
 *   immediately for a best-effort candidate reminder.
 * - This is intentionally decoupled from reminder extraction.
 */
class EsmTriggerCWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Workers can run before any Activity is launched.
        try { org.muilab.notigpt.util.SharedPreferencesManager.init(applicationContext) } catch (_: Exception) {}

        val db = AppDatabase.getInstance(applicationContext)
        val esmDao = db.esmDao()
        val reminderDao = db.reminderListDao()

        val nowMs = System.currentTimeMillis()
        val lastAnsweredAt = esmDao.getLastAnsweredAt() ?: 0L
        val lastAvailableAt = esmDao.getLastAvailableAt() ?: 0L
        val lastActivityAt = maxOf(lastAnsweredAt, lastAvailableAt)

        val okToTrigger = lastActivityAt <= 0L || (nowMs - lastActivityAt) >= EsmConfig.TRIGGER_C_NO_TRIGGER_WINDOW_MS
        if (!okToTrigger) {
            val deltaMin = ((nowMs - lastActivityAt) / 60_000L)
            val windowMin = (EsmConfig.TRIGGER_C_NO_TRIGGER_WINDOW_MS / 60_000L)
            Log.d(TAG, "Skip Trigger C (within no-trigger window): sinceLastActivity=${deltaMin}min < ${windowMin}min lastAnsweredAt=$lastAnsweredAt lastAvailableAt=$lastAvailableAt")
            return Result.success()
        }

        // Pick candidate reminder for Trigger C.
        // Only auto-extracted tasks (associatedNotis not empty).
        val tasksVisible = try { reminderDao.observeTasks().first() } catch (_: Exception) { emptyList() }
        val autoExtracted = tasksVisible.filter { it.associatedNotis.isNotEmpty() }

        if (autoExtracted.isEmpty()) {
            Log.d(TAG, "Skip Trigger C: no auto-extracted tasks visible (tasksVisible=${tasksVisible.size})")
            return Result.success()
        }

        val pickedId = autoExtracted.firstOrNull { !it.isCompleted }?.reminderId
            ?: autoExtracted.firstOrNull { it.isCompleted }?.reminderId
            ?: autoExtracted.firstOrNull()?.reminderId

        if (pickedId.isNullOrBlank()) {
            Log.d(TAG, "Skip Trigger C: failed to pick reminderId")
            return Result.success()
        }

        val esmRepo = EsmRepository(applicationContext)
        if (esmRepo.hasAnsweredOrAvailableInstanceForReminder(pickedId)) {
            // Trigger C reuse policy: block if user already answered for this reminder OR one is currently available.
            Log.d(TAG, "Skip Trigger C: reminderId=$pickedId already has an ANSWERED/AVAILABLE ESM instance")
            return Result.success()
        }

        // We need a snapshotId to bind the ESM. Use the reminder's extraction snapshot if available.
        val reminder = esmRepo.getReminder(pickedId)
        val snapshotId = reminder?.extractionSnapshotId
        if (snapshotId.isNullOrBlank()) {
            // No snapshot to display context; skip Trigger C.
            Log.d(TAG, "Skip Trigger C: reminderId=$pickedId has no extractionSnapshotId")
            return Result.success()
        }

        return try {
            val inst = esmRepo.createEsmForSnapshot(
                reminderId = pickedId,
                snapshotId = snapshotId,
                triggerType = EsmTriggerTypes.C_AUTO_GENERATED,
                availableDelayMs = 0L,
            )

            if (EsmTriggerPolicy.isAppInForeground(applicationContext)) {
                Log.i(TAG, "Trigger C created instanceId=${inst.instanceId}; appInForeground=true -> enqueue delivery now")
                enqueueEsmDelivery(applicationContext, inst.instanceId, 0L)
            } else {
                Log.i(TAG, "Trigger C created instanceId=${inst.instanceId}; appInForeground=false -> addPendingEnqueue")
                EsmScheduling.addPendingEnqueue(applicationContext, inst.instanceId)
            }

            Log.i(TAG, "Trigger C fired for reminderId=$pickedId instanceId=${inst.instanceId}")
            Result.success()
        } catch (_: IllegalStateException) {
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed firing Trigger C", t)
            Result.success()
        }
    }

    companion object {
        private const val TAG = "EsmTriggerCWorker"
    }
}
