package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.ExtractionPreference

/**
 * Local access layer for user extraction preferences.
 *
 * These rows are app behavior inputs, not UI-only settings. Keep conflict detection and chat flows
 * reading through this DAO so preference state has one local source of truth.
 */
@Dao
interface ExtractionPreferenceDao {

    @Query("SELECT * FROM extraction_preferences ORDER BY updatedAt DESC")
    fun getAllPreferencesFlow(): Flow<List<ExtractionPreference>>

    @Query("SELECT * FROM extraction_preferences ORDER BY updatedAt DESC")
    suspend fun getAllPreferences(): List<ExtractionPreference>

    @Upsert
    suspend fun upsertPreference(preference: ExtractionPreference)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(preferences: List<ExtractionPreference>)

    @Query("DELETE FROM extraction_preferences WHERE id = :id")
    suspend fun deletePreference(id: String)

    @Query("DELETE FROM extraction_preferences")
    suspend fun deleteAll()

    /**
     * Atomically wipe the current rule set and replace it with the server's
     * newly merged list.  Used after a QUICK_SYNC round-trip.
     */
    @Transaction
    suspend fun replacePreferences(preferences: List<ExtractionPreference>) {
        deleteAll()
        insertAll(preferences)
    }
}

