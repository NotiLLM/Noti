package org.muilab.notigpt.model.esm

import androidx.room.Entity
import androidx.room.Index

/**
 * Append-only answer events. Stored as JSON for maximum flexibility.
 */
@Entity(
    tableName = "esm_answer_event",
    primaryKeys = ["instanceId", "questionId"],
    indices = [Index(value = ["instanceId"], name = "idx_esm_answer_instance")]
)
data class EsmAnswerEvent(
    val instanceId: String,
    val questionId: String,

    /** JSON blob representing the answer value (choice ids, free text, likert numeric, etc.). */
    val answerJson: String,

    /** ms since epoch */
    val answeredAt: Long,
)

