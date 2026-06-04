package org.muilab.notigpt.data.remote.n8n.workers.handlers

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.data.remote.n8n.formatter.N8nRecordFormatter
import org.muilab.notigpt.data.remote.n8n.context.N8nWorkerContext
import org.muilab.notigpt.domain.reminder.ReminderSnapshotStatuses
import org.muilab.notigpt.model.features.ReminderExtractionSnapshot
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.domain.reminder.ReminderAssociationMerger
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.util.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Worker handler for reminder extraction over selected notification records.
 *
 * This is the largest n8n workflow bridge: it builds record context, calls extraction, merges snapshot payloads,
 * and persists reminder/subtask results. Keep helper functions near the handler until a stable domain service
 * boundary emerges.
 */
internal object ReminderExtractionHandler {

    /**
     * Runs one extraction job from WorkManager input through remote n8n output and local Room writes.
     *
     * User-triggered extraction uses the UI-selected records, while periodic extraction claims eligible
     * records before sending them. Keep those two paths visible in this function unless they are split
     * into separate handlers with the same snapshot/provenance guarantees.
     */
    suspend fun handle(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        val gson = Gson()

        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e("N8nWebhook", "No webhook_path for extract")
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

        val db = ctx.database
        val reminderRepository = ctx.reminderRepository
        val snapshotDao = db.reminderSnapshotDao()

        val drawerDao = db.drawerDao()

        // === User-triggered (single notification) ===
        // UI passes exactly one notiKey. For this path we IGNORE extracted/claimed flags and
        // send visible records + a few older records for context, but we still upsert reminders.
        if (userTriggered) {
            val notiKey = notiKeys.firstOrNull()
            if (notiKey.isNullOrBlank()) {
                Log.d("N8nWebhook", "User-triggered extraction: missing notiKey")
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
                Log.d("N8nWebhook", "User-triggered extraction: no records found for key=$notiKey")
                return ctx.success()
            }

            // Create extraction snapshot for this request (v2: recordIds + notiKey mapping)
            val snapshotId = "snap_${UUID.randomUUID()}"
            val snapNow = System.currentTimeMillis()

            // Include past extracted context too, so the snapshot reflects the FULL LLM input context.
            val pastRecords = if (pastCnt > 0) {
                recordDao.getLastExtractedRecordsByKey(notiKey, pastCnt).sortedBy { it.time }
            } else {
                emptyList()
            }

            val allSnapshotRecords = (combined + pastRecords)
                .distinctBy { it.notiRecordId }
                .sortedBy { it.time }

            val byKey = allSnapshotRecords.groupBy { it.notiKey }
            val snapshotPayload = JSONObject().apply {
                put("v", 2)
                put("recordIds", JSONArray(allSnapshotRecords.map { it.notiRecordId }.distinct()))
                put("notiKeyToRecordIds", JSONObject().apply {
                    byKey.forEach { (k, recs) ->
                        put(k, JSONArray(recs.map { it.notiRecordId }.distinct()))
                    }
                })
                // v2.1: keep an explicit list of the currently-visible recordIds for debugging.
                put("visibleRecordIds", JSONArray(combined.map { it.notiRecordId }.distinct()))
            }.toString()

            snapshotDao.upsertSnapshot(
                ReminderExtractionSnapshot(
                    snapshotId = snapshotId,
                    status = ReminderSnapshotStatuses.STAGED,
                    savedItemId = null,
                    payloadJson = snapshotPayload,
                    createdAt = snapNow,
                )
            )

            // In explicit mode, only send what the user saw.
            val contents = combined.map { r -> N8nRecordFormatter.format(r, unit.isPeople) }

            // Keep schema compatible with periodic payload.
            val lastRecord = combined.lastOrNull()
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

            val notisPayload = listOf(
                mapOf(
                    "notiKey" to notiKey,
                    "appName" to unit.appName,
                    "overallTitle" to overallTitle,
                    "secondOverallTitle" to secondOverallTitle,
                    "notiContent" to contents,
                    // IMPORTANT: do not include inferred pastContext here; the UI already chose visible records.
                    "pastContext" to emptyList<Any>(),
                    "hasTask" to unit.hasTask,
                    "hasMemo" to unit.hasMemo,
                    "hasEvent" to unit.hasEvent
                )
            )

            val currentReminders: List<SavedItem> = try {
                reminderRepository.observeAll().first()
            } catch (_: Exception) {
                emptyList<SavedItem>()
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            val remindersForPayload = currentReminders.map { r ->
                val deadlineIso = if (r.deadlineAtMs > 0L) sdf.format(Date(r.deadlineAtMs)) else -1L
                val startAtMsIso = if (r.startAtMs > 0L) sdf.format(Date(r.startAtMs)) else -1L
                val endAtMsIso = if (r.endAtMs > 0L) sdf.format(Date(r.endAtMs)) else -1L
                mapOf(
                    "savedItemId" to r.savedItemId,
                    "title" to r.title,
                    "content" to r.content,
                    "isTask" to r.isTask,
                    "isEvent" to r.isEvent,
                    "deadlineTimeString" to deadlineIso,
                    "startAtMsString" to startAtMsIso,
                    "endAtMsString" to endAtMsIso,
                    "estimatedCompletionMinutes" to r.estimatedCompletionTime,
                    "sourceNotiRecordIds" to r.sourceNotiRecordIds.toList(),
                    "userEdited" to r.userEdited,
                    "isCompleted" to r.isCompleted,
                    "buttons" to r.buttons,
                    "sortScore" to r.sortScore,
                    "isPinned" to r.isPinned,
                    "reRankHistory" to r.reRankHistory,
                )
            }

            val payload = mapOf(
                "userId" to SharedPreferencesManager.userId,
                "language" to Locale.getDefault().toLanguageTag(),
                "timezone" to TimeZone.getDefault().displayName,
                "currentTime" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date()),
                "targetExtractionLanguage" to ctx.getTargetExtractionLanguage(),
                "userTriggered" to true,
                "notis" to notisPayload,
                "currentReminders" to remindersForPayload,
                "extractionPreferences" to ctx.getExtractionPreferencesPayload(),
                "userContexts" to ctx.getUserContextsPayload()
            )

            val jsonPayload = gson.toJson(payload)
            val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

            Log.d("N8nWebhook", "JSON Payload (user-triggered): $jsonPayload")

            val response = try {
                ctx.n8nApiService.postToWebhook(webhookPath, requestBody)
            } catch (t: Throwable) {
                Log.e("N8nWebhook", "TaskExtraction network exception", t)
                return ctx.retry()
            }

            if (!response.isSuccessful) {
                return when {
                    response.code() == 429 -> ctx.retry()
                    response.code() in 500..599 -> ctx.retry()
                    else -> ctx.failure()
                }
            }

            val bodyStr = response.body()?.string() ?: return ctx.success()
            Log.d("N8nWebhook", "Extraction Response (user-triggered): $bodyStr")

            try {
                val arr = JSONArray(bodyStr)

                if (arr.length() == 0) {
                    // No reminders extracted -> discard snapshot
                    snapshotDao.deleteSnapshot(snapshotId)
                } else {
                    for (i in 0 until arr.length()) {
                        val it = arr.getJSONObject(i)
                        val savedItemId = savedItemIdFrom(it)
                        val title = titleFrom(it)
                        val content = contentFrom(it)
                        val isTask = it.optBoolean("isTask", true)
                        val isEvent = it.optBoolean("isEvent", false)
                        val deadlineMs = isoToUnixMillis(it.optString("deadlineTimeString", "-1"))
                        val startAtMsMs = startAtMsFrom(it)
                        val endAtMsMs = endAtMsFrom(it)
                        val estimate = it.optLong("estimatedCompletionTime", it.optLong("estimatedCompletionMinutes", 0L))
                        val responseAssocIds = ReminderAssociationMerger.associationIdsFrom(it)
                        val existing = if (savedItemId.isNotBlank()) reminderRepository.getById(savedItemId) else null
                        val assocIds = ReminderAssociationMerger.merge(
                            existing = existing,
                            responseAssociationIds = responseAssocIds,
                            requestRecordIds = combined.map { rec -> rec.notiRecordId }.toSet(),
                        )
                        val isCompleted = it.optBoolean("isCompleted", false)

                        // Parse buttons
                        val buttonsArr = it.optJSONArray("buttons")
                        val buttons = buttonsArr?.toString() ?: "[]"

                        // Parse sortScore and reRankRecord
                        val sortScore = it.optDouble("sortScore", 50.0).toFloat()
                        val reRankRecord = it.optJSONObject("reRankRecord")
                        val reRankHistory = JSONArray().apply {
                            if (reRankRecord != null) {
                                put(reRankRecord)
                            } else {
                                put(JSONObject().apply {
                                    put("rankedAt", System.currentTimeMillis())
                                    put("trigger", "INITIAL_CREATE")
                                    put("newScore", sortScore)
                                    put("scoreExplanation", "Initial creation from user-triggered extraction")
                                })
                            }
                        }.toString()

                        // If we're overwriting an existing reminder, merge relevant records from its old snapshot
                        // into this extraction snapshot (so downstream sync/UI have the full context).
                        try {
                            val oldSnapshotId = existing?.sourceExtractionSnapshotId
                            if (!oldSnapshotId.isNullOrBlank() && assocIds.isNotEmpty()) {
                                val oldSnap = snapshotDao.getSnapshot(oldSnapshotId)
                                    ?: snapshotDao.getLatestKeptSnapshotForReminder(savedItemId)
                                if (oldSnap != null) {
                                    val merged = mergedSnapshotPayloadJson(snapshotPayload, oldSnap, assocIds.toSet())
                                    if (merged != snapshotPayload) {
                                        snapshotDao.upsertSnapshot(
                                            ReminderExtractionSnapshot(
                                                snapshotId = snapshotId,
                                                status = ReminderSnapshotStatuses.STAGED,
                                                savedItemId = null,
                                                payloadJson = merged,
                                                createdAt = snapNow,
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("N8nWebhook", "Snapshot merge skipped for savedItemId=$savedItemId", e)
                        }

                        val newUnit = SavedItem(
                            savedItemId = savedItemId,
                            title = title,
                            content = content,
                            itemType = if (isTask) SavedItemType.Task else SavedItemType.Keep,
                            state = if (isCompleted) SavedItemState.Completed else SavedItemState.New,
                            lastUpdateTimestamp = System.currentTimeMillis(),
                            deadlineAtMs = deadlineMs,
                            startAtMs = startAtMsMs,
                            endAtMs = endAtMsMs,
                            estimatedCompletionTime = estimate,
                            sourceNotiRecordIds = assocIds,
                            sourceExtractionSnapshotId = snapshotId,
                            origin = "llm_manual_extraction",
                            humanEditCount = 0,
                            deletedAtMs = null,
                            userEdited = false,
                            buttons = buttons,
                            isViewed = false,
                            isPinned = false,
                            sortScore = sortScore,
                            reRankHistory = reRankHistory,
                        )

                        reminderRepository.upsert(newUnit)
                        persistReturnedSubTasks(ctx, savedItemId, it, System.currentTimeMillis())
                    }

                    // Mark snapshot kept and link to (first) savedItemId.
                    val firstObj = arr.optJSONObject(0)
                    val firstReminderId = firstObj?.let { savedItemIdFrom(it) }
                    snapshotDao.updateSnapshotStatusAndReminderId(snapshotId, ReminderSnapshotStatuses.KEPT, firstReminderId)

                }

                drawerDao.setShouldExtractReminderByKeys(listOf(notiKey), false)
            } catch (e: Exception) {
                Log.e("N8nWebhook", "Error parsing extract response (user-triggered)", e)
                // Parsing failed -> don't keep snapshot
                snapshotDao.deleteSnapshot(snapshotId)
                return ctx.failure()
            }

            return ctx.success()
        }

        // === Periodic flow below remains unchanged ===

        val keysToProcess: List<String> = notiKeys.ifEmpty {
            drawerDao.getAllActiveShouldExtractKeys()
        }

        val nowTs = System.currentTimeMillis()
        val staleMs = 5 * 60 * 1000L
        db.recordDao().reclaimStaleClaims(nowTs, staleMs)

        val candidateRecordIds = keysToProcess.flatMap { key ->
            db.recordDao().getUnclaimedUnextractedByKey(key).map { it.notiRecordId }
        }.distinct()

        if (candidateRecordIds.isEmpty()) {
            Log.d("N8nWebhook", "No candidate records to extract")
            return ctx.success()
        }

        val claimTs = System.currentTimeMillis()
        val claimedCount = if (candidateRecordIds.isNotEmpty())
            db.recordDao().claimRecordsForExtractionWithTs(candidateRecordIds, claimTs)
        else 0

        Log.d(
            "N8nWebhook",
            "Attempted to claim ${candidateRecordIds.size} records, actually claimed=$claimedCount (ts=$claimTs)"
        )

        if (claimedCount <= 0) {
            Log.d("N8nWebhook", "No records could be claimed; another worker likely claimed them")
            return ctx.success()
        }

        val claimedRecords = db.recordDao().getClaimedRecordsByIds(candidateRecordIds).sortedBy { it.time }
        if (claimedRecords.isEmpty()) {
            Log.d("N8nWebhook", "No claimed records returned after claim; aborting")
            return ctx.success()
        }

        val claimedByKey: Map<String, List<NotiRecord>> = claimedRecords.groupBy { it.notiKey }

        // Create extraction snapshot for this request (v2)
        val snapshotId = "snap_${UUID.randomUUID()}"
        val snapNow = System.currentTimeMillis()

        // Include past extracted context for each key, since it is part of LLM input.
        val pastCnt = SharedPreferencesManager.maxPastContext
        val pastByKey: Map<String, List<NotiRecord>> = if (pastCnt > 0) {
            claimedByKey.keys.associateWith { key ->
                db.recordDao().getLastExtractedRecordsByKey(key, pastCnt).sortedBy { it.time }
            }
        } else {
            emptyMap()
        }

        val allSnapshotRecords = buildList {
            addAll(claimedRecords)
            pastByKey.values.forEach { addAll(it) }
        }.distinctBy { it.notiRecordId }.sortedBy { it.time }

        val snapshotMapping = JSONObject().apply {
            // Include only keys in this extraction request.
            claimedByKey.keys.forEach { key ->
                val current = claimedByKey[key].orEmpty()
                val past = pastByKey[key].orEmpty()
                val ids = (current + past).map { it.notiRecordId }.distinct()
                put(key, JSONArray(ids))
            }
        }

        val snapshotPayload = JSONObject().apply {
            put("v", 2)
            put("recordIds", JSONArray(allSnapshotRecords.map { it.notiRecordId }.distinct()))
            put("notiKeyToRecordIds", snapshotMapping)
        }.toString()

        snapshotDao.upsertSnapshot(
            ReminderExtractionSnapshot(
                snapshotId = snapshotId,
                status = ReminderSnapshotStatuses.STAGED,
                savedItemId = null,
                payloadJson = snapshotPayload,
                createdAt = snapNow,
            )
        )

        val submittedRecordIds = mutableListOf<String>()
        val submittedKeys = mutableListOf<String>()

        val notisPayload = claimedByKey.map { (key, records) ->
            val unit = ctx.getNotiUnit(key) ?: return@map null

            submittedKeys += key
            submittedRecordIds += records.map { it.notiRecordId }

            val contents = records.sortedBy { it.time }.map { r -> N8nRecordFormatter.format(r, unit.isPeople) }
            val pastCnt = SharedPreferencesManager.maxPastContext
            val pastRecs = if (pastCnt > 0) db.recordDao().getLastExtractedRecordsByKey(key, pastCnt).sortedBy { it.time } else emptyList()
            val pastCtx = pastRecs.map { N8nRecordFormatter.format(it, unit.isPeople) }

            val lastRecord = records.lastOrNull()
            val lastTitle = lastRecord?.title ?: ""

            val notiOverallTitle = if (lastRecord != null) {
                when {
                    lastRecord.extraConversationTitle != "null" -> lastRecord.extraConversationTitle
                    lastTitle != "null" -> lastTitle
                    lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                    else -> ""
                }
            } else ""

            val notiSecondOverallTitle = if (lastRecord != null) {
                when {
                    lastRecord.extraConversationTitle != "null" && lastTitle != "null" -> lastTitle
                    lastRecord.extraConversationTitle == "null" && lastTitle != "null" && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                    lastRecord.extraConversationTitle == "null" && lastTitle != "null" -> ""
                    else -> ""
                }
            } else ""

            mapOf(
                "notiKey" to key,
                "appName" to unit.appName,
                "overallTitle" to notiOverallTitle,
                "secondOverallTitle" to notiSecondOverallTitle,
                "notiContent" to contents,
                "pastContext" to pastCtx,
                "hasTask" to unit.hasTask,
                "hasMemo" to unit.hasMemo,
                "hasEvent" to unit.hasEvent
            )
        }.filterNotNull()

        if (notisPayload.isEmpty()) {
            db.recordDao().clearClaimedRecords(candidateRecordIds)
            return ctx.success()
        }

        // Include current reminders (tasks + memos) as context for extraction.
        val currentReminders: List<SavedItem> = try {
            reminderRepository.observeAll().first()
        } catch (_: Exception) {
            emptyList<SavedItem>()
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val remindersForPayload = currentReminders.map { r ->
            val deadlineIso = if (r.deadlineAtMs > 0L) sdf.format(Date(r.deadlineAtMs)) else -1L
            val startAtMsIso = if (r.startAtMs > 0L) sdf.format(Date(r.startAtMs)) else -1L
            val endAtMsIso = if (r.endAtMs > 0L) sdf.format(Date(r.endAtMs)) else -1L
            mapOf(
                "savedItemId" to r.savedItemId,
                "title" to r.title,
                "content" to r.content,
                "isTask" to r.isTask,
                "isEvent" to r.isEvent,
                "deadlineTimeString" to deadlineIso,
                "startAtMsString" to startAtMsIso,
                "endAtMsString" to endAtMsIso,
                "estimatedCompletionMinutes" to r.estimatedCompletionTime,
                "sourceNotiRecordIds" to r.sourceNotiRecordIds.toList(),
                "userEdited" to r.userEdited,
                "isCompleted" to r.isCompleted,
                "buttons" to r.buttons,
                "sortScore" to r.sortScore,
                "isPinned" to r.isPinned,
                "reRankHistory" to r.reRankHistory,
            )
        }

        val payload = mapOf(
            "userId" to SharedPreferencesManager.userId,
            "language" to Locale.getDefault().toLanguageTag(),
            "timezone" to TimeZone.getDefault().displayName,
            "currentTime" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date()),
            "targetExtractionLanguage" to ctx.getTargetExtractionLanguage(),
            "userTriggered" to false,
            "notis" to notisPayload,
            "currentReminders" to remindersForPayload,
            "extractionPreferences" to ctx.getExtractionPreferencesPayload(),
            "userContexts" to ctx.getUserContextsPayload()
        )

        val jsonPayload = gson.toJson(payload)
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        Log.d("N8nWebhook", "JSON Payload (claimed): $jsonPayload")

        val response = try {
            ctx.n8nApiService.postToWebhook(webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e("N8nWebhook", "TaskExtraction network exception", t)
            db.recordDao().clearClaimedRecords(candidateRecordIds)
            return ctx.retry()
        }

        if (!response.isSuccessful) {
            db.recordDao().clearClaimedRecords(candidateRecordIds)
            return when {
                response.code() == 429 -> ctx.retry()
                response.code() in 500..599 -> ctx.retry()
                else -> ctx.failure()
            }
        }

        val bodyStr = response.body()?.string() ?: return ctx.success()
        Log.d("N8nWebhook", "Extraction Response: $bodyStr")

        try {
            val arr = JSONArray(bodyStr)

            if (arr.length() == 0) {
                snapshotDao.deleteSnapshot(snapshotId)
            } else {
                var firstCreatedReminderId: String? = null

                for (i in 0 until arr.length()) {
                    val it = arr.getJSONObject(i)
                    val savedItemId = savedItemIdFrom(it)
                    val title = titleFrom(it)
                    val content = contentFrom(it)
                    val isTask = it.optBoolean("isTask", true)
                    val isEvent = it.optBoolean("isEvent", false)
                    val deadlineMs = isoToUnixMillis(it.optString("deadlineTimeString", "-1"))
                    val startAtMsMs = startAtMsFrom(it)
                    val endAtMsMs = endAtMsFrom(it)
                    val estimate = it.optLong("estimatedCompletionTime", it.optLong("estimatedCompletionMinutes", 0L))
                    val responseAssocIds = ReminderAssociationMerger.associationIdsFrom(it)
                    val existing = if (savedItemId.isNotBlank()) reminderRepository.getById(savedItemId) else null
                    val assocIds = ReminderAssociationMerger.merge(
                        existing = existing,
                        responseAssociationIds = responseAssocIds,
                        requestRecordIds = candidateRecordIds.toSet(),
                    )
                    val isCompleted = it.optBoolean("isCompleted", false)

                    // Parse buttons
                    val buttonsArr = it.optJSONArray("buttons")
                    val buttons = buttonsArr?.toString() ?: "[]"

                    // Parse sortScore and reRankRecord
                    val sortScore = it.optDouble("sortScore", 50.0).toFloat()
                    val reRankRecord = it.optJSONObject("reRankRecord")
                    val reRankHistory = JSONArray().apply {
                        if (reRankRecord != null) {
                            put(reRankRecord)
                        } else {
                            put(JSONObject().apply {
                                put("rankedAt", System.currentTimeMillis())
                                put("trigger", "INITIAL_CREATE")
                                put("newScore", sortScore)
                                put("scoreExplanation", "Initial creation from auto extraction")
                            })
                        }
                    }.toString()

                    // Merge old snapshot context if this savedItemId already exists.
                    try {
                        val oldSnapshotId = existing?.sourceExtractionSnapshotId
                        if (!oldSnapshotId.isNullOrBlank() && assocIds.isNotEmpty()) {
                            val oldSnap = snapshotDao.getSnapshot(oldSnapshotId)
                                ?: snapshotDao.getLatestKeptSnapshotForReminder(savedItemId)
                            if (oldSnap != null) {
                                val merged = mergedSnapshotPayloadJson(snapshotPayload, oldSnap, assocIds.toSet())
                                if (merged != snapshotPayload) {
                                    snapshotDao.upsertSnapshot(
                                        ReminderExtractionSnapshot(
                                            snapshotId = snapshotId,
                                            status = ReminderSnapshotStatuses.STAGED,
                                            savedItemId = null,
                                            payloadJson = merged,
                                            createdAt = snapNow,
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("N8nWebhook", "Snapshot merge skipped for savedItemId=$savedItemId", e)
                    }

                    val unit = SavedItem(
                        savedItemId = savedItemId,
                        title = title,
                        content = content,
                        itemType = if (isTask) SavedItemType.Task else SavedItemType.Keep,
                        state = if (isCompleted) SavedItemState.Completed else SavedItemState.New,
                        lastUpdateTimestamp = System.currentTimeMillis(),
                        deadlineAtMs = deadlineMs,
                        startAtMs = startAtMsMs,
                        endAtMs = endAtMsMs,
                        estimatedCompletionTime = estimate,
                        sourceNotiRecordIds = assocIds,
                        sourceExtractionSnapshotId = snapshotId,
                        origin = "llm_auto_extraction",
                        humanEditCount = 0,
                        deletedAtMs = null,
                        userEdited = false,
                        buttons = buttons,
                        isViewed = false,
                        isPinned = false,
                        sortScore = sortScore,
                        reRankHistory = reRankHistory,
                    )

                    reminderRepository.upsert(unit)
                    persistReturnedSubTasks(ctx, savedItemId, it, System.currentTimeMillis())
                }

                drawerDao.setShouldExtractReminderByKeys(keysToProcess, false)

                val firstObj = arr.optJSONObject(0)
                val firstReminderId = firstObj?.let { savedItemIdFrom(it) }
                snapshotDao.updateSnapshotStatusAndReminderId(snapshotId, ReminderSnapshotStatuses.KEPT, firstReminderId)
            }

        } catch (e: Exception) {
            Log.e("N8nWebhook", "Error parsing extract response", e)
            snapshotDao.deleteSnapshot(snapshotId)
            db.recordDao().clearClaimedRecords(candidateRecordIds)
        }

        return ctx.success()
    }

    /** Reads reminder IDs from both the current app schema and the still-deployed n8n schema. */
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
     * Persists child items from the current n8n `subTasks` response shape.
     *
     * The array is treated as the complete visible child list for that parent. Existing omitted children are
     * soft-deleted by the repository; if the field is absent, older workflows are left untouched.
     */
    suspend fun persistReturnedSubTasks(ctx: N8nWorkerContext, parentSavedItemId: String, obj: JSONObject, ts: Long) {
        if (!obj.has("subTasks")) return
        val arr = obj.optJSONArray("subTasks") ?: JSONArray()
        val subTasks = buildList {
            for (i in 0 until arr.length()) {
                val sub = arr.optJSONObject(i) ?: continue
                val id = sub.optString("savedSubItemId", sub.optString("subTaskId"))
                if (id.isBlank()) continue
                val deadlineMs = isoToUnixMillis(sub.optString("deadlineTimeString", "-1"))
                add(
                    SavedSubItem(
                        savedSubItemId = id,
                        parentSavedItemId = parentSavedItemId,
                        title = sub.optString("title", ""),
                        description = sub.optString("description", sub.optString("content", "")),
                        itemType = if (sub.optBoolean("isTask", true)) SavedItemType.Task else SavedItemType.Keep,
                        isCompleted = sub.optBoolean("isCompleted", false),
                        deadlineAtMs = deadlineMs,
                        startAtMs = startAtMsFrom(sub),
                        endAtMs = endAtMsFrom(sub),
                        buttons = sub.optJSONArray("buttons")?.toString() ?: "[]",
                        sortOrder = sub.optInt("sortOrder", i),
                        createdAt = ts,
                        lastUpdateTimestamp = ts,
                        isVisible = true,
                    )
                )
            }
        }
        ctx.savedSubItemRepository.replaceVisibleForParent(parentSavedItemId, subTasks, ts)
    }

    /**
     * Converts n8n deadline/start/end timestamps into local epoch millis.
     *
     * Keep the no-deadline sentinel handling here so response parsing does not scatter timestamp
     * edge cases across reminder construction code.
     */
    fun isoToUnixMillis(iso: String): Long {
        if (iso == "-1") return -1L  // no-deadline sentinel from the n8n response schema

        // Most common: has an offset like +08:00 or ends with Z
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            // Fallback: sometimes it's a full zone format or slightly different ISO variant
            ZonedDateTime.parse(iso).toInstant().toEpochMilli()
        }
    }

    /**
     * Reads record provenance from a v2 reminder snapshot payload.
     *
     * This exists only for merging old and new extraction context during reminder updates. If more
     * callers need it, move this wrapper to ReminderSnapshotPayload and keep this handler orchestration-only.
     */
    private fun parseRecordIdsFromSnapshotPayload(payloadJson: String): Pair<Set<String>, Map<String, Set<String>>?> {
        return try {
            val obj = JSONObject(payloadJson)
            val v = obj.optInt("v", 1)
            if (v != 2) return emptySet<String>() to null

            val recordIds = mutableSetOf<String>()
            val arr = obj.optJSONArray("recordIds")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i)
                    if (!id.isNullOrBlank()) recordIds += id
                }
            }

            val mappingObj = obj.optJSONObject("notiKeyToRecordIds")
            val mapping = if (mappingObj != null) {
                val out = mutableMapOf<String, Set<String>>()
                val keys = mappingObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val idsArr = mappingObj.optJSONArray(key)
                    if (idsArr != null) {
                        val ids = mutableSetOf<String>()
                        for (j in 0 until idsArr.length()) {
                            val id = idsArr.optString(j)
                            if (!id.isNullOrBlank()) ids += id
                        }
                        out[key] = ids
                    }
                }
                out
            } else null

            recordIds to mapping
        } catch (_: Exception) {
            emptySet<String>() to null
        }
    }

    /**
     * Merge *relevant* recordIds from [oldSnap] into [newPayloadJson].
     *
     * Rule: only bring over records whose notiKey is in [associatedNotiKeys].
     * We merge both `recordIds` and `notiKeyToRecordIds` (v2/v2.1 compatible).
     */
    private fun mergedSnapshotPayloadJson(
        newPayloadJson: String,
        oldSnap: ReminderExtractionSnapshot,
        associatedNotiKeys: Set<String>,
    ): String {
        if (associatedNotiKeys.isEmpty()) return newPayloadJson

        val (_, oldMapping) = parseRecordIdsFromSnapshotPayload(oldSnap.payloadJson)
        val oldRecordIdsToMerge: Set<String> = if (oldMapping != null) {
            associatedNotiKeys.flatMap { k -> oldMapping[k].orEmpty() }.toSet()
        } else {
            // Can't safely filter without mapping; be conservative and skip.
            emptySet()
        }

        if (oldRecordIdsToMerge.isEmpty()) return newPayloadJson

        return try {
            val newObj = JSONObject(newPayloadJson)
            val v = newObj.optInt("v", 1)
            if (v != 2) return newPayloadJson

            // Merge recordIds
            val newRecordIds = mutableSetOf<String>()
            val newArr = newObj.optJSONArray("recordIds")
            if (newArr != null) {
                for (i in 0 until newArr.length()) {
                    val id = newArr.optString(i)
                    if (!id.isNullOrBlank()) newRecordIds += id
                }
            }
            newRecordIds += oldRecordIdsToMerge
            newObj.put("recordIds", JSONArray(newRecordIds.toList()))

            // Merge notiKeyToRecordIds mapping
            val mappingObj = newObj.optJSONObject("notiKeyToRecordIds") ?: JSONObject().also {
                newObj.put("notiKeyToRecordIds", it)
            }

            for (k in associatedNotiKeys) {
                val existingIds = mutableSetOf<String>()
                val existArr = mappingObj.optJSONArray(k)
                if (existArr != null) {
                    for (i in 0 until existArr.length()) {
                        val id = existArr.optString(i)
                        if (!id.isNullOrBlank()) existingIds += id
                    }
                }

                val oldIdsForKey = oldMapping?.get(k).orEmpty()
                if (oldIdsForKey.isNotEmpty()) {
                    existingIds += oldIdsForKey
                    mappingObj.put(k, JSONArray(existingIds.toList()))
                }
            }

            // Keep v2.1 visibleRecordIds untouched.
            newObj.toString()
        } catch (_: Exception) {
            newPayloadJson
        }
    }
}
