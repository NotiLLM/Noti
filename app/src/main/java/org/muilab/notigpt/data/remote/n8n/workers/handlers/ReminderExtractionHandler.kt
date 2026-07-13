package org.muilab.notigpt.data.remote.n8n.workers.handlers

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.data.remote.n8n.ExtractionStatusStore
import org.muilab.notigpt.data.remote.n8n.enqueueDelayedTaskExtraction
import org.muilab.notigpt.data.remote.n8n.formatter.N8nRecordFormatter
import org.muilab.notigpt.data.remote.n8n.formatter.N8nTimeFormatter
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.data.repository.reminder.ExtractionJournalRepository
import org.muilab.notigpt.model.features.ExtractionJournalEntry
import org.muilab.notigpt.model.features.ExtractionJournalEventType
import org.muilab.notigpt.model.features.NotiSavedItemLinkSource
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemChangeType
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.domain.reminder.ReminderAssociationMerger
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.util.GeneratingNotiUtils
import org.muilab.notigpt.util.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Worker handler for reminder extraction over selected notification records (contract v2).
 *
 * The request carries, per notiUnit, the records to extract from plus that unit's extraction
 * journal (what was already generated from the thread and what the user did with it), the fully
 * detailed reminders already associated with those units, and a slim title list of everything
 * else. The response is an ops envelope: `create` ops insert new items; `update` ops carry only
 * the CHANGES (append fragment, added/removed sub-tasks, old→new field updates) — existing content
 * is never rewritten, because the user has already read it.
 *
 * Every op must cite `evidenceRecordIds`; ids not present in the request are discarded, and an op
 * with no surviving evidence is dropped entirely.
 */
internal object ReminderExtractionHandler {

    private const val TAG = "N8nWebhook"

    /** Per-request caps: a recovery backlog drains in chunks instead of one giant request. */
    private const val MAX_RECORDS_PER_REQUEST = 40
    private const val MAX_UNITS_PER_REQUEST = 10

    /** Delay before the follow-up drain request when a capped batch left records behind. */
    private const val DRAIN_FOLLOWUP_DELAY_SECONDS = 5L

    /** Idle-thread fold requests piggybacked per periodic request, to bound payload growth. */
    private const val MAX_IDLE_FOLDS_PER_REQUEST = 3

    /** Batch size above which the instance health endpoint is pinged before claiming. */
    private const val HEALTH_CHECK_BATCH_THRESHOLD = 15

    suspend fun handle(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        val gson = Gson()

        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e(TAG, "No webhook_path for extract")
            return ctx.failure()
        }

        val userTriggered = inputData.getBoolean("user_triggered", false)

