package org.muilab.notigpt.data.remote.n8n.workers.handlers

import org.json.JSONObject
import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.data.repository.suggestion.SuggestedItem
import org.muilab.notigpt.data.repository.suggestion.SuggestionConstants
import org.muilab.notigpt.data.repository.suggestion.SuggestionRefreshError
import org.muilab.notigpt.data.repository.suggestion.SuggestionSnapshotStore
import org.muilab.notigpt.model.features.SavedItem

/** Runs the local-only Suggested pipeline: cheap high-recall G shortlist, then rich-context H. */
internal object SuggestionRefreshHandler {

    suspend fun handle(ctx: N8nWorkerContext): androidx.work.ListenableWorker.Result {
        val store = SuggestionSnapshotStore.getInstance(ctx.appContext)
        store.beginRefresh()
        return try {
            val active = ctx.savedItemRepository.getAllActive()
                .filter { !it.isCompleted && !it.isArchived }
            if (active.isEmpty()) {
                store.replace(emptyList())
                return ctx.success()
            }

            val candidateIds = if (active.size <= SuggestionConstants.G_SKIP_AT_OR_BELOW_ITEM_COUNT) {
                active.map { it.savedItemId }
            } else {
                shortlist(ctx, active) ?: return retryOrFinish(ctx, store, lastHttp)
            }
            if (candidateIds.isEmpty()) {
                store.replace(emptyList())
                return ctx.success()
            }

            val candidates = candidateIds.mapNotNull { id -> active.firstOrNull { it.savedItemId == id } }
            decide(ctx, candidates)?.let { suggestions ->
                store.replace(suggestions)
                ctx.success()
            } ?: retryOrFinish(ctx, store, lastHttp)
        } catch (_: Throwable) {
            store.fail(SuggestionRefreshError.Unknown)
            ctx.failure()
        }
    }

    private var lastHttp: ExtractionStageSupport.Http = ExtractionStageSupport.Http.Fail

    private suspend fun shortlist(ctx: N8nWorkerContext, active: List<SavedItem>): List<String>? {
        val payload = ExtractionStageSupport.baseEnvelope(ctx).apply {
            put("items", active.map(ExtractionStageSupport::itemCompact))
            put("extractionPreferences", ctx.getExtractionPreferencesPayload())
            put("userContexts", ctx.getUserContextsPayload())
            put("maxCandidates", SuggestionConstants.G_MAX_CANDIDATES)
        }
        lastHttp = ExtractionStageSupport.call(ctx, BuildConfig.N8N_SUGGEST_G_SHORTLIST_PATH, payload)
        val body = (lastHttp as? ExtractionStageSupport.Http.Ok)?.body ?: return null
        val root = ExtractionStageSupport.parseObject(body) ?: return null
        val validIds = active.mapTo(hashSetOf()) { it.savedItemId }
        return root.optJSONArray("candidateIds")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it in validIds && it !in this }?.let(::add)
                    if (size == SuggestionConstants.G_MAX_CANDIDATES) break
                }
            }
        }
    }

    private suspend fun decide(ctx: N8nWorkerContext, candidates: List<SavedItem>): List<SuggestedItem>? {
        val payload = ExtractionStageSupport.baseEnvelope(ctx).apply {
            put("candidates", candidates.map { suggestionDetail(ctx, it) })
            put("extractionPreferences", ctx.getExtractionPreferencesPayload())
            put("userContexts", ctx.getUserContextsPayload())
            put("maxSuggestions", SuggestionConstants.H_MAX_SUGGESTIONS)
        }
        lastHttp = ExtractionStageSupport.call(ctx, BuildConfig.N8N_SUGGEST_H_DECIDE_PATH, payload)
        val body = (lastHttp as? ExtractionStageSupport.Http.Ok)?.body ?: return null
        val root = ExtractionStageSupport.parseObject(body) ?: return null
        val array = root.optJSONArray("suggestions") ?: return null
        val validIds = candidates.mapTo(hashSetOf()) { it.savedItemId }
        return buildList {
            for (index in 0 until array.length()) {
                val obj: JSONObject = array.optJSONObject(index) ?: continue
                val id = obj.optString("savedItemId")
                val reason = obj.optString("reason").trim()
                if (id in validIds && reason.isNotBlank() && none { it.savedItemId == id }) {
                    add(SuggestedItem(id, reason))
                }
                if (size == SuggestionConstants.H_MAX_SUGGESTIONS) break
            }
        }
    }

    /** Rich item context intentionally excludes notification sources, history and inactive state. */
    private suspend fun suggestionDetail(ctx: N8nWorkerContext, item: SavedItem): Map<String, Any> {
        val steps = runCatching { ctx.todoStepRepository.getBySavedItemId(item.savedItemId) }
            .getOrDefault(emptyList())
            .map { step ->
                mapOf(
                    "todoStepId" to step.todoStepId,
                    "text" to step.text,
                    "isCompleted" to step.isCompleted,
                    "position" to step.position,
                )
            }
        return mapOf(
            "savedItemId" to item.savedItemId,
            "title" to item.title,
            "content" to item.content,
            "type" to if (item.isTodo) "todo" else "keep",
            "deadline" to ExtractionStageSupport.iso(item.deadlineAtMs),
            "isStarred" to item.isStarred,
            "userEdited" to item.userEdited,
            "lastUpdatedAt" to ExtractionStageSupport.iso(item.lastUpdateTimestamp),
            "buttons" to item.buttons,
            "steps" to steps,
        )
    }

    private fun retryOrFinish(
        ctx: N8nWorkerContext,
        store: SuggestionSnapshotStore,
        http: ExtractionStageSupport.Http,
    ): androidx.work.ListenableWorker.Result = when (http) {
        ExtractionStageSupport.Http.Retry -> {
            store.fail(SuggestionRefreshError.Network)
            ctx.retry()
        }
        else -> {
            store.fail(SuggestionRefreshError.InvalidResponse)
            ctx.failure()
        }
    }
}
