package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.muilab.notigpt.model.features.ProposedOpRecord

@Dao
interface ProposedOpRecordDao {
    @Upsert
    suspend fun upsertAll(proposals: List<ProposedOpRecord>)

    @Query("SELECT * FROM proposed_op_record WHERE proposalId = :proposalId")
    suspend fun getById(proposalId: String): ProposedOpRecord?

    @Query("SELECT * FROM proposed_op_record WHERE opId IN (:opIds)")
    suspend fun getByOpIds(opIds: List<Long>): List<ProposedOpRecord>

    @Query("UPDATE proposed_op_record SET decision = :decision, decisionAt = :decisionAt WHERE opId IN (:opIds)")
    suspend fun setDecision(opIds: List<Long>, decision: String, decisionAt: Long)

    @Query("DELETE FROM proposed_op_record WHERE uid = :uid")
    suspend fun deleteForAccount(uid: String)
}
