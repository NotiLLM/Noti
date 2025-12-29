package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.notifications.NotiGroup

@Dao
interface NotiGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: NotiGroup)

    @Update
    suspend fun update(group: NotiGroup)

    @Query("SELECT * FROM noti_group")
    fun getAllGroupsFlow(): Flow<List<NotiGroup>>

    @Query("DELETE FROM noti_group WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("SELECT * FROM noti_group WHERE groupId = :groupId")
    suspend fun getGroupById(groupId: String): NotiGroup?

    @Query("UPDATE noti_group SET isExpanded = :expanded WHERE groupId = :groupId")
    suspend fun updateExpansion(groupId: String, expanded: Boolean)

    @Query("UPDATE noti_group SET title = :title WHERE groupId = :groupId")
    suspend fun updateTitle(groupId: String, title: String)
}