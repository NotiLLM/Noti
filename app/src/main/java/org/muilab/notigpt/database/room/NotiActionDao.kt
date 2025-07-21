package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.muilab.notigpt.model.notifications.NotiAction

@Dao
interface NotiActionDao {
    @Insert
    fun insert(notiAction: NotiAction)

    @Query("SELECT * FROM notiAction")
    fun getAllActions(): List<NotiAction>

    @Insert
    fun insertAllActions(notiActions: List<NotiAction>)
}