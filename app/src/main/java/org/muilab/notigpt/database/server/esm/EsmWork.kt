package org.muilab.notigpt.database.server.esm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.muilab.notigpt.database.server.workers.esm.EsmDeliveryWorker
import org.muilab.notigpt.domain.esm.EsmReceptiveWindow
import org.muilab.notigpt.util.SharedPreferencesManager
import java.util.concurrent.TimeUnit

fun enqueueEsmDelivery(context: Context, instanceId: String, delayMs: Long) {
    // Workers can run before any Activity is launched.
    try { SharedPreferencesManager.init(context.applicationContext) } catch (_: Exception) {}

    val gateDelayMs = EsmReceptiveWindow.delayUntilNextReceptive(System.currentTimeMillis())
    val effectiveDelayMs = delayMs.coerceAtLeast(0L) + gateDelayMs

    val inputData = Data.Builder()
        .putString(EsmDeliveryWorker.KEY_INSTANCE_ID, instanceId)
        .build()

    val constraints = Constraints.Builder()
        // No network required - this is local survey.
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        .build()

    val req = OneTimeWorkRequestBuilder<EsmDeliveryWorker>()
        .setInitialDelay(effectiveDelayMs, TimeUnit.MILLISECONDS)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
        .setConstraints(constraints)
        .setInputData(inputData)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork("esm_deliver_$instanceId", ExistingWorkPolicy.REPLACE, req)
}
