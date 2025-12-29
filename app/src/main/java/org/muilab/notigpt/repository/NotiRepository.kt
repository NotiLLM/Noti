package org.muilab.notigpt.repository

import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.database.server.enqueueTaskScan
import org.muilab.notigpt.database.server.enqueueTaskExtraction
import org.muilab.notigpt.database.server.enqueueDelayedTaskExtraction
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.database.room.NotiActionDao
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiRecordDao
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiUnitWithRecords
import org.muilab.notigpt.util.Constants.Companion.MAX_EXPIRED_RECORDS_PER_KEY
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_SAVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_RECORD_EXPIRE_TIME_MS
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.getAppCategoryByAppName
import kotlin.collections.map

class NotiRepository(
    private val appContext: Context,
    private val db: AppDatabase, // Add database instance
    private val notiDrawerDao: NotiDrawerDao,
    private val notiActionDao: NotiActionDao,
    private val notiRecordDao: NotiRecordDao
) {

    // In-memory maps to track pending counters and debounce jobs per notiKey
    private val detectionCounters = mutableMapOf<String, Int>()
    private val detectionJobs = mutableMapOf<String, Job?>()
    private val extractionCounters = mutableMapOf<String, Int>()

    private val scope = CoroutineScope(Dispatchers.IO)

    // Helper function to avoid code duplication
    private fun mapToDisplayUnit(listWithRecords: List<NotiUnitWithRecords>): List<NotiDisplayUnit> {
        return listWithRecords.map { unitWithRecords ->
            val mappedNotiRecords = unitWithRecords.notiRecords.map { visibleRecord ->
                NotiRecord(
                    // KEYS
                    notiRecordId = visibleRecord.notiRecordId,
                    notiKey = visibleRecord.notiKey,

                    // TIME RELATED
                    whenTime = visibleRecord.whenTime,
                    postTime = visibleRecord.postTime,

                    // TITLE RELATED
                    person = visibleRecord.person,
                    extraTitle = visibleRecord.extraTitle,
                    extraBigTitle = visibleRecord.extraBigTitle,
                    extraConversationTitle = visibleRecord.extraConversationTitle,

                    // CONTENT RELATED
                    extraBigText = visibleRecord.extraBigText,
                    extraText = visibleRecord.extraText,
                    extraTextLines = visibleRecord.extraTextLines,
                    extraSummaryText = visibleRecord.extraSummaryText,
                    extraInfoText = visibleRecord.extraInfoText,
                    extraSubText = visibleRecord.extraSubText,

                    // STATUS
                    isRead = visibleRecord.isRead,
                    isVisible = visibleRecord.isVisible
                )
            }
            NotiDisplayUnit(unitWithRecords.notiUnit, mappedNotiRecords.sortedBy { it.time })
        }
    }

    suspend fun removeExpiredNotiRecords() {
        val expireTimestamp = System.currentTimeMillis() - NOTI_RECORD_EXPIRE_TIME_MS
        notiRecordDao.removeExpiredReadRecords(
            expireTimestamp,
            MAX_EXPIRED_RECORDS_PER_KEY
        )
    }

    // NEW REPO METHOD 1
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getManuallySorted(
        category: Flow<String>,
        appCategory: Flow<String>,
        isAppCategoryView: Flow<Boolean>
    ): Flow<List<NotiDisplayUnit>> {
        return combine(category, appCategory, isAppCategoryView) { cat, appCat, isAppView ->
            Triple(cat, appCat, isAppView)
        }.flatMapLatest { (cat, appCat, isAppView) ->
            notiDrawerDao.getManuallySortedNotifications(cat, appCat, isAppView)
                .map { mapToDisplayUnit(it) }
        }
    }

    // NEW REPO METHOD 2
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAutoSorted(
        category: Flow<String>,
        appCategory: Flow<String>,
        isAppCategoryView: Flow<Boolean>
    ): Flow<List<NotiDisplayUnit>> {
        return combine(category, appCategory, isAppCategoryView) { cat, appCat, isAppView ->
            Triple(cat, appCat, isAppView)
        }.flatMapLatest { (cat, appCat, isAppView) ->
            // Prepare flows
            val fastFlow = notiDrawerDao.getAutoSortedNotificationsNoRelationLimited(cat, appCat, isAppView, 50)
            val fullFlow = notiDrawerDao.getAutoSortedNotificationsNoRelation(cat, appCat, isAppView)

            // Build a flow that first emits a fast mapped snapshot, then continues emitting mapped fullFlow updates.
            flow {
                // Fetch first fast snapshot (suspend until available)
                val units = fastFlow.first()
                val tStart = System.currentTimeMillis()
                Log.d("NotiRepoPerf", "Fast AutoSorted snapshot: parentUnits=${units.size}")

                // Emit a parent-only snapshot immediately so the UI can react (hide spinner)
                val parentOnly = units.map { unit -> NotiDisplayUnit(unit, emptyList()) }
                emit(parentOnly)

                val keys = units.map { it.notiKey }
                val tAfterParents = System.currentTimeMillis()
                Log.d("NotiRepoPerf", "Fast parent query time: ${tAfterParents - tStart} ms; fetching records for ${keys.size} keys")

                val latestRecords = if (keys.isNotEmpty()) {
                    val tFetchStart = System.currentTimeMillis()
                    val recs = fetchLatestRecordsConcurrently(keys, 3)
                    val tFetchEnd = System.currentTimeMillis()
                    Log.d("NotiRepoPerf", "Fast fetched latestRecords=${recs.size} in ${tFetchEnd - tFetchStart} ms")
                    recs
                } else emptyList()

                val tAfterFetch = System.currentTimeMillis()
                val grouped = latestRecords.groupBy { it.notiKey }
                val mapped = units.map { unit ->
                    val recs = grouped[unit.notiKey]?.sortedBy { it.time } ?: emptyList()
                    NotiDisplayUnit(unit, recs)
                }
                // Log mapping summary for debugging (sample up to 10 units)
                mapped.take(10).forEach { ndu ->
                    Log.d("NotiRepoDebug", "fast mapped unit key=${ndu.notiUnit.notiKey} records=${ndu.notiRecords.size}")
                }
                val tEnd = System.currentTimeMillis()
                Log.d("NotiRepoPerf", "Fast mapping time: ${tEnd - tAfterFetch} ms; total fast processing: ${tEnd - tStart} ms")

                // Emit the mapped snapshot with records
                emit(mapped)

                // Merge two streams: (A) live updates for the initial fast keys, and (B) the fullFlow mapped stream
                // Using merge avoids blocking: both streams can emit independently and the UI will receive updates.
                val initialKeysFlow = if (keys.isNotEmpty()) {
                    notiRecordDao.getVisibleRecordsFlowByKeys(keys).map { recs ->
                        val groupedLive = recs.groupBy { it.notiKey }
                        val liveMapped = units.map { unit ->
                            val recsForUnit = groupedLive[unit.notiKey]?.sortedBy { it.time } ?: emptyList()
                            NotiDisplayUnit(unit, recsForUnit)
                        }
                        liveMapped.take(10).forEach { ndu ->
                            Log.d("NotiRepoDebug", "fast live mapped unit key=${ndu.notiUnit.notiKey} records=${ndu.notiRecords.size}")
                        }
                        liveMapped
                    }
                } else {
                    kotlinx.coroutines.flow.emptyFlow()
                }

                val fullMappedStream = fullFlow.flatMapLatest { fullUnits ->
                    flow {
                        val tFullStart = System.currentTimeMillis()
                        Log.d("NotiRepoPerf", "Full AutoSorted emission: parentUnits=${fullUnits.size}")

                        // Emit parent-only snapshot immediately for full emission
                        val parentOnlyFull = fullUnits.map { unit -> NotiDisplayUnit(unit, emptyList()) }
                        emit(parentOnlyFull)

                        val fullKeys = fullUnits.map { it.notiKey }
                        val tAfterParentsFull = System.currentTimeMillis()
                        Log.d("NotiRepoPerf", "Full parent query time: ${tAfterParentsFull - tFullStart} ms; fetching records for ${fullKeys.size} keys")

                        val fullLatestRecords = if (fullKeys.isNotEmpty()) {
                            val tFetchStart = System.currentTimeMillis()
                            // Use Int.MAX_VALUE to indicate 'no artificial upper limit' — fetch all visible records per key.
                            val recs = fetchLatestRecordsConcurrently(fullKeys, Int.MAX_VALUE)
                            val tFetchEnd = System.currentTimeMillis()
                            Log.d("NotiRepoPerf", "Full fetched latestRecords=${recs.size} in ${tFetchEnd - tFetchStart} ms")
                            recs
                        } else emptyList()

                        val tAfterFetchFull = System.currentTimeMillis()
                        val groupedFull = fullLatestRecords.groupBy { it.notiKey }
                        val mappedFull = fullUnits.map { unit ->
                            val recs = groupedFull[unit.notiKey]?.sortedBy { it.time } ?: emptyList()
                            NotiDisplayUnit(unit, recs)
                        }
                        mappedFull.take(20).forEach { ndu ->
                            Log.d("NotiRepoDebug", "full mapped unit key=${ndu.notiUnit.notiKey} records=${ndu.notiRecords.size}")
                        }
                        val tEndFull = System.currentTimeMillis()
                        Log.d("NotiRepoPerf", "Full mapping time: ${tEndFull - tAfterFetchFull} ms; total full processing: ${tEndFull - tFullStart} ms")

                        // Emit full mapped initial snapshot
                        emit(mappedFull)

                        // Then emit live updates for these fullKeys (this inner emitAll is fine inside flatMapLatest)
                        if (fullKeys.isNotEmpty()) {
                            emitAll(
                                notiRecordDao.getVisibleRecordsFlowByKeys(fullKeys).map { recs ->
                                    val groupedLive = recs.groupBy { it.notiKey }
                                    val liveMapped = fullUnits.map { unit ->
                                        val recsForUnit = groupedLive[unit.notiKey]?.sortedBy { it.time } ?: emptyList()
                                        NotiDisplayUnit(unit, recsForUnit)
                                    }
                                    liveMapped.take(20).forEach { ndu ->
                                        Log.d("NotiRepoDebug", "full live mapped unit key=${ndu.notiUnit.notiKey} records=${ndu.notiRecords.size}")
                                    }
                                    liveMapped
                                }
                            )
                        }

                    }
                }

                emitAll(kotlinx.coroutines.flow.merge(initialKeysFlow, fullMappedStream))
             }
          }
      }

    suspend fun updateSortPositionsInBulk(updates: List<Pair<String, Pair<Int, Long>>>, isAppCategoryView: Boolean, listSize: Int) {
        if (updates.isEmpty()) return

        val columnName = if (isAppCategoryView) "appCategorySortPosition" else "sortPosition"

        val sql = buildString {
            append("UPDATE noti_drawer SET $columnName = CASE notiKey ")
            updates.forEach { (key, posAndTime) ->
                // Note: It's crucial that 'key' is properly escaped or known to be safe.
                // Since notiKey comes from the system, it's generally safe.
                append("WHEN '$key' THEN ${posAndTime.first} ")
            }
            append("END WHERE notiKey IN (")
            updates.joinTo(this, separator = ", ") { "'${it.first}'" }
            append(")")
        }

        // Execute the raw query in a transaction for atomicity.
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL(sql)
        }

        updates.forEach { (notiKey, posAndTime) ->
            val metadata = "${posAndTime.first}/$listSize"
            if (isAppCategoryView)
               logAction(notiKey, "set_app_category_sort_position", metadata = metadata, actionTime = posAndTime.second)
            else
                logAction(notiKey, "set_overall_sort_position", metadata = metadata, actionTime = posAndTime.second)
        }
    }


    suspend fun updateSortPositions(updates: List<Pair<String, Int>>, isAppCategoryView: Boolean) {
        notiDrawerDao.updateSortPositions(updates, isAppCategoryView)
    }

    suspend fun resetSortPosition(notiKey: String, isAppCategoryView: Boolean) {
        if (isAppCategoryView) {
            notiDrawerDao.updateAppCategorySortPosition(notiKey, -1)
            logAction(notiKey, "reset_app_category_sort_position")
        } else {
            notiDrawerDao.updateSortPosition(notiKey, -1)
            logAction(notiKey, "reset_overall_sort_position")
        }
    }

    suspend fun resetAllSortPositions() {
        notiDrawerDao.resetAllSortPositions()
    }

    fun getNotificationKeys(): List<String> {
        return notiDrawerDao.getAllVisibleKeys()
    }

    fun getNotificationCount(): Int {
        return notiDrawerDao.getVisibleNotiCount()
    }

    fun getVisibleNotiCountByCategory(category: String): Int {
        return notiDrawerDao.getVisibleNotiCountByCategory(category)
    }

    fun getVisibleNotReadNotificationCountByCategory(category: String): Int {
        return notiDrawerDao.getVisibleNotReadCountByCategory(category)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun upsertNotiUnit(context: Context, sbn: StatusBarNotification, isInit: Boolean) {
        val existingNoti = notiDrawerDao.getByNotiKey(sbn.key)
        val newNoti = NotiUnit(context, sbn)

        if (existingNoti == null) {
            notiDrawerDao.insert(newNoti)
        } else if (!isInit) {
            existingNoti.updateNoti(context, sbn)
            notiDrawerDao.update(existingNoti)
        }
    }

    fun updateNotiUnit(notiUnit: NotiUnit) {
        notiDrawerDao.update(notiUnit)
    }

    fun removeNotiUnit(notiKey: String) {
        notiDrawerDao.setUnitInvisibleByKey(notiKey)
        notiRecordDao.setRecordsInvisibleByKey(notiKey)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun insertNotiRecord(sbn: StatusBarNotification) {
        val notiRecord = NotiRecord(sbn)
        notiRecordDao.upsert(notiRecord)
        // Register arrival for detection scheduling
        registerNewRecordForNotiUnit(notiRecord.notiKey)

        // If this noti unit is pinned, we want to ensure extraction is scheduled for new content.
        try {
            val existing = notiDrawerDao.getByNotiKey(notiRecord.notiKey)
            if (existing != null && existing.isPinned) {
                // Only set shouldExtractTask to true if it's not already true. Then register extraction scheduling.
                if (!existing.shouldExtractTask) {
                    notiDrawerDao.setShouldExtractTaskByKey(notiRecord.notiKey, true)
                    Log.d("NotiRepo", "Pinned unit received new record; set shouldExtractTask for key=${notiRecord.notiKey}")
                    // Register for extraction debounce/scheduling
                    registerShouldExtractForNotiUnit(notiRecord.notiKey)
                } else {
                    Log.d("NotiRepo", "Pinned unit received new record; shouldExtractTask already true for key=${notiRecord.notiKey}")
                }
            }
        } catch (e: Exception) {
            Log.e("NotiRepo", "Error checking pinned state for key=${notiRecord.notiKey}", e)
        }
    }

    private fun registerNewRecordForNotiUnit(notiKey: String) {
        // Increment counter
        val newCount = (detectionCounters[notiKey] ?: 0) + 1
        detectionCounters[notiKey] = newCount

        // Cancel existing job if present
        detectionJobs[notiKey]?.cancel()

        // Start a new debounce job
        val job = scope.launch {
            val waitSeconds = SharedPreferencesManager.waitSecondsBeforeNotiUnitSync
            val maxRecords = SharedPreferencesManager.maxRecordsBeforeNotiSync
            // If count exceeds max, trigger immediately
            if (newCount >= maxRecords) {
                enqueueTaskScan(appContext, notiKey)
                detectionCounters.remove(notiKey)
                detectionJobs.remove(notiKey)
                return@launch
            }
            delay(waitSeconds * 1000L)
            enqueueTaskScan(appContext, notiKey)
            detectionCounters.remove(notiKey)
            detectionJobs.remove(notiKey)
        }

        detectionJobs[notiKey] = job
    }

    private fun registerShouldExtractForNotiUnit(notiKey: String) {
         synchronized(extractionCounters) {
             extractionCounters[notiKey] = (extractionCounters[notiKey] ?: 0) + 1
         }

        // Log for debugging
        val totalPending = synchronized(extractionCounters) { extractionCounters.values.sum() }
        Log.d("NotiRepo", "registerShouldExtractForNotiUnit() triggered for=$notiKey totalPending=$totalPending")

        val maxCount = SharedPreferencesManager.maxRecordsBeforeDrawerSync
        val waitSeconds = SharedPreferencesManager.waitSecondsBeforeDrawerSync

        // If we have reached the threshold, submit immediately using only the candidate keys
        if (totalPending >= maxCount) {
            val candidateKeys: List<String> = synchronized(extractionCounters) { extractionCounters.keys.toList() }
            val toSubmit = candidateKeys.filter { k -> notiDrawerDao.getByNotiKey(k)?.shouldExtractTask == true }
            Log.d("NotiRepo", "Immediate extraction triggered: candidateKeys=${candidateKeys.size} toSubmit=${toSubmit.size}")
            if (toSubmit.isNotEmpty()) {
                notiDrawerDao.setShouldExtractTaskByKeys(toSubmit, false)
                enqueueTaskExtraction(appContext, toSubmit)
            }
            synchronized(extractionCounters) { extractionCounters.clear() }
            return
        }

        // Otherwise schedule a unique delayed WorkManager job that will run after waitSeconds
        // This replaces the previous scheduled delayed job if one exists, making debounce survive process death.
        enqueueDelayedTaskExtraction(appContext, waitSeconds.toLong())
    }

    fun getNotSyncedNotiActions(notiKey: String, timestamp: Long): List<NotiAction> {
        return notiActionDao.getNotSyncedActionsByKey(notiKey, timestamp)
    }

    fun getNotiUnit(notiKey: String): NotiUnit? {
        return notiDrawerDao.getByNotiKey(notiKey)
    }


    fun actOnNoti(notiKey: String, action: String) {
        when (action) {
            "dismiss_swipe" -> {
                // Fetch the current state of the notification from the database.
                val noti = notiDrawerDao.getByNotiKey(notiKey)
                // Only set it to invisible if it exists and is NOT pinned.
                if (noti != null && !noti.isPinned) {
                    notiDrawerDao.setUnitInvisibleByKey(notiKey)
                    logAction(notiKey, action)
                }
                return
            }
            "access_click_dismiss" -> {
                // You should apply the same logic here for consistency.
                val noti = notiDrawerDao.getByNotiKey(notiKey)
                if (noti != null) {
                    if (!noti.isPinned) {
                        notiDrawerDao.setUnitInvisibleByKey(notiKey)
                        logAction(notiKey, action)
                    } else {
                        logAction(notiKey, "access_click")
                    }
                }
                return
            }

            "archive" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_ARCHIVE)
            "unarchive" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_GENERAL)
            "make_task" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_MAKETASK)
            "dismiss_task" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_GENERAL)
            "save" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_SAVE)
            "unsave" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_GENERAL)

            "unpin" -> setPinnedState(notiKey, true)
            "pin" -> setPinnedState(notiKey, false)

            "mark_task_in_progress" -> notiDrawerDao.incrementTaskState(notiKey)
            "mark_task_completed" -> notiDrawerDao.incrementTaskState(notiKey)
            "mark_task_reset" -> notiDrawerDao.incrementTaskState(notiKey)
            "mark_read" -> markNotiRead(notiKey)
        }
        logAction(notiKey, action)
    }

    fun markNotiRead(notiKey: String) {
        notiDrawerDao.setUnitReadByKey(notiKey)
        notiRecordDao.setRecordsReadByKey(notiKey)
    }

    fun markAllNotisRead(category: String) {
        val notReadNotiKeys = notiDrawerDao.getVisibleNotReadKeysByCategory(category)
        val notiKeys = notiDrawerDao.getVisibleKeysByCategory(category)
        notReadNotiKeys.forEach { notiKey ->
            logAction(notiKey, "mark_all_read")
        }
        notiDrawerDao.setUnitsReadByKeys(notReadNotiKeys)
        notiRecordDao.setRecordsReadByIds(notiKeys)
    }

    fun deleteAllNotis(category: String) {
        val notiKeys = notiDrawerDao.getVisibleNotPinnedKeysByCategory(category)
        notiKeys.forEach { notiKey ->
            logAction(notiKey, "delete_all")
        }

        notiDrawerDao.setUnitsInvisibleByKeys(notiKeys)
        notiDrawerDao.setUnitsReadByKeys(notiKeys)
        notiRecordDao.setRecordsInvisibleByKeys(notiKeys)
        notiRecordDao.setRecordsReadByIds(notiKeys)
    }

    fun updateSeenNotifications(seenNotis: Set<Pair<String, Long>>, seenInfos: Set<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            notiDrawerDao.setUnitsReadByKeys(seenNotis.map { it.first }.toSet().toList())
            notiRecordDao.setRecordsReadByIds(seenInfos.toList())
            seenNotis.forEach {
                logAction(it.first, "scroll_read", actionTime = it.second)
            }
        }
    }

    fun syncAppCategories(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val notiUnits = notiDrawerDao.getAll()
            notiUnits.forEach { notiUnit ->
                notiUnit.displayState.appCategory = getAppCategoryByAppName(context, notiUnit.appName)
                notiDrawerDao.update(notiUnit)
            }
        }
    }

    fun exportLog(includeContext: Boolean, includeDismissed: Boolean): JSONArray {
        val notiUnits = if (includeDismissed)
            notiDrawerDao.getAll()
        else
            notiDrawerDao.getAllVisible()
        val notificationLogs = JSONArray()

        notiUnits.forEach { notiUnit ->

            val notiKey = notiUnit.notiKey
            val notiRecords = if (includeContext) {
                notiRecordDao.getRecordsByKey(notiKey)
            } else {
                notiRecordDao.getVisibleRecordsByKey(notiKey)
            }
                .sortedBy { it.time }
            val notiActions = notiActionDao.getActionsByKey(notiKey)
                .sortedBy { it.time }

            val notiJson = JSONObject()
            notiJson.put("id", notiUnit.notiKey)
            notiJson.put("app", notiUnit.appName)
            notiJson.put("isPeople", notiUnit.isPeople)
            notiJson.put("category", notiUnit.category)
            notiJson.put("appCategory", notiUnit.appCategory)

            val lastRecord = notiRecords.last()
            val lastTitle = lastRecord.title
            val notiOverallTitle = when {
                lastRecord.extraConversationTitle != "null" -> lastRecord.extraConversationTitle
                lastTitle != "null" -> lastTitle
                lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                else -> ""
            }
            val notiSecondOverallTitle = when {
                lastRecord.extraConversationTitle != "null" && lastTitle != "null" -> lastTitle
                lastRecord.extraConversationTitle == "null" && lastTitle != "null" && lastRecord.extraSubText != "null" -> lastRecord.extraSubText
                lastRecord.extraConversationTitle == "null" && lastTitle != "null" -> ""
                else -> ""
            }

            notiJson.put("overall_title", notiOverallTitle)
            notiJson.put("second_title", notiSecondOverallTitle)

            // Order and merge the two lists by time
            val mergedData = (notiRecords.map { it.time to it } + notiActions.map { it.time to it })
                .sortedBy { it.first }

            val timelineDataArray = JSONArray()
            var logActions: Boolean = includeContext

            mergedData.forEach {
                when (it.second) {
                    is NotiRecord -> {
                        val notiRecord = it.second as NotiRecord
                        val notiRecordJson = JSONObject()
                        notiRecordJson.put("type", "noti")
                        notiRecordJson.put("title", notiRecord.getDisplayedTitle(notiUnit.isPeople))
                        notiRecordJson.put("content", notiRecord.content.takeIf { it != "null" } ?: "")
                        notiRecordJson.put("time", notiRecord.time)
                        notiRecordJson.put("is_read", notiRecord.isRead)
                        notiRecordJson.put("is_visible", notiRecord.isVisible)
                        timelineDataArray.put(notiRecordJson)
                        logActions = true
                    }
                    is NotiAction -> {
                        if (logActions) {
                            val notiAction = it.second as NotiAction
                            val notiActionJson = JSONObject()
                            notiActionJson.put("type", "action")
                            notiActionJson.put("time", notiAction.time)
                            notiActionJson.put("last_resume_time", notiAction.lastAppResumeTime)
                            notiActionJson.put("action", notiAction.actionType)
                            notiActionJson.put("metadata", notiAction.metadata)
                            timelineDataArray.put(notiActionJson)
                        }
                    }
                }
            }

            notiJson.put("timeline_data", timelineDataArray)
            notificationLogs.put(notiJson)
        }
        return notificationLogs
    }

    fun logAction(notiKey: String, action: String, metadata: String = "", actionTime: Long = System.currentTimeMillis()) {
        val lastAppResumeTime = SharedPreferencesManager.lastAppResumeTime
        notiActionDao.insert(NotiAction(notiKey, action, actionTime, lastAppResumeTime, metadata))
    }

    fun setHasGenuineTask(notiKey: String, hasGenuine: Boolean) {
         val existing = notiDrawerDao.getByNotiKey(notiKey) ?: return
         val prev = existing.hasGenuineTask
         notiDrawerDao.setHasGenuineTaskByKey(notiKey, hasGenuine)

         if (!prev && hasGenuine) {
             // changed false -> true
             notiDrawerDao.setShouldExtractTaskByKey(notiKey, true)
             registerShouldExtractForNotiUnit(notiKey)
         } else if (prev && !hasGenuine) {
             // changed true -> false, set shouldExtractTask false only if not pinned
             if (!existing.isPinned) {
                 notiDrawerDao.setShouldExtractTaskByKey(notiKey, false)
             }
         }
     }

    fun setPinnedState(notiKey: String, pinned: Boolean) {
        val existing = notiDrawerDao.getByNotiKey(notiKey) ?: return
        val prev = existing.isPinned
        // flip pin in DB via existing flipPin method if desired, but here we set explicitly
        notiDrawerDao.flipPin(notiKey)
        if (!prev && pinned) {
            // changed false -> true
            notiDrawerDao.setShouldExtractTaskByKey(notiKey, true)
            registerShouldExtractForNotiUnit(notiKey)
        } else if (prev && !pinned) {
            // changed true -> false, set shouldExtractTask false only if hasGenuineTask is also false
            if (!existing.hasGenuineTask) {
                notiDrawerDao.setShouldExtractTaskByKey(notiKey, false)
            }
        }
     }

    // Helper to recompute shouldExtractTask based on current DB state for a notiKey
    fun recomputeShouldExtractForKey(notiKey: String) {
        val current = notiDrawerDao.getByNotiKey(notiKey) ?: return
        val should = current.hasGenuineTask || current.isPinned
        notiDrawerDao.setShouldExtractTaskByKey(notiKey, should)
    }

    fun getVisibleNotificationKeys(): List<String> {
        return notiDrawerDao.getAllVisibleKeys()
    }

    // Debug helper: number of visible records for a key
    fun getVisibleRecordsCountForKey(notiKey: String): Int {
        return notiRecordDao.getVisibleRecordsByKey(notiKey).size
    }

    // Debug helper: sample of visible record IDs for a key (ordered oldest->latest)
    fun getVisibleRecordIdsForKey(notiKey: String, limit: Int = 5): List<String> {
        val recs = notiRecordDao.getVisibleRecordsByKey(notiKey).sortedBy { it.time }
        return recs.takeLast(limit.coerceAtLeast(1)).map { it.notiRecordId }
    }

    fun requestRandomTaskExtraction(count: Int) {
        val keys = getVisibleNotificationKeys()
        if (keys.isEmpty() || count <= 0) return
        val randomKeys = keys.shuffled().take(count)
        if (randomKeys.isNotEmpty()) {
            enqueueTaskExtraction(appContext, randomKeys)
        }
    }

    // New helper function to fetch latest visible records efficiently.
    // Prefer a single DAO call for the full key list; fallback to chunked parallel calls if too many bind params.
    private suspend fun fetchLatestRecordsConcurrently(
        keys: List<String>,
        perKeyLimit: Int = 1
    ): List<NotiRecord> {
         if (keys.isEmpty()) return emptyList()

         // SQLite default max variables is often 999; stay well under that. Use 900 as a safe threshold.
         val safeThreshold = 900

        // We'll fetch visible records for the provided keys, group them and pick the latest `perKeyLimit` per key.
        return if (keys.size <= safeThreshold) {
            withContext(Dispatchers.IO) {
                val allRecs = notiRecordDao.getVisibleRecordsByKeys(keys)
                val grouped = allRecs.groupBy { it.notiKey }
                val selected = grouped.flatMap { (_, list) ->
                    list.sortedByDescending { if (it.whenTime != 0L) it.whenTime else it.postTime }
                        .take(perKeyLimit)
                }
                // Return flattened selection; upstream code groups by notiKey again.
                selected
            }
        } else {
            // Chunk keys to avoid SQL variable limits and aggregate results.
            coroutineScope {
                val chunkSize = safeThreshold
                val deferred = keys.chunked(chunkSize).map { chunk ->
                    async(Dispatchers.IO) {
                        notiRecordDao.getVisibleRecordsByKeys(chunk)
                    }
                }

                val results = deferred.awaitAll().flatten()
                val grouped = results.groupBy { it.notiKey }
                grouped.flatMap { (_, list) ->
                    list.sortedByDescending { if (it.whenTime != 0L) it.whenTime else it.postTime }
                        .take(perKeyLimit)
                }
            }
        }
     }

    fun getPreviewRecordsForKeys(keys: List<String>, perKeyLimit: Int = 3): List<NotiRecord> {
        if (keys.isEmpty()) return emptyList()
        val all = notiRecordDao.getVisibleRecordsByKeys(keys)
        return all.groupBy { it.notiKey }
            .flatMap { (_, list) -> list.sortedByDescending { if (it.whenTime != 0L) it.whenTime else it.postTime }.take(perKeyLimit) }
    }

    fun visibleRecordsFlowForKey(notiKey: String): Flow<List<NotiRecord>> {
        return notiRecordDao.getVisibleRecordsFlowByKey(notiKey)
            .map { it.sortedBy { r -> r.time } }
    }

    suspend fun fetchVisibleRecordsForKey(notiKey: String): List<NotiRecord> {
        return withContext(Dispatchers.IO) {
            notiRecordDao.getVisibleRecordsByKey(notiKey).sortedBy { it.time }
        }
    }
 }
