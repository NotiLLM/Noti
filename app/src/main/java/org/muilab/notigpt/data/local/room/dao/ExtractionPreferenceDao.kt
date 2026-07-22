package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.ExtractionPreference

/** Local access contract for confirmed Extraction Preferences. */
@Dao
interface ExtractionPreferenceDao {
    @Query("SELECT * FROM extraction_preferences ORDER BY updatedAt DESC, id ASC")
    fun observeAll(): Flow<List<ExtractionPreference>>

    @Query("SELECT * FROM extraction_preferences ORDER BY updatedAt DESC, id ASC")
    suspend fun getAll(): List<ExtractionPreference>

    @Query("SELECT * FROM extraction_preferences WHERE id = :id")
    suspend fun getById(id: String): ExtractionPreference?

    @Upsert
    suspend fun upsert(preference: ExtractionPreference)

    @Query("DELETE FROM extraction_preferences WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM extraction_preferences")
    suspend fun deleteAll()

}
