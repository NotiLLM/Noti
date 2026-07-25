package org.muilab.notigpt.data.remote.n8n.workers.handlers

import android.util.Log
import androidx.work.ListenableWorker
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.data.remote.n8n.workers.N8nWorkerInput
import org.muilab.notigpt.model.features.PendingProposedOpType

/** Runs the user-requested, two-pass Split workflow and stages its atomic review batch. */
internal object SavedItemSplitHandler {
    private const val TAG = "N8nSplit"

    suspend fun handle(
        ctx: N8nWorkerContext,
        input: N8nWorkerInput.SplitOne,
    ): ListenableWorker.Result {
        val item = ctx.savedItemRepository.getById(input.savedItemId) ?: return ctx.success()
        if (item.isCompleted || item.isArchived) return ctx.success()
        val processing = ctx.pendingProposedOpRepository().getPendingForTarget(item.savedItemId)
            .singleOrNull { it.opType == PendingProposedOpType.Split }
        val requestedVersion = processing?.let {
            runCatching { JSONObject(it.payload).optLong("sourceVersion", item.lastUpdateTimestamp) }.getOrNull()
        } ?: item.lastUpdateTimestamp
        val personalization = ctx.personalizationPayloadBuilder()
        val context = SavedItemRegenerationHandler.buildNotiContextForSavedItem(ctx, item)
        val payload = SavedItemRegenerationHandler.buildPayload(
            savedItems = listOf(item.copy(lastUpdateTimestamp = requestedVersion)),
            stepsByItem = mapOf(item.savedItemId to ctx.database.todoStepDao().getBySavedItemId(item.savedItemId)),
            notiContextMap = mapOf(item.savedItemId to context),
            linkedByItem = ctx.savedItemRepository.getLinkedRecordIdsFor(listOf(item.savedItemId)),
            trigger = "SPLIT_ONE",
            personalizationEnvelope = personalization.regenerateEnvelope(),
        )
        val bodyJson = Gson().toJson(payload)
        val response = try {
            ctx.n8nApiService.postToWebhook(
                input.webhookPath,
                bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()),
            )
        } catch (t: Throwable) {
            // A workflow execution contains both model passes. Retrying the WorkManager job would
            // rerun the entire operation, so terminal network failure is surfaced to the UI.
            Log.e(TAG, "Split request failed", t)
            ctx.pendingProposedOpRepository().clearProcessingTransform(item.savedItemId, PendingProposedOpType.Split)
            return ctx.failure()
        }
        if (!response.isSuccessful) {
            Log.w(TAG, "Split failed: HTTP ${response.code()}")
            ctx.pendingProposedOpRepository().clearProcessingTransform(item.savedItemId, PendingProposedOpType.Split)
            return ctx.failure()
        }
        val raw = response.body()?.string().orEmpty()
        val result = parseResult(raw) ?: run {
            ctx.pendingProposedOpRepository().clearProcessingTransform(item.savedItemId, PendingProposedOpType.Split)
            return ctx.failure()
        }
        if (result.optString("status", "split") == "no_split") {
            ctx.pendingProposedOpRepository().clearProcessingTransform(item.savedItemId, PendingProposedOpType.Split)
            return ctx.success()
        }
        val children = result.optJSONArray("children")
        if (children == null || children.length() < 2 || !structurallyValid(children)) {
            ctx.pendingProposedOpRepository().clearProcessingTransform(item.savedItemId, PendingProposedOpType.Split)
            return ctx.failure()
        }

        result.put("sourceVersion", requestedVersion)
        val staged = ctx.pendingProposedOpRepository().stageTransform(
            sourceItemId = item.savedItemId,
            type = PendingProposedOpType.Split,
            result = result,
        )
        if (staged == null) {
            ctx.pendingProposedOpRepository().clearProcessingTransform(item.savedItemId, PendingProposedOpType.Split)
            return ctx.failure()
        }
        return ctx.success()
    }

    private fun parseResult(raw: String): JSONObject? = try {
        when {
            raw.trimStart().startsWith("[") -> JSONArray(raw).optJSONObject(0)
            else -> JSONObject(raw)
        }
    } catch (_: Exception) {
        null
    }

    private fun structurallyValid(children: JSONArray): Boolean {
        for (index in 0 until children.length()) {
            val child = children.optJSONObject(index) ?: return false
            if (child.optString("title").isBlank()) return false
            if (child.optString("itemType") !in setOf("todo", "keep")) return false
            if (child.optString("itemType") == "keep" &&
                child.optString("deadlineTimeString", "-1") !in setOf("", "-1")
            ) return false
        }
        return true
    }
}
