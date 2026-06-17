package org.muilab.notigpt.data.repository.reminder

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.local.room.dao.SavedItemDao
import org.muilab.notigpt.model.features.NotiSavedItemLink
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.data.remote.firestore.FirestoreSyncRepository

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
        reminderListDao.upsert(reminder)
        // Best-effort; never block core UX.
        firestoreSync.syncReminder(reminder)
    }

    suspend fun deleteById(savedItemId: String, ts: Long) = withContext(Dispatchers.IO) {
        // Persist deletedAtMs before flipping visibility.
        reminderListDao.setDeletedAt(savedItemId, ts)
        reminderListDao.softDeleteById(savedItemId, ts)

        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
    }

    suspend fun setCompleted(savedItemId: String, completed: Boolean, ts: Long) = withContext(Dispatchers.IO) {
        reminderListDao.setCompleted(savedItemId, completed, ts)

        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
    }

    suspend fun setState(savedItemId: String, state: String, ts: Long) = withContext(Dispatchers.IO) {
        reminderListDao.setState(savedItemId, state, ts)
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
        reminderListDao.softDeleteByIds(savedItemIds, ts)
        savedItemIds.mapNotNull { reminderListDao.getById(it) }.forEach { firestoreSync.syncReminder(it) }
    }

    suspend fun getById(savedItemId: String): SavedItem? = withContext(Dispatchers.IO) { reminderListDao.getById(savedItemId) }

    suspend fun setViewed(savedItemId: String) = withContext(Dispatchers.IO) {
        reminderListDao.setViewed(savedItemId)
        val updated = reminderListDao.getById(savedItemId) ?: return@withContext
        firestoreSync.syncReminder(updated)
    }

    // ========== Noti <-> saved-item links ==========

    /**
     * Replaces all links for [savedItemId] with one row per record id in [recordIds].
     *
     * [type] mirrors the owning saved item's itemType ("task"/"keep"). Existing links are cleared first so
     * the link set always reflects the latest extraction/regeneration result.
     */
    suspend fun replaceLinks(savedItemId: String, recordIds: Collection<String>, type: String) = withContext(Dispatchers.IO) {
        linkDao.deleteBySavedItemId(savedItemId)
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
                    createdAt = now,
                )
            }
        if (links.isNotEmpty()) linkDao.upsertAll(links)
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
}
