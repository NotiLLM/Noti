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
import org.muilab.notigpt.data.remote.n8n.N8nOpParsing
import org.muilab.notigpt.data.remote.n8n.formatter.N8nRecordFormatter
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.model.features.ExtractionJournalEntry
import org.muilab.notigpt.model.features.ExtractionJournalEventType
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemChangeType
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.domain.saveditem.SavedItemNormalization
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.util.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Worker handler for regenerating one item's content from its stored notification context.
 *
 * Use this when an existing item needs fresh backend output without re-running the extraction
 * pipeline. Keep payload building here and shared snapshot/status rules in the item domain
 * helpers.
 */
/** Regenerates one SavedItem from its notification evidence. */
internal object SavedItemRegenerationHandler {

    private const val TAG = "N8nRegeneration"

    /**
     * Regenerates one item using its current local item row and related notification context.
     *
     * The item ID stays stable; n8n is asked to revise content, not create a separate item.
     */
    suspend fun handleOne(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e(TAG, "No webhook_path for regenerate_one")
            return ctx.failure()
        }
        val savedItemId = inputData.getString("saved_item_id")
            ?: inputData.getString("reminder_id")
            ?: run {
            Log.e(TAG, "No saved_item_id for regenerate_one")
            return ctx.failure()
        }

        val item = ctx.savedItemRepository.getById(savedItemId) ?: run {
            Log.w(TAG, "SavedItem $savedItemId not found")
            return ctx.success()
        }

        val notiContext = buildNotiContextForSavedItem(ctx, item)
        val payload = buildPayload(
            savedItems = listOf(item),
            notiContextMap = mapOf(savedItemId to notiContext),
            linkedByItem = ctx.savedItemRepository.getLinkedRecordIdsFor(listOf(savedItemId)),
            trigger = "REGENERATE_ONE",
            extractionPreferences = ctx.getExtractionPreferencesPayload(),
            userContexts = ctx.getUserContextsPayload(),
        )

        return postAndApply(ctx, webhookPath, payload, trigger = "REGENERATE_ONE")
    }

    /**
     * Builds notification context records from a item's stored provenance.
     *
     * This is shared preparation for regeneration payloads. If item context loading is needed by
     * UI or sync too, move it behind a SavedItemContextRepository instead of copying this DB traversal.
     */
    private suspend fun buildNotiContextForSavedItem(
        ctx: N8nWorkerContext,
        item: SavedItem,
    ): List<Map<String, Any>> {
        val db = ctx.database
        val wantedKeys = ctx.savedItemRepository.getLinkedKeys(item.savedItemId)
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
        savedItems: List<SavedItem>,
        notiContextMap: Map<String, List<Map<String, Any>>>,
        linkedByItem: Map<String, List<String>>,
        trigger: String,
        extractionPreferences: List<Map<String, String>>,
        userContexts: List<Map<String, String>>,
    ): Map<String, Any> {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

        val savedItemsPayload = savedItems.map { r ->
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
                "sourceNotiRecordIds" to (linkedByItem[r.savedItemId] ?: emptyList()),
                "userEdited" to r.userEdited,
                "buttons" to r.buttons,
                "notiContext" to (notiContextMap[r.savedItemId] ?: emptyList<Any>()),
            )
        }

        return mapOf(
            "userId" to SharedPreferencesManager.userId,
            "language" to Locale.getDefault().toLanguageTag(),
            // Send the stable IANA zone ID so n8n can interpret local calendar values reliably.
            "timezone" to TimeZone.getDefault().id,
            "currentTime" to sdf.format(Date()),
            "targetExtractionLanguage" to SharedPreferencesManager.targetExtractionLanguage,
            "trigger" to trigger,
            "contractVersion" to 3,
            "savedItems" to savedItemsPayload,
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

        Log.d(TAG, "Payload ($trigger) bytes=${jsonPayload.length}")

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
        Log.d(TAG, "Response ($trigger) bytes=${bodyStr.length}")

        try {
            // NonCancellable: a response is already in hand at this point. A concurrent REPLACE of
            // this unique work slot must not cut off persistence partway through the array, or
            // later savedItems in the same response are silently dropped even though the network
            // call already succeeded.
            withContext(NonCancellable) {
            val arr = JSONArray(bodyStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val savedItemId = N8nOpParsing.savedItemIdFrom(obj)
                if (savedItemId.isBlank()) continue

                val existing = ctx.savedItemRepository.getById(savedItemId)
                val now = System.currentTimeMillis()

                val title = N8nOpParsing.titleFrom(obj, existing?.title ?: "")
                val content = N8nOpParsing.contentFrom(obj, existing?.content ?: "")
                val isTask = obj.optBoolean("isTask", existing?.isTask ?: true)
                val deadlineMs = N8nOpParsing.isoToUnixMillis(obj.optString("deadlineTimeString", "-1"))
                val startAtMsMs = N8nOpParsing.startAtMsFrom(obj)
                val endAtMsMs = N8nOpParsing.endAtMsFrom(obj)
                val isCompleted = obj.optBoolean("isCompleted", existing?.isCompleted ?: false)

                // Parse buttons
                val buttonsArr = obj.optJSONArray("buttons")
                val buttons = SavedItemNormalization.mergeButtons(
                    buttonsArr?.toString() ?: existing?.buttons ?: "[]",
                    N8nOpParsing.childButtons(obj.optJSONArray("subTasks")),
                )

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
                    origin = existing?.origin ?: "llm_auto_extraction",
                    humanEditCount = existing?.humanEditCount ?: 0,
                    // Preserve manual-edit provenance; this flag no longer blocks model updates
                    // or Firestore reconciliation.
                    userEdited = existing?.userEdited ?: false,
                    buttons = buttons,
                    isViewed = false,
                    // User-owned fields: regeneration must never reset them.
                    isStarred = existing?.isStarred ?: false,
                    whenAtMs = existing?.whenAtMs ?: 0L,
                    // Advance the review cursor past this regeneration's change-log row so it never
                    // surfaces as "what's new" and revert never treats the rewrite as pending.
                    lastViewedChangeAt = now,
                )

                val returnedSubTasks = N8nOpParsing.parseSubTasks(
                    obj.optJSONArray("subTasks"),
                    savedItemId,
                    now,
                    baseSortOrder = 0,
                )
                val normalized = SavedItemNormalization.normalize(unit, returnedSubTasks)
                ctx.savedItemRepository.upsert(normalized.item)
                ctx.savedSubItemRepository.replaceForParent(savedItemId, normalized.subItems)
                // Regeneration rewrites content from the item's existing noti context; it neither
                // adds nor removes provenance, so links stay untouched.

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
                    ctx.savedItemRepository.getLinkedKeys(savedItemId).forEach { key ->
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
