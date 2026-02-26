package org.muilab.notigpt.database.server.workers.esm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.database.server.esm.enqueueEsmDelivery
import org.muilab.notigpt.domain.esm.EsmReceptiveWindow
import org.muilab.notigpt.domain.esm.EsmStatuses
import org.muilab.notigpt.util.SharedPreferencesManager
// TODO: ESM notifications disabled
// import org.muilab.notigpt.util.postEsmIndicatorNotification

class EsmDeliveryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Workers can run before any Activity is launched.
        try { SharedPreferencesManager.init(applicationContext) } catch (_: Exception) {}

        val instanceId = inputData.getString(KEY_INSTANCE_ID) ?: return Result.success()
        val db = AppDatabase.getInstance(applicationContext)
        val esmDao = db.esmDao()

        val inst = esmDao.getInstance(instanceId) ?: return Result.success()
        val now = System.currentTimeMillis()

        // If already answered/discarded, no-op.
        if (inst.status !in listOf(EsmStatuses.PENDING, EsmStatuses.AVAILABLE)) {
            return Result.success()
        }

        // Expire if too late.
        if (now > inst.expiresAt) {
            esmDao.setInstanceStatus(instanceId, EsmStatuses.EXPIRED)
            // TODO: ESM notifications disabled
            // postEsmIndicatorNotification(applicationContext)
            return Result.success()
        }

        // Extra robustness: if we're outside the user's receptive window, postpone delivery.
        val postponeMs = EsmReceptiveWindow.delayUntilNextReceptive(now)
        if (postponeMs > 0L) {
            // Re-enqueue ourselves; keep it PENDING.
            enqueueEsmDelivery(applicationContext, instanceId, postponeMs)
            return Result.success()
        }

        // Mark available.
        if (inst.status != EsmStatuses.AVAILABLE) {
            esmDao.setInstanceStatus(instanceId, EsmStatuses.AVAILABLE)
        }

        // TODO: ESM notifications disabled
        // postEsmIndicatorNotification(applicationContext)
        return Result.success()
    }

    companion object {
        const val KEY_INSTANCE_ID = "esm_instance_id"
    }
}
