package org.muilab.notigpt.database.server.workers.n8n

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.muilab.notigpt.util.SharedPreferencesManager

internal object PostNotificationActionHandler {

    suspend fun handle(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        val gson = Gson()

        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e("N8nWebhook", "No webhook_path for postNotificationAction")
            return ctx.failure()
        }

        val notiKey = inputData.getString("noti_key") ?: return ctx.failure()
        val actionType = inputData.getString("action_type") ?: return ctx.failure()
        val actionTime = inputData.getLong("action_time", -1L)

        val payload = mapOf(
            "userId" to SharedPreferencesManager.userId,
            "notiKey" to notiKey,
            "actionType" to actionType,
            "actionTime" to actionTime,
        )

        val json = gson.toJson(payload)
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        return try {
            val response = ctx.n8nApiService.postToWebhook(webhookPath, requestBody)
            if (!response.isSuccessful) {
                Log.e("N8nWebhook", "postNotificationAction failed: ${response.code()}")
                if (response.code() in 500..599 || response.code() == 429) ctx.retry() else ctx.failure()
            } else {
                ctx.success()
            }
        } catch (t: Throwable) {
            Log.e("N8nWebhook", "postNotificationAction exception", t)
            ctx.retry()
        }
    }
}

