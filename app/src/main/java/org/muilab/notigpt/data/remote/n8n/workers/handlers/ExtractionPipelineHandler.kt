package org.muilab.notigpt.data.remote.n8n.workers.handlers

import android.util.Log
import androidx.work.ListenableWorker
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.muilab.notigpt.BuildConfig
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.data.remote.n8n.workers.N8nWorkerInput
import org.muilab.notigpt.data.remote.n8n.workers.handlers.ExtractionStageSupport.Http
import org.muilab.notigpt.data.repository.reminder.PendingOpRepository
import org.muilab.notigpt.model.features.PendingOp
import org.muilab.notigpt.model.features.PendingOpType
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.util.GeneratingNotiUtils
import org.muilab.notigpt.util.SharedPreferencesManager
import java.util.UUID

/**
 * Runs the per-notiKey extraction pipeline for one thread (contract v3).
 *
 * Stages, in order: A scan (skipped when forced) → B item extraction → C summary fold → D1 merge
 * shortlist → E1 merge resolution. Each stage is a separate n8n webhook; this handler chains them
 * in one worker job so intermediate state (the staged rows, the batch id) stays in memory.
 *
 * The pipeline never writes saved items directly — B/E1 land their ops in `pending_op` for review.
 * Only A and B gate the worker's retry: once ops are staged, the merge-refinement stages are
 * best-effort (a failure there must not re-run B and double-stage).
 */
internal object ExtractionPipelineHandler {

    private const val ITEM_EXTRACTION_COOLDOWN_MS = 5 * 60 * 1000L

