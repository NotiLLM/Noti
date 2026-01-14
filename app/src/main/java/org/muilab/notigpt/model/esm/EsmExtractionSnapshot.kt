package org.muilab.notigpt.model.esm

import androidx.room.Entity
import androidx.room.Index

/**
 * Frozen extraction snapshot captured at the moment we send NotiCard context to the LLM.
 *
 * This snapshot is intentionally JSON-first so we can evolve/extend content without schema churn.
 */
@Entity(
    tableName = "esm_extraction_snapshot",
    primaryKeys = ["snapshotId"],
    indices = [
        Index(value = ["status", "createdAt"], name = "idx_esm_snap_status_time"),
        Index(value = ["reminderId"], name = "idx_esm_snap_reminderId"),
    ]
)
data class EsmExtractionSnapshot(
    val snapshotId: String,

    /** STAGED, KEPT, DISCARDED */
    val status: String,

    /** Optional once linked. */
    val reminderId: String? = null,

    /** JSON object holding list of per-notiKey payloads, matching what we sent to LLM. */
    val payloadJson: String,

    /** ms since epoch */
    val createdAt: Long,
)

