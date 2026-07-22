package org.muilab.notigpt.data.repository

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.AppDatabase

/**
 * Executes the two user-visible destructive data operations as separate privacy boundaries.
 *
 * Local notification history contains raw content and never requires a network call. Cloud deletion
 * removes generated/account-owned documents first and clears corresponding local generated state
 * only after Firestore confirms, preventing an offline partial delete from silently losing local data.
 */
@Singleton
class DataDeletionRepository @Inject constructor(
    private val database: AppDatabase,
) {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun clearLocalNotificationHistory() = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.notiSavedItemLinkDao().deleteAll()
            database.reminderDao().deleteAllNotiRecordRefs()
            database.extractionJournalDao().deleteAllEntries()
            database.extractionJournalDao().deleteAllSummaries()
            database.notiLlmStateDao().deleteAll()
            database.actionDao().deleteAll()
            database.recordDao().deleteAll()
            database.drawerDao().hardDeleteAll()
        }
    }

    suspend fun deleteCloudAndLocalGeneratedData(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid?.takeIf(String::isNotBlank)
                ?: error("No signed-in account")
            val userDoc = firestore.collection(USERS).document(uid)
            userDoc.collection(SAVED_ITEMS).get().await().documents.forEach { item ->
                item.reference.delete().await()
            }
            userDoc.collection(PROPOSED_OP_RECORDS).get().await().documents.forEach { record ->
                record.reference.delete().await()
            }
            userDoc.delete().await()

            clearLocalGeneratedData(uid)
        }
    }

    private suspend fun clearLocalGeneratedData(uid: String) {
        database.withTransaction {
            database.savedItemDao().deleteAllForAccountSwitch()
            database.todoStepDao().deleteAllForAccountSwitch()
            database.pendingProposedOpDao().deleteAllForAccountSwitch()
            database.pendingReviewDraftDao().deleteAll()
            database.rejectedMergeDao().deleteAllForAccountSwitch()
            database.extractionJournalDao().deleteAllEntries()
            database.extractionJournalDao().deleteAllSummaries()
            database.notiLlmStateDao().deleteAll()
            database.extractionPreferenceDao().deleteAll()
            database.preferenceConflictDao().deleteAll()
            database.userContextDao().deleteAll()
            database.proposedOpRecordDao().deleteForAccount(uid)
            database.reminderDao().deleteAllSavedItemRefs()
            database.reminderDao().deleteAllSavedItemReminders()
            database.firestoreOutboxDao().deleteForAccount(uid)
        }
    }

    private companion object {
        const val USERS = "users"
        const val SAVED_ITEMS = "savedItems"
        const val PROPOSED_OP_RECORDS = "proposedOpRecords"
    }
}
