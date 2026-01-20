package org.muilab.notigpt.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object EsmTriggerCWork {

    private const val TAG = "EsmTriggerCWork"
    private const val UNIQUE_NAME = "esm_trigger_c_check"
    private const val UNIQUE_KICK_NAME = "esm_trigger_c_check_kick"

    fun enqueue(context: Context) {
        val req = PeriodicWorkRequest.Builder(
            EsmTriggerCWorker::class.java,
            15,
            TimeUnit.MINUTES,
        ).build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, req)

        Log.i(TAG, "Enqueued Trigger C periodic work name=$UNIQUE_NAME id=${req.id}")
    }

    fun kickNow(context: Context) {
        val req = OneTimeWorkRequest.Builder(EsmTriggerCWorker::class.java)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_KICK_NAME, ExistingWorkPolicy.REPLACE, req)

        Log.i(TAG, "Enqueued Trigger C one-time kick name=$UNIQUE_KICK_NAME id=${req.id}")
    }
}

