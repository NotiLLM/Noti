package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.NotiLlmState

/**
 * Local access layer for per-notiKey LLM-derived thread state (scan flags, categories).
 *
 * Rows are created lazily: a thread without a row is equivalent to one with all-default state.
 * Callers should read-modify-write through upsert instead of partial column updates so the
 * single-row-per-key invariant stays obvious.
 */
@Dao
interface NotiLlmStateDao {

    @Query("SELECT * FROM noti_llm_state WHERE notiKey = :notiKey")
    fun getByKey(notiKey: String): NotiLlmState?

    @Query("SELECT * FROM noti_llm_state WHERE notiKey IN (:notiKeys)")
    fun getByKeys(notiKeys: List<String>): List<NotiLlmState>

    /** All thread LLM state, for classifying the active drawer into Communication/Content on the home screen. */
    @Query("SELECT * FROM noti_llm_state")
    fun observeAll(): Flow<List<NotiLlmState>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(state: NotiLlmState)

    /** Keys flagged for extraction whose notification is still active in the drawer. */
    @Query(
        """
        SELECT s.notiKey FROM noti_llm_state s
        JOIN noti_drawer d ON d.notiKey = s.notiKey
        WHERE d.isDismissed = 0 AND s.shouldExtractReminder = 1
        """
    )
    fun getActiveShouldExtractKeys(): List<String>

    @Query("UPDATE noti_llm_state SET shouldExtractReminder = :value, updatedAt = :ts WHERE notiKey IN (:notiKeys)")
    fun setShouldExtractByKeys(notiKeys: List<String>, value: Boolean, ts: Long)

    /** Account-switch wipe. */
    @Query("DELETE FROM noti_llm_state")
    suspend fun deleteAll()
}
