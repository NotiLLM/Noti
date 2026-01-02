package org.muilab.notigpt.database.server.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class N8nAPIWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("N8nWebhook", "Running Worker")

        val parsed = N8nWorkerInput.from(inputData)
        if (parsed == null) {
            Log.w("N8nWebhook", "Unknown or invalid input, skipping")
            return Result.success()
        }

        return try {
            N8nWorkerHandlers.dispatch(this, parsed, inputData)
        } catch (e: Exception) {
            Log.e("N8nWebhook", "Error in worker", e)
            Result.failure()
        }
    }

    // NOTE: Large handler methods were extracted into database.server.workers.n8n.*
    // (UpdateNotificationHandler, TaskScanHandler, TaskExtractionHandler, PostNotificationActionHandler)
}
