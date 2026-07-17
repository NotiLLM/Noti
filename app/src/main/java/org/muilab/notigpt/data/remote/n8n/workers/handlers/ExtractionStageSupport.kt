package org.muilab.notigpt.data.remote.n8n.workers.handlers

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.data.remote.n8n.ExtractionStatusStore
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.data.remote.n8n.formatter.N8nRecordFormatter
import org.muilab.notigpt.data.repository.saveditem.PendingProposedOpRepository
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.util.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Shared request/response plumbing for the per-notiKey extraction pipeline stages (A–E2).
 *
 * Each stage is its own n8n workflow/webhook; the Android side runs them sequentially inside one
 * worker job. This object owns the common payload envelope, the item/record formatting the stages
 * agree on, and the HTTP call + tolerant JSON unwrap so the stage handlers stay readable.
 */
internal object ExtractionStageSupport {

    const val TAG = "N8nWebhook"
    private val gson = Gson()

    /** Outcome of one stage HTTP call. [Retry] means transient (network / 5xx / 429). */
    sealed class Http {
        data class Ok(val body: String) : Http()
        object Retry : Http()
        object Fail : Http()
    }

    // ========== Envelope & time ==========

    /** Common top-level fields every stage payload carries. Stage-specific fields are added on top. */
    suspend fun baseEnvelope(ctx: N8nWorkerContext): MutableMap<String, Any> = mutableMapOf(
        "userId" to SharedPreferencesManager.userId,
        "language" to Locale.getDefault().toLanguageTag(),
        "timezone" to TimeZone.getDefault().id,
        "currentTime" to isoNow(),
        "targetExtractionLanguage" to ctx.getTargetExtractionLanguage(),
        "contractVersion" to 3,
    )

    fun isoNow(): String = isoFormat.format(Date())

    fun iso(ms: Long): Any = if (ms > 0L) isoFormat.format(Date(ms)) else -1L

    private val isoFormat get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

    // ========== Record & item formatting ==========

    fun formatRecords(records: List<NotiRecord>, isPeople: Boolean): List<Map<String, Any>> =
        records.map { N8nRecordFormatter.format(it, isPeople) }

    /** Compact one-line item, used in the D-stage shortlist/grouping lists. */
    fun itemCompact(item: SavedItem): Map<String, Any> = mapOf(
        "itemId" to item.savedItemId,
        "title" to item.title,
        "type" to if (item.isTask) "task" else "keep",
        "deadline" to iso(item.deadlineAtMs),
        "when" to if (SavedItem.isSomeday(item.whenAtMs)) "someday" else iso(item.whenAtMs),
    )

