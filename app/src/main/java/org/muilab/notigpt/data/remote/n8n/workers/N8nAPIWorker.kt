package org.muilab.notigpt.data.remote.n8n.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext

/**
 * WorkManager worker that dispatches typed n8n jobs to workflow-specific handlers.
 *
 * Keep this class as the dependency/context assembly point. The worker should parse input, build a shared
 * N8nWorkerContext, and delegate side effects to handlers rather than containing workflow logic directly.
 */
@HiltWorker
class N8nAPIWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val workerContext: N8nWorkerContext,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("N8nWebhook", "Running Worker")

        val parsed = N8nWorkerInput.from(inputData)
        if (parsed == null) {
            Log.w("N8nWebhook", "Unknown or invalid input, skipping")
            return Result.success()
        }

        return try {
            N8nWorkerHandlers.dispatch(workerContext, parsed, inputData, runAttemptCount)
        } catch (e: Exception) {
            Log.e("N8nWebhook", "Error in worker", e)
            Result.failure()
        }
    }

    // NOTE: Large handler methods were extracted into data.remote.n8n.workers.handlers.*
    // Workflow-specific behavior stays in the handler classes.
}