        val visibleIdsJson = inputData.getString("visible_record_ids_json")
        val visibleRecordIds: List<String> = if (!visibleIdsJson.isNullOrBlank()) {
            try {
                gson.fromJson(visibleIdsJson, Array<String>::class.java).toList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val keysJson = inputData.getString("noti_keys_json") ?: "[]"
        val notiKeys: List<String> = try {
            gson.fromJson(keysJson, Array<String>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }

        return if (userTriggered) {
            handleUserTriggered(ctx, gson, webhookPath, notiKeys, visibleRecordIds)
        } else {
            handlePeriodic(ctx, gson, webhookPath, notiKeys)
        }
    }

    // === User-triggered (single notification) ===
    // UI passes exactly one notiKey. For this path we IGNORE extracted/claimed flags and
    // send visible records + a few older records for context, but we still apply returned ops.
    private suspend fun handleUserTriggered(
        ctx: N8nWorkerContext,
        gson: Gson,
        webhookPath: String,
        notiKeys: List<String>,
        visibleRecordIds: List<String>,
    ): ListenableWorker.Result {
        val db = ctx.database
        val notiKey = notiKeys.firstOrNull()
        if (notiKey.isNullOrBlank()) {
            Log.d(TAG, "User-triggered extraction: missing notiKey")
            return ctx.success()
        }

        val unit = ctx.getNotiUnit(notiKey) ?: return ctx.success()
        val recordDao = db.recordDao()

        // IMPORTANT: when user triggers extraction from a context card, use EXACTLY the records
        // that were visible in the UI (searched/loaded/expanded), even if the notification is dismissed.
        val visible: List<NotiRecord> = if (visibleRecordIds.isNotEmpty()) {
            recordDao.getRecordsByIds(visibleRecordIds).sortedBy { it.time }
        } else {
            // Backward compatible fallback: previous behavior
            recordDao.getActiveRecordsByKey(notiKey).sortedBy { it.time }
        }

        val pastCnt = SharedPreferencesManager.maxPastContext
        // "older" is only for the fallback path; in the explicit recordIds mode, UI already decided context.
        val older = if (visibleRecordIds.isEmpty() && pastCnt > 0) {
            recordDao.getRecordsByKey(notiKey).sortedByDescending { it.time }.take(pastCnt)
        } else {
            emptyList()
        }

        val combined = (visible + older)
            .distinctBy { it.notiRecordId }
            .sortedBy { it.time }

        if (combined.isEmpty()) {
            Log.d(TAG, "User-triggered extraction: no records found for key=$notiKey")
            return ctx.success()
        }

        val journalPayloads = ctx.journalRepository.payloadFor(listOf(notiKey))
        val foldRequests = ctx.journalRepository.buildFoldRequests(listOf(notiKey))

        // In explicit mode, only send what the user saw; the UI already chose context, so pastContext stays empty.
        val notisPayload = listOf(
            buildNotiEntry(
                ctx = ctx,
                notiKey = notiKey,
                records = combined,
                pastRecords = emptyList(),
                journal = journalPayloads[notiKey],
            ) ?: return ctx.success()
        )

        val payload = buildExtractionPayload(
            ctx = ctx,
            gson = gson,
            userTriggered = true,
            notisPayload = notisPayload,
            requestKeys = listOf(notiKey),
            foldRequests = foldRequests,
        )

        val jsonPayload = gson.toJson(payload)
        Log.d(TAG, "JSON Payload (user-triggered): $jsonPayload")

        val response = try {
            ctx.n8nApiService.postToWebhook(webhookPath, jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
        } catch (t: Throwable) {
            Log.e(TAG, "TaskExtraction network exception", t)
            ExtractionStatusStore.recordFailure(ExtractionStatusStore.FailureKind.Network, userTriggered = true)
            return ctx.retry()
        }

        if (!response.isSuccessful) {
            ExtractionStatusStore.recordFailure(ExtractionStatusStore.FailureKind.Server, userTriggered = true)
            return when {
                response.code() == 429 -> ctx.retry()
                response.code() in 500..599 -> ctx.retry()
                else -> ctx.failure()
            }
        }
        ExtractionStatusStore.recordSuccess()

        val bodyStr = response.body()?.string() ?: return ctx.success()
        Log.d(TAG, "Extraction Response (user-triggered): $bodyStr")

        return try {
            // NonCancellable: a response is already in hand at this point. A concurrent REPLACE of
            // this unique work slot (e.g. another extraction trigger firing mid-write) must not cut
            // off persistence partway through the ops list, or later ops in the same response are
            // silently dropped even though the network call already succeeded.
            withContext(NonCancellable) {
                journalRecordsSent(ctx, mapOf(notiKey to combined))
                val validRequestIds = combined.map { it.notiRecordId }.toSet()
                applyResponse(
                    ctx = ctx,
                    bodyStr = bodyStr,
                    validRequestIds = validRequestIds,
                    linkSource = NotiSavedItemLinkSource.LlmManualExtraction,
                    origin = "llm_manual_extraction",
                    foldRequests = foldRequests,
                    requestKeys = listOf(notiKey),
                )
                ctx.database.notiLlmStateDao().setShouldExtractByKeys(listOf(notiKey), false, System.currentTimeMillis())
            }
            ctx.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing extract response (user-triggered)", e)
            ctx.failure()
        }
    }

    // === Periodic flow ===
    private suspend fun handlePeriodic(
        ctx: N8nWorkerContext,
        gson: Gson,
        webhookPath: String,
        notiKeys: List<String>,
    ): ListenableWorker.Result {
        val db = ctx.database

        val keysWithWork: List<String> = notiKeys.ifEmpty {
            db.notiLlmStateDao().getActiveShouldExtractKeys()
        }

        val nowTs = System.currentTimeMillis()
        val staleMs = 5 * 60 * 1000L
        db.recordDao().reclaimStaleClaims(nowTs, staleMs)

        // Cap the batch (whole units first, then a record ceiling) so a post-outage backlog
        // drains in bounded chunks; leftovers are re-enqueued after a successful response.
        val unclaimedByKey: Map<String, List<String>> = keysWithWork.associateWith { key ->
            db.recordDao().getUnclaimedUnextractedByKey(key).map { it.notiRecordId }
        }.filterValues { it.isNotEmpty() }

        val keysToProcess = mutableListOf<String>()
        val candidateRecordIds = mutableListOf<String>()
        for ((key, ids) in unclaimedByKey) {
            if (keysToProcess.size >= MAX_UNITS_PER_REQUEST) break
            if (candidateRecordIds.isNotEmpty() && candidateRecordIds.size + ids.size > MAX_RECORDS_PER_REQUEST) continue
            keysToProcess += key
            candidateRecordIds += ids.take(MAX_RECORDS_PER_REQUEST)
        }
        val hasBacklog = unclaimedByKey.keys.size > keysToProcess.size ||
            unclaimedByKey.values.sumOf { it.size } > candidateRecordIds.size

        if (candidateRecordIds.isEmpty()) {
            Log.d(TAG, "No candidate records to extract")
            return ctx.success()
        }

        // Cheap-fail before claiming a large batch: if the server is down, leave the records
        // unclaimed and let backoff handle the retry instead of churning the claim window.
        if (candidateRecordIds.size > HEALTH_CHECK_BATCH_THRESHOLD) {
            val healthy = try {
                ctx.n8nApiService.healthCheck().isSuccessful
            } catch (_: Throwable) {
                false
            }
            if (!healthy) {
                Log.w(TAG, "Health check failed before large batch (${candidateRecordIds.size} records); retrying later")
                ExtractionStatusStore.recordFailure(ExtractionStatusStore.FailureKind.Server)
                return ctx.retry()
            }
        }

        val claimTs = System.currentTimeMillis()
        val claimedCount = db.recordDao().claimRecordsForExtractionWithTs(candidateRecordIds, claimTs)

        Log.d(
            TAG,
            "Attempted to claim ${candidateRecordIds.size} records, actually claimed=$claimedCount (ts=$claimTs, backlog=$hasBacklog)"
        )

        if (claimedCount <= 0) {
            Log.d(TAG, "No records could be claimed; another worker likely claimed them")
            return ctx.success()
        }

        val claimedRecords = db.recordDao().getClaimedRecordsByIds(candidateRecordIds).sortedBy { it.time }
        if (claimedRecords.isEmpty()) {
            Log.d(TAG, "No claimed records returned after claim; aborting")
            return ctx.success()
        }

        val claimedByKey: Map<String, List<NotiRecord>> = claimedRecords.groupBy { it.notiKey }

        // Background generation is now underway — show an ongoing notification and always retract it
        // (success, empty, or failure) via the finally below.
        GeneratingNotiUtils.showGenerating(ctx.appContext)
        try {
            // Deterministic thread order keeps identical retried payloads byte-stable for the
            // provider-side prompt cache.
            val requestKeys = claimedByKey.keys.sorted()
            val journalPayloads = ctx.journalRepository.payloadFor(requestKeys)
            val foldRequests = (
                ctx.journalRepository.buildFoldRequests(requestKeys) +
                    buildIdleFoldRequests(ctx, excludeKeys = requestKeys)
                ).distinctBy { it.notiKey }

            val pastByKey = mutableMapOf<String, List<NotiRecord>>()
            val notisPayload = requestKeys.mapNotNull { key ->
                val records = claimedByKey.getValue(key)
                val pastCnt = SharedPreferencesManager.maxPastContext
                val pastRecs = if (pastCnt > 0) {
                    db.recordDao().getLastExtractedRecordsByKey(key, pastCnt).sortedBy { it.time }
                } else emptyList()
                pastByKey[key] = pastRecs
                buildNotiEntry(ctx, key, records.sortedBy { it.time }, pastRecs, journalPayloads[key])
            }

            if (notisPayload.isEmpty()) {
                db.recordDao().clearClaimedRecords(candidateRecordIds)
                return ctx.success()
            }

            val payload = buildExtractionPayload(
                ctx = ctx,
                gson = gson,
                userTriggered = false,
                notisPayload = notisPayload,
                requestKeys = requestKeys,
                foldRequests = foldRequests,
            )

            val jsonPayload = gson.toJson(payload)
            Log.d(TAG, "JSON Payload (claimed): $jsonPayload")

            val response = try {
                ctx.n8nApiService.postToWebhook(webhookPath, jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            } catch (t: Throwable) {
                Log.e(TAG, "TaskExtraction network exception", t)
                db.recordDao().clearClaimedRecords(candidateRecordIds)
                ExtractionStatusStore.recordFailure(ExtractionStatusStore.FailureKind.Network)
                return ctx.retry()
            }

            if (!response.isSuccessful) {
                db.recordDao().clearClaimedRecords(candidateRecordIds)
                ExtractionStatusStore.recordFailure(ExtractionStatusStore.FailureKind.Server)
                return when {
                    response.code() == 429 -> ctx.retry()
                    response.code() in 500..599 -> ctx.retry()
                    else -> ctx.failure()
                }
            }
            ExtractionStatusStore.recordSuccess()

            val bodyStr = response.body()?.string() ?: return ctx.success()
            Log.d(TAG, "Extraction Response: $bodyStr")

            try {
                // NonCancellable: a response is already in hand at this point. A concurrent REPLACE of
                // this unique work slot (e.g. another extraction trigger firing mid-write) must not cut
                // off persistence partway through the ops list, or later ops in the same response are
                // silently dropped even though the network call already succeeded.
                withContext(NonCancellable) {
                    journalRecordsSent(ctx, claimedByKey)
                    // Evidence may cite the extracted records or the past-context records sent with them.
                    val validRequestIds = buildSet {
                        claimedRecords.forEach { add(it.notiRecordId) }
                        pastByKey.values.flatten().forEach { add(it.notiRecordId) }
                    }
                    applyResponse(
                        ctx = ctx,
                        bodyStr = bodyStr,
                        validRequestIds = validRequestIds,
                        linkSource = NotiSavedItemLinkSource.LlmAutoExtraction,
                        origin = "llm_auto_extraction",
                        foldRequests = foldRequests,
                        requestKeys = requestKeys,
                    )
                    db.notiLlmStateDao().setShouldExtractByKeys(keysToProcess, false, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing extract response", e)
                db.recordDao().clearClaimedRecords(candidateRecordIds)
            }

            if (hasBacklog) {
                Log.d(TAG, "Backlog remains after capped batch; scheduling follow-up drain")
                enqueueDelayedTaskExtraction(ctx.appContext, DRAIN_FOLLOWUP_DELAY_SECONDS)
            }

            return ctx.success()
        } finally {
            GeneratingNotiUtils.cancelGenerating(ctx.appContext)
        }
    }

    // === Payload building ===

    /** One entry of the `notis` array: thread metadata, formatted records, and the journal block. */
    private fun buildNotiEntry(
        ctx: N8nWorkerContext,
        notiKey: String,
        records: List<NotiRecord>,
        pastRecords: List<NotiRecord>,
        journal: ExtractionJournalRepository.JournalPayload?,
    ): Map<String, Any>? {
        val unit = ctx.getNotiUnit(notiKey) ?: return null
        val llmState = ctx.database.notiLlmStateDao().getByKey(notiKey)
        val contents = records.map { r -> N8nRecordFormatter.format(r, unit.isPeople) }
        val pastCtx = pastRecords.map { N8nRecordFormatter.format(it, unit.isPeople) }

        val lastRecord = records.lastOrNull()
        val lastTitle = lastRecord?.title ?: ""
        val overallTitle = if (lastRecord != null) {
            when {
                lastRecord.extraConversationTitle != "null" -> lastRecord.extraConversationTitle
                lastTitle != "null" -> lastTitle
                lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                else -> ""
            }
        } else ""
        val secondOverallTitle = if (lastRecord != null) {
            when {
                lastRecord.extraConversationTitle != "null" && lastTitle != "null" -> lastTitle
                lastRecord.extraConversationTitle == "null" && lastTitle != "null" && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                lastRecord.extraConversationTitle == "null" && lastTitle != "null" -> ""
                else -> ""
            }
        } else ""

        return mapOf(
            "notiKey" to notiKey,
            "appName" to unit.appName,
            "overallTitle" to overallTitle,
            "secondOverallTitle" to secondOverallTitle,
            "notiContent" to contents,
            "pastContext" to pastCtx,
            "hasTask" to (llmState?.hasTask ?: false),
            "hasMemo" to (llmState?.hasMemo ?: false),
            "hasEvent" to (llmState?.hasEvent ?: false),
            "journal" to mapOf(
                "summary" to (journal?.summaryText ?: ""),
                "recentEntries" to (journal?.recentEntries ?: emptyList()).map { e ->
                    mapOf(
                        "at" to N8nTimeFormatter.isoTime(e.createdAt),
                        "eventType" to e.eventType,
                        "origin" to ExtractionJournalRepository.originOf(e.eventType),
                        "itemTitle" to e.itemTitle,
                        "detail" to e.detail,
                    )
                },
            ),
        )
    }

    /**
     * Assembles the contract-v2 payload: full detail only for reminders already associated with
     * the request's notiUnits (via links or journal); everything else as a slim title list for the
     * shortlist pass. This is the token-control core: the LLM never sees the full reminder list.
     */
    private suspend fun buildExtractionPayload(
        ctx: N8nWorkerContext,
        gson: Gson,
        userTriggered: Boolean,
        notisPayload: List<Map<String, Any>>,
        requestKeys: List<String>,
        foldRequests: List<ExtractionJournalRepository.FoldRequest>,
    ): Map<String, Any> {
        val reminderRepository = ctx.reminderRepository

        val currentReminders: List<SavedItem> = try {
            reminderRepository.observeAll().first()
        } catch (_: Exception) {
            emptyList()
        }

        val associatedIds: Set<String> = buildSet {
            addAll(ctx.database.notiSavedItemLinkDao().getSavedItemIdsByNotiKeys(requestKeys))
            addAll(ctx.journalRepository.getSavedItemIdsForKeys(requestKeys))
        }
        val (associated, others) = currentReminders.partition { it.savedItemId in associatedIds }

        val linkedByItem = reminderRepository.getLinkedRecordIdsFor(associated.map { it.savedItemId })

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        fun iso(ms: Long): Any = if (ms > 0L) sdf.format(Date(ms)) else -1L

        val associatedPayload = associated.map { r ->
            val subTasks = try {
                ctx.database.subTaskDao().getByReminderId(r.savedItemId).map { st ->
                    mapOf(
                        "subTaskId" to st.savedSubItemId,
                        "title" to st.title,
                        "description" to st.description,
                        "isTask" to (st.itemType == SavedItemType.Task),
                        "isCompleted" to st.isCompleted,
                        "deadlineTimeString" to iso(st.deadlineAtMs),
                        "sortOrder" to st.sortOrder,
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
            mapOf(
                "savedItemId" to r.savedItemId,
                "reminderTitle" to r.title,
                "reminderContent" to r.content,
                "isTask" to r.isTask,
                "isEvent" to r.isEvent,
                "deadlineTimeString" to iso(r.deadlineAtMs),
                "startTimeString" to iso(r.startAtMs),
                "endTimeString" to iso(r.endAtMs),
                "estimatedCompletionMinutes" to r.estimatedCompletionTime,
                "sourceNotiRecordIds" to (linkedByItem[r.savedItemId] ?: emptyList()),
                "userEdited" to r.userEdited,
                "isCompleted" to r.isCompleted,
                // doDate is user context for the model. The "someday" sentinel (Long.MAX_VALUE) is not
                // a real date, so surface it as a self-describing marker instead of a year-292M ISO string.
                "doDate" to if (SavedItem.isSomeday(r.doAtMs)) "someday" else iso(r.doAtMs),
                "buttons" to r.buttons,
                "sortScore" to r.sortScore,
                "subTasks" to subTasks,
            )
        }

        val othersPayload = others.map { r ->
            mapOf(
                "savedItemId" to r.savedItemId,
                "title" to r.title,
                "oneLineSummary" to r.content.replace('\n', ' ').trim().take(120),
                "deadlineTimeString" to iso(r.deadlineAtMs),
                "state" to r.state,
            )
        }

        val foldPayload = foldRequests.map { fr ->
            mapOf(
                "notiKey" to fr.notiKey,
                "currentSummary" to fr.currentSummary,
                "entriesToFold" to fr.entriesToFold.map { e ->
                    mapOf(
                        "at" to N8nTimeFormatter.isoTime(e.createdAt),
                        "eventType" to e.eventType,
                        "origin" to ExtractionJournalRepository.originOf(e.eventType),
                        "itemTitle" to e.itemTitle,
                        "detail" to e.detail,
                    )
                },
            )
        }

        return mapOf(
            "userId" to SharedPreferencesManager.userId,
            "language" to Locale.getDefault().toLanguageTag(),
            "timezone" to TimeZone.getDefault().displayName,
            "currentTime" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date()),
            "targetExtractionLanguage" to ctx.getTargetExtractionLanguage(),
            "userTriggered" to userTriggered,
            "contractVersion" to 2,
            "notis" to notisPayload,
            "associatedReminders" to associatedPayload,
            "otherReminders" to othersPayload,
            "journalFoldRequests" to foldPayload,
            "extractionPreferences" to ctx.getExtractionPreferencesPayload(),
            "userContexts" to ctx.getUserContextsPayload(),
        )
    }

    /**
     * Fold requests for idle threads, piggybacked on this request so dormant journals get
     * condensed without a dedicated call. Threads that never produced an item are truncated
     * locally instead — nothing there is worth an LLM summary.
     */
    private suspend fun buildIdleFoldRequests(
        ctx: N8nWorkerContext,
        excludeKeys: List<String>,
    ): List<ExtractionJournalRepository.FoldRequest> {
        val idleDays = SharedPreferencesManager.journalIdleFoldDays
        if (idleDays <= 0) return emptyList()
        val cutoff = System.currentTimeMillis() - idleDays * 24L * 60 * 60 * 1000
        val exclude = excludeKeys.toSet()

        val idleKeys = mutableListOf<String>()
        for (key in ctx.journalRepository.getKeysWithEntries()) {
            if (key in exclude) continue
            val lastRecordAt = ctx.database.recordDao().getLastRecordTimeByKey(key) ?: 0L
            if (lastRecordAt >= cutoff) continue
            val hasItems = ctx.database.notiSavedItemLinkDao().getSavedItemIdsByNotiKeys(listOf(key)).isNotEmpty() ||
                ctx.journalRepository.getSavedItemIdsForKeys(listOf(key)).isNotEmpty()
            if (hasItems) {
                idleKeys += key
                if (idleKeys.size >= MAX_IDLE_FOLDS_PER_REQUEST) break
            } else {
                ctx.journalRepository.truncateOrphanJournal(key)
            }
        }
        return ctx.journalRepository.buildIdleFoldRequests(idleKeys)
    }

    /** Journals one records_sent entry per notiUnit whose records were successfully extracted. */
    private suspend fun journalRecordsSent(ctx: N8nWorkerContext, recordsByKey: Map<String, List<NotiRecord>>) {
        val now = System.currentTimeMillis()
        recordsByKey.forEach { (key, records) ->
            ctx.journalRepository.append(
                ExtractionJournalEntry(
                    notiKey = key,
                    createdAt = now,
                    eventType = ExtractionJournalEventType.RecordsSent,
                    detail = "${records.size} records",
                )
            )
        }
    }

    // === Response application ===

    /**
     * Parses the v2 envelope `{ops, updatedJournalSummaries, threadVerdicts}` (tolerating a bare
     * ops array) and applies each op behind the evidence gate.
     */
    private suspend fun applyResponse(
        ctx: N8nWorkerContext,
        bodyStr: String,
        validRequestIds: Set<String>,
        linkSource: String,
        origin: String,
        foldRequests: List<ExtractionJournalRepository.FoldRequest>,
        requestKeys: List<String>,
    ) {
        val trimmed = bodyStr.trim()
        val ops: JSONArray
        var summaries: JSONArray? = null
        var verdicts: JSONArray? = null
        if (trimmed.startsWith("{")) {
            val envelope = JSONObject(trimmed)
            ops = envelope.optJSONArray("ops") ?: JSONArray()
            summaries = envelope.optJSONArray("updatedJournalSummaries")
            verdicts = envelope.optJSONArray("threadVerdicts")
        } else {
            ops = JSONArray(trimmed)
        }

        if (ops.length() == 0) {
            Log.d(TAG, "Extraction: no ops returned")
        }

        val opTouchedKeys = mutableSetOf<String>()
        for (i in 0 until ops.length()) {
            val op = ops.optJSONObject(i) ?: continue
            val citedIds = ReminderAssociationMerger.evidenceIdsFrom(op)
            val evidenceIds = ReminderAssociationMerger.filterValidEvidence(citedIds, validRequestIds)
            if (evidenceIds.isEmpty()) {
                // Uncited claims never reach the database: an op the model cannot ground in the
                // records we actually sent should not have been produced at all.
                Log.w(TAG, "Dropping op without valid evidence: op=${op.optString("op", "create")} id=${savedItemIdFrom(op)} cited=$citedIds")
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                        .recordException(IllegalStateException("Extraction op dropped: no valid evidence ids"))
                } catch (_: Exception) {
                }
                continue
            }
            when (op.optString("op", "create")) {
                "update" -> applyUpdateOp(ctx, op, evidenceIds, linkSource)
                else -> applyCreateOp(ctx, op, evidenceIds, linkSource, origin)
            }
            evidenceIds.mapTo(opTouchedKeys) { it.substringBeforeLast("_") }
        }

        // Journal per-thread no_extraction verdicts so the next pass knows this thread was
        // reviewed and why nothing came of it. Only threads we actually sent, and never threads
        // that produced an op in this same response.
        if (verdicts != null) {
            val validKeys = requestKeys.toSet()
            val now = System.currentTimeMillis()
            for (i in 0 until verdicts.length()) {
                val v = verdicts.optJSONObject(i) ?: continue
                val key = v.optString("notiKey")
                if (key !in validKeys || key in opTouchedKeys) continue
                ctx.journalRepository.appendNoExtraction(key, v.optString("reason"), now)
            }
        }

        // Fold journal summaries returned for this request; folded entries are deleted only now.
        if (summaries != null) {
            val foldByKey = foldRequests.associateBy { it.notiKey }
            for (i in 0 until summaries.length()) {
                val s = summaries.optJSONObject(i) ?: continue
                val key = s.optString("notiKey")
                val summary = s.optString("summary")
                val request = foldByKey[key] ?: continue
                ctx.journalRepository.applyFoldedSummary(request, summary)
            }
        }
    }

    private suspend fun applyCreateOp(
        ctx: N8nWorkerContext,
        op: JSONObject,
        evidenceIds: Set<String>,
        linkSource: String,
        origin: String,
    ) {
        val savedItemId = savedItemIdFrom(op)
        if (savedItemId.isBlank()) return
        val now = System.currentTimeMillis()

        val title = titleFrom(op)
        val content = contentFrom(op)
        val isTask = op.optBoolean("isTask", true)
        val isCompleted = op.optBoolean("isCompleted", false)
        val sortScore = op.optDouble("sortScore", 50.0).toFloat()
        val reRankRecord = op.optJSONObject("reRankRecord")
        val reRankHistory = JSONArray().apply {
            put(
                reRankRecord ?: JSONObject().apply {
                    put("rankedAt", now)
                    put("trigger", "INITIAL_CREATE")
                    put("newScore", sortScore)
                    put("scoreExplanation", "Initial creation from extraction")
                }
            )
        }.toString()

        val unit = SavedItem(
            savedItemId = savedItemId,
            title = title,
            content = content,
            itemType = if (isTask) SavedItemType.Task else SavedItemType.Keep,
            state = if (isCompleted) SavedItemState.Completed else SavedItemState.New,
            lastUpdateTimestamp = now,
            deadlineAtMs = isoToUnixMillis(op.optString("deadlineTimeString", "-1")),
            startAtMs = startAtMsFrom(op),
            endAtMs = endAtMsFrom(op),
            estimatedCompletionTime = op.optLong("estimatedCompletionTime", op.optLong("estimatedCompletionMinutes", 0L)),
            origin = origin,
            humanEditCount = 0,
            deletedAtMs = null,
            userEdited = false,
            buttons = op.optJSONArray("buttons")?.toString() ?: "[]",
            isViewed = false,
            sortScore = sortScore,
            reRankHistory = reRankHistory,
        )

        ctx.reminderRepository.upsert(unit)
        ctx.reminderRepository.addEvidenceLinks(savedItemId, evidenceIds, unit.itemType, linkSource)
        persistReturnedSubTasks(ctx, savedItemId, op, now)

        val changeSummary = op.optString("changeSummary", "")
        ctx.changeLogRepository.record(
            SavedItemChangeLog(
                savedItemId = savedItemId,
                createdAt = now,
                changeType = SavedItemChangeType.Created,
                changeSummary = changeSummary,
                evidenceRecordIdsJson = JSONArray(evidenceIds.toList()).toString(),
                origin = "llm",
            )
        )
        journalItemEvent(ctx, evidenceIds, ExtractionJournalEventType.ItemCreated, savedItemId, title, changeSummary)
    }

    private suspend fun applyUpdateOp(
        ctx: N8nWorkerContext,
        op: JSONObject,
        evidenceIds: Set<String>,
        linkSource: String,
    ) {
        val savedItemId = savedItemIdFrom(op)
        val existing = ctx.reminderRepository.getById(savedItemId)
        if (existing == null) {
            Log.w(TAG, "Update op for unknown savedItemId=$savedItemId; dropping")
            return
        }
        val now = System.currentTimeMillis()
        val changes = op.optJSONObject("changes") ?: JSONObject()

        // Content is append-only: the user has already read what's there.
        val fragment = changes.optString("appendedContent", "").trim()
        val newContent = if (fragment.isNotBlank()) {
            existing.content + ctx.reminderRepository.buildUpdateSection(fragment, now)
        } else {
            existing.content
        }

        val changedFields = changes.optJSONObject("changedFields") ?: JSONObject()
        // Whitelisted field updates only — doAtMs/isStarred are user-owned and never touched.
        val newTitle = changedFields.optJSONObject("title")?.optString("new")?.takeIf { it.isNotBlank() } ?: existing.title
        val newDeadline = changedFields.optJSONObject("deadlineTimeString")
            ?.let { isoToUnixMillis(it.optString("new", "-1")) } ?: existing.deadlineAtMs
        val newStart = changedFields.optJSONObject("startTimeString")
            ?.let { isoToUnixMillis(it.optString("new", "-1")).let { v -> if (v == -1L) 0L else v } } ?: existing.startAtMs
        val newEnd = changedFields.optJSONObject("endTimeString")
            ?.let { isoToUnixMillis(it.optString("new", "-1")).let { v -> if (v == -1L) 0L else v } } ?: existing.endAtMs
        val newEstimate = changedFields.optJSONObject("estimatedCompletionMinutes")
            ?.optLong("new", existing.estimatedCompletionTime) ?: existing.estimatedCompletionTime

        val sortScore = if (op.has("sortScore")) op.optDouble("sortScore", existing.sortScore.toDouble()).toFloat() else existing.sortScore
        val reRankRecord = op.optJSONObject("reRankRecord")
        val reRankHistory = if (reRankRecord != null) {
            try {
                JSONArray(existing.reRankHistory).put(reRankRecord).toString()
            } catch (_: Exception) {
                JSONArray().put(reRankRecord).toString()
            }
        } else {
            existing.reRankHistory
        }

        ctx.reminderRepository.upsert(
            existing.copy(
                title = newTitle,
                content = newContent,
                state = SavedItemState.Updated,
                lastUpdateTimestamp = now,
                deadlineAtMs = newDeadline,
                startAtMs = newStart,
                endAtMs = newEnd,
                estimatedCompletionTime = newEstimate,
                buttons = op.optJSONArray("buttons")?.toString() ?: existing.buttons,
                isViewed = false,
                sortScore = sortScore,
                reRankHistory = reRankHistory,
            )
        )
        ctx.reminderRepository.addEvidenceLinks(savedItemId, evidenceIds, existing.itemType, linkSource)

        // Sub-tasks are additive: new ones inserted, removed ones soft-hidden with their reason
        // recorded; existing rows (ids, titles, completion) are never rewritten.
        val addedSubTasks = parseSubTasks(changes.optJSONArray("addedSubTasks"), savedItemId, now, baseSortOrder = existingSubTaskCount(ctx, savedItemId))
        if (addedSubTasks.isNotEmpty()) ctx.savedSubItemRepository.addSubTasks(addedSubTasks)

        val removedArr = changes.optJSONArray("removedSubTasks") ?: JSONArray()
        val removedForLog = JSONArray()
        val removedIds = buildList {
            for (i in 0 until removedArr.length()) {
                val r = removedArr.optJSONObject(i) ?: continue
                val id = r.optString("subTaskId", r.optString("savedSubItemId"))
                if (id.isBlank()) continue
                add(id)
                val title = ctx.savedSubItemRepository.getById(id)?.title ?: ""
                removedForLog.put(JSONObject().put("savedSubItemId", id).put("title", title).put("reason", r.optString("reason", "")))
            }
        }
        if (removedIds.isNotEmpty()) ctx.savedSubItemRepository.hideSubTasks(removedIds, now)

        val changeSummary = changes.optString("changeSummary", "")
        ctx.changeLogRepository.record(
            SavedItemChangeLog(
                savedItemId = savedItemId,
                createdAt = now,
                changeType = SavedItemChangeType.LlmUpdate,
                changeSummary = changeSummary,
                appendedContent = fragment,
                addedSubTasksJson = JSONArray().apply {
                    addedSubTasks.forEach { st ->
                        put(JSONObject().put("savedSubItemId", st.savedSubItemId).put("title", st.title))
                    }
                }.toString(),
                removedSubTasksJson = removedForLog.toString(),
                changedFieldsJson = changedFields.toString(),
                evidenceRecordIdsJson = JSONArray(evidenceIds.toList()).toString(),
                origin = "llm",
            )
        )
        journalItemEvent(ctx, evidenceIds, ExtractionJournalEventType.ItemUpdated, savedItemId, newTitle, changeSummary)
    }

    /** Journals an item event to every notiUnit that evidenced it. */
    private suspend fun journalItemEvent(
        ctx: N8nWorkerContext,
        evidenceIds: Set<String>,
        eventType: String,
        savedItemId: String,
        itemTitle: String,
        detail: String,
    ) {
        val now = System.currentTimeMillis()
        evidenceIds.map { it.substringBeforeLast("_") }.distinct().forEach { key ->
            ctx.journalRepository.append(
                ExtractionJournalEntry(
                    notiKey = key,
                    createdAt = now,
                    eventType = eventType,
                    savedItemId = savedItemId,
                    itemTitle = itemTitle,
                    detail = detail,
                )
            )
        }
    }

    private suspend fun existingSubTaskCount(ctx: N8nWorkerContext, savedItemId: String): Int = try {
        ctx.database.subTaskDao().getByReminderId(savedItemId).size
    } catch (_: Exception) {
        0
    }

    private fun parseSubTasks(arr: JSONArray?, parentSavedItemId: String, ts: Long, baseSortOrder: Int): List<SavedSubItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val sub = arr.optJSONObject(i) ?: continue
                val id = sub.optString("savedSubItemId", sub.optString("subTaskId"))
                if (id.isBlank()) continue
                add(
                    SavedSubItem(
                        savedSubItemId = id,
                        parentSavedItemId = parentSavedItemId,
                        title = sub.optString("title", ""),
                        description = sub.optString("description", sub.optString("content", "")),
                        itemType = if (sub.optBoolean("isTask", true)) SavedItemType.Task else SavedItemType.Keep,
                        isCompleted = sub.optBoolean("isCompleted", false),
                        deadlineAtMs = isoToUnixMillis(sub.optString("deadlineTimeString", "-1")),
                        startAtMs = startAtMsFrom(sub),
                        endAtMs = endAtMsFrom(sub),
                        buttons = sub.optJSONArray("buttons")?.toString() ?: "[]",
                        sortOrder = sub.optInt("sortOrder", baseSortOrder + i),
                        createdAt = ts,
                        lastUpdateTimestamp = ts,
                        isVisible = true,
                    )
                )
            }
        }
    }

    /** Reads reminder IDs from both the current app schema and older n8n schemas. */
    fun savedItemIdFrom(obj: JSONObject): String = obj.optString(
        "savedItemId",
        obj.optString("reminderId", obj.optString("taskId")),
    )

    fun titleFrom(obj: JSONObject, fallback: String = ""): String = obj.optString(
        "title",
        obj.optString("reminderTitle", fallback),
    )

    fun contentFrom(obj: JSONObject, fallback: String = ""): String = obj.optString(
        "content",
        obj.optString("reminderContent", obj.optString("taskDescription", fallback)),
    )

    fun startAtMsFrom(obj: JSONObject): Long = isoToUnixMillis(
        obj.optString("startAtMsString", obj.optString("startTimeString", "-1")),
    ).let { v -> if (v == -1L) 0L else v }

    fun endAtMsFrom(obj: JSONObject): Long = isoToUnixMillis(
        obj.optString("endAtMsString", obj.optString("endTimeString", "-1")),
    ).let { v -> if (v == -1L) 0L else v }

    /**
     * Persists child items from a full `subTasks` response array (create ops and regeneration).
     *
     * The array is treated as the complete visible child list for that parent. Existing omitted children are
     * soft-deleted by the repository; if the field is absent, the existing children are left untouched.
     */
    suspend fun persistReturnedSubTasks(ctx: N8nWorkerContext, parentSavedItemId: String, obj: JSONObject, ts: Long) {
        if (!obj.has("subTasks")) return
        val subTasks = parseSubTasks(obj.optJSONArray("subTasks"), parentSavedItemId, ts, baseSortOrder = 0)
        ctx.savedSubItemRepository.replaceVisibleForParent(parentSavedItemId, subTasks, ts)
    }

    /**
     * Converts n8n deadline/start/end timestamps into local epoch millis.
     *
     * Keep the no-deadline sentinel handling here so response parsing does not scatter timestamp
     * edge cases across reminder construction code.
     */
    fun isoToUnixMillis(iso: String): Long {
        if (iso == "-1" || iso.isBlank()) return -1L  // no-deadline sentinel from the n8n response schema

        // Most common: has an offset like +08:00 or ends with Z
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            // Fallback: sometimes it's a full zone format or slightly different ISO variant
            ZonedDateTime.parse(iso).toInstant().toEpochMilli()
        }
    }
}
