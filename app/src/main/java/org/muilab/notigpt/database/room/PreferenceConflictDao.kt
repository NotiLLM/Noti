package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.PreferenceConflict

@Dao
interface PreferenceConflictDao {

    @Query("SELECT * FROM preference_conflicts ORDER BY createdAt DESC")
    fun getAllConflictsFlow(): Flow<List<PreferenceConflict>>

    @Query("SELECT * FROM preference_conflicts ORDER BY createdAt DESC")
    suspend fun getAllConflicts(): List<PreferenceConflict>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conflicts: List<PreferenceConflict>)

    @Query("DELETE FROM preference_conflicts WHERE conflictId = :conflictId")
    suspend fun deleteConflict(conflictId: String)

    @Query("DELETE FROM preference_conflicts")
    suspend fun deleteAll()
}

