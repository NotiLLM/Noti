package org.muilab.notigpt.data.remote.n8n.workers

import androidx.work.Data
import androidx.work.ListenableWorker
import org.muilab.notigpt.data.remote.n8n.workers.handlers.utils.N8nWorkerContext
import org.muilab.notigpt.data.remote.n8n.workers.handlers.PostNotificationActionHandler
import org.muilab.notigpt.data.remote.n8n.workers.handlers.PreferenceQuickSyncHandler
import org.muilab.notigpt.data.remote.n8n.workers.handlers.ReminderExtractionHandler
import org.muilab.notigpt.data.remote.n8n.workers.handlers.ReminderRegenerationHandler
import org.muilab.notigpt.data.remote.n8n.workers.handlers.ReminderScanHandler
import org.muilab.notigpt.data.remote.n8n.workers.handlers.RerankHandler
import org.muilab.notigpt.data.remote.n8n.workers.handlers.UpdateNotificationHandler

/**
 * Thin routing layer so [N8nAPIWorker] stays readable.
 */
internal object N8nWorkerHandlers {

    suspend fun dispatch(worker: N8nAPIWorker, input: N8nWorkerInput, raw: Data): ListenableWorker.Result {
        val ctx = N8nWorkerContext(worker.applicationContext)
        return when (input) {
            is N8nWorkerInput.UpdateNotification -> UpdateNotificationHandler.handle(ctx, raw)
            is N8nWorkerInput.ReminderScan -> ReminderScanHandler.handle(ctx, raw)
            is N8nWorkerInput.ReminderExtraction -> ReminderExtractionHandler.handle(ctx, raw)
            is N8nWorkerInput.PostNotificationAction -> PostNotificationActionHandler.handle(ctx, raw)
            is N8nWorkerInput.PreferenceQuickSync -> PreferenceQuickSyncHandler.handle(ctx, raw)
            is N8nWorkerInput.RegenerateOne -> ReminderRegenerationHandler.handleOne(ctx, raw)
            is N8nWorkerInput.RegenerateAll -> ReminderRegenerationHandler.handleAll(ctx, raw)
            is N8nWorkerInput.Rerank -> RerankHandler.handle(ctx, raw)
        }
    }
}
