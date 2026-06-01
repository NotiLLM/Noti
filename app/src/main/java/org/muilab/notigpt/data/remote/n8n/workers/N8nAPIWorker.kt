package org.muilab.notigpt.data.remote.n8n.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker that dispatches typed n8n jobs to workflow-specific handlers.
 *
 * Keep this class as the dependency/context assembly point. The worker should parse input, build a shared
 * N8nWorkerContext, and delegate side effects to handlers rather than containing workflow logic directly.
 */
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

    // NOTE: Large handler methods were extracted into data.remote.n8n.workers.handlers.*
    // (UpdateNotificationHandler, TaskScanHandler, TaskExtractionHandler, PostNotificationActionHandler)
}
