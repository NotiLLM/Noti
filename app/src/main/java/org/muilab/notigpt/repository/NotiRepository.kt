package org.muilab.notigpt.repository

import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.sqlite.db.SimpleSQLiteQuery
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.database.server.enqueueTaskScan
import org.muilab.notigpt.database.server.enqueueTaskExtraction
import org.muilab.notigpt.database.server.enqueueDelayedTaskExtraction
import org.muilab.notigpt.database.room.NotiActionDao
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiGroupDao
import org.muilab.notigpt.database.room.NotiRecordDao
import org.muilab.notigpt.model.notifications.NotiAction
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiDrawerItem
import org.muilab.notigpt.model.notifications.NotiGroup
import org.muilab.notigpt.model.notifications.NotiGroupItem
import org.muilab.notigpt.model.notifications.NotiItem
import org.muilab.notigpt.util.Constants.Companion.MAX_EXPIRED_RECORDS_PER_KEY
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_SAVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_RECORD_EXPIRE_TIME_MS
import org.muilab.notigpt.util.SharedPreferencesManager
import org.muilab.notigpt.util.getAppCategoryByAppName
import java.util.UUID

class NotiRepository(
    private val appContext: Context,
    private val notiDrawerDao: NotiDrawerDao,
    private val notiActionDao: NotiActionDao,
    private val notiRecordDao: NotiRecordDao,
    private val notiGroupDao: NotiGroupDao
) {

    private val detectionCounters = mutableMapOf<String, Int>()
    private val detectionJobs = mutableMapOf<String, Job?>()
    private val extractionCounters = mutableMapOf<String, Int>()

    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun removeExpiredNotiRecords() {
        val expireTimestamp = System.currentTimeMillis() - NOTI_RECORD_EXPIRE_TIME_MS
        notiRecordDao.removeExpiredReadRecords(
            expireTimestamp,
            MAX_EXPIRED_RECORDS_PER_KEY
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getGroupedNotifications(
        categoryFlow: Flow<String>,
        appCategoryFlow: Flow<String>,
    ): Flow<List<NotiDrawerItem>> {
        return combine(
            categoryFlow,
            appCategoryFlow,
            notiGroupDao.getAllGroupsFlow()
        ) { cat, appCat, groups ->
            Tuple(cat, appCat, groups)
        }.flatMapLatest { (cat, appCat, groups) ->
            // This retrieves items already sorted by SQL (ToTop > Time)
            val unitsFlow = notiDrawerDao.getAutoSortedNotificationsNoRelation(cat, appCat)

            unitsFlow.flatMapLatest { units ->
                val keys = units.map { it.notiKey }

                val displayUnitsFlow = if (keys.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    notiRecordDao.getVisibleRecordsFlowByKeys(keys).map { recs ->
                        val groupedRecs = recs.groupBy { it.notiKey }
                        units.map { unit ->
                            val unitRecs = groupedRecs[unit.notiKey]?.sortedBy { it.time } ?: emptyList()
                            NotiDisplayUnit(unit, unitRecs)
                        }
                    }
                }

                displayUnitsFlow.map { displayUnits ->
                    val groupMap = groups.associateBy { it.groupId }

                    // Grouping Logic
                    val groupedItemsMap = displayUnits
                        .filter { it.notiUnit.groupId != null }
                        .groupBy { it.notiUnit.groupId!! }

                    val looseItems = displayUnits.filter { it.notiUnit.groupId == null }.toMutableList()
                    val result = mutableListOf<NotiDrawerItem>()

                    groupedItemsMap.forEach { (groupId, children) ->
                        val group = groupMap[groupId]
                        if (group != null && children.size > 1) {
                            // Sort children within group: Top > TopTime > UpdateTime
                            val sortedChildren = children.sortedWith(
                                compareByDescending<NotiDisplayUnit> { it.notiUnit.isSetToTop }
                                    .thenByDescending { it.notiUnit.setToTopTime }
                                    .thenByDescending { it.lastUpdateTime }
                            )
                            result.add(NotiGroupItem(group, sortedChildren))
                        } else {
                            looseItems.addAll(children)
                        }
                    }

                    result.addAll(looseItems.map { NotiItem(it) })

                    // Final Sort for the Drawer List
                    // Priority: IsTop > TopTime > LatestTime
                    result.sortedWith(
                        compareByDescending<NotiDrawerItem> { it.isSetToTop }
                            .thenByDescending { it.setToTopTime }
                            .thenByDescending { it.latestTime }
                    )
                }
            }
        }
    }

    // --- NEW: Feature 2 (Group Actions) ---
    suspend fun actOnGroup(groupId: String, action: String) {
        when (action) {
            "to_top" -> notiDrawerDao.updateToTopStatusByGroupId(groupId, true, System.currentTimeMillis())
            "undo_to_top" -> notiDrawerDao.updateToTopStatusByGroupId(groupId, false, 0L)
            "dismiss_swipe" -> {
                notiDrawerDao.setGroupInvisible(groupId)
                val remainingVisibleItems = notiDrawerDao.getVisibleCountForGroup(groupId)
                if (remainingVisibleItems <= 1) {
                    notiDrawerDao.ungroupItems(groupId)
                    notiGroupDao.deleteGroup(groupId)
                }
            }
            "archive" -> notiDrawerDao.updateCategoryByGroupId(groupId, NOTI_CATEGORY_ARCHIVE)
            "unarchive" -> notiDrawerDao.updateCategoryByGroupId(groupId, NOTI_CATEGORY_GENERAL)
            "make_task" -> notiDrawerDao.updateCategoryByGroupId(groupId, NOTI_CATEGORY_MAKETASK)
            "dismiss_task" -> notiDrawerDao.updateCategoryByGroupId(groupId, NOTI_CATEGORY_GENERAL)
            "save" -> notiDrawerDao.updateCategoryByGroupId(groupId, NOTI_CATEGORY_SAVE)
            "unsave" -> notiDrawerDao.updateCategoryByGroupId(groupId, NOTI_CATEGORY_GENERAL)
        }
    }

    suspend fun merge(dragId: String, targetId: String) {
        val dragUnit = notiDrawerDao.getByNotiKey(dragId)
        val targetUnit = notiDrawerDao.getByNotiKey(targetId)
        val targetGroup = notiGroupDao.getGroupById(targetId)
        val dragGroup = notiGroupDao.getGroupById(dragId)

        if (targetUnit != null) {
            // TARGET IS AN ITEM
            if (dragUnit != null) {
                // Item -> Item : Create Group
                val newGroupId = "g_" + UUID.randomUUID().toString().take(8)
                val newTitle = targetUnit.appName
                val newGroup = NotiGroup(groupId = newGroupId, title = newTitle)

                notiGroupDao.insert(newGroup)
                setGroupId(targetUnit.notiKey, newGroupId)
                setGroupId(dragUnit.notiKey, newGroupId)
            } else if (dragGroup != null) {
                // Group -> Item : Create group containing item + group contents
                val newGroupId = "g_" + UUID.randomUUID().toString().take(8)
                val newGroup = NotiGroup(groupId = newGroupId, title = dragGroup.title)
                notiGroupDao.insert(newGroup)

                setGroupId(targetUnit.notiKey, newGroupId)
                moveGroupChildren(dragGroup.groupId, newGroupId)
                notiGroupDao.deleteGroup(dragGroup.groupId)
            }
        } else if (targetGroup != null) {
            // TARGET IS A GROUP
            if (dragUnit != null) {
                // Item -> Group : Add item
                setGroupId(dragUnit.notiKey, targetGroup.groupId)
            } else if (dragGroup != null) {
                // Group -> Group : Merge children
                moveGroupChildren(dragGroup.groupId, targetGroup.groupId)
                notiGroupDao.deleteGroup(dragGroup.groupId)
            }
        }
    }

    suspend fun ungroup(groupId: String) {
        moveGroupChildren(groupId, null)
        notiGroupDao.deleteGroup(groupId)
    }

    suspend fun updateGroupExpansion(groupId: String, expanded: Boolean) {
        notiGroupDao.updateExpansion(groupId, expanded)
    }

    suspend fun updateGroupTitle(groupId: String, title: String) {
        notiGroupDao.updateTitle(groupId, title)
    }

    private suspend fun setGroupId(notiKey: String, groupId: String?) {
        notiDrawerDao.updateGroupId(notiKey, groupId)
    }

    private suspend fun moveGroupChildren(oldGroupId: String, newGroupId: String?) {
        notiDrawerDao.moveGroupChildren(oldGroupId, newGroupId)
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
            existingNoti.isRead = false
            notiDrawerDao.update(existingNoti)
        }
    }

    fun updateNotiUnit(notiUnit: NotiUnit) {
        notiDrawerDao.update(notiUnit)
    }

    suspend fun removeNotiUnit(notiKey: String) {
        notiDrawerDao.setUnitInvisibleByKey(notiKey)
        notiRecordDao.setRecordsInvisibleByKey(notiKey)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun insertNotiRecord(sbn: StatusBarNotification) {
        val notiRecord = NotiRecord(sbn)
        notiRecordDao.upsert(notiRecord)
        registerNewRecordForNotiUnit(notiRecord.notiKey)

        try {
            val existing = notiDrawerDao.getByNotiKey(notiRecord.notiKey)
            if (existing != null && existing.isPinned) {
                if (!existing.shouldExtractTask) {
                    notiDrawerDao.setShouldExtractTaskByKey(notiRecord.notiKey, true)
                    registerShouldExtractForNotiUnit(notiRecord.notiKey)
                }
            }
        } catch (e: Exception) {
            Log.e("NotiRepo", "Error checking pinned state for key=${notiRecord.notiKey}", e)
        }
    }

    private fun registerNewRecordForNotiUnit(notiKey: String) {
        val newCount = (detectionCounters[notiKey] ?: 0) + 1
        detectionCounters[notiKey] = newCount
        detectionJobs[notiKey]?.cancel()

        val job = scope.launch {
            val waitSeconds = SharedPreferencesManager.waitSecondsBeforeNotiUnitSync
            val maxRecords = SharedPreferencesManager.maxRecordsBeforeNotiSync
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
        val totalPending = synchronized(extractionCounters) { extractionCounters.values.sum() }
        val maxCount = SharedPreferencesManager.maxRecordsBeforeDrawerSync
        val waitSeconds = SharedPreferencesManager.waitSecondsBeforeDrawerSync

        if (totalPending >= maxCount) {
            val candidateKeys: List<String> = synchronized(extractionCounters) { extractionCounters.keys.toList() }
            val toSubmit = candidateKeys.filter { k -> notiDrawerDao.getByNotiKey(k)?.shouldExtractTask == true }
            if (toSubmit.isNotEmpty()) {
                notiDrawerDao.setShouldExtractTaskByKeys(toSubmit, false)
                enqueueTaskExtraction(appContext, toSubmit)
            }
            synchronized(extractionCounters) { extractionCounters.clear() }
            return
        }

        enqueueDelayedTaskExtraction(appContext, waitSeconds.toLong())
    }

    fun getNotSyncedNotiActions(notiKey: String, timestamp: Long): List<NotiAction> {
        return notiActionDao.getNotSyncedActionsByKey(notiKey, timestamp)
    }

    fun getNotiUnit(notiKey: String): NotiUnit? {
        return notiDrawerDao.getByNotiKey(notiKey)
    }

    fun getNotiUnitByKeys(notiKeys: List<String>): List<NotiUnit> {
        return notiDrawerDao.getByNotiKeys(notiKeys)
    }

    suspend fun actOnNoti(notiKey: String, action: String) {
        when (action) {
            "dismiss_swipe" -> {
                val noti = notiDrawerDao.getByNotiKey(notiKey)
                if (noti != null && !noti.isPinned) {
                    notiDrawerDao.setUnitInvisibleByKey(notiKey)
                    logAction(notiKey, action)
                }
                return
            }
            "access_click_dismiss" -> {
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
            "to_top" -> notiDrawerDao.updateToTopStatus(notiKey, true, System.currentTimeMillis())
            "undo_to_top" -> notiDrawerDao.updateToTopStatus(notiKey, false, 0L)
            "archive" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_ARCHIVE)
            "unarchive" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_GENERAL)
            "make_task" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_MAKETASK)
            "dismiss_task" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_GENERAL)
            "save" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_SAVE)
            "unsave" -> notiDrawerDao.updateCategory(notiKey, NOTI_CATEGORY_GENERAL)
            "unpin" -> setPinnedState(notiKey, true)
            "pin" -> setPinnedState(notiKey, false)
            "mark_read" -> markNotiRead(notiKey)
        }
        logAction(notiKey, action)
    }

    suspend fun markNotiRead(notiKey: String) {
        notiDrawerDao.setUnitReadByKey(notiKey)
    }

    suspend fun markAllNotisRead(category: String) {
        val notReadNotiKeys = notiDrawerDao.getVisibleNotReadKeysByCategory(category)
        notReadNotiKeys.forEach { notiKey ->
            logAction(notiKey, "mark_all_read")
        }
        notiDrawerDao.setUnitsReadByKeys(notReadNotiKeys)
    }

    suspend fun deleteAllNotis(category: String) {
        val notiKeys = notiDrawerDao.getVisibleNotPinnedKeysByCategory(category)
        notiKeys.forEach { notiKey ->
            logAction(notiKey, "delete_all")
        }
        notiDrawerDao.setUnitsInvisibleByKeys(notiKeys)
        notiDrawerDao.setUnitsReadByKeys(notiKeys)
        notiRecordDao.setRecordsInvisibleByKeys(notiKeys)
    }

    fun updateSeenNotifications(seenNotis: Set<String>) { // Changed signature
        CoroutineScope(Dispatchers.IO).launch {
            notiDrawerDao.setUnitsReadByKeys(seenNotis.toList())
            seenNotis.forEach {
                logAction(it, "scroll_read")
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
            }.sortedBy { it.time }
            val notiActions = notiActionDao.getActionsByKey(notiKey).sortedBy { it.time }

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

            val mergedData = (notiRecords.map { it.time to it } + notiActions.map { it.time to it })
                .sortedBy { it.first }

            val timelineDataArray = JSONArray()
            var logActions: Boolean = includeContext

            mergedData.forEach { it ->
                when (it.second) {
                    is NotiRecord -> {
                        val notiRecord = it.second as NotiRecord
                        val notiRecordJson = JSONObject()
                        notiRecordJson.put("type", "noti")
                        notiRecordJson.put("title", notiRecord.getDisplayedTitle(notiUnit.isPeople))
                        notiRecordJson.put("content", notiRecord.content.takeIf { it != "null" } ?: "")
                        notiRecordJson.put("time", notiRecord.time)
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

    suspend fun setHasGenuineTask(notiKey: String, hasGenuine: Boolean) {
        val existing = notiDrawerDao.getByNotiKey(notiKey) ?: return
        val prev = existing.hasGenuineTask
        notiDrawerDao.setHasGenuineTaskByKey(notiKey, hasGenuine)

        if (!prev && hasGenuine) {
            notiDrawerDao.setShouldExtractTaskByKey(notiKey, true)
            registerShouldExtractForNotiUnit(notiKey)
        } else if (prev && !hasGenuine) {
            if (!existing.isPinned) {
                notiDrawerDao.setShouldExtractTaskByKey(notiKey, false)
            }
        }
    }

    suspend fun setPinnedState(notiKey: String, pinned: Boolean) {
        val existing = notiDrawerDao.getByNotiKey(notiKey) ?: return
        val prev = existing.isPinned
        notiDrawerDao.flipPin(notiKey)
        if (!prev && pinned) {
            notiDrawerDao.setShouldExtractTaskByKey(notiKey, true)
            registerShouldExtractForNotiUnit(notiKey)
        } else if (prev && !pinned) {
            if (!existing.hasGenuineTask) {
                notiDrawerDao.setShouldExtractTaskByKey(notiKey, false)
            }
        }
    }

    suspend fun recomputeShouldExtractForKey(notiKey: String) {
        val current = notiDrawerDao.getByNotiKey(notiKey) ?: return
        val should = current.hasGenuineTask || current.isPinned
        notiDrawerDao.setShouldExtractTaskByKey(notiKey, should)
    }

    fun getVisibleNotificationKeys(): List<String> {
        return notiDrawerDao.getAllVisibleKeys()
    }

    fun getVisibleRecordsCountForKey(notiKey: String): Int {
        return notiRecordDao.getVisibleRecordsByKey(notiKey).size
    }

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

    private suspend fun fetchLatestRecordsConcurrently(keys: List<String>, perKeyLimit: Int = 1): List<NotiRecord> {
        if (keys.isEmpty()) return emptyList()
        val safeThreshold = 900
        return if (keys.size <= safeThreshold) {
            withContext(Dispatchers.IO) {
                val allRecs = notiRecordDao.getVisibleRecordsByKeys(keys)
                val grouped = allRecs.groupBy { it.notiKey }
                grouped.flatMap { (_, list) ->
                    list.sortedByDescending { if (it.whenTime != 0L) it.whenTime else it.postTime }
                        .take(perKeyLimit)
                }
            }
        } else {
            coroutineScope {
                val deferred = keys.chunked(safeThreshold).map { chunk ->
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

    suspend fun removeFromGroup(notiKey: String) {
        setGroupId(notiKey, null)
    }

    // [NEW] Advanced Search Logic
    suspend fun searchNotifications(rawInput: String, includeHistory: Boolean): Map<String, List<NotiRecord>> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()

        // 1. Parse Exact Phrases (e.g., "baseball match")
        val quoteRegex = "\"([^\"]*)\"".toRegex()
        var remainingInput = rawInput

        quoteRegex.findAll(rawInput).forEach { match ->
            val phrase = match.groupValues[1]
            if (phrase.isNotBlank()) {
                // Condition: Phrase must be in text OR title
                conditions.add("(extraText LIKE ? OR extraBigText LIKE ? OR extraTitle LIKE ? OR person LIKE ?)")
                val likePhrase = "%$phrase%"
                repeat(4) { args.add(likePhrase) }
            }
        }
        // Remove quotes from input to process remaining keywords
        remainingInput = quoteRegex.replace(remainingInput, " ")

        // 2. Parse '+' combined keywords (AND logic)
        // Split by + first, then trim. Empty parts are ignored.
        val terms = remainingInput.split("+").map { it.trim() }.filter { it.isNotBlank() }

        terms.forEach { term ->
            conditions.add("(extraText LIKE ? OR extraBigText LIKE ? OR extraTitle LIKE ? OR person LIKE ?)")
            val likeTerm = "%$term%"
            repeat(4) { args.add(likeTerm) }
        }

        // 3. Construct Query
        val whereClause = if (conditions.isNotEmpty()) {
            conditions.joinToString(" AND ")
        } else {
            "1 = 1" // Fallback match all if parsing failed
        }

        val visibilityClause = if (includeHistory) "" else " AND isVisible = 1"

        val finalSql = "SELECT * FROM noti_record WHERE $whereClause $visibilityClause ORDER BY whenTime DESC LIMIT 100"

        val query = SimpleSQLiteQuery(finalSql, args.toTypedArray())
        val records = notiRecordDao.searchRecordsRaw(query)

        return records.groupBy { it.notiKey }
    }

    // [NEW] Gap Filling
    suspend fun getRecordsBetween(notiKey: String, start: Long, end: Long): List<NotiRecord> {
        return notiRecordDao.getRecordsBetween(notiKey, start, end)
    }

    suspend fun getContextRecords(
        notiKey: String,
        pivotTime: Long,
        isOlder: Boolean,
        includeHistory: Boolean
    ): List<NotiRecord> {
        return if (isOlder) {
            // "Older" means time < pivot, ordered DESC.
            // We want the result to be chronological eventually, but the DAO returns them closest to pivot first.
            notiRecordDao.getContextOlder(notiKey, pivotTime, 10, includeHistory).sortedBy { it.time }
        } else {
            // "Newer" means time > pivot, ordered ASC.
            notiRecordDao.getContextNewer(notiKey, pivotTime, 10, includeHistory)
        }
    }

    suspend fun getGapRecords(
        notiKey: String,
        minTime: Long,
        maxTime: Long,
        limit: Int,
        fromStart: Boolean
    ): List<NotiRecord> {
        return if (fromStart) {
            // Load "Next" records after minTime
            notiRecordDao.getGapRecordsNewer(notiKey, minTime, maxTime, limit)
        } else {
            // Load "Previous" records before maxTime
            // Note: DAO returns DESC, we might want to reverse here or let ViewModel sort
            notiRecordDao.getGapRecordsOlder(notiKey, minTime, maxTime, limit)
        }
    }

    suspend fun hasRecordsInGap(notiKey: String, minTime: Long, maxTime: Long, includeHistory: Boolean): Boolean {
        return notiRecordDao.hasRecordsInGap(notiKey, minTime, maxTime, includeHistory) > 0
    }
}

data class Tuple<A, B, C>(val first: A, val second: B, val third: C)