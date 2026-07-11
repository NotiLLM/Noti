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
import org.muilab.notigpt.util.SharedPreferencesManager
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

    /** Returns visible reminders in the canonical list order used by the reminders screen. */
    fun observeAll(): Flow<List<SavedItem>> = reminderListDao.observeAll()
    fun observeTasks(): Flow<List<SavedItem>> = reminderListDao.observeTasks()
    fun observeMemos(): Flow<List<SavedItem>> = reminderListDao.observeMemos()
    fun observeActiveKeeps(): Flow<List<SavedItem>> = reminderListDao.observeActiveKeeps()
    fun observeArchivedKeeps(): Flow<List<SavedItem>> = reminderListDao.observeArchivedKeeps()
    fun observeCompletedTasks(): Flow<List<SavedItem>> = reminderListDao.observeCompletedTasks()
    fun observeNewItems(): Flow<List<SavedItem>> = reminderListDao.observeNewItems()

    suspend fun getAllVisible(): List<SavedItem> = withContext(Dispatchers.IO) { reminderListDao.getAllVisible() }

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

    suspend fun deleteById(savedItemId: String, ts: Long) = withContext(Dispatchers.IO) {
        journalUserEvent(savedItemId, ExtractionJournalEventType.UserDeleted)

        // Persist deletedAtMs before flipping visibility.
        reminderListDao.setDeletedAt(savedItemId, ts)
        reminderListDao.softDeleteById(savedItemId, ts)

        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
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
        savedItemIds.forEach { journalUserEvent(it, ExtractionJournalEventType.UserDeleted) }
        reminderListDao.softDeleteByIds(savedItemIds, ts)
        savedItemIds.mapNotNull { reminderListDao.getById(it) }.forEach { firestoreSync.syncReminder(it) }
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

    suspend fun updateSortScoreAndHistory(savedItemId: String, sortScore: Float, reRankHistory: String) = withContext(Dispatchers.IO) {
        reminderListDao.updateSortScoreAndHistory(savedItemId, sortScore, reRankHistory)
        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
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
}
