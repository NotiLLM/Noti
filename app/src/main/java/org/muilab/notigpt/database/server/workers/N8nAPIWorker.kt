package org.muilab.notigpt.database.server.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.database.server.N8nAPIClient
import org.muilab.notigpt.database.server.N8nUpdateNotificationPayload
import org.muilab.notigpt.repository.NotiRepositoryProvider
import org.muilab.notigpt.repository.TaskRepositoryProvider
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_DELETED
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.toN8nNotiActions
import org.muilab.notigpt.util.toN8nNotiRecords
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.muilab.notigpt.model.notifications.NotiRecord

class N8nAPIWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("N8nWebhook", "Running Worker")

        val parsed = N8nWorkerInput.from(inputData)
        if (parsed == null) {
            Log.w("N8nWebhook", "Unknown or invalid input, skipping")
            return Result.success()
        }

        return try {
            N8nWorkerHandlers.dispatch(this, parsed, inputData)
        } catch (e: Exception) {
            Log.e("N8nWebhook", "Error in worker", e)
            Result.failure()
        }
    }

    // Ensure these methods are declared 'internal' (not private)
    internal suspend fun updateNotification(inputData: Data): Result = withContext(Dispatchers.IO) {
        Log.d("N8nWebhook", "Update Notification")

        val n8nAPIService = N8nAPIClient.n8nAPIService
        val gson = Gson()

        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e("N8nWebhook", "No webhook_path provided")
            return@withContext Result.failure()
        }

        val notiKey = inputData.getString("noti_key") ?: ""

        val notiRepository = NotiRepositoryProvider.provideNotiRepository(applicationContext)
        val notiUnit = notiRepository.getNotiUnit(notiKey)

        if (notiUnit == null) {
            Log.e("N8nWebhook", "Notification unit not found for key: $notiKey")
            return@withContext Result.failure()
        }

        val lastSyncTime = notiUnit.lastUpdateTime
        val pastSummary = notiUnit.summary
        val db = AppDatabase.getInstance(applicationContext)
        val notiRecords = db.recordDao().getNotSyncedRecordsByKey(notiKey, lastSyncTime)
        val notiActions = notiRepository.getNotSyncedNotiActions(notiKey, lastSyncTime)

        if (notiRecords.isEmpty()) {
            Log.d("N8nWebhook", "No new notifications to sync.")
            return@withContext Result.success()
        }

        val payload = N8nUpdateNotificationPayload(
            userId = SharedPreferencesManager.userId,
            noti_contents_str = gson.toJson(toN8nNotiRecords(notiUnit, notiRecords)),
            noti_actions_str = gson.toJson(toN8nNotiActions(notiActions)),
            noti_past_summary = pastSummary,
        )

        val json = gson.toJson(payload)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toRequestBody(mediaType)

        val response = try {
            n8nAPIService.postToWebhook(webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e("N8nWebhook", "Network exception posting updateNotification", t)
            return@withContext Result.retry()
        }

        if (!response.isSuccessful) {
            Log.e("N8nWebhook", "API call unsuccessful: ${response.code()}")
            return@withContext when {
                response.code() == 429 -> Result.retry()
                response.code() in 500..599 -> Result.retry()
                else -> Result.failure()
            }
        }

        val jsonString = response.body()?.string()
        Log.d("N8nWebhook", "Response: $jsonString")

        // --- Below is your original Dify parsing logic.
        //     You can keep or adapt it depending on what your n8n workflow returns. ---

        try {
            if (jsonString.isNullOrEmpty()) {
                return@withContext Result.success()
            }

            val root = JSONObject(jsonString)

            // If your n8n workflow returns the same "data" / "outputs" format, this will still work.
            val data = root.optJSONObject("data") ?: return@withContext Result.success()
            val status = data.optString("status") ?: ""

            if (status != "succeeded") {
                return@withContext Result.success()
            }

            val jsonOutputs = data.optJSONObject("outputs") ?: return@withContext Result.success()

            if (jsonOutputs.has("retry")) {
                // The backend explicitly requested retry.
                return@withContext Result.retry()
            }

            val featureStrRaw = jsonOutputs
                .getString("features")
                .removePrefix("```json\n")
                .removeSuffix("\n```")
            val features = JSONObject(featureStrRaw)

            val evaluationStrRaw = jsonOutputs
                .getString("evaluations")
                .removePrefix("```json\n")
                .removeSuffix("\n```")
            val evaluations = JSONObject(evaluationStrRaw)

            Log.d("N8nWebhook", "Features: $features")
            Log.d("N8nWebhook", "Evaluations: $evaluations")

            val explanationSB = StringBuilder()

            val evaluationsWithScores = listOf(
                "sort-score-before-notice",
                "sort-score-after-notice",
                "sort-score-after-pinned",
                "sort-score-after-acted"
            )

            evaluationsWithScores.forEach { attr ->
                evaluations.optJSONObject(attr)?.let {
                    explanationSB.append(
                        "${attr.replace("-", " ").replaceFirstChar { c -> c.uppercase() }} (${it.getDouble("score")})\n${it.getString("reason")}\n"
                    )
                }
            }

            val assignedCategory = evaluations.optJSONObject("assigned-category")
            assignedCategory?.let {
                val newCategory = it.getString("category")
                explanationSB.append("[Assigned Category] $newCategory\n${it.getString("reason")}\n")
                notiUnit.category = when {
                    SharedPreferencesManager.autoArchive && newCategory == NOTI_CATEGORY_ARCHIVE -> NOTI_CATEGORY_ARCHIVE
                    SharedPreferencesManager.autoDelete && newCategory == NOTI_CATEGORY_DELETED -> NOTI_CATEGORY_DELETED
                    else -> NOTI_CATEGORY_GENERAL
                }
            }

            val textSummary = evaluations.optJSONObject("text-summary")
            textSummary?.let {
                explanationSB.append("[Text Summary]\n${it.getString("reason")}\n")
                notiUnit.summary = it.getString("summary")
            }

            val featuresWithScores = listOf(
                "necessity",
                "time-relevance",
                "sender-attractiveness",
                "content-attractiveness",
                "urgency",
                "importance",
            )

            featuresWithScores.forEach { attr ->
                features.optJSONObject(attr)?.let {
                    explanationSB.append(
                        "${attr.replace("-", " ").replaceFirstChar { c -> c.uppercase() }} (${it.getDouble("score")})\n${it.getString("reason")}\n"
                    )
                }
            }

            val tasks = features.optJSONArray("tasks")
            tasks?.let {
                explanationSB.append("Tasks:\n")
                for (i in 0 until it.length()) {
                    val task = it.getJSONObject(i)
                    explanationSB.append("${i + 1}: ${task.getString("task")}, due ${task.getString("deadline")}\n")
                }
                explanationSB.append("\n")
            }

            val keyTimings = features.optJSONArray("key-timings")
            keyTimings?.let {
                explanationSB.append("Key Timings:\n")
                for (i in 0 until it.length()) {
                    val timing = it.getJSONObject(i)
                    explanationSB.append("${timing.getString("key-timing")}: ${timing.getString("event")}\n")
                }
                explanationSB.append("\n")
            }

            notiUnit.sortScore =
                evaluations.optJSONObject("sort-score-before-notice")?.optDouble("score") ?: 0.0
            notiUnit.explanation = explanationSB.toString()

            notiRepository.updateNotiUnit(notiUnit)

            Log.d("N8nWebhook", "Notification ${notiRecords.last().getDisplayedTitle(notiUnit.isPeople)} updated.")
        } catch (_: Exception) {
            Log.e("N8nWebhook", "Error parsing response")
            // You can decide to fail or still succeed depending on what critical the parsing is.
        }

        delay(5000)
        Result.success()
    }

    internal suspend fun performTaskScan(inputData: Data): Result = withContext(Dispatchers.IO) {
        val n8nAPIService = N8nAPIClient.n8nAPIService
        val gson = Gson()

        Log.d("N8nWebhook", "Performing Task Scan")

        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e("N8nWebhook", "No webhook_path for scan")
            return@withContext Result.failure()
        }
        val notiKey = inputData.getString("noti_key") ?: return@withContext Result.failure()

        val notiRepository = NotiRepositoryProvider.provideNotiRepository(applicationContext)
        val notiUnit = notiRepository.getNotiUnit(notiKey) ?: return@withContext Result.failure()

        val db = AppDatabase.getInstance(applicationContext)
        // Use new query for efficiency
        // ensure noti_contents are ordered by time ascending (oldest -> newest)
        val notiRecords = db.recordDao().getUnscannedRecordsByKey(notiKey).sortedBy { it.time }

        if (notiRecords.isEmpty()) return@withContext Result.success()

        // Build payload for scan: noti_contents as list of formatted strings, past_context
        val notiContentList = notiRecords.map { rec -> formatNotiRecord(rec, notiUnit.isPeople) }
        val pastCount = SharedPreferencesManager.maxPastContext
        // getLastScannedRecordsByKey returns most recent first (DESC); sort ascending so latest is last
        val pastRecords = if (pastCount > 0) db.recordDao().getLastScannedRecordsByKey(notiKey, pastCount).sortedBy { it.time } else emptyList()
        val pastContextList = pastRecords.map { formatNotiRecord(it, notiUnit.isPeople) }

        val lastRecord = notiRecords.lastOrNull()
        val lastTitle = lastRecord?.title ?: ""

        val notiOverallTitle = if (lastRecord != null) {
            when {
                lastRecord.extraConversationTitle != "null" -> lastRecord.extraConversationTitle
                lastTitle != "null" -> lastTitle
                lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                else -> ""
            }
        } else {
            ""
        }

        val notiSecondOverallTitle = if (lastRecord != null) {
            when {
                lastRecord.extraConversationTitle != "null" && lastTitle != "null" -> lastTitle
                lastRecord.extraConversationTitle == "null" && lastTitle != "null" && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                lastRecord.extraConversationTitle == "null" && lastTitle != "null" -> ""
                else -> ""
            }
        } else {
            ""
        }

        val payload = mapOf(
            "userId" to SharedPreferencesManager.userId,
            "notiKey" to notiKey,
            "appName" to notiUnit.appName,
            "appCategory" to notiUnit.category,
            "overallTitle" to notiOverallTitle,
            "secondOverallTitle" to notiSecondOverallTitle,
            "notiContent" to notiContentList,
            "pastContext" to pastContextList
        )
        val json = gson.toJson(payload)
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val response = try {
            n8nAPIService.postToWebhook(webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e("N8nWebhook", "TaskScan network exception", t)
            return@withContext Result.retry()
        }
        if (!response.isSuccessful) return@withContext when {
            response.code() == 429 -> Result.retry()
            response.code() in 500..599 -> Result.retry()
            else -> Result.failure()
        }

        val bodyStr = response.body()?.string() ?: return@withContext Result.success()
        try {
            val root = JSONObject(bodyStr)
            val hasGenuine = root.optBoolean("hasGenuineTask", false)

            // Update DB
            notiRepository.setHasGenuineTask(notiKey, hasGenuine)
            // Mark detection scanned for these records
            db.recordDao().setRecordsTaskScannedByIds(notiRecords.map { it.notiRecordId })
        } catch (_: Exception) {
            Log.e("N8nWebhook", "Error parsing scan response")
        }

        Result.success()
    }

    internal suspend fun performTaskExtraction(inputData: Data): Result = withContext(Dispatchers.IO) {
        val n8nAPIService = N8nAPIClient.n8nAPIService
        val gson = Gson()

        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e("N8nWebhook", "No webhook_path for extract")
            return@withContext Result.failure()
        }

        val keysJson = inputData.getString("noti_keys_json") ?: "[]"
        val notiKeys: List<String> = try {
            gson.fromJson(keysJson, Array<String>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }

        val db = AppDatabase.getInstance(applicationContext)
        val notiRepository = NotiRepositoryProvider.provideNotiRepository(applicationContext)
        val taskRepository = TaskRepositoryProvider.provideTaskRepository(applicationContext)

        // For each notiKey, fetch candidate notiRecordIds where taskExtracted == false
        val drawerDao = db.drawerDao()

        val keysToProcess: List<String> = if (notiKeys.isEmpty()) {
            // Query DB for visible noti units with shouldExtractTask == true
            val visible = drawerDao.getAllVisible()
            visible.filter { it.shouldExtractTask }.map { it.notiKey }
        } else {
            notiKeys
        }

        // Reclaim stale claims first (records claimed long ago but never finalized)
        val nowTs = System.currentTimeMillis()
        val staleMs = 5 * 60 * 1000L // 5 minutes stale threshold; reclaim claims older than this
        db.recordDao().reclaimStaleClaims(nowTs, staleMs)

        // Collect candidate record ids per key (only unclaimed & unextracted)
        val candidateRecordIds = keysToProcess.flatMap { key ->
            db.recordDao().getUnclaimedUnextractedByKey(key).map { it.notiRecordId }
        }.distinct()

        if (candidateRecordIds.isEmpty()) {
            Log.d("N8nWebhook", "No candidate records to extract")
            return@withContext Result.success()
        }

        // Attempt to claim candidate records atomically, with a timestamp. Returns number of rows updated.
        val claimTs = System.currentTimeMillis()
        val claimedCount = if (candidateRecordIds.isNotEmpty()) db.recordDao().claimRecordsForExtractionWithTs(candidateRecordIds, claimTs) else 0
        Log.d("N8nWebhook", "Attempted to claim ${candidateRecordIds.size} records, actually claimed=$claimedCount (ts=$claimTs)")

        if (claimedCount <= 0) {
            Log.d("N8nWebhook", "No records could be claimed; another worker likely claimed them")
            return@withContext Result.success()
        }

        // Fetch the claimed records (only those claimed and still unextracted)
        val claimedRecords = db.recordDao().getClaimedRecordsByIds(candidateRecordIds).sortedBy { it.time }

        if (claimedRecords.isEmpty()) {
            Log.d("N8nWebhook", "No claimed records returned after claim; aborting")
            return@withContext Result.success()
        }

        // Group claimed records by notiKey so we can build payload per noti
        val claimedByKey: Map<String, List<NotiRecord>> = claimedRecords.groupBy { it.notiKey }

        // Track ids/keys we actually submit so we can reliably update DB afterwards.
        val submittedRecordIds = mutableListOf<String>()
        val submittedKeys = mutableListOf<String>()

        val notisPayload = claimedByKey.map { (key, records) ->
            val unit = notiRepository.getNotiUnit(key)
            if (unit == null) return@map null

            submittedKeys += key
            submittedRecordIds += records.map { it.notiRecordId }

            val contents = records.sortedBy { it.time }.map { r -> formatNotiRecord(r, unit.isPeople) }
            val pastCnt = SharedPreferencesManager.maxPastContext
            val pastRecs = if (pastCnt > 0) db.recordDao().getLastExtractedRecordsByKey(key, pastCnt).sortedBy { it.time } else emptyList()
            val pastCtx = pastRecs.map { formatNotiRecord(it, unit.isPeople) }

            val lastRecord = records.lastOrNull()
            val lastTitle = lastRecord?.title ?: ""

            val notiOverallTitle = if (lastRecord != null) {
                when {
                    lastRecord.extraConversationTitle != "null" -> lastRecord.extraConversationTitle
                    lastTitle != "null" -> lastTitle
                    lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                    else -> ""
                }
            } else {
                ""
            }

            val notiSecondOverallTitle = if (lastRecord != null) {
                when {
                    lastRecord.extraConversationTitle != "null" && lastTitle != "null" -> lastTitle
                    lastRecord.extraConversationTitle == "null" && lastTitle != "null" && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                    lastRecord.extraConversationTitle == "null" && lastTitle != "null" -> ""
                    else -> ""
                }
            } else {
                ""
            }

            mapOf(
                "notiKey" to key,
                "appName" to unit.appName,
                "appCategory" to unit.category,
                "overallTitle" to notiOverallTitle,
                "secondOverallTitle" to notiSecondOverallTitle,
                "notiContent" to contents,
                "pastContext" to pastCtx,
                "hasTask" to unit.hasGenuineTask,
                "isPinned" to unit.isPinned,
                "recordIds" to records.map { it.notiRecordId }
            )
        }.filterNotNull()

        if (notisPayload.isEmpty()) {
            // Nothing to send (units may have been missing or no payload); release claims
            db.recordDao().clearClaimedRecords(candidateRecordIds)
            return@withContext Result.success()
        }

        // Include current uncompleted (visible) tasks in the payload for context to the extractor
        val visibleTasksList = try {
            taskRepository.observeVisibleTasks().first()
        } catch (_: Exception) {
            emptyList()
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val tasksForPayload = visibleTasksList.filter { !it.isCompleted }.map { t ->
            val deadlineIso = if (t.deadlineTimestamp > 0L) sdf.format(Date(t.deadlineTimestamp)) else -1L
            mapOf(
                "taskId" to t.taskId,
                "taskDescription" to t.taskDescription,
                // deadline as ISO string with timezone when present, otherwise -1
                "deadlineTimestamp" to deadlineIso,
                "estimatedCompletionMinutes" to t.estimatedCompletionTime,
                "associatedNotis" to t.associatedNotis.toList(),
                "userEdited" to t.userEdited,
                "isCompleted" to t.isCompleted
            )
        }

        val payload = mapOf(
            "userId" to SharedPreferencesManager.userId,
            "language" to Locale.getDefault().toLanguageTag(),
            "currentTime" to SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.getDefault()
            ).format(
                Date()
            ),
            "notis" to notisPayload,
            "currentTasks" to tasksForPayload
        )

        val jsonPayload = gson.toJson(payload)
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        Log.d("N8nWebhook", "JSON Payload (claimed): $jsonPayload")

        val response = try {
            n8nAPIService.postToWebhook(webhookPath, requestBody)
        } catch (t: Throwable) {
            Log.e("N8nWebhook", "TaskExtraction network exception", t)
            // Clear claims so records can be retried later
            db.recordDao().clearClaimedRecords(candidateRecordIds)
            return@withContext Result.retry()
        }

        if (!response.isSuccessful) {
            // Clear claims so records can be retried later
            db.recordDao().clearClaimedRecords(candidateRecordIds)
            return@withContext when {
                response.code() == 429 -> Result.retry()
                response.code() in 500..599 -> Result.retry()
                else -> Result.failure()
            }
        }

        val bodyStr = response.body()?.string() ?: return@withContext Result.success()

        Log.d("N8nWebhook", "Extraction Response: $bodyStr")

        try {
            // parse returned tasks and upsert
            val arr = JSONArray(bodyStr)
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                val taskId = it.optString("taskId")
                val taskDescription = it.optString("taskDescription")
                val deadlineMs = if (it.has("deadlineTimestamp") && !it.isNull("deadlineTimestamp")) it.optLong("deadlineTimestamp", -1L) else -1L
                val estimate = it.optLong("estimatedCompletionTime", 0L)
                val assocKeys = mutableSetOf<String>()
                val assoc = it.optJSONArray("associatedNotis")
                if (assoc != null) {
                    for (j in 0 until assoc.length()) {
                        assocKeys.add(assoc.optString(j))
                    }
                }
                val isCompleted = it.optBoolean("isCompleted", false)
                val taskUnit = org.muilab.notigpt.model.features.TaskUnit(
                    taskId = taskId,
                    isCompleted = isCompleted,
                    isVisible = true,
                    taskDescription = taskDescription,
                    deadlineTimestamp = deadlineMs,
                    estimatedCompletionTime = estimate,
                    associatedNotis = assocKeys.toSet(),
                    userEdited = false
                )
                taskRepository.upsert(taskUnit)
            }

            // Collect all recordIds we actually sent and mark them extracted atomically
            if (submittedRecordIds.isNotEmpty()) {
                db.recordDao().setClaimedRecordsExtracted(submittedRecordIds.distinct())
            }

            // Also clear shouldExtractTask flag for the submitted noti units
            if (submittedKeys.isNotEmpty()) {
                drawerDao.setShouldExtractTaskByKeys(submittedKeys.distinct(), false)
            }

        } catch (e: Exception) {
            Log.e("N8nWebhook", "Error parsing extract response", e)
            // On parsing error, clear claimed records for future retry
            db.recordDao().clearClaimedRecords(candidateRecordIds)
        }

        Result.success()
    }

    internal suspend fun postNotificationAction(inputData: Data): Result = withContext(Dispatchers.IO) {
        val n8nAPIService = N8nAPIClient.n8nAPIService
        val gson = Gson()

        val webhookPath = inputData.getString("webhook_path") ?: run {
            Log.e("N8nWebhook", "No webhook_path for postNotificationAction")
            return@withContext Result.failure()
        }

        val notiKey = inputData.getString("noti_key") ?: return@withContext Result.failure()
        val actionType = inputData.getString("action_type") ?: return@withContext Result.failure()
        val actionTime = inputData.getLong("action_time", -1L)

        val payload = mapOf(
            "userId" to SharedPreferencesManager.userId,
            "notiKey" to notiKey,
            "actionType" to actionType,
            "actionTime" to actionTime,
        )

        val json = gson.toJson(payload)
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        return@withContext try {
            val response = n8nAPIService.postToWebhook(webhookPath, requestBody)
            if (!response.isSuccessful) {
                Log.e("N8nWebhook", "postNotificationAction failed: ${response.code()}")
                // Prefer retry for transient server failures.
                if (response.code() in 500..599 || response.code() == 429) Result.retry() else Result.failure()
            } else {
                Result.success()
            }
        } catch (t: Throwable) {
            Log.e("N8nWebhook", "postNotificationAction exception", t)
            Result.retry()
        }
    }

    // If you later want to support postNotificationAction via n8n, you can adapt the commented
    // method below similarly, using N8nPostNotificationActionPayload and webhook_path.

    private fun formatNotiRecord(record: NotiRecord, isPeople: Boolean): Map<String, Any> {
         // Absolute time: use ISO-like timestamp (Locale.US for stable ASCII formatting)
         val absTime = SimpleDateFormat(
             "yyyy-MM-dd'T'HH:mm:ssXXX",
             Locale.getDefault()
         ).format(
             Date(record.time)
         )
          val title = record.getDisplayedTitle(isPeople)
          val content = record.content
          val rel = relativeTime(record.time)
          return mapOf(
              "abs_time" to absTime,
              "title" to title,
              "content" to content,
              "rel_time" to rel
          )
      }

    private fun relativeTime(timeMs: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timeMs
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            seconds >= 0 -> "${seconds}s ago"
            else -> "0s ago"
        }
    }
}
