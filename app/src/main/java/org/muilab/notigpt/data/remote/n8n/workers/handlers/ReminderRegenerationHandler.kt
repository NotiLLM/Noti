package org.muilab.notigpt.data.remote.n8n.workers.handlers

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.data.remote.n8n.formatter.N8nRecordFormatter
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.model.features.ExtractionJournalEntry
import org.muilab.notigpt.model.features.ExtractionJournalEventType
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemChangeType
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
            linkedByItem = ctx.reminderRepository.getLinkedRecordIdsFor(listOf(savedItemId)),
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
            linkedByItem = ctx.reminderRepository.getLinkedRecordIdsFor(allReminders.map { it.savedItemId }),
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
        val db = ctx.database
        val wantedKeys = ctx.reminderRepository.getLinkedKeys(reminder.savedItemId)
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
        linkedByItem: Map<String, List<String>>,
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
                "sourceNotiRecordIds" to (linkedByItem[r.savedItemId] ?: emptyList()),
                "userEdited" to r.userEdited,
                "buttons" to r.buttons,
                "sortScore" to r.sortScore,
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
            // NonCancellable: a response is already in hand at this point. A concurrent REPLACE of
            // this unique work slot must not cut off persistence partway through the array, or
            // later reminders in the same response are silently dropped even though the network
            // call already succeeded.
            withContext(NonCancellable) {
            val arr = JSONArray(bodyStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val savedItemId = ReminderExtractionHandler.savedItemIdFrom(obj)
                if (savedItemId.isBlank()) continue

                val existing = ctx.reminderRepository.getById(savedItemId)
                val now = System.currentTimeMillis()

                val title = ReminderExtractionHandler.titleFrom(obj, existing?.title ?: "")
                val content = ReminderExtractionHandler.contentFrom(obj, existing?.content ?: "")
                val isTask = obj.optBoolean("isTask", existing?.isTask ?: true)
                val isEvent = obj.optBoolean("isEvent", existing?.isEvent ?: false)
                val deadlineMs = ReminderExtractionHandler.isoToUnixMillis(obj.optString("deadlineTimeString", "-1"))
                val startAtMsMs = ReminderExtractionHandler.startAtMsFrom(obj)
                val endAtMsMs = ReminderExtractionHandler.endAtMsFrom(obj)
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

                // Regeneration is user-initiated, so it must NOT drop the item into the review queue
                // (an unrevertible wholesale rewrite has no place in the swipe-to-reject flow). A brand
                // new row is still reviewable; an existing pending item is auto-acknowledged to `saved`;
                // otherwise the prior list state (saved/completed/archived) is preserved.
                val newState = when {
                    existing == null -> SavedItemState.New
                    SavedItemState.isNewLike(existing.state) -> SavedItemState.Saved
                    else -> existing.state
                }

                // Preserve linked record ids from existing links when the response omits them.
                val unit = SavedItem(
                    savedItemId = savedItemId,
                    title = title,
                    content = content,
                    itemType = if (isTask) SavedItemType.Task else SavedItemType.Keep,
                    state = newState,
                    lastUpdateTimestamp = now,
                    deadlineAtMs = deadlineMs,
                    startAtMs = startAtMsMs,
                    endAtMs = endAtMsMs,
                    estimatedCompletionTime = estimate,
                    origin = existing?.origin ?: "llm_auto_extraction",
                    humanEditCount = existing?.humanEditCount ?: 0,
                    deletedAtMs = null,
                    userEdited = false,
                    isVisible = true,
                    buttons = buttons,
                    isViewed = false,
                    sortScore = sortScore,
                    reRankHistory = existingHistory.toString(),
                    // User-owned fields: regeneration must never reset them.
                    isStarred = existing?.isStarred ?: false,
                    doAtMs = existing?.doAtMs ?: 0L,
                    // Advance the review cursor past this regeneration's change-log row so it never
                    // surfaces as "what's new" and revert never treats the rewrite as pending.
                    lastViewedChangeAt = now,
                )

                ctx.reminderRepository.upsert(unit)
                // Regeneration rewrites content from the item's existing noti context; it neither
                // adds nor removes provenance, so links stay untouched.
                ReminderExtractionHandler.persistReturnedSubTasks(ctx, savedItemId, obj, now)

                // Record the rewrite in the change history (regeneration is the one flow allowed
                // to replace text wholesale — the user explicitly asked for it) and the journal.
                if (existing != null) {
                    val changeSummary = obj.optString("changeSummary", "")
                    val changedFields = JSONObject().apply {
                        if (existing.title != title) {
                            put("title", JSONObject().put("old", existing.title).put("new", title))
                        }
                        if (existing.content != content) {
                            put("content", JSONObject().put("old", existing.content).put("new", content))
                        }
                        if (existing.deadlineAtMs != deadlineMs) {
                            put("deadlineAtMs", JSONObject().put("old", existing.deadlineAtMs).put("new", deadlineMs))
                        }
                    }
                    ctx.changeLogRepository.record(
                        SavedItemChangeLog(
                            savedItemId = savedItemId,
                            createdAt = now,
                            changeType = SavedItemChangeType.Regenerated,
                            changeSummary = changeSummary,
                            changedFieldsJson = changedFields.toString(),
                            origin = "llm",
                        )
                    )
                    ctx.reminderRepository.getLinkedKeys(savedItemId).forEach { key ->
                        ctx.journalRepository.append(
                            ExtractionJournalEntry(
                                notiKey = key,
                                createdAt = now,
                                eventType = ExtractionJournalEventType.ItemUpdated,
                                savedItemId = savedItemId,
                                itemTitle = title,
                                detail = changeSummary.ifBlank { "Regenerated ($trigger)" },
                            )
                        )
                    }
                }
            }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response ($trigger)", e)
        }

        return ctx.success()
    }
}

