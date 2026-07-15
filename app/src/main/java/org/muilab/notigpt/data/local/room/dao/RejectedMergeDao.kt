package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.muilab.notigpt.model.features.RejectedMerge

/** Access layer for merge-rejection cool-down pairs (see [RejectedMerge]). */
@Dao
interface RejectedMergeDao {

    @Upsert
    suspend fun upsertAll(pairs: List<RejectedMerge>)

    /** Pairs still inside the cool-down window, for filtering merge-stage candidate inputs. */
    @Query("SELECT * FROM rejected_merge WHERE rejectedAt >= :sinceMs")
    suspend fun getActiveSince(sinceMs: Long): List<RejectedMerge>

    /** Purges pairs whose cool-down expired; call opportunistically before merge stages. */
    @Query("DELETE FROM rejected_merge WHERE rejectedAt < :beforeMs")
    suspend fun purgeOlderThan(beforeMs: Long)

    /** Drops cool-downs involving a deleted item. */
    @Query("DELETE FROM rejected_merge WHERE itemIdA = :savedItemId OR itemIdB = :savedItemId")
    suspend fun deleteForItem(savedItemId: String)

    @Query("DELETE FROM rejected_merge")
    suspend fun deleteAllForAccountSwitch()
}
