package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.ExtractionPreferenceV2

/** Prepared v55 access contract; the active v54 database continues using [ExtractionPreferenceDao]. */
@Dao
interface ExtractionPreferenceV2Dao {
    @Query("SELECT * FROM extraction_preferences ORDER BY updatedAt DESC, id ASC")
    fun observeAll(): Flow<List<ExtractionPreferenceV2>>

    @Query("SELECT * FROM extraction_preferences ORDER BY updatedAt DESC, id ASC")
    suspend fun getAll(): List<ExtractionPreferenceV2>

    @Query("SELECT * FROM extraction_preferences WHERE id = :id")
    suspend fun getById(id: String): ExtractionPreferenceV2?

    @Upsert
    suspend fun upsert(preference: ExtractionPreferenceV2)

    @Query("DELETE FROM extraction_preferences WHERE id = :id")
    suspend fun deleteById(id: String)
}
