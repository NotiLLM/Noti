package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.features.PendingOp

/**
 * Access layer for staged pipeline instructions awaiting review.
 *
 * Rows are short-lived: inserted when a pipeline run stages its ops, deleted when the user
 * accepts (after apply) or rejects (discard) them in review.
 */
@Dao
interface PendingOpDao {

    @Insert
    suspend fun insertAll(ops: List<PendingOp>): List<Long>

    @Query("SELECT * FROM pending_op ORDER BY createdAt ASC, opId ASC")
    fun observeAll(): Flow<List<PendingOp>>

    @Query("SELECT * FROM pending_op ORDER BY createdAt ASC, opId ASC")
    suspend fun getAll(): List<PendingOp>

    @Query("SELECT * FROM pending_op WHERE opId IN (:opIds)")
    suspend fun getByIds(opIds: List<Long>): List<PendingOp>

    /** Ops proposing changes to an existing item (update/merge survivor). */
    @Query("SELECT * FROM pending_op WHERE targetItemId = :savedItemId")
    suspend fun getByTargetItemId(savedItemId: String): List<PendingOp>

    /**
     * Item ids that currently have staged ops against them (as update/merge target). Items whose
     * pending ops are unreviewed are excluded from merge-stage inputs to keep the dataflow simple.
     */
    @Query("SELECT DISTINCT targetItemId FROM pending_op WHERE targetItemId != ''")
    suspend fun getTargetedItemIds(): List<String>

    @Query("DELETE FROM pending_op WHERE opId IN (:opIds)")
    suspend fun deleteByIds(opIds: List<Long>)

    @Query("DELETE FROM pending_op WHERE batchId = :batchId")
    suspend fun deleteByBatch(batchId: String)

    /** Drops ops that referenced an item deleted out from under them. */
    @Query("DELETE FROM pending_op WHERE targetItemId = :savedItemId")
    suspend fun deleteByTargetItemId(savedItemId: String)

    @Query("DELETE FROM pending_op")
    suspend fun deleteAllForAccountSwitch()
}
