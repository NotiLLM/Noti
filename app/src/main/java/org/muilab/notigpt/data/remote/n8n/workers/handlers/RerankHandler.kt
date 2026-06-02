package org.muilab.notigpt.data.remote.n8n.workers.handlers

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.data.remote.n8n.formatter.N8nRecordFormatter
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.util.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Worker handler for reranking or refreshing a reminder after feedback-triggered events.
 *
 * Keep this as a focused backend bridge over one reminder. Broader reminder regeneration should continue using
 * the regeneration handler so feedback paths stay easy to reason about.
 */
internal object RerankHandler {

    private const val TAG = "N8nRerank"

    suspend fun handle(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e(TAG, "No webhook_path for rerank")
            return ctx.failure()
        }
        val reminderId = inputData.getString("reminder_id") ?: run {
            Log.e(TAG, "No reminder_id for rerank")
            return ctx.failure()
        }
        val trigger = inputData.getString("trigger") ?: run {
            Log.e(TAG, "No trigger for rerank")
            return ctx.failure()
        }

        val reminder = ctx.reminderRepository.getById(reminderId) ?: run {
            Log.w(TAG, "Reminder $reminderId not found")
            return ctx.success()
        }

        // Build notification context
        val notiContext = buildNotiContext(ctx, reminder)

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val deadlineIso = if (reminder.deadlineTimestamp > 0L) sdf.format(Date(reminder.deadlineTimestamp)) else ""

        val payload = mapOf(
            "userId" to SharedPreferencesManager.userId,
            "language" to Locale.getDefault().toLanguageTag(),
            "timezone" to TimeZone.getDefault().displayName,
            "currentTime" to sdf.format(Date()),
            "targetExtractionLanguage" to SharedPreferencesManager.targetExtractionLanguage,
            "trigger" to trigger,
            "reminder" to mapOf(
                "reminderId" to reminder.reminderId,
                "reminderTitle" to reminder.reminderTitle,
                "reminderContent" to reminder.reminderContent,
                "isTask" to reminder.isTask,
                "isEvent" to reminder.isEvent,
                "isCompleted" to reminder.isCompleted,
                "deadlineTimeString" to deadlineIso,
                "sortScore" to reminder.sortScore,
                "isViewed" to reminder.isViewed,
                "isPinned" to reminder.isPinned,
                "reRankHistory" to reminder.reRankHistory,
            ),
            "notiContext" to notiContext,
            "extractionPreferences" to ctx.getExtractionPreferencesPayload(),
            "userContexts" to ctx.getUserContextsPayload(),
        )

        val gson = Gson()
        val jsonPayload = gson.toJson(payload)
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        Log.d(TAG, "Rerank payload: $jsonPayload")

        val response = try {
            ctx.n8nApiService.postToWebhook(webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e(TAG, "Rerank network exception", t)
            return ctx.retry()
        }

        if (!response.isSuccessful) return when {
            response.code() == 429 -> ctx.retry()
            response.code() in 500..599 -> ctx.retry()
            else -> ctx.failure()
        }

        val bodyStr = response.body()?.string() ?: return ctx.success()
        Log.d(TAG, "Rerank response: $bodyStr")

        try {
            val obj = JSONObject(bodyStr)
            val newSortScore = obj.optDouble("newSortScore", reminder.sortScore.toDouble()).toFloat()

            val reRankRecord = obj.optJSONObject("reRankRecord")
            val history = try {
                JSONArray(reminder.reRankHistory)
            } catch (_: Exception) {
                JSONArray()
            }

            if (reRankRecord != null) {
                history.put(reRankRecord)
            } else {
                history.put(JSONObject().apply {
                    put("rankedAt", System.currentTimeMillis())
                    put("trigger", trigger)
                    put("newScore", newSortScore)
                    put("scoreExplanation", "Reranked by $trigger")
                })
            }

            ctx.reminderRepository.updateSortScoreAndHistory(
                reminderId = reminderId,
                sortScore = newSortScore,
                reRankHistory = history.toString(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing rerank response", e)
        }

        return ctx.success()
    }

    private suspend fun buildNotiContext(
        ctx: N8nWorkerContext,
        reminder: org.muilab.notigpt.model.features.ReminderUnit,
    ): List<Map<String, Any>> {
        if (reminder.associatedNotiRecords.isEmpty()) return emptyList()

        val db = ctx.database
        val wantedKeys = reminder.associatedNotiKeys.toList()
        if (wantedKeys.isEmpty()) return emptyList()

        val records = try {
            db.recordDao().getRecordsByKeys(wantedKeys)
        } catch (_: Exception) {
            return emptyList()
        }

        val units = try {
            db.drawerDao().getByNotiKeys(wantedKeys).associateBy { it.notiKey }
        } catch (_: Exception) {
            emptyMap()
        }

        return records.sortedBy { it.time }.map { r ->
            val unit = units[r.notiKey]
            val isPeople = unit?.isPeople ?: false
            N8nRecordFormatter.format(r, isPeople)
        }
    }
}

