package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.GeneralPreference

/** Prepared local access contract for confirmed General Preferences. */
@Dao
interface GeneralPreferenceDao {
    @Query("SELECT * FROM general_preferences ORDER BY updatedAt DESC, id ASC")
    fun observeAll(): Flow<List<GeneralPreference>>

    @Query("SELECT * FROM general_preferences ORDER BY updatedAt DESC, id ASC")
    suspend fun getAll(): List<GeneralPreference>

    @Query("SELECT * FROM general_preferences WHERE id = :id")
    suspend fun getById(id: String): GeneralPreference?

    @Upsert
    suspend fun upsert(preference: GeneralPreference)

    @Query("DELETE FROM general_preferences WHERE id = :id")
    suspend fun deleteById(id: String)
}
