package org.muilab.notigpt.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderPeriodicWork {

    private const val UNIQUE_NAME = "reminder_periodic_scan_extract"

    fun enqueue(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = PeriodicWorkRequest.Builder(ReminderPeriodicWorker::class.java, 15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            // WorkManager will back off retries. We also cap retries inside the Worker.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
