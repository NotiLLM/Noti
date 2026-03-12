package org.muilab.notigpt.database.server.workers

import androidx.work.Data
import androidx.work.ListenableWorker
import org.muilab.notigpt.database.server.workers.n8n.N8nWorkerContext
import org.muilab.notigpt.database.server.workers.n8n.PostNotificationActionHandler
import org.muilab.notigpt.database.server.workers.n8n.PreferenceQuickSyncHandler
import org.muilab.notigpt.database.server.workers.n8n.ReminderExtractionHandler
import org.muilab.notigpt.database.server.workers.n8n.ReminderScanHandler
import org.muilab.notigpt.database.server.workers.n8n.UpdateNotificationHandler

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
        }
    }
}
