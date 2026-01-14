package org.muilab.notigpt.database.server.workers.n8n

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.muilab.notigpt.database.server.N8nUpdateNotificationPayload
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_DELETED
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.toN8nNotiActions
import org.muilab.notigpt.util.toN8nNotiRecords

internal object UpdateNotificationHandler {

    suspend fun handle(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        Log.d("N8nWebhook", "Update Notification")

        val gson = Gson()

        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e("N8nWebhook", "No webhook_path provided")
            return ctx.failure()
        }

        val notiKey = inputData.getString("noti_key") ?: ""

        val notiUnit = ctx.getNotiUnit(notiKey)
        if (notiUnit == null) {
            Log.e("N8nWebhook", "Notification unit not found for key: $notiKey")
            return ctx.failure()
        }

        val lastSyncTime = notiUnit.lastUpdateTime
        val pastSummary = notiUnit.summary

        val notiRecords = ctx.database.recordDao().getNotSyncedRecordsByKey(notiKey, lastSyncTime)
        val notiActions = ctx.getNotSyncedNotiActions(notiKey, lastSyncTime)

        if (notiRecords.isEmpty()) {
            Log.d("N8nWebhook", "No new notifications to sync.")
            return ctx.success()
        }

        val payload = N8nUpdateNotificationPayload(
            userId = SharedPreferencesManager.userId,
            noti_contents_str = gson.toJson(toN8nNotiRecords(notiUnit, notiRecords)),
            noti_actions_str = gson.toJson(toN8nNotiActions(notiActions)),
            noti_past_summary = pastSummary,
        )

        val json = gson.toJson(payload)
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val response = try {
            ctx.n8nApiService.postToWebhook(webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e("N8nWebhook", "Network exception posting updateNotification", t)
            return ctx.retry()
        }

        if (!response.isSuccessful) {
            Log.e("N8nWebhook", "API call unsuccessful: ${response.code()}")
            return when {
                response.code() == 429 -> ctx.retry()
                response.code() in 500..599 -> ctx.retry()
                else -> ctx.failure()
            }
        }

        val jsonString = response.body()?.string()
        Log.d("N8nWebhook", "Response: $jsonString")

        try {
            if (jsonString.isNullOrEmpty()) return ctx.success()

            val root = JSONObject(jsonString)
            val data = root.optJSONObject("data") ?: return ctx.success()
            val status = data.optString("status") ?: ""
            if (status != "succeeded") return ctx.success()

            val jsonOutputs = data.optJSONObject("outputs") ?: return ctx.success()
            if (jsonOutputs.has("retry")) return ctx.retry()

            val featureStrRaw = jsonOutputs.getString("features")
                .removePrefix("```json\n")
                .removeSuffix("\n```")
            val features = JSONObject(featureStrRaw)

            val evaluationStrRaw = jsonOutputs.getString("evaluations")
                .removePrefix("```json\n")
                .removeSuffix("\n```")
            val evaluations = JSONObject(evaluationStrRaw)

            val explanationSB = StringBuilder()

            val evaluationsWithScores = listOf(
                "sort-score-before-notice",
                "sort-score-after-notice",
                "sort-score-after-pinned",
                "sort-score-after-acted"
            )

            evaluationsWithScores.forEach { attr ->
                evaluations.optJSONObject(attr)?.let {
                    explanationSB.append(
                        "${attr.replace("-", " ").replaceFirstChar { c -> c.uppercase() }} (${it.getDouble("score")})\n${it.getString("reason")}\n"
                    )
                }
            }

            val assignedCategory = evaluations.optJSONObject("assigned-category")
            assignedCategory?.let {
                // Categories have been removed. Keep the explanation text for transparency.
                val newCategory = it.optString("category")
                explanationSB.append("[Assigned Category] $newCategory\n${it.optString("reason")}\n")
            }

            val textSummary = evaluations.optJSONObject("text-summary")
            textSummary?.let {
                explanationSB.append("[Text Summary]\n${it.getString("reason")}\n")
                notiUnit.summary = it.getString("summary")
            }

            val featuresWithScores = listOf(
                "necessity",
                "time-relevance",
                "sender-attractiveness",
                "content-attractiveness",
                "urgency",
                "importance",
            )

            featuresWithScores.forEach { attr ->
                features.optJSONObject(attr)?.let {
                    explanationSB.append(
                        "${attr.replace("-", " ").replaceFirstChar { c -> c.uppercase() }} (${it.getDouble("score")})\n${it.getString("reason")}\n"
                    )
                }
            }

            val tasks = features.optJSONArray("tasks")
            tasks?.let {
                explanationSB.append("Tasks:\n")
                for (i in 0 until it.length()) {
                    val task = it.getJSONObject(i)
                    explanationSB.append("${i + 1}: ${task.getString("task")}, due ${task.getString("deadline")}\n")
                }
                explanationSB.append("\n")
            }

            val keyTimings = features.optJSONArray("key-timings")
            keyTimings?.let {
                explanationSB.append("Key Timings:\n")
                for (i in 0 until it.length()) {
                    val timing = it.getJSONObject(i)
                    explanationSB.append("${timing.getString("key-timing")}: ${timing.getString("event")}\n")
                }
                explanationSB.append("\n")
            }

            notiUnit.sortScore = evaluations.optJSONObject("sort-score-before-notice")?.optDouble("score") ?: 0.0
            notiUnit.explanation = explanationSB.toString()

            ctx.notiRepository.updateNotiUnit(notiUnit)

            Log.d("N8nWebhook", "Notification ${notiRecords.last().getDisplayedTitle(notiUnit.isPeople)} updated.")
        } catch (e: Exception) {
            Log.e("N8nWebhook", "Error parsing response", e)
        }

        delay(5000)
        return ctx.success()
    }
}
