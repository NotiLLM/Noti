package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Supported cloud-mirror operations. Payload content is deliberately never stored in the queue. */
object FirestoreOutboxKind {
    const val UpsertSavedItem = "upsert_saved_item"
    const val DeleteSavedItem = "delete_saved_item"
    const val SyncProposedOpRecord = "sync_proposed_op_record"
    const val SyncPreferencesAndContexts = "sync_preferences_and_contexts"
}

/**
 * Durable, ID-only instruction for converging local state to Firestore.
 *
 * The item body is re-read from Room when an upsert runs. A delete needs only its item ID. Keeping
 * the queue payload-free prevents raw notification content (or stale generated content) from being
 * duplicated in retry storage. One row per account/item means the newest local intent supersedes an
 * older one safely: an edit replaces an edit, and a later delete replaces a pending upload.
 */
@Entity(
    tableName = "firestore_outbox",
    indices = [Index(value = ["uid", "createdAt"])],
)
data class FirestoreOutboxOp(
    @PrimaryKey val operationKey: String,
    val uid: String,
    val kind: String,
    val entityId: String,
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastError: String = "",
) {
    companion object {
        fun savedItem(uid: String, kind: String, savedItemId: String, createdAt: Long) =
            FirestoreOutboxOp(
                operationKey = "$uid:saved_item:$savedItemId",
                uid = uid,
                kind = kind,
                entityId = savedItemId,
                createdAt = createdAt,
            )

        fun proposedOpRecord(uid: String, proposalId: String, createdAt: Long) =
            FirestoreOutboxOp(
                operationKey = "$uid:proposed_op_record:$proposalId",
                uid = uid,
                kind = FirestoreOutboxKind.SyncProposedOpRecord,
                entityId = proposalId,
                createdAt = createdAt,
            )

        fun preferencesAndContexts(uid: String, createdAt: Long) =
            FirestoreOutboxOp(
                operationKey = "$uid:preferences_and_contexts",
                uid = uid,
                kind = FirestoreOutboxKind.SyncPreferencesAndContexts,
                entityId = uid,
                createdAt = createdAt,
            )
    }
}
