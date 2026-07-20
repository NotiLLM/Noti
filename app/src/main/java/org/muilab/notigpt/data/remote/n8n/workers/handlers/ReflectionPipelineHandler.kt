package org.muilab.notigpt.data.remote.n8n.workers.handlers

import androidx.work.ListenableWorker
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.data.remote.n8n.workers.handlers.ExtractionStageSupport.Http
import org.muilab.notigpt.data.repository.saveditem.PendingProposedOpRepository
import org.muilab.notigpt.model.features.PendingProposedOp
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedSubItem
import java.util.UUID

/**
 * Periodic cross-thread reflection. D2 runs once, then each strongest candidate group is resolved by
 * its own E2 request. Pending review groups participate through their eventual in-memory preview,
 * so a later merge/update naturally joins the same item-level review card.
 */
internal object ReflectionPipelineHandler {

    internal const val MAX_REFLECTION_GROUPS = 5
    internal const val MAX_CONCURRENT_E2_CALLS = 2

    internal data class Candidate(
        val ref: String,
        val item: SavedItem,
        val subItems: List<SavedSubItem>,
        val group: PendingProposedOpRepository.OpGroup? = null,
        val evidenceRecordIds: Set<String> = emptySet(),
    ) {
        val isPending: Boolean get() = group != null
    }

    suspend fun handle(ctx: N8nWorkerContext): ListenableWorker.Result {
        val pendingRepo = ctx.pendingProposedOpRepository()
        val candidates = buildCandidates(ctx, pendingRepo)
        if (candidates.size < 2) return ctx.success()

        val excludePairs = pendingRepo.getActiveMergeCooldowns().map { listOf(it.itemIdA, it.itemIdB) }
        val d2Payload = ExtractionStageSupport.baseEnvelope(ctx).apply {
            put("candidates", candidates.map(::compactCandidate))
            // Kept for old deployed D2 templates while the compatible workflow update rolls out.
            put("activeItems", candidates.filterNot(Candidate::isPending).map { ExtractionStageSupport.itemCompact(it.item) })
            put("excludePairs", excludePairs)
            put("maxGroups", MAX_REFLECTION_GROUPS)
        }
        val d2Body = when (val result = ExtractionStageSupport.call(ctx, BuildConfig.N8N_EXTRACT_D2_GROUPING_PATH, d2Payload)) {
            is Http.Ok -> result.body
            is Http.Retry -> return ctx.retry()
            is Http.Fail -> return ctx.success()
        }
        val rawGroups = ExtractionStageSupport.parseObject(d2Body)?.optJSONArray("groups") ?: return ctx.success()
        val candidateByRef = candidates.associateBy(Candidate::ref)
        val groups = normalizeGroups(rawGroups, candidateByRef.keys, MAX_REFLECTION_GROUPS)
        if (groups.isEmpty()) return ctx.success()

        val preferences = ctx.getExtractionPreferencesPayload()
        val userContexts = ctx.getUserContextsPayload()
        var sawRetryableFailure = false

        // Bound request bursts while still avoiding a potentially 25-minute sequential worker.
        for (chunk in groups.chunked(MAX_CONCURRENT_E2_CALLS)) {
            val results = coroutineScope {
                chunk.map { refs ->
                    async {
                        val details = refs.mapNotNull(candidateByRef::get).map { candidateDetail(ctx, it) }
                        val payload = ExtractionStageSupport.baseEnvelope(ctx).apply {
                            put("extractionPreferences", preferences)
                            put("userContexts", userContexts)
                            put("group", mapOf("items" to details))
                            // Legacy compatibility: new clients guarantee exactly one element.
                            put("groups", listOf(mapOf("items" to details)))
                        }
                        refs to ExtractionStageSupport.call(ctx, BuildConfig.N8N_EXTRACT_E2_MERGE_PATH, payload)
                    }
                }.awaitAll()
            }

            for ((refs, result) in results) {
                when (result) {
                    is Http.Retry -> sawRetryableFailure = true
                    is Http.Fail -> Unit
                    is Http.Ok -> {
                        val ops = ExtractionStageSupport.parseObject(result.body)?.optJSONArray("ops") ?: JSONArray()
                        if (ops.length() == 0 || !validateE2Ops(ops, refs, candidateByRef)) continue
                        val pendingGroups = refs.mapNotNull { ref -> candidateByRef[ref]?.group?.let { ref to it } }.toMap()
                        val savedIds = refs.mapNotNull { ref ->
                            candidateByRef[ref]?.takeIf { it.group == null }?.item?.savedItemId?.let { ref to it }
                        }.toMap()
                        val whens = refs.associateWith { ref -> candidateByRef.getValue(ref).item.whenAtMs }
                        pendingRepo.stageReflectionOps(
                            batchId = UUID.randomUUID().toString(),
                            ops = ops,
                            pendingGroupsByRef = pendingGroups,
                            savedItemIdsByRef = savedIds,
                            candidateWhenByRef = whens,
                        )
                    }
                }
            }
        }
        return if (sawRetryableFailure) ctx.retry() else ctx.success()
    }

