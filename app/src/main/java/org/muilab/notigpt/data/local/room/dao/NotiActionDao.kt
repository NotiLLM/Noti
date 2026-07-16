package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import org.muilab.notigpt.model.notifications.NotiAction

/**
 * Local access layer for actions that can be applied back to Android notifications.
 *
 * This DAO stores executable notification actions separately from notification display state.
 * Keep framework-specific action execution outside the DAO so Room remains storage-only.
 */
@Dao
interface NotiActionDao {
    @Upsert
    fun insert(notiAction: NotiAction)

    @Query("SELECT * FROM notiAction")
    fun getAllActions(): List<NotiAction>

    @Query("DELETE FROM notiAction")
    suspend fun deleteAll()

    @Query("SELECT * FROM notiAction WHERE notiKey = :notiKey")
    fun getActionsByKey(notiKey: String): List<NotiAction>

    @Query("SELECT * FROM notiAction WHERE notiKey = :notiKey AND time > :timestamp")
    fun getNotSyncedActionsByKey(notiKey: String, timestamp: Long): List<NotiAction>

    @Insert
    fun insertAllActions(notiActions: List<NotiAction>)
}
