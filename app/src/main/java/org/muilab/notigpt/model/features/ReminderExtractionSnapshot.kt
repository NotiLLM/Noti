package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.Index

/**
 * Frozen extraction snapshot captured at the moment we send NotiCard context to the LLM.
 *
 * This snapshot is JSON-first so we can evolve/extend content without schema churn.
 */
@Entity(
    tableName = "reminder_extraction_snapshot",
    primaryKeys = ["snapshotId"],
    indices = [
        Index(value = ["status", "createdAt"], name = "idx_reminder_snap_status_time"),
        Index(value = ["reminderId"], name = "idx_reminder_snap_reminderId"),
    ]
)
data class ReminderExtractionSnapshot(
    val snapshotId: String,

    /** STAGED, KEPT, DISCARDED */
    val status: String,

    /** Optional once linked. */
    val reminderId: String? = null,

    /** JSON payload of the full LLM input context and mapping. */
    val payloadJson: String,

    /** ms since epoch */
    val createdAt: Long,
)

