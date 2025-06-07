package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Insert
import org.muilab.notigpt.model.notifications.NotiAction

@Dao
interface NotiActionDao {
    @Insert
    fun insert(notiAction: NotiAction)
}