    suspend fun handle(ctx: N8nWorkerContext, input: N8nWorkerInput.ExtractionPipeline): ListenableWorker.Result {
        val notiKey = input.notiKey
        val unit = ctx.getNotiUnit(notiKey) ?: run {
            Log.d(ExtractionStageSupport.TAG, "Extraction pipeline: no drawer unit for $notiKey")
            return ctx.success()
        }
        val recordDao = ctx.database.recordDao()
        val watermark = ctx.journalRepository.getWatermark(notiKey)
        val records = recordDao.getRecordsByKeyAfter(notiKey, watermark).sortedBy { it.time }

        if (records.isEmpty() && !input.forced) {
            // Nothing new since the last fold; still give a quiet thread a chance to compact.
            maybeCompact(ctx, notiKey, records, watermark)
            return ctx.success()
        }

        val priorSummary = ctx.database.extractionJournalDao().getSummary(notiKey)?.summaryText ?: ""

        // === Stage A — scan (auto runs only) ===
        var shouldExtract = input.forced
        if (!input.forced) {
            val payload = ExtractionStageSupport.baseEnvelope(ctx).apply {
                put("notiKey", notiKey)
                put("appName", unit.appName)
                put("priorSummary", priorSummary)
                put("category", ctx.database.notiLlmStateDao().getByKey(notiKey)?.categories ?: "[]")
                put("records", ExtractionStageSupport.formatRecords(records, unit.isPeople))
            }
            when (val res = ExtractionStageSupport.call(ctx, BuildConfig.N8N_EXTRACT_A_SCAN_PATH, payload)) {
                is Http.Retry -> return ctx.retry()
                is Http.Fail -> return ctx.success() // give up this pass without churning
                is Http.Ok -> {
                    val obj = ExtractionStageSupport.parseObject(res.body)
                    shouldExtract = obj != null && ExtractionStageSupport.optBoolLoose(obj, "shouldExtract")
                    val category = obj?.optString("category").orEmpty()
                    if (category.isNotBlank()) {
                        ctx.notiLlmStateRepository.applyClassification(
                            notiKey = notiKey,
                            categories = listOf(category),
                            reason = obj?.optString("reason").orEmpty(),
                            source = "llm",
                            recordCount = recordDao.getRecordCountByKey(notiKey),
                        )
                    }
                }
            }
            if (!shouldExtract) {
                maybeCompact(ctx, notiKey, records, watermark)
                return ctx.success()
            }
        }

        if (records.isEmpty()) return ctx.success() // forced with nothing to extract

        // Manual extraction intentionally bypasses this rate limit. Auto runs still execute A;
        // B is skipped during the cooldown, so a subsequent scheduled pass must receive a fresh
        // true verdict from A before it can make another costly item-extraction request.
        if (!input.forced) {
            val lastItemExtractionAt = ctx.database.notiLlmStateDao().getByKey(notiKey)?.lastItemExtractionAt ?: 0L
            if (System.currentTimeMillis() - lastItemExtractionAt < ITEM_EXTRACTION_COOLDOWN_MS) {
                Log.d(ExtractionStageSupport.TAG, "Skipping Stage B for $notiKey: cooldown active")
                return ctx.success()
            }
        }

        // Mark the thread as having extractable content pending (drives the banner + periodic net).
        ctx.database.notiLlmStateDao().setShouldExtractByKeys(listOf(notiKey), true, System.currentTimeMillis())

        // === Stage B — item extraction ===
        GeneratingNotiUtils.showGenerating(ctx.appContext)
        try {
            val pastCnt = SharedPreferencesManager.maxPastContext
            val pastContext = if (pastCnt > 0) {
                recordDao.getRecordsByKeyBeforeOrAt(notiKey, watermark, pastCnt).sortedBy { it.time }
            } else emptyList()

            val linkedItemIds = buildSet {
                addAll(ctx.database.notiSavedItemLinkDao().getSavedItemIdsByNotiKey(notiKey))
                addAll(ctx.journalRepository.getSavedItemIdsForKeys(listOf(notiKey)))
            }
            val linkedItems = linkedItemIds.mapNotNull { id -> ctx.reminderRepository.getById(id) }
                .map { ExtractionStageSupport.itemDetail(ctx, it) }

            val journalEntries = ctx.journalRepository.payloadFor(listOf(notiKey))[notiKey]
                ?.recentEntries?.map { e ->
                    mapOf(
                        "eventType" to e.eventType,
                        "itemTitle" to e.itemTitle,
                        "detail" to e.detail,
                    )
                } ?: emptyList()

            val bPayload = ExtractionStageSupport.baseEnvelope(ctx).apply {
                put("extractionPreferences", ctx.getExtractionPreferencesPayload())
                put("userContexts", ctx.getUserContextsPayload())
                put("notiKey", notiKey)
                put("appName", unit.appName)
                put("priorSummary", priorSummary)
                put("linkedItems", linkedItems)
                put("journalEntries", journalEntries)
                put("pastContext", ExtractionStageSupport.formatRecords(pastContext, unit.isPeople))
                put("records", ExtractionStageSupport.formatRecords(records, unit.isPeople))
                put("forced", input.forced)
            }

            val body = when (val res = ExtractionStageSupport.call(ctx, BuildConfig.N8N_EXTRACT_B_ITEMS_PATH, bPayload)) {
                is Http.Retry -> return ctx.retry()
                is Http.Fail -> return ctx.success()
                is Http.Ok -> res.body
            }
            // A completed B response starts the automatic cooldown, even when the call was a
            // manual override. Retryable failures remain eligible so WorkManager can recover them.
            ctx.database.notiLlmStateDao().markItemExtractionCompleted(notiKey, System.currentTimeMillis())
            val envelope = ExtractionStageSupport.parseObject(body)
            val ops = envelope?.optJSONArray("ops") ?: JSONArray()
            val reason = envelope?.optString("reason").orEmpty()

            val validRecordIds = buildSet {
                records.forEach { add(it.notiRecordId) }
                pastContext.forEach { add(it.notiRecordId) }
            }

            val batchId = UUID.randomUUID().toString()
            val staged = ctx.pendingOpRepository().stageExtractionOps(notiKey, batchId, ops, validRecordIds)

            // NonCancellable: once B has produced staged ops, don't let a REPLACE of this work slot
            // cut off the fold/merge follow-up partway.
            withContext(NonCancellable) {
                if (staged.isEmpty()) {
                    ctx.journalRepository.appendNoExtraction(notiKey, reason, System.currentTimeMillis())
                    maybeCompact(ctx, notiKey, records, watermark)
                    return@withContext
                }
                // We processed these records into ops → fold them into the summary now.
                runSummaryFold(ctx, notiKey, unit.isPeople, priorSummary, records)
                runMergeResolution(ctx, batchId, staged)
                ctx.database.notiLlmStateDao().setShouldExtractByKeys(listOf(notiKey), false, System.currentTimeMillis())
            }
            return ctx.success()
        } finally {
            GeneratingNotiUtils.cancelGenerating(ctx.appContext)
        }
    }

    /**
     * Compaction guard: fold the unprocessed records into the summary (advancing the watermark) when
     * the thread has gone quiet or accumulated too many unprocessed records. A negative setting
     * disables that guard. Runs C silently — no items, no review.
     */
    private suspend fun maybeCompact(
        ctx: N8nWorkerContext,
        notiKey: String,
        records: List<NotiRecord>,
        watermark: Long,
    ) {
        if (records.isEmpty()) return
        val quietMin = SharedPreferencesManager.extractionQuietWindowMinutes
        val maxUnprocessed = SharedPreferencesManager.extractionMaxUnprocessedRecords
        val now = System.currentTimeMillis()
        val newestPost = records.maxOf { it.postTime }
        val quietTrip = quietMin >= 0 && (now - newestPost) >= quietMin * 60_000L
        val countTrip = maxUnprocessed >= 0 && records.size >= maxUnprocessed
        if (!quietTrip && !countTrip) return
        val unit = ctx.getNotiUnit(notiKey) ?: return
        val priorSummary = ctx.database.extractionJournalDao().getSummary(notiKey)?.summaryText ?: ""
        runSummaryFold(ctx, notiKey, unit.isPeople, priorSummary, records)
        // Folded away with no items — nothing left pending for this thread.
        ctx.database.notiLlmStateDao().setShouldExtractByKeys(listOf(notiKey), false, System.currentTimeMillis())
    }

