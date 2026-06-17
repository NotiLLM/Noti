package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.muilab.notigpt.model.features.NotiSavedItemLink

/**
 * Local access layer for the noti-to-saved-item join table.
 *
 * This is the sole source of truth for which notification records back a saved item. Resolve provenance
 * in both directions (saved item -> its notis, noti -> its saved items) through this DAO instead of
 * parsing serialized id sets.
 */
@Dao
interface NotiSavedItemLinkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<NotiSavedItemLink>)

    @Query("SELECT * FROM noti_saved_item_link WHERE savedItemId = :savedItemId")
    suspend fun getBySavedItemId(savedItemId: String): List<NotiSavedItemLink>

    @Query("SELECT * FROM noti_saved_item_link WHERE savedItemId IN (:savedItemIds)")
    suspend fun getBySavedItemIds(savedItemIds: List<String>): List<NotiSavedItemLink>

    @Query("SELECT * FROM noti_saved_item_link WHERE notiKey = :notiKey")
    suspend fun getByNotiKey(notiKey: String): List<NotiSavedItemLink>

    @Query("SELECT * FROM noti_saved_item_link WHERE notiRecordId = :notiRecordId")
    suspend fun getByNotiRecordId(notiRecordId: String): List<NotiSavedItemLink>

    @Query("DELETE FROM noti_saved_item_link WHERE savedItemId = :savedItemId")
    suspend fun deleteBySavedItemId(savedItemId: String)

    @Query("DELETE FROM noti_saved_item_link WHERE notiRecordId = :notiRecordId")
    suspend fun deleteByNotiRecordId(notiRecordId: String)
}
