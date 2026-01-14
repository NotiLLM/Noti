package org.muilab.notigpt.model.esm

import androidx.room.Entity
import androidx.room.Index

/**
 * An ESM questionnaire instance (a single scheduled/answered survey).
 *
 * Storage goals:
 * - Forward compatible: rendering uses [questionnaireId] + [questionnaireVersion] and answer events.
 * - Stable stimulus: [snapshotId] points to a frozen extraction snapshot (LLM input context).
 */
@Entity(
    tableName = "esm_instance",
    primaryKeys = ["instanceId"],
    indices = [
        Index(value = ["status", "availableAt"], name = "idx_esm_status_available"),
        Index(value = ["status", "expiresAt"], name = "idx_esm_status_expires"),
        Index(value = ["reminderId"], name = "idx_esm_reminderId"),
    ]
)
data class EsmInstance(
    val instanceId: String,

    val questionnaireId: String,
    val questionnaireVersion: Int,

    /** A/B/C/DEBUG */
    val triggerType: String,

    /** Generated reminder that this ESM is about. */
    val reminderId: String,

    /** Frozen extraction snapshot for ESM NotiCard rendering. */
    val snapshotId: String,

    /** Creation time (ms since epoch). */
    val createdAt: Long,

    /** ESM becomes answerable at this time (ms since epoch). */
    val availableAt: Long,

    /** Expiration time (ms since epoch). Still answerable if not superseded; marked late. */
    val expiresAt: Long,

    /** PENDING, AVAILABLE, ANSWERED, EXPIRED, DISCARDED_SUPERSEDED */
    val status: String,

    /** When user submitted. */
    val answeredAt: Long = 0L,

    /** Whether answered after [expiresAt]. */
    val isLate: Boolean = false,
)

