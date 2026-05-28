package org.muilab.notigpt.database.server.workers.n8n

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.util.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Handles regeneration of one or all reminders via n8n webhooks.
 */
internal object ReminderRegenerationHandler {

    private const val TAG = "N8nRegeneration"

    /**
     * Regenerate a single reminder.
     */
    suspend fun handleOne(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e(TAG, "No webhook_path for regenerate_one")
            return ctx.failure()
        }
        val reminderId = inputData.getString("reminder_id") ?: run {
            Log.e(TAG, "No reminder_id for regenerate_one")
            return ctx.failure()
        }

        val reminder = ctx.reminderRepository.getById(reminderId) ?: run {
            Log.w(TAG, "Reminder $reminderId not found")
            return ctx.success()
        }

        val notiContext = buildNotiContextForReminder(ctx, reminder)
        val payload = buildPayload(
            reminders = listOf(reminder),
            notiContextMap = mapOf(reminderId to notiContext),
            trigger = "REGENERATE_ONE",
            extractionPreferences = ctx.getExtractionPreferencesPayload(),
            userContexts = ctx.getUserContextsPayload(),
        )

        return postAndApply(ctx, webhookPath, payload, trigger = "REGENERATE_ONE")
    }

    /**
     * Regenerate all visible reminders.
     */
    suspend fun handleAll(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e(TAG, "No webhook_path for regenerate_all")
            return ctx.failure()
        }

        val allReminders = ctx.reminderRepository.getAllVisible()
        if (allReminders.isEmpty()) {
            Log.d(TAG, "No visible reminders to regenerate")
            return ctx.success()
        }

        val notiContextMap = mutableMapOf<String, List<Map<String, Any>>>()
        for (r in allReminders) {
            notiContextMap[r.reminderId] = buildNotiContextForReminder(ctx, r)
        }

        val payload = buildPayload(
            reminders = allReminders,
            notiContextMap = notiContextMap,
            trigger = "REGENERATE_ALL",
            extractionPreferences = ctx.getExtractionPreferencesPayload(),
            userContexts = ctx.getUserContextsPayload(),
        )

        return postAndApply(ctx, webhookPath, payload, trigger = "REGENERATE_ALL")
    }

    /**
     * Build notification context records for a single reminder.
     */
    private suspend fun buildNotiContextForReminder(
        ctx: N8nWorkerContext,
        reminder: ReminderUnit,
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

    private fun buildPayload(
        reminders: List<ReminderUnit>,
        notiContextMap: Map<String, List<Map<String, Any>>>,
        trigger: String,
        extractionPreferences: List<Map<String, String>>,
        userContexts: List<Map<String, String>>,
    ): Map<String, Any> {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

        val remindersPayload = reminders.map { r ->
            val deadlineIso = if (r.deadlineTimestamp > 0L) sdf.format(Date(r.deadlineTimestamp)) else -1L
            val startTimeIso = if (r.startTime > 0L) sdf.format(Date(r.startTime)) else -1L
            val endTimeIso = if (r.endTime > 0L) sdf.format(Date(r.endTime)) else -1L
            mapOf(
                "reminderId" to r.reminderId,
                "reminderTitle" to r.reminderTitle,
                "reminderContent" to r.reminderContent,
                "isTask" to r.isTask,
                "isEvent" to r.isEvent,
                "isCompleted" to r.isCompleted,
                "deadlineTimeString" to deadlineIso,
                "startTimeString" to startTimeIso,
                "endTimeString" to endTimeIso,
                "estimatedCompletionMinutes" to r.estimatedCompletionTime,
                "associatedNotiRecords" to r.associatedNotiRecords.toList(),
                "userEdited" to r.userEdited,
                "buttons" to r.buttons,
                "sortScore" to r.sortScore,
                "isPinned" to r.isPinned,
                "reRankHistory" to r.reRankHistory,
                "notiContext" to (notiContextMap[r.reminderId] ?: emptyList<Any>()),
            )
        }

        return mapOf(
            "userId" to SharedPreferencesManager.userId,
            "language" to Locale.getDefault().toLanguageTag(),
            "timezone" to TimeZone.getDefault().displayName,
            "currentTime" to sdf.format(Date()),
            "targetExtractionLanguage" to SharedPreferencesManager.targetExtractionLanguage,
            "trigger" to trigger,
            "reminders" to remindersPayload,
            "extractionPreferences" to extractionPreferences,
            "userContexts" to userContexts,
        )
    }

    private suspend fun postAndApply(
        ctx: N8nWorkerContext,
        webhookPath: String,
        payload: Map<String, Any>,
        trigger: String,
    ): ListenableWorker.Result {
        val gson = Gson()
        val jsonPayload = gson.toJson(payload)
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        Log.d(TAG, "Payload ($trigger): $jsonPayload")

        val response = try {
            ctx.n8nApiService.postToWebhook(webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e(TAG, "Network exception ($trigger)", t)
            return ctx.retry()
        }

        if (!response.isSuccessful) return when {
            response.code() == 429 -> ctx.retry()
            response.code() in 500..599 -> ctx.retry()
            else -> ctx.failure()
        }

        val bodyStr = response.body()?.string() ?: return ctx.success()
        Log.d(TAG, "Response ($trigger): $bodyStr")

        try {
            val arr = JSONArray(bodyStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val reminderId = obj.optString("reminderId", obj.optString("taskId"))
                if (reminderId.isBlank()) continue

                val existing = ctx.reminderRepository.getById(reminderId)

                val reminderTitle = obj.optString("reminderTitle", existing?.reminderTitle ?: "")
                val reminderContent = obj.optString("reminderContent", existing?.reminderContent ?: "")
                val isTask = obj.optBoolean("isTask", existing?.isTask ?: true)
                val isEvent = obj.optBoolean("isEvent", existing?.isEvent ?: false)
                val deadlineMs = ReminderExtractionHandler.isoToUnixMillis(obj.optString("deadlineTimeString", "-1"))
                val startTimeMs = ReminderExtractionHandler.isoToUnixMillis(obj.optString("startTimeString", "-1")).let { v -> if (v == -1L) 0L else v }
                val endTimeMs = ReminderExtractionHandler.isoToUnixMillis(obj.optString("endTimeString", "-1")).let { v -> if (v == -1L) 0L else v }
                val estimate = obj.optLong("estimatedCompletionTime", obj.optLong("estimatedCompletionMinutes", existing?.estimatedCompletionTime ?: 0L))
                val isCompleted = obj.optBoolean("isCompleted", existing?.isCompleted ?: false)

                // Parse buttons
                val buttonsArr = obj.optJSONArray("buttons")
                val buttons = buttonsArr?.toString() ?: existing?.buttons ?: "[]"

                // Parse sortScore
                val sortScore = obj.optDouble("sortScore", (existing?.sortScore ?: 50f).toDouble()).toFloat()

                // Parse reRankRecord and append to history
                val reRankRecord = obj.optJSONObject("reRankRecord")
                val existingHistory = try {
                    JSONArray(existing?.reRankHistory ?: "[]")
                } catch (_: Exception) {
                    JSONArray()
                }
                if (reRankRecord != null) {
                    existingHistory.put(reRankRecord)
                } else {
                    // Auto-generate a record
                    existingHistory.put(JSONObject().apply {
                        put("rankedAt", System.currentTimeMillis())
                        put("trigger", trigger)
                        put("newScore", sortScore)
                        put("scoreExplanation", "Regenerated by $trigger")
                    })
                }

                // Preserve associatedNotiRecords from existing
                val assocIds = mutableSetOf<String>()
                val assoc = obj.optJSONArray("associatedNotiRecords") ?: obj.optJSONArray("associatedNotis")
                if (assoc != null) {
                    for (j in 0 until assoc.length()) assocIds.add(assoc.optString(j))
                }
                if (assocIds.isEmpty() && existing != null) {
                    assocIds.addAll(existing.associatedNotiRecords)
                }

                val unit = ReminderUnit(
                    reminderId = reminderId,
                    reminderTitle = reminderTitle,
                    reminderContent = reminderContent,
                    isTask = isTask,
                    isEvent = isEvent,
                    isCompleted = isCompleted,
                    lastUpdateTimestamp = System.currentTimeMillis(),
                    deadlineTimestamp = deadlineMs,
                    startTime = startTimeMs,
                    endTime = endTimeMs,
                    estimatedCompletionTime = estimate,
                    associatedNotiRecords = assocIds.toSet(),
                    extractionSnapshotId = existing?.extractionSnapshotId,
                    origin = existing?.origin ?: "llm_auto_extraction",
                    humanEditCount = existing?.humanEditCount ?: 0,
                    deletedAtMs = null,
                    userEdited = false,
                    isVisible = true,
                    buttons = buttons,
                    isViewed = false,
                    isPinned = existing?.isPinned ?: false,
                    sortScore = sortScore,
                    reRankHistory = existingHistory.toString(),
                )

                ctx.reminderRepository.upsert(unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response ($trigger)", e)
        }

        return ctx.success()
    }
}

