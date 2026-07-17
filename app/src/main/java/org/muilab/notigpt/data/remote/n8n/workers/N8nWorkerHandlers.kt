package org.muilab.notigpt.data.remote.n8n.workers

import androidx.work.Data
import androidx.work.ListenableWorker
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.data.remote.n8n.workers.handlers.ExtractionPipelineHandler
import org.muilab.notigpt.data.remote.n8n.workers.handlers.PreferenceQuickSyncHandler
import org.muilab.notigpt.data.remote.n8n.workers.handlers.ReflectionPipelineHandler
import org.muilab.notigpt.data.remote.n8n.workers.handlers.SavedItemRegenerationHandler
import org.muilab.notigpt.data.remote.n8n.workers.handlers.UpdateNotificationHandler

/**
 * Dispatcher from typed WorkManager input to the matching n8n workflow handler.
 *
 * Add new workflow types here only after defining a typed N8nWorkerInput and a handler. This keeps N8nAPIWorker
 * small and makes the supported background jobs easy to audit.
 */
internal object N8nWorkerHandlers {

    /**
     * Routes one typed worker input to its workflow handler.
     *
     * Keep the mapping exhaustive with N8nWorkerInput so unsupported WorkManager Data fails before side effects.
     */
    suspend fun dispatch(ctx: N8nWorkerContext, input: N8nWorkerInput, raw: Data): ListenableWorker.Result {
        return when (input) {
            is N8nWorkerInput.UpdateNotification -> UpdateNotificationHandler.handle(ctx, raw)
            is N8nWorkerInput.ExtractionPipeline -> ExtractionPipelineHandler.handle(ctx, input)
            is N8nWorkerInput.ReflectionPipeline -> ReflectionPipelineHandler.handle(ctx)
            is N8nWorkerInput.PreferenceQuickSync -> PreferenceQuickSyncHandler.handle(ctx, raw)
            is N8nWorkerInput.RegenerateOne -> SavedItemRegenerationHandler.handleOne(ctx, raw)
        }
    }
}
