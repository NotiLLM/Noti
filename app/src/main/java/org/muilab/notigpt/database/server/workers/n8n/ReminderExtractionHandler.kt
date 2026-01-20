package org.muilab.notigpt.database.server.workers.n8n

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.database.server.esm.enqueueEsmDelivery
import org.muilab.notigpt.domain.esm.EsmConfig
import org.muilab.notigpt.domain.esm.EsmScheduling
import org.muilab.notigpt.domain.esm.EsmTriggerPolicy
import org.muilab.notigpt.domain.esm.EsmTriggerTypes
import org.muilab.notigpt.domain.esm.EsmSnapshotStatuses
import org.muilab.notigpt.model.features.ReminderExtractionSnapshot
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.repository.EsmRepository
import org.muilab.notigpt.util.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.time.Instant

internal object ReminderExtractionHandler {

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
                    status = EsmSnapshotStatuses.STAGED,
                    reminderId = null,
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
                    "hasMemo" to unit.hasMemo
                )
            )

            val currentReminders: List<ReminderUnit> = try {
                reminderRepository.observeAll().first()
            } catch (_: Exception) {
                emptyList<ReminderUnit>()
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            val remindersForPayload = currentReminders.map { r ->
                val deadlineIso = if (r.deadlineTimestamp > 0L) sdf.format(Date(r.deadlineTimestamp)) else -1L
                mapOf(
                    "reminderId" to r.reminderId,
                    "reminderTitle" to r.reminderTitle,
                    "reminderContent" to r.reminderContent,
                    "isTask" to r.isTask,
                    "deadlineTimeString" to deadlineIso,
                    "estimatedCompletionMinutes" to r.estimatedCompletionTime,
                    "associatedNotis" to r.associatedNotis.toList(),
                    "userEdited" to r.userEdited,
                    "isCompleted" to r.isCompleted
                )
            }

            val payload = mapOf(
                "userId" to SharedPreferencesManager.userId,
                "language" to Locale.getDefault().toLanguageTag(),
                "timezone" to TimeZone.getDefault().displayName,
                "currentTime" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date()),
                "userTriggered" to true,
                "notis" to notisPayload,
                "currentReminders" to remindersForPayload
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
                    // Track the first reminderId we actually upsert (used for snapshot link + ESM binding).
                    var firstCreatedReminderId: String? = null

                    for (i in 0 until arr.length()) {
                        val it = arr.getJSONObject(i)
                        val reminderId = it.optString("reminderId", it.optString("taskId"))
                        if (firstCreatedReminderId == null && reminderId.isNotBlank()) {
                            firstCreatedReminderId = reminderId
                        }
                        val reminderTitle = it.optString("reminderTitle", "")
                        val reminderContent = it.optString("reminderContent", it.optString("taskDescription"))
                        val isTask = it.optBoolean("isTask", true)
                        val deadlineMs = isoToUnixMillis(it.optString("deadlineTimeString", "-1"))
                        val estimate = it.optLong("estimatedCompletionTime", it.optLong("estimatedCompletionMinutes", 0L))
                        val assocKeys = mutableSetOf<String>()
                        val assoc = it.optJSONArray("associatedNotis")
                        if (assoc != null) {
                            for (j in 0 until assoc.length()) {
                                assocKeys.add(assoc.optString(j))
                            }
                        }
                        val isCompleted = it.optBoolean("isCompleted", false)

                        val newUnit = ReminderUnit(
                            reminderId = reminderId,
                            reminderTitle = reminderTitle,
                            reminderContent = reminderContent,
                            isTask = isTask,
                            isCompleted = isCompleted,
                            lastUpdateTimestamp = System.currentTimeMillis(),
                            deadlineTimestamp = deadlineMs,
                            estimatedCompletionTime = estimate,
                            associatedNotis = assocKeys.toSet(),
                            extractionSnapshotId = snapshotId,
                            origin = "llm_manual_extraction",
                            humanEditCount = 0,
                            deletedAtMs = null,
                            userEdited = false,
                        )

                        reminderRepository.upsert(newUnit)
                    }

                    // Mark snapshot kept and link to (first) reminderId.
                    val firstObj = arr.optJSONObject(0)
                    val firstReminderId = firstObj?.optString("reminderId").takeIf { !it.isNullOrBlank() }
                        ?: firstObj?.optString("taskId")
                    snapshotDao.updateSnapshotStatusAndReminderId(snapshotId, EsmSnapshotStatuses.KEPT, firstReminderId)

                    // === Trigger A: create an ESM after a user-triggered extraction actually created a reminder ===
                    val bindReminderId = firstCreatedReminderId ?: firstReminderId
                    if (!bindReminderId.isNullOrBlank()) {
                        try {
                            val esmRepo = EsmRepository(ctx.appContext)
                            if (!esmRepo.hasAnyInstanceForReminder(bindReminderId)) {
                                val requestedDelay = esmRepo.computeTriggerAbRequestedDelayMs(EsmConfig.TRIGGER_A_AVAILABLE_DELAY_MS)
                                if (requestedDelay <= EsmConfig.TRIGGER_AB_RECENT_WINDOW_MS) {
                                    val inst = esmRepo.createEsmForSnapshot(
                                        reminderId = bindReminderId,
                                        snapshotId = snapshotId,
                                        triggerType = EsmTriggerTypes.A_USER_TRIGGERED_EXTRACTION,
                                        // If it's been >= 1h since last ESM, deliver immediately.
                                        availableDelayMs = requestedDelay,
                                    )
                                    enqueueEsmDelivery(
                                        ctx.appContext,
                                        inst.instanceId,
                                        requestedDelay
                                    )
                                }
                            }
                        } catch (_: IllegalStateException) {
                            // ESM already exists for this reminder; no-op.
                        } catch (e: Exception) {
                            Log.w("N8nWebhook", "Failed to create/enqueue Trigger A ESM", e)
                        }
                    }
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
                status = EsmSnapshotStatuses.STAGED,
                reminderId = null,
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
                "hasMemo" to unit.hasMemo
            )
        }.filterNotNull()

        if (notisPayload.isEmpty()) {
            db.recordDao().clearClaimedRecords(candidateRecordIds)
            return ctx.success()
        }

        // Include current reminders (tasks + memos) as context for extraction.
        val currentReminders: List<ReminderUnit> = try {
            reminderRepository.observeAll().first()
        } catch (_: Exception) {
            emptyList<ReminderUnit>()
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val remindersForPayload = currentReminders.map { r ->
            val deadlineIso = if (r.deadlineTimestamp > 0L) sdf.format(Date(r.deadlineTimestamp)) else -1L
            mapOf(
                "reminderId" to r.reminderId,
                "reminderTitle" to r.reminderTitle,
                "reminderContent" to r.reminderContent,
                "isTask" to r.isTask,
                "deadlineTimeString" to deadlineIso,
                "estimatedCompletionMinutes" to r.estimatedCompletionTime,
                "associatedNotis" to r.associatedNotis.toList(),
                "userEdited" to r.userEdited,
                "isCompleted" to r.isCompleted
            )
        }

        val payload = mapOf(
            "userId" to SharedPreferencesManager.userId,
            "language" to Locale.getDefault().toLanguageTag(),
            "currentTime" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date()),
            "notis" to notisPayload,
            "currentReminders" to remindersForPayload
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
                    val reminderId = it.optString("reminderId", it.optString("taskId"))
                    if (firstCreatedReminderId == null && reminderId.isNotBlank()) {
                        firstCreatedReminderId = reminderId
                    }
                    val reminderTitle = it.optString("reminderTitle", "")
                    val reminderContent = it.optString("reminderContent", it.optString("taskDescription"))
                    val isTask = it.optBoolean("isTask", true)
                    val deadlineMs = isoToUnixMillis(it.optString("deadlineTimeString", "-1"))
                    val estimate = it.optLong("estimatedCompletionTime", it.optLong("estimatedCompletionMinutes", 0L))
                    val assocKeys = mutableSetOf<String>()
                    val assoc = it.optJSONArray("associatedNotis")
                    if (assoc != null) {
                        for (j in 0 until assoc.length()) {
                            assocKeys.add(assoc.optString(j))
                        }
                    }
                    val isCompleted = it.optBoolean("isCompleted", false)

                    val unit = ReminderUnit(
                        reminderId = reminderId,
                        reminderTitle = reminderTitle,
                        reminderContent = reminderContent,
                        isTask = isTask,
                        isCompleted = isCompleted,
                        lastUpdateTimestamp = System.currentTimeMillis(),
                        deadlineTimestamp = deadlineMs,
                        estimatedCompletionTime = estimate,
                        associatedNotis = assocKeys.toSet(),
                        extractionSnapshotId = snapshotId,
                        origin = "llm_auto_extraction",
                        humanEditCount = 0,
                        deletedAtMs = null,
                        userEdited = false,
                    )

                    reminderRepository.upsert(unit)
                }

                drawerDao.setShouldExtractReminderByKeys(keysToProcess, false)

                val firstObj = arr.optJSONObject(0)
                val firstReminderId = firstObj?.optString("reminderId").takeIf { !it.isNullOrBlank() }
                    ?: firstObj?.optString("taskId")
                snapshotDao.updateSnapshotStatusAndReminderId(snapshotId, EsmSnapshotStatuses.KEPT, firstReminderId)

                // NOTE: Trigger C (auto-generated ESM) is scheduled by a separate periodic/timed check.
                // We intentionally do NOT create Trigger C here, because it should fire once two hours
                // have passed since the last ESM was answered or shown, independent of extraction.
            }

        } catch (e: Exception) {
            Log.e("N8nWebhook", "Error parsing extract response", e)
            snapshotDao.deleteSnapshot(snapshotId)
            db.recordDao().clearClaimedRecords(candidateRecordIds)
        }

        return ctx.success()
    }

    fun isoToUnixMillis(iso: String): Long {
        if (iso == "-1") return -1L  // your "no deadline" sentinel

        // Most common: has an offset like +08:00 or ends with Z
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (e: Exception) {
            // Fallback: sometimes it's a full zone format or slightly different ISO variant
            ZonedDateTime.parse(iso).toInstant().toEpochMilli()
        }
    }

    fun unixMillisToIsoLocalCompat(ms: Long): String {
        if (ms < 0) return "-1"
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(ms))
    }
}