    private suspend fun buildCandidates(
        ctx: N8nWorkerContext,
        pendingRepo: PendingProposedOpRepository,
    ): List<Candidate> {
        val pending = pendingRepo.getPending()
        val groups = pendingRepo.groupOps(pending)
        val blockedSavedIds = buildSet {
            pending.forEach { op ->
                op.targetItemId.takeIf(String::isNotBlank)?.let(::add)
                val sources = runCatching { JSONArray(op.mergeSourceItemIds) }.getOrNull()
                if (sources != null) for (i in 0 until sources.length()) sources.optString(i).takeIf(String::isNotBlank)?.let(::add)
            }
        }

        val confirmed = ctx.savedItemRepository.getAllActive()
            .filter { !it.isCompleted && !it.isArchived && it.savedItemId !in blockedSavedIds }
            .map { item -> Candidate(ref = item.savedItemId, item = item, subItems = emptyList()) }

        val proposed = groups.mapNotNull { group ->
            val draft = ctx.database.pendingReviewDraftDao().getByKey(group.key)
            val preview = pendingRepo.buildPreview(group, reviewWhenAtMs = draft?.whenAtMs) ?: return@mapNotNull null
            val participantIds = buildSet {
                group.targetItemId?.takeIf(String::isNotBlank)?.let(::add)
                group.ops.flatMap(::mergeSourceIdsOf).forEach(::add)
            }.toList()
            val persistedEvidence = if (participantIds.isEmpty()) emptyList() else {
                ctx.savedItemRepository.getLinkedRecordIdsFor(participantIds).values.flatten()
            }
            Candidate(
                ref = "review:${group.key}",
                item = preview.item,
                subItems = preview.subItems,
                group = group,
                evidenceRecordIds = (group.ops.flatMap(::evidenceOf) + persistedEvidence).toSet(),
            )
        }
        return confirmed + proposed
    }

    private fun compactCandidate(candidate: Candidate): Map<String, Any> =
        ExtractionStageSupport.itemCompact(candidate.item) + mapOf(
            "candidateRef" to candidate.ref,
            "candidateKind" to if (candidate.isPending) "pending" else "saved",
        )

    private suspend fun candidateDetail(ctx: N8nWorkerContext, candidate: Candidate): Map<String, Any> {
        val base = if (!candidate.isPending) {
            ExtractionStageSupport.itemDetail(ctx, candidate.item)
        } else {
            mapOf(
                "itemId" to candidate.item.savedItemId,
                "title" to candidate.item.title,
                "content" to candidate.item.content,
                "type" to if (candidate.item.isTask) "task" else "keep",
                "deadline" to ExtractionStageSupport.iso(candidate.item.deadlineAtMs),
                "startTime" to ExtractionStageSupport.iso(candidate.item.startAtMs),
                "endTime" to ExtractionStageSupport.iso(candidate.item.endAtMs),
                "when" to if (SavedItem.isSomeday(candidate.item.whenAtMs)) "someday" else ExtractionStageSupport.iso(candidate.item.whenAtMs),
                "userEdited" to candidate.item.userEdited,
                "isStarred" to candidate.item.isStarred,
                "isCompleted" to candidate.item.isCompleted,
                "buttons" to candidate.item.buttons,
                "sourceNotiRecordIds" to candidate.evidenceRecordIds.toList(),
                "sourceNotiKeys" to candidate.evidenceRecordIds.map { it.substringBeforeLast("_") }.distinct(),
                "subTasks" to candidate.subItems.map { sub ->
                    mapOf("subTaskId" to sub.savedSubItemId, "text" to sub.text, "isCompleted" to sub.isCompleted, "position" to sub.position)
                },
            )
        }
        return base + mapOf(
            "candidateRef" to candidate.ref,
            "candidateKind" to if (candidate.isPending) "pending" else "saved",
        )
    }

