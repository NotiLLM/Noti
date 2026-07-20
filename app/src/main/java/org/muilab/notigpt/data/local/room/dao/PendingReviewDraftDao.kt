package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.PendingReviewDraft

@Dao
interface PendingReviewDraftDao {
    @Query("SELECT * FROM pending_review_draft")
    fun observeAll(): Flow<List<PendingReviewDraft>>

    @Query("SELECT * FROM pending_review_draft WHERE reviewKey = :reviewKey")
    suspend fun getByKey(reviewKey: String): PendingReviewDraft?

    @Upsert
    suspend fun upsert(draft: PendingReviewDraft)

    @Query("DELETE FROM pending_review_draft WHERE reviewKey = :reviewKey")
    suspend fun deleteByKey(reviewKey: String)

    @Query("DELETE FROM pending_review_draft WHERE reviewKey NOT LIKE 'legacy_%' AND reviewKey NOT IN (:activeKeys)")
    suspend fun deleteOrphans(activeKeys: List<String>)

    @Query("DELETE FROM pending_review_draft WHERE reviewKey NOT LIKE 'legacy_%'")
    suspend fun deleteNonLegacy()

    @Query("DELETE FROM pending_review_draft")
    suspend fun deleteAll()
}
