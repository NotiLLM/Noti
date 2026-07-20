package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.SavedItemChangeLog

/**
 * Local access layer for per-saved-item change history.
 *
 * Rows are append-only records of what the LLM or user changed; nothing here is required to render
 * the item itself (SavedItem.content stays the full text). Read newest-first for display.
 */
@Dao
interface SavedItemChangeLogDao {

    @Insert
    suspend fun insert(change: SavedItemChangeLog): Long

    @Query("SELECT * FROM saved_item_change_log WHERE savedItemId = :savedItemId ORDER BY createdAt DESC")
    fun observeByItem(savedItemId: String): Flow<List<SavedItemChangeLog>>

    @Query("SELECT * FROM saved_item_change_log WHERE savedItemId = :savedItemId ORDER BY createdAt DESC")
    suspend fun getByItem(savedItemId: String): List<SavedItemChangeLog>

    @Query("SELECT * FROM saved_item_change_log WHERE savedItemId IN (:savedItemIds) ORDER BY createdAt DESC")
    suspend fun getByItems(savedItemIds: List<String>): List<SavedItemChangeLog>

    @Query("SELECT * FROM saved_item_change_log WHERE savedItemId = :savedItemId AND createdAt > :sinceTs ORDER BY createdAt DESC")
    suspend fun getNewerThan(savedItemId: String, sinceTs: Long): List<SavedItemChangeLog>

    @Query("SELECT MAX(createdAt) FROM saved_item_change_log WHERE savedItemId = :savedItemId")
    suspend fun getLatestChangeTs(savedItemId: String): Long?

    /** Removes a single change-log row, used to undo a revert's own audit entry. */
    @Query("DELETE FROM saved_item_change_log WHERE changeId = :changeId")
    suspend fun deleteById(changeId: Long)

    @Query("DELETE FROM saved_item_change_log WHERE changeId IN (:changeIds)")
    suspend fun deleteByIds(changeIds: List<Long>)

    @Query("DELETE FROM saved_item_change_log WHERE savedItemId = :savedItemId")
    suspend fun deleteByItem(savedItemId: String)

    @Upsert
    suspend fun upsertAll(changes: List<SavedItemChangeLog>)
}
