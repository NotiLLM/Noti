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
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.util.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Worker handler for regenerating reminder content from stored notification context.
 *
 * Use this when an existing reminder needs fresh backend output without re-running scan selection. Keep payload
 * building here and shared snapshot/status rules in the reminder domain helpers.
 */
internal object ReminderRegenerationHandler {

    private const val TAG = "N8nRegeneration"

    /**
     * Regenerates one reminder using its current local reminder row and related notification context.
     *
     * The reminder ID stays stable; n8n is asked to revise content, not create a separate reminder.
     */
    suspend fun handleOne(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e(TAG, "No webhook_path for regenerate_one")
            return ctx.failure()
        }
        val savedItemId = inputData.getString("reminder_id") ?: run {
            Log.e(TAG, "No reminder_id for regenerate_one")
            return ctx.failure()
        }

        val reminder = ctx.reminderRepository.getById(savedItemId) ?: run {
            Log.w(TAG, "Reminder $savedItemId not found")
            return ctx.success()
        }

        val notiContext = buildNotiContextForReminder(ctx, reminder)
        val payload = buildPayload(
            reminders = listOf(reminder),
            notiContextMap = mapOf(savedItemId to notiContext),
            trigger = "REGENERATE_ONE",
            extractionPreferences = ctx.getExtractionPreferencesPayload(),
            userContexts = ctx.getUserContextsPayload(),
        )

        return postAndApply(ctx, webhookPath, payload, trigger = "REGENERATE_ONE")
    }

    /**
     * Regenerates every visible reminder in one remote request.
     *
     * Keep this path batch-oriented so global reranking/regeneration can see the current reminder set
     * together instead of applying isolated single-reminder edits.
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
            notiContextMap[r.savedItemId] = buildNotiContextForReminder(ctx, r)
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
     * Builds notification context records from a reminder's stored provenance.
     *
     * This is shared preparation for regeneration payloads. If reminder context loading is needed by
     * UI or sync too, move it behind a ReminderContextRepository instead of copying this DB traversal.
     */
    private suspend fun buildNotiContextForReminder(
        ctx: N8nWorkerContext,
        reminder: SavedItem,
    ): List<Map<String, Any>> {
        if (reminder.sourceNotiRecordIds.isEmpty()) return emptyList()

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
        reminders: List<SavedItem>,
        notiContextMap: Map<String, List<Map<String, Any>>>,
        trigger: String,
        extractionPreferences: List<Map<String, String>>,
        userContexts: List<Map<String, String>>,
    ): Map<String, Any> {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

        val remindersPayload = reminders.map { r ->
            val deadlineIso = if (r.deadlineAtMs > 0L) sdf.format(Date(r.deadlineAtMs)) else -1L
            val startAtMsIso = if (r.startAtMs > 0L) sdf.format(Date(r.startAtMs)) else -1L
            val endAtMsIso = if (r.endAtMs > 0L) sdf.format(Date(r.endAtMs)) else -1L
            mapOf(
                "savedItemId" to r.savedItemId,
                "title" to r.title,
                "content" to r.content,
                "isTask" to r.isTask,
                "isEvent" to r.isEvent,
                "isCompleted" to r.isCompleted,
                "deadlineTimeString" to deadlineIso,
                "startAtMsString" to startAtMsIso,
                "endAtMsString" to endAtMsIso,
                "estimatedCompletionMinutes" to r.estimatedCompletionTime,
                "sourceNotiRecordIds" to r.sourceNotiRecordIds.toList(),
                "userEdited" to r.userEdited,
                "buttons" to r.buttons,
                "sortScore" to r.sortScore,
                "isPinned" to r.isPinned,
                "reRankHistory" to r.reRankHistory,
                "notiContext" to (notiContextMap[r.savedItemId] ?: emptyList<Any>()),
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
                val savedItemId = obj.optString("savedItemId", obj.optString("taskId"))
                if (savedItemId.isBlank()) continue

                val existing = ctx.reminderRepository.getById(savedItemId)

                val title = obj.optString("title", existing?.title ?: "")
                val content = obj.optString("content", existing?.content ?: "")
                val isTask = obj.optBoolean("isTask", existing?.isTask ?: true)
                val isEvent = obj.optBoolean("isEvent", existing?.isEvent ?: false)
                val deadlineMs = ReminderExtractionHandler.isoToUnixMillis(obj.optString("deadlineTimeString", "-1"))
                val startAtMsMs = ReminderExtractionHandler.isoToUnixMillis(obj.optString("startAtMsString", "-1")).let { v -> if (v == -1L) 0L else v }
                val endAtMsMs = ReminderExtractionHandler.isoToUnixMillis(obj.optString("endAtMsString", "-1")).let { v -> if (v == -1L) 0L else v }
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

                // Preserve sourceNotiRecordIds from existing
                val assocIds = mutableSetOf<String>()
                val assoc = obj.optJSONArray("sourceNotiRecordIds") ?: obj.optJSONArray("associatedNotis")
                if (assoc != null) {
                    for (j in 0 until assoc.length()) assocIds.add(assoc.optString(j))
                }
                if (assocIds.isEmpty() && existing != null) {
                    assocIds.addAll(existing.sourceNotiRecordIds)
                }

                val unit = SavedItem(
                    savedItemId = savedItemId,
                    title = title,
                    content = content,
                    itemType = if (isTask) SavedItemType.Task else SavedItemType.Keep,
                    state = if (existing == null) SavedItemState.New else SavedItemState.Updated,
                    lastUpdateTimestamp = System.currentTimeMillis(),
                    deadlineAtMs = deadlineMs,
                    startAtMs = startAtMsMs,
                    endAtMs = endAtMsMs,
                    estimatedCompletionTime = estimate,
                    sourceNotiRecordIds = assocIds.toSet(),
                    sourceExtractionSnapshotId = existing?.sourceExtractionSnapshotId,
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

