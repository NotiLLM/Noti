package org.muilab.notigpt.database.server.workers

import androidx.work.Data
import androidx.work.ListenableWorker

/**
 * Thin routing layer so [N8nAPIWorker] stays readable.
 *
 * Each handler delegates to the existing methods in N8nAPIWorker to preserve behavior.
 */
internal object N8nWorkerHandlers {

    suspend fun dispatch(worker: N8nAPIWorker, input: N8nWorkerInput, raw: Data): ListenableWorker.Result {
        return when (input) {
            is N8nWorkerInput.UpdateNotification -> worker.updateNotification(raw)
            is N8nWorkerInput.TaskScan -> worker.performTaskScan(raw)
            is N8nWorkerInput.TaskExtraction -> worker.performTaskExtraction(raw)
            is N8nWorkerInput.PostNotificationAction -> worker.postNotificationAction(raw)
        }
    }
}
