package org.muilab.notigpt.data.remote.n8n.workers.handlers

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.muilab.notigpt.data.remote.n8n.formatter.N8nRecordFormatter
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Worker handler for the thread classification (scan) stage.
 *
 * One cheap model pass per notiUnit does two jobs: gate flags (hasTask/hasMemo/hasEvent → whether
 * the thread enters reminder extraction) and thread categories (communication/content), both
 * stored in noti_llm_state. Extraction itself remains a separate queued workflow so scan and
 * extraction can retry independently.
 */
internal object ReminderScanHandler {

    /** Most recent records sent on a forced re-classification pass. */
    private const val RECLASSIFY_RECORD_CAP = 20

    /**
     * Runs one scan job and writes only thread-state flags/categories back to noti_llm_state.
     *
     * Do not create reminders here. Keeping scan as a lightweight classification stage makes it safe
     * to retry separately from extraction.
     */
    suspend fun handle(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        Log.d("N8nWebhook", "Performing Task Scan")

        val gson = Gson()

        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e("N8nWebhook", "No webhook_path for scan")
            return ctx.failure()
        }
        val notiKey = inputData.getString("noti_key") ?: return ctx.failure()
        val forceReclassify = inputData.getBoolean("force_reclassify", false)

        val notiUnit = ctx.getNotiUnit(notiKey) ?: return ctx.failure()

        // ensure noti_contents are ordered by time ascending (oldest -> newest)
        val notiRecords = if (forceReclassify) {
            // Re-check pass: classify from the most recent records even if all are already scanned.
            ctx.database.recordDao().getRecordsByKey(notiKey)
                .sortedByDescending { it.time }.take(RECLASSIFY_RECORD_CAP).sortedBy { it.time }
        } else {
            ctx.database.recordDao().getUnscannedRecordsByKey(notiKey).sortedBy { it.time }
        }

        if (notiRecords.isEmpty()) return ctx.success()

        // Build payload for scan
        val notiContentList =
            notiRecords.map { rec -> N8nRecordFormatter.format(rec, notiUnit.isPeople) }
        val pastCount = if (forceReclassify) 0 else SharedPreferencesManager.maxPastContext
        val pastRecords = if (pastCount > 0) ctx.database.recordDao()
            .getLastScannedRecordsByKey(notiKey, pastCount).sortedBy { it.time } else emptyList()
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
            "overallTitle" to notiOverallTitle,
            "secondOverallTitle" to notiSecondOverallTitle,
            "notiContent" to notiContentList,
            "pastContext" to pastContextList,
            "extractionPreferences" to ctx.getExtractionPreferencesPayload()
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

            // Most common for your n8n setup: JSON is inside "text"
            val text = root.optString("text", "")
            val innerJson = extractJsonFromTextField(text) ?: run {
                Log.e("N8nWebhook", "Scan response missing parsable JSON. body=$bodyStr")
                return ctx.success()
            }

            val inner = JSONObject(innerJson)

            // hasTask/hasMemo/hasEvent are 1/0 in your response
            val hasTask = inner.optInt("hasTask", 0) == 1 || inner.optBoolean("hasTask", false)
            val hasMemo = inner.optInt("hasMemo", 0) == 1 || inner.optBoolean("hasMemo", false)
            val hasEvent = inner.optInt("hasEvent", 0) == 1 || inner.optBoolean("hasEvent", false)

            ctx.notiRepository.setScanStates(notiKey, hasTask, hasMemo, hasEvent)

            // Thread categories (communication/content/custom) ride along in the same response.
            val categories = inner.optJSONArray("categories")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
            } ?: emptyList()
            ctx.notiLlmStateRepository.applyClassification(
                notiKey = notiKey,
                categories = categories,
                reason = inner.optString("reason", ""),
                source = "llm",
                recordCount = ctx.database.recordDao().getRecordCountByKey(notiKey),
            )

            if (!forceReclassify) {
                ctx.database.recordDao().setRecordsTaskScannedByIds(notiRecords.map { it.notiRecordId })
            }

        } catch (e: Exception) {
            Log.e("N8nWebhook", "Error parsing scan response. body=$bodyStr", e)
        }

        return ctx.success()
    }
    private fun extractJsonFromTextField(text: String): String? {
        val trimmed = text.trim()

        // Case A: already JSON object string: {"hasTask":1,...}
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed

        // Case B: markdown fenced JSON
        val r = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```", RegexOption.IGNORE_CASE)
        return r.find(trimmed)?.groupValues?.getOrNull(1)
    }
}
