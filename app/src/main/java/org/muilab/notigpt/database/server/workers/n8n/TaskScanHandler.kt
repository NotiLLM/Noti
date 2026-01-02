package org.muilab.notigpt.database.server.workers.n8n

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.muilab.notigpt.util.SharedPreferencesManager

internal object TaskScanHandler {

    suspend fun handle(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        Log.d("N8nWebhook", "Performing Task Scan")

        val gson = Gson()

        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e("N8nWebhook", "No webhook_path for scan")
            return ctx.failure()
        }
        val notiKey = inputData.getString("noti_key") ?: return ctx.failure()

        val notiUnit = ctx.getNotiUnit(notiKey) ?: return ctx.failure()

        // ensure noti_contents are ordered by time ascending (oldest -> newest)
        val notiRecords = ctx.database.recordDao().getUnscannedRecordsByKey(notiKey).sortedBy { it.time }

        if (notiRecords.isEmpty()) return ctx.success()

        // Build payload for scan
        val notiContentList = notiRecords.map { rec -> N8nRecordFormatter.format(rec, notiUnit.isPeople) }
        val pastCount = SharedPreferencesManager.maxPastContext
        val pastRecords = if (pastCount > 0) ctx.database.recordDao().getLastScannedRecordsByKey(notiKey, pastCount).sortedBy { it.time } else emptyList()
        val pastContextList = pastRecords.map { N8nRecordFormatter.format(it, notiUnit.isPeople) }

        val lastRecord = notiRecords.lastOrNull()
        val lastTitle = lastRecord?.title ?: ""

        val notiOverallTitle = if (lastRecord != null) {
            when {
                lastRecord.extraConversationTitle != "null" -> lastRecord.extraConversationTitle
                lastTitle != "null" -> lastTitle
                lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                else -> ""
            }
        } else {
            ""
        }

        val notiSecondOverallTitle = if (lastRecord != null) {
            when {
                lastRecord.extraConversationTitle != "null" && lastTitle != "null" -> lastTitle
                lastRecord.extraConversationTitle == "null" && lastTitle != "null" && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                lastRecord.extraConversationTitle == "null" && lastTitle != "null" -> ""
                else -> ""
            }
        } else {
            ""
        }

        val payload = mapOf(
            "userId" to SharedPreferencesManager.userId,
            "notiKey" to notiKey,
            "appName" to notiUnit.appName,
            "appCategory" to notiUnit.category,
            "overallTitle" to notiOverallTitle,
            "secondOverallTitle" to notiSecondOverallTitle,
            "notiContent" to notiContentList,
            "pastContext" to pastContextList
        )
        val json = gson.toJson(payload)
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val response = try {
            ctx.n8nApiService.postToWebhook(webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e("N8nWebhook", "TaskScan network exception", t)
            return ctx.retry()
        }
        if (!response.isSuccessful) return when {
            response.code() == 429 -> ctx.retry()
            response.code() in 500..599 -> ctx.retry()
            else -> ctx.failure()
        }

        val bodyStr = response.body()?.string() ?: return ctx.success()
        try {
            val root = JSONObject(bodyStr)
            val hasGenuine = root.optBoolean("hasGenuineTask", false)

            ctx.notiRepository.setHasGenuineTask(notiKey, hasGenuine)
            ctx.database.recordDao().setRecordsTaskScannedByIds(notiRecords.map { it.notiRecordId })
        } catch (e: Exception) {
            Log.e("N8nWebhook", "Error parsing scan response", e)
        }

        return ctx.success()
    }
}
