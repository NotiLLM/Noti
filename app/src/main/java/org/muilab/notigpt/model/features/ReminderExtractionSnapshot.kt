package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.Index

/**
 * Room entity for the notification-record snapshot used during reminder extraction.
 *
 * The JSON payload stores provenance/context for later UI rendering and sync while avoiding schema churn
 * for each payload version. Keep this model as storage; format interpretation belongs in reminder-domain
 * helpers so snapshot versions can evolve safely.
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

