package org.muilab.notigpt.data.remote.n8n.workers.handlers

import androidx.work.ListenableWorker
import org.json.JSONArray
import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.data.remote.n8n.workers.handlers.ExtractionStageSupport.Http
import java.util.UUID

/**
 * Periodic cross-thread reflection merge: groups look-alike active items (D2), then resolves each
 * group into merge ops (E2) staged for review.
 *
 * Items already under review (with pending ops) are excluded, and pairs still inside the
 * merge-rejection cool-down are passed as `excludePairs` so the model stops re-proposing them for a
 * while. Nothing is applied here — E2's ops land in `pending_proposed_op`.
 */
internal object ReflectionPipelineHandler {

    suspend fun handle(ctx: N8nWorkerContext): ListenableWorker.Result {
        val pendingProposedOpRepo = ctx.pendingProposedOpRepository()
        val activeItems = ExtractionStageSupport.activeCompactList(ctx, pendingProposedOpRepo)
        if (activeItems.size < 2) return ctx.success()

        val excludePairs = pendingProposedOpRepo.getActiveMergeCooldowns().map { listOf(it.itemIdA, it.itemIdB) }

        // D2 — candidate groups.
        val d2Payload = ExtractionStageSupport.baseEnvelope(ctx).apply {
            put("activeItems", activeItems)
            put("excludePairs", excludePairs)
        }
        val d2Body = (ExtractionStageSupport.call(ctx, BuildConfig.N8N_EXTRACT_D2_GROUPING_PATH, d2Payload) as? Http.Ok)?.body
            ?: return ctx.success()
        val groups = ExtractionStageSupport.parseObject(d2Body)?.optJSONArray("groups") ?: return ctx.success()
        if (groups.length() == 0) return ctx.success()

        // Build E2 group details (skip degenerate groups of < 2 real items).
        val groupsPayload = mutableListOf<Map<String, Any>>()
        for (i in 0 until groups.length()) {
            val ids = groups.optJSONArray(i) ?: continue
            val items = buildList {
                for (j in 0 until ids.length()) {
                    val item = ctx.reminderRepository.getById(ids.optString(j)) ?: continue
                    add(ExtractionStageSupport.itemDetail(ctx, item))
                }
            }
            if (items.size >= 2) groupsPayload += mapOf("items" to items)
        }
        if (groupsPayload.isEmpty()) return ctx.success()

        // E2 — resolve merges.
        val e2Payload = ExtractionStageSupport.baseEnvelope(ctx).apply {
            put("extractionPreferences", ctx.getExtractionPreferencesPayload())
            put("userContexts", ctx.getUserContextsPayload())
            put("groups", groupsPayload)
        }
        val e2Body = (ExtractionStageSupport.call(ctx, BuildConfig.N8N_EXTRACT_E2_MERGE_PATH, e2Payload) as? Http.Ok)?.body
            ?: return ctx.success()
        val ops = ExtractionStageSupport.parseObject(e2Body)?.optJSONArray("ops") ?: JSONArray()
        if (ops.length() > 0) {
            pendingProposedOpRepo.stageMergeOps(UUID.randomUUID().toString(), ops, emptyMap())
        }
        return ctx.success()
    }
}