    /** Stage C — fold the given records into the rolling summary and advance the watermark. */
    private suspend fun runSummaryFold(
        ctx: N8nWorkerContext,
        notiKey: String,
        isPeople: Boolean,
        priorSummary: String,
        records: List<NotiRecord>,
    ) {
        if (records.isEmpty()) return
        val newWatermark = records.maxOf { it.postTime }
        val unit = ctx.getNotiUnit(notiKey)
        val payload = ExtractionStageSupport.baseEnvelope(ctx).apply {
            put("notiKey", notiKey)
            put("appName", unit?.appName ?: "")
            put("priorSummary", priorSummary)
            put("records", ExtractionStageSupport.formatRecords(records, isPeople))
        }
        val summary = when (val res = ExtractionStageSupport.call(ctx, BuildConfig.N8N_EXTRACT_C_SUMMARY_PATH, payload)) {
            is Http.Ok -> ExtractionStageSupport.parseObject(res.body)?.optString("summary").orEmpty()
            else -> "" // fold still advances the watermark to bound reprocessing, keeping the prior text
        }
        ctx.journalRepository.foldRecordsIntoSummary(notiKey, summary, newWatermark)
    }

    /**
     * Stages D1 + E1: shortlist existing items each staged CREATE might duplicate, then resolve the
     * shortlisted pairs into merge ops. A resolved merge consumes the staged create (one reviewable
     * decision). Best-effort — a stage failure just leaves the creates standing.
     */
    private suspend fun runMergeResolution(ctx: N8nWorkerContext, batchId: String, staged: List<PendingOp>) {
        val creates = staged.filter { it.opType == PendingOpType.Create }
        if (creates.isEmpty()) return
        val pendingOpRepo = ctx.pendingOpRepository()

        val activeItems = ExtractionStageSupport.activeCompactList(ctx, pendingOpRepo)
        if (activeItems.isEmpty()) return

        val createsByRef = LinkedHashMap<String, PendingOp>()
        val newItems = mutableListOf<Map<String, Any>>()
        for (create in creates) {
            val ref = "op_${create.opId}"
            val group = PendingOpRepository.OpGroup(key = "create_${create.opId}", ops = listOf(create), targetItemId = null)
            val preview = pendingOpRepo.buildPreview(group) ?: continue
            createsByRef[ref] = create
            newItems += ExtractionStageSupport.previewDetail(preview, ref)
        }
        if (newItems.isEmpty()) return

        // D1 — shortlist.
        val d1Payload = ExtractionStageSupport.baseEnvelope(ctx).apply {
            put("newItems", newItems)
            put("activeItems", activeItems)
        }
        val d1Body = (ExtractionStageSupport.call(ctx, BuildConfig.N8N_EXTRACT_D1_SHORTLIST_PATH, d1Payload) as? Http.Ok)?.body ?: return
        val candidates = ExtractionStageSupport.parseObject(d1Body)?.optJSONArray("candidates") ?: return
        if (candidates.length() == 0) return

        // Build E1 pairs from the shortlist: each new op ref + the full detail of its existing candidates.
        val pairs = mutableListOf<Map<String, Any>>()
        for (i in 0 until candidates.length()) {
            val c = candidates.optJSONObject(i) ?: continue
            val ref = c.optString("newOpRef")
            val create = createsByRef[ref] ?: continue
            val group = PendingOpRepository.OpGroup(key = "create_${create.opId}", ops = listOf(create), targetItemId = null)
            val preview = pendingOpRepo.buildPreview(group) ?: continue
            val existingIdsArr = c.optJSONArray("existingItemIds") ?: JSONArray()
            val existing = buildList {
                for (j in 0 until existingIdsArr.length()) {
                    val id = existingIdsArr.optString(j)
                    val item = ctx.reminderRepository.getById(id) ?: continue
                    add(ExtractionStageSupport.itemDetail(ctx, item))
                }
            }
            if (existing.isEmpty()) continue
            pairs += mapOf(
                "newOp" to ExtractionStageSupport.previewDetail(preview, ref),
                "existingItems" to existing,
            )
        }
        if (pairs.isEmpty()) return

        // E1 — resolve.
        val e1Payload = ExtractionStageSupport.baseEnvelope(ctx).apply {
            put("extractionPreferences", ctx.getExtractionPreferencesPayload())
            put("userContexts", ctx.getUserContextsPayload())
            put("pairs", pairs)
        }
        val e1Body = (ExtractionStageSupport.call(ctx, BuildConfig.N8N_EXTRACT_E1_MERGE_PATH, e1Payload) as? Http.Ok)?.body ?: return
        val mergeOps = ExtractionStageSupport.parseObject(e1Body)?.optJSONArray("ops") ?: return
        if (mergeOps.length() == 0) return
        ctx.pendingOpRepository().stageMergeOps(batchId, mergeOps, createsByRef)
    }
}
