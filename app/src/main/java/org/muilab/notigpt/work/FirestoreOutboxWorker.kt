package org.muilab.notigpt.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.remote.firestore.FirestoreSyncRepository
import org.muilab.notigpt.model.features.FirestoreOutboxKind

/** Replays durable ID-only operations until the signed-in account's Firestore mirror converges. */
@HiltWorker
class FirestoreOutboxWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val database: AppDatabase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) return Result.success()

        val dao = database.firestoreOutboxDao()
        val sync = FirestoreSyncRepository(applicationContext, db = database)
        repeat(MAX_BATCHES_PER_RUN) {
            val pending = dao.getPending(uid)
            if (pending.isEmpty()) return Result.success()
            for (operation in pending) {
                val succeeded = when (operation.kind) {
                    FirestoreOutboxKind.UpsertSavedItem -> {
                        val item = database.savedItemDao().getById(operation.entityId)
                        item == null || sync.syncSavedItem(item)
                    }
                    FirestoreOutboxKind.DeleteSavedItem ->
                        sync.markSavedItemDeleted(operation.entityId, operation.createdAt)
                    FirestoreOutboxKind.SyncProposedOpRecord -> {
                        val proposal = database.proposedOpRecordDao().getById(operation.entityId)
                        proposal == null || sync.syncProposedOpRecord(proposal)
                    }
                    else -> true // Unknown old operation: discard instead of retrying forever.
                }
                if (succeeded) {
                    // Do not delete a newer mutation that replaced this row while its network call ran.
                    dao.deleteIfUnchanged(operation.operationKey, operation.createdAt)
                } else {
                    dao.recordFailure(operation.operationKey, operation.createdAt, "Firestore operation failed")
                    return Result.retry()
                }
            }
        }
        // Yield after a bounded drain; WorkManager backoff prevents a giant queue monopolizing IO.
        return Result.retry()
    }

    private companion object {
        const val MAX_BATCHES_PER_RUN = 10
    }
}

/** Scheduling facade used after a transaction records one or more outbox rows. */
object FirestoreOutboxWork {
    private const val UNIQUE_NAME = "firestore-id-only-outbox"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<FirestoreOutboxWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            // Append while a drain is running so a mutation arriving after its final query cannot
            // be stranded merely because KEEP ignored the new wake-up request.
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