    /** Coalesces accidental overlaps into connected components, then applies strongest-first cap. */
    internal fun normalizeGroups(raw: JSONArray, validRefs: Set<String>, limit: Int): List<List<String>> {
        val groups = buildList {
            for (i in 0 until raw.length()) {
                val arr = raw.optJSONArray(i) ?: continue
                val refs = buildList {
                    for (j in 0 until arr.length()) arr.optString(j).takeIf { it in validRefs && it !in this }?.let(::add)
                }
                if (refs.size >= 2) add(refs)
            }
        }
        return normalizeGroups(groups, limit)
    }

    internal fun normalizeGroups(groups: List<List<String>>, limit: Int): List<List<String>> {
        val merged = mutableListOf<LinkedHashSet<String>>()
        for (group in groups) {
            val overlapIndexes = merged.indices.filter { index -> merged[index].any(group::contains) }
            if (overlapIndexes.isEmpty()) {
                merged += LinkedHashSet(group)
                continue
            }
            val component = linkedSetOf<String>().apply {
                overlapIndexes.forEach { addAll(merged[it]) }
                addAll(group)
            }
            val first = overlapIndexes.first()
            overlapIndexes.asReversed().forEach(merged::removeAt)
            merged.add(first, component)
        }
        return merged.take(limit.coerceAtLeast(0)).map(Set<String>::toList)
    }

    private fun validateE2Ops(ops: JSONArray, groupRefs: List<String>, candidates: Map<String, Candidate>): Boolean {
        val allowed = groupRefs.toSet()
        val used = mutableSetOf<String>()
        for (i in 0 until ops.length()) {
            val op = ops.optJSONObject(i) ?: return false
            val targetRef = op.optString("targetCandidateRef")
            val sources = op.optJSONArray("sourceCandidateRefs") ?: JSONArray()
            val refs = buildList {
                targetRef.takeIf(String::isNotBlank)?.let(::add)
                for (j in 0 until sources.length()) sources.optString(j).takeIf(String::isNotBlank)?.let(::add)
            }.distinct()
            if (refs.size < 2 || refs.any { it !in allowed } || refs.any { !used.add(it) }) return false
            val items = refs.map { candidates.getValue(it).item }
            if (items.map { it.itemType }.distinct().size != 1) return false
            if (op.optString("op") == "merge") {
                val target = candidates[targetRef] ?: return false
                if (target.group?.isCreate == true) return false
            } else if (op.optString("op") == "consolidate_create") {
                if (refs.any { candidates[it]?.group?.isCreate != true }) return false
            } else return false
        }
        return true
    }

    private fun evidenceOf(op: PendingProposedOp): List<String> = runCatching {
        val arr = JSONArray(op.evidenceRecordIds)
        buildList { for (i in 0 until arr.length()) arr.optString(i).takeIf(String::isNotBlank)?.let(::add) }
    }.getOrDefault(emptyList())

    private fun mergeSourceIdsOf(op: PendingProposedOp): List<String> = runCatching {
        val arr = JSONArray(op.mergeSourceItemIds)
        buildList { for (i in 0 until arr.length()) arr.optString(i).takeIf(String::isNotBlank)?.let(::add) }
    }.getOrDefault(emptyList())
}
