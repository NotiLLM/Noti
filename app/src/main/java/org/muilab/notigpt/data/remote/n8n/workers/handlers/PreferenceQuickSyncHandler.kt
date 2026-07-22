package org.muilab.notigpt.data.remote.n8n.workers.handlers

import android.util.Log
import androidx.work.ListenableWorker
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.data.remote.n8n.workers.N8nWorkerInput

/**
 * Compatibility transport for Quick Sync jobs queued before the typed client cutover.
 *
 * Current Quick Sync calls use [org.muilab.notigpt.data.remote.n8n.PreferenceQuickSyncClient] and
 * apply a user-selected typed change set through [org.muilab.notigpt.data.repository.personalization.PersonalizationRepository].
 * A stale WorkManager job may finish its already-queued request, but its legacy response must never
 * rewrite confirmed personalization outside that repository boundary.
 */
internal object PreferenceQuickSyncHandler {

    private const val TAG = "PrefQuickSync"

    suspend fun handle(
        ctx: N8nWorkerContext,
        input: N8nWorkerInput.PreferenceQuickSync,
    ): ListenableWorker.Result {
        val requestBody = input.payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType())

        val response = try {
            ctx.n8nApiService.postToWebhook(input.webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e(TAG, "Network error", t)
            return ctx.retry()
        }

        if (!response.isSuccessful) {
            return when {
                response.code() == 429 -> ctx.retry()
                response.code() in 500..599 -> ctx.retry()
                else -> ctx.failure()
            }
        }

        response.body()?.close()
        Log.d(TAG, "Completed queued compatibility request without applying legacy response state")
        return ctx.success()
    }
}
