package org.muilab.notigpt.data.repository.reminder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.dao.SavedItemChangeLogDao
import org.muilab.notigpt.model.features.SavedItemChangeLog

/**
 * Repository for the append-only saved-item change history.
 *
 * Handlers write one row per LLM op or user edit; the reminder detail UI reads newest-first to
 * render "what's new" (rows newer than SavedItem.lastViewedChangeAt) and the full change history.
 */
class SavedItemChangeLogRepository(private val changeLogDao: SavedItemChangeLogDao) {

    suspend fun record(change: SavedItemChangeLog): Long = withContext(Dispatchers.IO) {
        changeLogDao.insert(change)
    }

    fun observeByItem(savedItemId: String): Flow<List<SavedItemChangeLog>> =
        changeLogDao.observeByItem(savedItemId)

    suspend fun getNewerThan(savedItemId: String, sinceTs: Long): List<SavedItemChangeLog> =
        withContext(Dispatchers.IO) { changeLogDao.getNewerThan(savedItemId, sinceTs) }

    suspend fun getLatestChangeTs(savedItemId: String): Long? =
        withContext(Dispatchers.IO) { changeLogDao.getLatestChangeTs(savedItemId) }
}
