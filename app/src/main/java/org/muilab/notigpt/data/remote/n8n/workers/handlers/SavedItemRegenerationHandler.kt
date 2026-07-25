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
import org.muilab.notigpt.model.features.PendingProposedOpType
import org.muilab.notigpt.model.features.TodoStep
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
    suspend fun handleOne(ctx: N8nWorkerContext, inputData: Data, runAttemptCount: Int = 0): ListenableWorker.Result {
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
        val processing = ctx.pendingProposedOpRepository().getPendingForTarget(savedItemId)
            .singleOrNull { it.opType == PendingProposedOpType.Regenerate }
        val requestedVersion = processing?.let {
            runCatching { JSONObject(it.payload).optLong("sourceVersion", item.lastUpdateTimestamp) }.getOrNull()
        } ?: item.lastUpdateTimestamp

        val personalization = ctx.personalizationPayloadBuilder()
        val notiContext = buildNotiContextForSavedItem(ctx, item)
        val payload = buildPayload(
            savedItems = listOf(item.copy(lastUpdateTimestamp = requestedVersion)),
            stepsByItem = mapOf(savedItemId to ctx.database.todoStepDao().getBySavedItemId(savedItemId)),
            notiContextMap = mapOf(savedItemId to notiContext),
            linkedByItem = ctx.savedItemRepository.getLinkedRecordIdsFor(listOf(savedItemId)),
            trigger = "REGENERATE_ONE",
            personalizationEnvelope = personalization.regenerateEnvelope(),
        )

        return postAndApply(
            ctx = ctx,
            webhookPath = webhookPath,
            payload = payload,
            trigger = "REGENERATE_ONE",
            runAttemptCount = runAttemptCount,
        )
    }

    /**
     * Builds notification context records from a item's stored provenance.
     *
     * This is shared preparation for regeneration payloads. If item context loading is needed by
     * UI or sync too, move it behind a SavedItemContextRepository instead of copying this DB traversal.
     */
    internal suspend fun buildNotiContextForSavedItem(
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

    internal fun buildPayload(
        savedItems: List<SavedItem>,
        stepsByItem: Map<String, List<org.muilab.notigpt.model.features.TodoStep>>,
        notiContextMap: Map<String, List<Map<String, Any>>>,
        linkedByItem: Map<String, List<String>>,
        trigger: String,
        personalizationEnvelope: Map<String, Any>,
    ): Map<String, Any> {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

        val savedItemsPayload = savedItems.map { r ->
            val deadlineIso = if (r.deadlineAtMs > 0L) sdf.format(Date(r.deadlineAtMs)) else -1L
            mapOf(
                "savedItemId" to r.savedItemId,
                "sourceVersion" to r.lastUpdateTimestamp,
                "title" to r.title,
                "content" to r.content,
                "itemType" to r.itemType,
                "isCompleted" to r.isCompleted,
                "deadlineTimeString" to deadlineIso,
                "sourceNotiRecordIds" to (linkedByItem[r.savedItemId] ?: emptyList()),
                "userEdited" to r.userEdited,
                "buttons" to r.buttons,
                "steps" to stepsByItem[r.savedItemId].orEmpty(),
                "notiContext" to (notiContextMap[r.savedItemId] ?: emptyList<Any>()),
            )
        }

        return linkedMapOf<String, Any>(
            "userId" to SharedPreferencesManager.userId,
            // Send the stable IANA zone ID so n8n can interpret local calendar values reliably.
            "timezone" to TimeZone.getDefault().id,
            "currentTime" to sdf.format(Date()),
            "trigger" to trigger,
            "contractVersion" to 2,
            "savedItems" to savedItemsPayload,
        ).apply {
            putAll(personalizationEnvelope)
        }
    }

    private suspend fun postAndApply(
        ctx: N8nWorkerContext,
        webhookPath: String,
        payload: Map<String, Any>,
        trigger: String,
        runAttemptCount: Int,
    ): ListenableWorker.Result {
        val gson = Gson()
        val jsonPayload = gson.toJson(payload)
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val sourceIds = (payload["savedItems"] as? List<*>).orEmpty().mapNotNull { raw ->
            (raw as? Map<*, *>)?.get("savedItemId") as? String
        }
        suspend fun clearProcessing() {
            sourceIds.forEach { id ->
                ctx.pendingProposedOpRepository().clearProcessingTransform(id, PendingProposedOpType.Regenerate)
            }
        }

        Log.d(TAG, "Payload ($trigger) bytes=${jsonPayload.length}")

        val response = try {
            ctx.n8nApiService.postToWebhook(webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e(TAG, "Network exception ($trigger)", t)
            if (runAttemptCount < 1) return ctx.retry()
            clearProcessing()
            return ctx.failure()
        }

        if (!response.isSuccessful) return when {
            (response.code() == 429 || response.code() in 500..599) && runAttemptCount < 1 -> ctx.retry()
            else -> { clearProcessing(); ctx.failure() }
        }

        val bodyStr = response.body()?.string() ?: run { clearProcessing(); return ctx.failure() }
        Log.d(TAG, "Response ($trigger) bytes=${bodyStr.length}")

        try {
            // NonCancellable: a response is already in hand at this point. A concurrent REPLACE of
            // this unique work slot must not cut off persistence partway through the array, or
            // later savedItems in the same response are silently dropped even though the network
            // call already succeeded.
            withContext(NonCancellable) {
            val arr = JSONArray(bodyStr)
            val expectedVersions = (payload["savedItems"] as? List<*>).orEmpty()
                .mapNotNull { raw ->
                    val row = raw as? Map<*, *> ?: return@mapNotNull null
                    val id = row["savedItemId"] as? String ?: return@mapNotNull null
                    val version = row["sourceVersion"] as? Long ?: return@mapNotNull null
                    id to version
                }.toMap()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val savedItemId = N8nOpParsing.savedItemIdFrom(obj)
                if (savedItemId.isBlank()) continue

                val existing = ctx.savedItemRepository.getById(savedItemId)
                if (existing == null) continue
                val expectedVersion = expectedVersions[savedItemId] ?: existing.syncModifiedAt
                val title = N8nOpParsing.titleFrom(obj, existing.title)
                val content = N8nOpParsing.contentFrom(obj, existing.content)
                val deadline = N8nOpParsing.isoToUnixMillis(obj.optString("deadlineTimeString", "-1"))
                val buttons = SavedItemNormalization.mergeButtons(
                    obj.optJSONArray("buttons")?.toString() ?: "[]",
                    N8nOpParsing.childButtons(obj.optJSONArray("steps")),
                )
                val returnedSteps = N8nOpParsing.parseSteps(
                    obj.optJSONArray("steps"),
                    savedItemId,
                    existing.syncModifiedAt,
                    baseSortOrder = 0,
                )
                val existingSteps = ctx.database.todoStepDao().getBySavedItemId(savedItemId)
                fun stepShape(items: List<org.muilab.notigpt.model.features.TodoStep>) = items.map {
                    TodoStep.normalizeText(it.text) to it.isCompleted
                }
                val materiallySame = title.trim() == existing.title.trim() &&
                    content.trim() == existing.content.trim() &&
                    deadline == existing.deadlineAtMs &&
                    buttons == existing.buttons &&
                    obj.optString("itemType", existing.itemType) == existing.itemType &&
                    stepShape(returnedSteps) == stepShape(existingSteps)
                if (materiallySame) {
                    ctx.pendingProposedOpRepository().clearProcessingTransform(
                        savedItemId,
                        PendingProposedOpType.Regenerate,
                    )
                    continue
                }

                ctx.pendingProposedOpRepository().stageTransform(
                    sourceItemId = savedItemId,
                    type = PendingProposedOpType.Regenerate,
                    result = JSONObject().apply {
                        put("sourceVersion", expectedVersion)
                        put("reason", obj.optString("changeSummary", "Regenerated"))
                        put("result", obj)
                    },
                ) ?: ctx.pendingProposedOpRepository().clearProcessingTransform(
                    savedItemId,
                    PendingProposedOpType.Regenerate,
                )
            }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response ($trigger)", e)
            clearProcessing()
            return ctx.failure()
        }

        return ctx.success()
    }

}
