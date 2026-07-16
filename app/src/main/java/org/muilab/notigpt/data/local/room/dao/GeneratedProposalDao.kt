package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.muilab.notigpt.model.features.GeneratedProposal

@Dao
interface GeneratedProposalDao {
    @Upsert
    suspend fun upsertAll(proposals: List<GeneratedProposal>)

    @Query("SELECT * FROM generated_proposal WHERE proposalId = :proposalId")
    suspend fun getById(proposalId: String): GeneratedProposal?

    @Query("SELECT * FROM generated_proposal WHERE opId IN (:opIds)")
    suspend fun getByOpIds(opIds: List<Long>): List<GeneratedProposal>

    @Query("UPDATE generated_proposal SET decision = :decision, decisionAt = :decisionAt WHERE opId IN (:opIds)")
    suspend fun setDecision(opIds: List<Long>, decision: String, decisionAt: Long)

    @Query("DELETE FROM generated_proposal WHERE uid = :uid")
    suspend fun deleteForAccount(uid: String)
}
