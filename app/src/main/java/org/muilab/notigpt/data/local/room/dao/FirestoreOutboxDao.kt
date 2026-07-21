package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.muilab.notigpt.model.features.FirestoreOutboxOp

/** Persistence boundary for the payload-free Firestore retry queue. */
@Dao
interface FirestoreOutboxDao {
    @Upsert
    suspend fun upsert(operation: FirestoreOutboxOp)

    @Query("SELECT * FROM firestore_outbox WHERE uid = :uid ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPending(uid: String, limit: Int = 50): List<FirestoreOutboxOp>

    @Query("SELECT EXISTS(SELECT 1 FROM firestore_outbox WHERE uid = :uid AND kind = :kind)")
    suspend fun hasPending(uid: String, kind: String): Boolean

    @Query("DELETE FROM firestore_outbox WHERE operationKey = :operationKey AND createdAt = :createdAt")
    suspend fun deleteIfUnchanged(operationKey: String, createdAt: Long)

    @Query(
        "UPDATE firestore_outbox SET attemptCount = attemptCount + 1, lastError = :error " +
            "WHERE operationKey = :operationKey AND createdAt = :createdAt"
    )
    suspend fun recordFailure(operationKey: String, createdAt: Long, error: String)

    @Query("DELETE FROM firestore_outbox WHERE uid = :uid")
    suspend fun deleteForAccount(uid: String)
}
