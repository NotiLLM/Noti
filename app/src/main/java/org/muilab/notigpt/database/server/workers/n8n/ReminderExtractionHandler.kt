package org.muilab.notigpt.database.server.workers.n8n

import android.util.Log
import androidx.work.Data
import androidx.work.ListenableWorker
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.util.SharedPreferencesManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object ReminderExtractionHandler {

    suspend fun handle(ctx: N8nWorkerContext, inputData: Data): ListenableWorker.Result {
        val gson = Gson()

        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e("N8nWebhook", "No webhook_path for extract")
            return ctx.failure()
        }

        val userTriggered = inputData.getBoolean("user_triggered", false)

        val keysJson = inputData.getString("noti_keys_json") ?: "[]"
        val notiKeys: List<String> = try {
            gson.fromJson(keysJson, Array<String>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }

        val db = ctx.database
        val reminderRepository = ctx.reminderRepository

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

            val visible = recordDao.getActiveRecordsByKey(notiKey).sortedBy { it.time }
            val pastCnt = SharedPreferencesManager.maxPastContext
            val older = if (pastCnt > 0) {
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
                    // Don't rely on previously-extracted context; we already included older records in notiContent.
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
                    "deadlineTimestamp" to deadlineIso,
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
                for (i in 0 until arr.length()) {
                    val it = arr.getJSONObject(i)
                    val reminderId = it.optString("reminderId", it.optString("taskId"))
                    val reminderTitle = it.optString("reminderTitle", "")
                    val reminderContent = it.optString("reminderContent", it.optString("taskDescription"))
                    val deadlineMs = if (it.has("deadlineTimestamp") && !it.isNull("deadlineTimestamp")) it.optLong("deadlineTimestamp", -1L) else -1L
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
                        isTask = true,
                        isCompleted = isCompleted,
                        lastUpdateTimestamp = System.currentTimeMillis(),
                        deadlineTimestamp = deadlineMs,
                        estimatedCompletionTime = estimate,
                        associatedNotis = assocKeys.toSet(),
                        userEdited = false,
                    )

                    reminderRepository.upsert(newUnit)
                }

                // Don't touch taskExtracted/taskExtractionClaimed flags for user-triggered.
                // Clear the per-notification extraction request flag so periodic flow doesn't keep picking it up.
                drawerDao.setShouldExtractReminderByKeys(listOf(notiKey), false)
            } catch (e: Exception) {
                Log.e("N8nWebhook", "Error parsing extract response (user-triggered)", e)
                return ctx.failure()
            }

            return ctx.success()
        }

        // === Periodic flow below remains unchanged ===

        val keysToProcess: List<String> = if (notiKeys.isEmpty()) {
            val active = drawerDao.getAllActive()
            active.filter { it.shouldExtractReminder }.map { it.notiKey }
        } else {
            notiKeys
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
                "deadlineTimestamp" to deadlineIso,
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
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                val reminderId = it.optString("reminderId", it.optString("taskId"))
                val reminderTitle = it.optString("reminderTitle", "")
                val reminderContent = it.optString("reminderContent", it.optString("taskDescription"))
                val deadlineMs = if (it.has("deadlineTimestamp") && !it.isNull("deadlineTimestamp")) it.optLong("deadlineTimestamp", -1L) else -1L
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
                    isTask = true,
                    isCompleted = isCompleted,
                    lastUpdateTimestamp = System.currentTimeMillis(),
                    deadlineTimestamp = deadlineMs,
                    estimatedCompletionTime = estimate,
                    associatedNotis = assocKeys.toSet(),
                    userEdited = false,
                )

                reminderRepository.upsert(unit)
            }

            if (submittedRecordIds.isNotEmpty()) {
                db.recordDao().setClaimedRecordsExtracted(submittedRecordIds.distinct())
            }

            if (submittedKeys.isNotEmpty()) {
                drawerDao.setShouldExtractReminderByKeys(submittedKeys.distinct(), false)
            }

        } catch (e: Exception) {
            Log.e("N8nWebhook", "Error parsing extract response", e)
            db.recordDao().clearClaimedRecords(candidateRecordIds)
        }

        return ctx.success()
    }
}
