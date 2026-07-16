package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object GeneratedProposalDecision {
    const val Pending = "pending"
    const val Approved = "approved"
    const val Rejected = "rejected"
    const val Superseded = "superseded"
}

/**
 * Permanent local record of one model-generated proposal and the user's eventual decision.
 *
 * `payload` is generated output, not a source notification snapshot. Evidence is represented by
 * record IDs inside the generated instruction; raw notification titles and bodies stay in the
 * device-only notification tables and are never copied here or to Firestore.
 */
@Entity(
    tableName = "generated_proposal",
    indices = [Index(value = ["uid", "createdAt"]), Index(value = ["opId"], unique = true)],
)
data class GeneratedProposal(
    @PrimaryKey val proposalId: String,
    val uid: String,
    val opId: Long,
    val batchId: String,
    val opType: String,
    val payload: String,
    val targetItemId: String,
    val itemType: String,
    val decision: String = GeneratedProposalDecision.Pending,
    val createdAt: Long,
    val decisionAt: Long = 0L,
)
