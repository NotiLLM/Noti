package org.muilab.notigpt.data.repository.reminder

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.muilab.notigpt.R
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.local.room.dao.SavedItemDao
import org.muilab.notigpt.model.features.ExtractionJournalEntry
import org.muilab.notigpt.model.features.ExtractionJournalEventType
import org.muilab.notigpt.model.features.NotiSavedItemLink
import org.muilab.notigpt.model.features.NotiSavedItemLinkRole
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemChangeLog
import org.muilab.notigpt.model.features.SavedItemChangeType
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.data.remote.firestore.FirestoreSyncRepository
import org.muilab.notigpt.data.remote.n8n.N8nOpParsing
import org.muilab.notigpt.domain.reminder.SavedItemRevertLogic
import org.muilab.notigpt.util.SharedPreferencesManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository for reminder CRUD, filtering queries, soft deletes, and export-oriented reminder operations.
 *
 * Keep reminder persistence rules here rather than in Compose screens. Sub-task persistence and notification
 * context live in adjacent repositories to keep reminder rows focused.
 */
class SavedItemRepository(
    private val reminderListDao: SavedItemDao,
    private val appContext: Context,
) {
    private val firestoreSync by lazy { FirestoreSyncRepository(appContext.applicationContext) }
    private val linkDao by lazy { AppDatabase.getInstance(appContext.applicationContext).notiSavedItemLinkDao() }
    private val journalRepo by lazy {
        ExtractionJournalRepository(AppDatabase.getInstance(appContext.applicationContext).extractionJournalDao())
    }
    private val changeLogDao by lazy { AppDatabase.getInstance(appContext.applicationContext).savedItemChangeLogDao() }
    private val subTaskDao by lazy { AppDatabase.getInstance(appContext.applicationContext).subTaskDao() }
    private val pendingOpDao by lazy { AppDatabase.getInstance(appContext.applicationContext).pendingOpDao() }
    private val rejectedMergeDao by lazy { AppDatabase.getInstance(appContext.applicationContext).rejectedMergeDao() }

    /** Returns visible reminders in the canonical list order used by the reminders screen. */
    fun observeAll(): Flow<List<SavedItem>> = reminderListDao.observeAll()
    fun observeTasks(): Flow<List<SavedItem>> = reminderListDao.observeTasks()
    fun observeMemos(): Flow<List<SavedItem>> = reminderListDao.observeMemos()
    fun observeActiveKeeps(): Flow<List<SavedItem>> = reminderListDao.observeActiveKeeps()
    fun observeArchivedKeeps(): Flow<List<SavedItem>> = reminderListDao.observeArchivedKeeps()
    fun observeCompletedTasks(): Flow<List<SavedItem>> = reminderListDao.observeCompletedTasks()
    fun observeNewItems(): Flow<List<SavedItem>> = reminderListDao.observeNewItems()

    suspend fun getAll(): List<SavedItem> = withContext(Dispatchers.IO) { reminderListDao.getAll() }

    /** Active (non-completed/archived) items — the merge stages' candidate pool. */
    suspend fun getAllActive(): List<SavedItem> = withContext(Dispatchers.IO) { reminderListDao.getAllActive() }

    /**
     * Upserts a reminder locally and mirrors the resulting row to Firestore.
     *
     * Callers should set edit timestamps and user-edit flags before calling this method so local and remote
     * copies share the same reminder semantics.
     */
    suspend fun upsert(reminder: SavedItem) = withContext(Dispatchers.IO) {
        // Editor saves (vs LLM handler upserts) carry origin="manual" + userEdited; record them in
        // the change history and the extraction journal so the pipeline knows the user took over.
        val old = reminderListDao.getById(reminder.savedItemId)
        val isEditorSave = reminder.userEdited && reminder.origin == "manual" &&
            old != null && (old.title != reminder.title || old.content != reminder.content ||
                old.deadlineAtMs != reminder.deadlineAtMs || old.itemType != reminder.itemType)

        reminderListDao.upsert(reminder)

        if (isEditorSave && old != null) {
            val changedFields = org.json.JSONObject().apply {
                if (old.title != reminder.title) {
                    put("title", org.json.JSONObject().put("old", old.title).put("new", reminder.title))
                }
                if (old.deadlineAtMs != reminder.deadlineAtMs) {
                    put("deadlineAtMs", org.json.JSONObject().put("old", old.deadlineAtMs).put("new", reminder.deadlineAtMs))
                }
            }
            changeLogDao.insert(
                SavedItemChangeLog(
                    savedItemId = reminder.savedItemId,
                    createdAt = reminder.lastUpdateTimestamp,
                    changeType = SavedItemChangeType.UserEdit,
                    changedFieldsJson = changedFields.toString(),
                    origin = "user",
                )
            )
            journalUserEvent(reminder.savedItemId, ExtractionJournalEventType.UserEdited, reminder.title)
        }

        // Best-effort; never block core UX.
        firestoreSync.syncReminder(reminder)
    }

    /**
     * User deletion is a hard delete: the row and its sub-items go away for good (links and
     * change logs cascade via FK). The journal entry is written first — it's the only trace the
     * pipeline keeps, so future extractions know the user discarded this item. The Firestore
     * mirror is marked deleted rather than removed, preserving the research record.
     */
    suspend fun deleteById(savedItemId: String, ts: Long) = withContext(Dispatchers.IO) {
        journalUserEvent(savedItemId, ExtractionJournalEventType.UserDeleted)
        firestoreSync.markReminderDeleted(savedItemId, ts)
        subTaskDao.hardDeleteByParentId(savedItemId)
        reminderListDao.hardDeleteById(savedItemId)
        // Staged ops against a gone item are unreviewable; merge cool-downs are moot too.
        pendingOpDao.deleteByTargetItemId(savedItemId)
        rejectedMergeDao.deleteForItem(savedItemId)
    }

    suspend fun setCompleted(savedItemId: String, completed: Boolean, ts: Long) = withContext(Dispatchers.IO) {
        reminderListDao.setCompleted(savedItemId, completed, ts)
        if (completed) journalUserEvent(savedItemId, ExtractionJournalEventType.UserCompleted)

        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
    }

    suspend fun setState(savedItemId: String, state: String, ts: Long) = withContext(Dispatchers.IO) {
        val old = reminderListDao.getById(savedItemId)
        reminderListDao.setState(savedItemId, state, ts)
        when {
            state == SavedItemState.Archived -> journalUserEvent(savedItemId, ExtractionJournalEventType.UserArchived)
            // Un-archiving a keep is a "this matters again" signal for future extractions.
            old?.state == SavedItemState.Archived && state == SavedItemState.Saved ->
                journalUserEvent(savedItemId, ExtractionJournalEventType.ItemRestored)
        }
        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
    }

    suspend fun markSavedByIds(savedItemIds: List<String>, ts: Long) = withContext(Dispatchers.IO) {
        if (savedItemIds.isEmpty()) return@withContext
        reminderListDao.markSavedByIds(savedItemIds, ts)
        savedItemIds.mapNotNull { reminderListDao.getById(it) }.forEach { firestoreSync.syncReminder(it) }
    }

    suspend fun deleteByIds(savedItemIds: List<String>, ts: Long) = withContext(Dispatchers.IO) {
        if (savedItemIds.isEmpty()) return@withContext
        savedItemIds.forEach {
            journalUserEvent(it, ExtractionJournalEventType.UserDeleted)
            firestoreSync.markReminderDeleted(it, ts)
        }
        subTaskDao.hardDeleteByParentIds(savedItemIds)
        reminderListDao.hardDeleteByIds(savedItemIds)
        savedItemIds.forEach {
            pendingOpDao.deleteByTargetItemId(it)
            rejectedMergeDao.deleteForItem(it)
        }
    }

    /**
     * Journals a user action on [savedItemId] into every notiUnit the item is linked to, so future
     * extractions from those threads know the user already handled it. No-op for unlinked items.
     */
    private suspend fun journalUserEvent(savedItemId: String, eventType: String, titleOverride: String? = null) {
        try {
            val item = reminderListDao.getById(savedItemId)
            val title = titleOverride ?: item?.title ?: return
            val keys = linkDao.getBySavedItemId(savedItemId).map { it.notiKey }.distinct()
            val now = System.currentTimeMillis()
            keys.forEach { key ->
                journalRepo.append(
                    ExtractionJournalEntry(
                        notiKey = key,
                        createdAt = now,
                        eventType = eventType,
                        savedItemId = savedItemId,
                        itemTitle = title,
                    )
                )
            }
        } catch (_: Exception) {
            // Journaling is best-effort; never block or fail a user action over it.
        }
    }

    suspend fun getById(savedItemId: String): SavedItem? = withContext(Dispatchers.IO) { reminderListDao.getById(savedItemId) }

    suspend fun setViewed(savedItemId: String) = withContext(Dispatchers.IO) {
        reminderListDao.setViewed(savedItemId)
        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
    }

    suspend fun setStarred(savedItemId: String, starred: Boolean, ts: Long) = withContext(Dispatchers.IO) {
        reminderListDao.setStarred(savedItemId, starred, ts)
        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
    }

    /** [doAtMs] = 0 clears the do date. */
    suspend fun setDoDate(savedItemId: String, doAtMs: Long, ts: Long) = withContext(Dispatchers.IO) {
        reminderListDao.setDoDate(savedItemId, doAtMs, ts)
        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
    }

    /** Explicit user acknowledgment of a New/Updated item (the review "got it" action). */
    suspend fun acknowledgeReview(savedItemId: String, ts: Long) = withContext(Dispatchers.IO) {
        reminderListDao.acknowledgeReview(savedItemId, ts)
        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
    }

    /** Batch acknowledgment for "approve all" in the review screen. */
    suspend fun acknowledgeReviewByIds(savedItemIds: List<String>, ts: Long) = withContext(Dispatchers.IO) {
        if (savedItemIds.isEmpty()) return@withContext
        reminderListDao.acknowledgeReviewByIds(savedItemIds, ts)
        savedItemIds.mapNotNull { reminderListDao.getById(it) }.forEach { firestoreSync.syncReminder(it) }
    }

    // ========== Reject-an-update (revert) ==========

    /**
     * What a [revertPendingLlmUpdates] call changed, carried back so the caller can offer Undo.
     *
     * [previousItem] is the item exactly as it was before the revert (re-upsert to undo the field
     * and content rollback). [hiddenSubTaskIds] were visible and the revert hid them; [restoredSubTaskIds]
     * were hidden and the revert brought them back — undo swaps both. [revertChangeId] is the audit
     * row the revert wrote; undo deletes it so the change history matches the restored state.
     */
    data class RevertOutcome(
        val previousItem: SavedItem,
        val hiddenSubTaskIds: List<String>,
        val restoredSubTaskIds: List<String>,
        val revertChangeId: Long,
    )

    /**
     * Rejects the pending LLM edits on an "updated" item, rolling it back to how it was before the
     * model touched it, and returns to the `saved` state.
     *
     * Only unacknowledged `llm_update` rows (createdAt > lastViewedChangeAt) are reverted, newest
     * first so stacked appended sections strip off the end in order. User edits and regenerations in
     * the window are left alone; each field is only rolled back if its current value still equals
     * what that LLM row set (so a later user edit — or a hallucinated `old` echo — is never clobbered).
     *
     * Returns null if the item no longer exists. If there is nothing pending to revert, the item is
     * simply acknowledged (equivalent to approve) and an outcome with no subtask changes is returned.
     */
    suspend fun revertPendingLlmUpdates(savedItemId: String, ts: Long): RevertOutcome? = withContext(Dispatchers.IO) {
        val item = reminderListDao.getById(savedItemId) ?: return@withContext null
        val pending = changeLogDao.getNewerThan(savedItemId, item.lastViewedChangeAt)
            .filter { it.origin == "llm" && it.changeType == SavedItemChangeType.LlmUpdate }

        var content = item.content
        var title = item.title
        var deadline = item.deadlineAtMs
        var start = item.startAtMs
        var end = item.endAtMs
        val hiddenSubTaskIds = mutableListOf<String>()
        val restoredSubTaskIds = mutableListOf<String>()

        // getNewerThan returns newest-first (ORDER BY createdAt DESC), which is the order we need.
        for (row in pending) {
            if (row.appendedContent.isNotBlank()) {
                stripUpdateSection(content, row.appendedContent, row.createdAt)?.let { content = it }
            }

            val changed = row.changedFieldsJson.toJsonObjectOrEmpty()
            changed.optJSONObject("title")?.let { obj ->
                title = SavedItemRevertLogic.revertField(title, obj.optString("new"), obj.optString("old", title))
            }
            changed.optJSONObject("deadlineTimeString")?.let { obj ->
                deadline = SavedItemRevertLogic.revertField(
                    deadline, deadlineMsFromIso(obj.optString("new", "-1")), deadlineMsFromIso(obj.optString("old", "-1")),
                )
            }
            changed.optJSONObject("startTimeString")?.let { obj ->
                start = SavedItemRevertLogic.revertField(
                    start, startEndMsFromIso(obj.optString("new", "-1")), startEndMsFromIso(obj.optString("old", "-1")),
                )
            }
            changed.optJSONObject("endTimeString")?.let { obj ->
                end = SavedItemRevertLogic.revertField(
                    end, startEndMsFromIso(obj.optString("new", "-1")), startEndMsFromIso(obj.optString("old", "-1")),
                )
            }
            parseSubTaskIds(row.addedSubTasksJson).forEach { id ->
                subTaskDao.softDeleteById(id, ts)
                hiddenSubTaskIds += id
            }
            val removed = parseSubTaskIds(row.removedSubTasksJson)
            if (removed.isNotEmpty()) {
                subTaskDao.restoreByIds(removed, ts)
                restoredSubTaskIds += removed
            }
        }

        val restored = item.copy(
            title = title,
            content = content,
            state = SavedItemState.Saved,
            deadlineAtMs = deadline,
            startAtMs = start,
            endAtMs = end,
            lastViewedChangeAt = ts,
            lastUpdateTimestamp = ts,
        )
        reminderListDao.upsert(restored)

        val revertChangeId = changeLogDao.insert(
            SavedItemChangeLog(
                savedItemId = savedItemId,
                createdAt = ts,
                changeType = SavedItemChangeType.Reverted,
                changeSummary = "",
                origin = "user",
            )
        )
        // Tell future extraction passes the user rejected these edits so they aren't re-applied.
        journalUserEvent(savedItemId, ExtractionJournalEventType.UserRevertedUpdate, restored.title)
        firestoreSync.syncReminder(restored)

        RevertOutcome(
            previousItem = item,
            hiddenSubTaskIds = hiddenSubTaskIds,
            restoredSubTaskIds = restoredSubTaskIds,
            revertChangeId = revertChangeId,
        )
    }

    /** Undoes a [revertPendingLlmUpdates], restoring the item and sub-task visibility as they were. */
    suspend fun undoRevert(outcome: RevertOutcome, ts: Long) = withContext(Dispatchers.IO) {
        reminderListDao.upsert(outcome.previousItem)
        if (outcome.hiddenSubTaskIds.isNotEmpty()) subTaskDao.restoreByIds(outcome.hiddenSubTaskIds, ts)
        outcome.restoredSubTaskIds.forEach { subTaskDao.softDeleteById(it, ts) }
        changeLogDao.deleteById(outcome.revertChangeId)
        firestoreSync.syncReminder(outcome.previousItem)
    }

    private fun deadlineMsFromIso(iso: String): Long = N8nOpParsing.isoToUnixMillis(iso)

    /** start/end use 0 (not -1) as their "unset" sentinel, mirroring how update ops are applied. */
    private fun startEndMsFromIso(iso: String): Long =
        N8nOpParsing.isoToUnixMillis(iso).let { if (it == -1L) 0L else it }

    private fun String.toJsonObjectOrEmpty(): JSONObject =
        try { JSONObject(this) } catch (_: Exception) { JSONObject() }

    private fun parseSubTaskIds(json: String): List<String> = try {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("savedSubItemId", obj.optString("subTaskId"))
                if (id.isNotBlank()) add(id)
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    // ========== Noti <-> saved-item links ==========

    /**
     * Adds evidence links from [savedItemId] to each record in [recordIds] without touching existing rows.
     *
     * Links are add-only provenance: a record already linked (same role) is silently skipped via the
     * unique index, and links from earlier extractions are never displaced by later responses.
     * [type] mirrors the owning saved item's itemType ("task"/"keep"); [source] labels the flow that
     * produced the citation ([NotiSavedItemLinkSource]).
     */
    suspend fun addEvidenceLinks(
        savedItemId: String,
        recordIds: Collection<String>,
        type: String,
        source: String,
        role: String = NotiSavedItemLinkRole.Evidence,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val links = recordIds
            .filter { it.isNotBlank() }
            .distinct()
            .map { recordId ->
                NotiSavedItemLink(
                    notiKey = recordId.substringBeforeLast("_"),
                    notiRecordId = recordId,
                    type = type,
                    savedItemId = savedItemId,
                    role = role,
                    source = source,
                    createdAt = now,
                )
            }
        if (links.isNotEmpty()) linkDao.insertAll(links)
    }

    /** Record ids linked to [savedItemId], in no particular order. */
    suspend fun getLinkedRecordIds(savedItemId: String): List<String> = withContext(Dispatchers.IO) {
        linkDao.getBySavedItemId(savedItemId).map { it.notiRecordId }.distinct()
    }

    /** Distinct notification group keys linked to [savedItemId]. */
    suspend fun getLinkedKeys(savedItemId: String): List<String> = withContext(Dispatchers.IO) {
        linkDao.getBySavedItemId(savedItemId).map { it.notiKey }.distinct()
    }

    /** Map of savedItemId -> its linked record ids, for batch payload building. */
    suspend fun getLinkedRecordIdsFor(savedItemIds: List<String>): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (savedItemIds.isEmpty()) return@withContext emptyMap()
        linkDao.getBySavedItemIds(savedItemIds)
            .groupBy { it.savedItemId }
            .mapValues { (_, links) -> links.map { it.notiRecordId }.distinct() }
    }

    suspend fun updateButtons(savedItemId: String, buttons: String) = withContext(Dispatchers.IO) {
        reminderListDao.updateButtons(savedItemId, buttons)
        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
    }

    /**
     * Formats an LLM append-fragment as a timestamped section to concatenate below existing content.
     *
     * The header label follows the user's target extraction language when set (so it matches the
     * content language the LLM writes in), falling back to the app locale's string resource.
     * Content is append-only by design: users have already read what's there, so updates arrive as
     * dated sections instead of rewrites.
     */
    fun buildUpdateSection(fragment: String, ts: Long): String {
        val label = when (SharedPreferencesManager.targetExtractionLanguage) {
            "en" -> "Update"
            "zh-TW" -> "更新"
            else -> appContext.getString(R.string.reminder_update_section_label)
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
        return "\n\n[$label — $time]\n$fragment"
    }

    /**
     * Inverse of [buildUpdateSection]: removes the dated section a given LLM [fragment] appended, so a
     * rejected update leaves [content] as it was before.
     *
     * Prefers stripping the exact section [buildUpdateSection] would rebuild for ([fragment], [ts]).
     * That can miss when the language preference or timezone changed since the append, so it falls
     * back to locating the fragment and stripping back to its `"\n\n["` header. Returns null when the
     * fragment can't be found at all (the user edited it away) — the caller then leaves content alone.
     */
    fun stripUpdateSection(content: String, fragment: String, ts: Long): String? =
        SavedItemRevertLogic.stripUpdateSection(content, fragment, buildUpdateSection(fragment, ts))
}