    /** Full item detail, used where a stage reasons over content (B linked items, E-stage pairs/groups). */
    suspend fun itemDetail(ctx: N8nWorkerContext, item: SavedItem): Map<String, Any> {
        val subTasks = try {
            ctx.database.subTaskDao().getBySavedItemId(item.savedItemId).map { st ->
                mapOf(
                    "subTaskId" to st.savedSubItemId,
                    "text" to st.text,
                    "isCompleted" to st.isCompleted,
                    "position" to st.position,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
        val recordIds = try {
            ctx.savedItemRepository.getLinkedRecordIdsFor(listOf(item.savedItemId))[item.savedItemId] ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val sourceNotiKeys = recordIds.map { it.substringBeforeLast("_") }.distinct()
        return mapOf(
            "itemId" to item.savedItemId,
            "title" to item.title,
            "content" to item.content,
            "type" to if (item.isTask) "task" else "keep",
            "deadline" to iso(item.deadlineAtMs),
            "startTime" to iso(item.startAtMs),
            "endTime" to iso(item.endAtMs),
            "when" to if (SavedItem.isSomeday(item.whenAtMs)) "someday" else iso(item.whenAtMs),
            "userEdited" to item.userEdited,
            "isCompleted" to item.isCompleted,
            "buttons" to item.buttons,
            "sourceNotiRecordIds" to recordIds,
            "sourceNotiKeys" to sourceNotiKeys,
            "subTasks" to subTasks,
        )
    }

    /** Item detail for a not-yet-persisted preview (a staged create), tagged with its op reference. */
    fun previewDetail(preview: PendingProposedOpRepository.Preview, newOpRef: String): Map<String, Any> = mapOf(
        "newOpRef" to newOpRef,
        "title" to preview.item.title,
        "content" to preview.item.content,
        "type" to if (preview.item.isTask) "task" else "keep",
        "deadline" to iso(preview.item.deadlineAtMs),
        "startTime" to iso(preview.item.startAtMs),
        "endTime" to iso(preview.item.endAtMs),
        "subTasks" to preview.subItems.map { st ->
            mapOf(
                "text" to st.text,
                "isCompleted" to st.isCompleted,
                "position" to st.position,
            )
        },
    )

    /**
     * Active items (excluding completed/archived) as compact lines, minus items that already have
     * unreviewed staged ops against them — the merge stages must not target items mid-review.
     */
    suspend fun activeCompactList(ctx: N8nWorkerContext, pendingProposedOpRepo: PendingProposedOpRepository): List<Map<String, Any>> {
        val targeted = pendingProposedOpRepo.getTargetedItemIds()
        return ctx.savedItemRepository.getAllActive()
            .filter { !it.isCompleted && !it.isArchived && it.savedItemId !in targeted }
            .map { itemCompact(it) }
    }

    // ========== HTTP ==========

    /** Posts [payload] (serialized to JSON) to [path]; records pipeline health for the banner. */
    suspend fun call(ctx: N8nWorkerContext, path: String, payload: Map<String, Any>): Http {
        val json = gson.toJson(payload)
        Log.d(TAG, "Stage POST $path bytes=${json.length}")
        val response = try {
            ctx.n8nApiService.postToWebhook(path, json.toRequestBody("application/json; charset=utf-8".toMediaType()))
        } catch (t: Throwable) {
            Log.e(TAG, "Stage network exception ($path)", t)
            ExtractionStatusStore.recordFailure(ExtractionStatusStore.FailureKind.Network)
            return Http.Retry
        }
        if (!response.isSuccessful) {
            ExtractionStatusStore.recordFailure(ExtractionStatusStore.FailureKind.Server)
            return when {
                response.code() == 429 || response.code() in 500..599 -> Http.Retry
                else -> Http.Fail
            }
        }
        ExtractionStatusStore.recordSuccess()
        val body = response.body()?.string() ?: return Http.Fail
        Log.d(TAG, "Stage response ($path) bytes=${body.length}")
        return Http.Ok(body)
    }

    /**
     * Parses a stage response body into a JSON object, tolerating the `{ "text": "{...}" }` wrapper
     * some n8n Gemini nodes emit (optionally markdown-fenced).
     */
    fun parseObject(body: String): JSONObject? {
        val trimmed = body.trim()
        val root = try {
            if (trimmed.startsWith("{")) JSONObject(trimmed) else return null
        } catch (_: Exception) {
            return null
        }
        // Direct object (already the stage payload).
        if (!root.has("text") || root.length() > 1) return root
        val inner = extractJsonFromText(root.optString("text", "")) ?: return root
        return try {
            JSONObject(inner)
        } catch (_: Exception) {
            root
        }
    }

    private fun extractJsonFromText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val fence = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```", RegexOption.IGNORE_CASE)
        return fence.find(trimmed)?.groupValues?.getOrNull(1)
    }

    /** Reads a boolean that the model may encode as true/false or 1/0. */
    fun optBoolLoose(obj: JSONObject, key: String, default: Boolean = false): Boolean =
        obj.optBoolean(key, obj.optInt(key, if (default) 1 else 0) == 1)

    fun jsonArray(obj: JSONObject, key: String): JSONArray = obj.optJSONArray(key) ?: JSONArray()
}
