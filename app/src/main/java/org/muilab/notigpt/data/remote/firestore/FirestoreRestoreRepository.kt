package org.muilab.notigpt.data.remote.firestore

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.model.features.ExtractionPreference
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedSubItem
import org.muilab.notigpt.model.features.UserContext

/**
 * Login/periodic reconciliation for the signed-in account's saved items, extraction preferences,
 * and user contexts.
 *
 * The newer snapshot wins for rows present on both sides. Local-only rows are uploaded so a device
 * that was offline for a long time can bootstrap its cloud mirror. [SavedItem.userEdited] remains
 * provenance/audit metadata; it is not a sync conflict veto.
 * Notification links/journal are device-bound and are not restored.
 */
class FirestoreRestoreRepository(
    private val appContext: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val db: AppDatabase = AppDatabase.getInstance(appContext),
) {

    private val tag = "FirestoreRestore"

    /** Backward-compatible entry point; sign-in now performs reconciliation rather than a one-way restore. */
    suspend fun restoreIfLocalEmpty() = reconcileAfterSignIn()

    /**
     * Reconciles local Room state with the signed-in account's Firestore mirror.
     *
     * - Empty local + populated cloud: restore cloud data.
     * - Populated local + empty cloud: backfill the cloud mirror from Room.
     * - Both populated: the newer item snapshot wins; local-only items are uploaded.
     */
    suspend fun reconcileAfterSignIn() = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext

        try {
            val remoteDocs = fetchReminderDocs(uid)
            val localItems = db.reminderListDao().getAll()
            val sync = FirestoreSyncRepository(appContext)

            when {
                localItems.isEmpty() -> {
                    restoreReminders(remoteDocs)
                    val cloudHasUserState = restorePreferencesAndContexts(uid)
                    if (remoteDocs.isEmpty()) {
                        sync.syncAllLocalReminders()
                        if (!cloudHasUserState) sync.syncPreferencesAndContexts()
                    } else if (!cloudHasUserState) {
                        sync.syncPreferencesAndContexts()
                    }
                }

                remoteDocs.isEmpty() -> {
                    // This is the important first-connect/backfill path for existing local data.
                    val cloudHasUserState = restorePreferencesAndContexts(uid)
                    sync.syncAllLocalReminders()
                    if (!cloudHasUserState) sync.syncPreferencesAndContexts()
                }

                else -> {
                    reconcileReminders(remoteDocs, localItems, sync)
                    if (!restorePreferencesAndContexts(uid)) {
                        sync.syncPreferencesAndContexts()
                    }
                }
            }

            Log.i(tag, "Reconciliation complete uid=$uid local=${localItems.size} cloud=${remoteDocs.size}")
        } catch (t: Throwable) {
            Log.w(tag, "Reconciliation failed uid=$uid", t)
        }
    }

    private suspend fun fetchReminderDocs(uid: String): List<DocumentSnapshot> {
        val docs = firestore
            .collection(FirestorePaths.COLLECTION_REMINDERS_ROOT)
            .document(uid)
            .collection(FirestorePaths.SUBCOLLECTION_REMINDERS)
            .get()
            .await()
            .documents
        Log.d(tag, "Fetched ${docs.size} cloud reminder docs uid=$uid")
        return docs
    }

    private suspend fun restoreReminders(docs: List<DocumentSnapshot>) {
        for (doc in docs) restoreOneReminder(doc)
        Log.d(tag, "Restored ${docs.size} reminder docs")
    }

    private suspend fun restoreOneReminder(doc: DocumentSnapshot) {
        val item = savedItemFrom(doc) ?: return
        // Remove stale local sub-items before applying the cloud snapshot.
        db.subTaskDao().hardDeleteByParentId(item.savedItemId)
        db.reminderListDao().upsert(item)
        subItemsFrom(doc, item.savedItemId).forEach { db.subTaskDao().upsert(it) }
    }

    private suspend fun reconcileReminders(
        remoteDocs: List<DocumentSnapshot>,
        localItems: List<SavedItem>,
        sync: FirestoreSyncRepository,
    ) {
        val localById = localItems.associateBy { it.savedItemId }
        val remoteIds = mutableSetOf<String>()

        for (doc in remoteDocs) {
            val id = doc.getString("savedItemId") ?: doc.id
            if (id.isBlank()) continue
            remoteIds += id

            val local = localById[id]
            when {
                local == null && !isCloudDeleted(doc) -> restoreOneReminder(doc)
                local != null && isCloudDeleted(doc) -> {
                    val cloudDeletedAt = cloudDeletionTimestamp(doc)
                    if (cloudDeletedAt <= 0L || cloudDeletedAt >= local.lastUpdateTimestamp) {
                        db.subTaskDao().hardDeleteByParentId(id)
                        db.reminderListDao().hardDeleteById(id)
                        Log.i(tag, "Removed locally deleted cloud item savedItemId=$id")
                    } else {
                        // A newer local edit intentionally resurrects the cloud mirror.
                        sync.syncReminder(local)
                    }
                }
                local != null -> {
                    val cloudUpdatedAt = cloudUpdateTimestamp(doc)
                    when {
                        cloudUpdatedAt > local.lastUpdateTimestamp -> restoreOneReminder(doc)
                        local.lastUpdateTimestamp > cloudUpdatedAt -> sync.syncReminder(local)
                        else -> restoreOneReminder(doc)
                    }
                }
            }
        }

        // A document absent from the cloud may simply be a local item created while offline.
        localItems
            .filter { it.savedItemId !in remoteIds }
            .forEach { sync.syncReminder(it) }
    }

    private fun isCloudDeleted(doc: DocumentSnapshot): Boolean =
        doc.getBoolean("deleted") == true ||
            doc.getBoolean("isVisible") == false ||
            (doc.getLong("deletedAtMsEpoch") ?: 0L) > 0L

    /** Missing legacy timestamps sort before any current local item, preserving local data. */
    private fun cloudUpdateTimestamp(doc: DocumentSnapshot): Long =
        doc.getLong("lastUpdateTimestampEpoch") ?: 0L

    /** A deletion without an epoch marker is still authoritative for safety. */
    private fun cloudDeletionTimestamp(doc: DocumentSnapshot): Long =
        doc.getLong("deletedAtMsEpoch") ?: 0L

    private fun savedItemFrom(doc: DocumentSnapshot): SavedItem? {
        val id = doc.getString("savedItemId") ?: doc.id
        if (id.isBlank()) return null
        // Deleted items stay in the cloud for analysis but are not resurrected locally.
        // "deleted" is the current marker; "isVisible == false" covers docs from schema <= 5.
        if (doc.getBoolean("deleted") == true) return null
        if (doc.getBoolean("isVisible") == false) return null
        if ((doc.getLong("deletedAtMsEpoch") ?: 0L) > 0L) return null
        return SavedItem(
            savedItemId = id,
            title = doc.getString("title").orEmpty(),
            content = doc.getString("content").orEmpty(),
            itemType = doc.getString("itemType")
                ?: if (doc.getBoolean("isTask") != false) SavedItemType.Task else SavedItemType.Keep,
            state = doc.getString("state") ?: SavedItemState.Saved,
            lastUpdateTimestamp = doc.getLong("lastUpdateTimestampEpoch") ?: System.currentTimeMillis(),
            deadlineAtMs = doc.getLong("deadlineAtMsEpoch") ?: -1L,
            startAtMs = doc.getLong("startAtMsEpoch") ?: 0L,
            endAtMs = doc.getLong("endAtMsEpoch") ?: 0L,
            origin = doc.getString("origin") ?: "manual",
            humanEditCount = (doc.getLong("humanEditCount") ?: 0L).toInt(),
            userEdited = doc.getBoolean("userEdited") ?: false,
            buttons = doc.getString("buttons") ?: "[]",
            isViewed = doc.getBoolean("isViewed") ?: true,
            isStarred = doc.getBoolean("isStarred") ?: false,
            doAtMs = doc.getLong("doAtMsEpoch") ?: 0L,
            lastViewedChangeAt = doc.getLong("lastViewedChangeAtEpoch") ?: 0L,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun subItemsFrom(doc: DocumentSnapshot, parentId: String): List<SavedSubItem> {
        val raw = doc.get("subTasks") as? List<Map<String, Any?>> ?: return emptyList()
        return raw.mapNotNull { m ->
            val id = m["savedSubItemId"] as? String ?: return@mapNotNull null
            SavedSubItem(
                savedSubItemId = id,
                parentSavedItemId = parentId,
                title = m["title"] as? String ?: "",
                description = m["description"] as? String ?: "",
                itemType = m["itemType"] as? String ?: SavedItemType.Task,
                isCompleted = m["isCompleted"] as? Boolean ?: false,
                deadlineAtMs = (m["deadlineAtMsEpoch"] as? Number)?.toLong() ?: 0L,
                startAtMs = (m["startAtMsEpoch"] as? Number)?.toLong() ?: 0L,
                endAtMs = (m["endAtMsEpoch"] as? Number)?.toLong() ?: 0L,
                buttons = m["buttons"] as? String ?: "[]",
                sortOrder = (m["sortOrder"] as? Number)?.toInt() ?: 0,
                createdAt = (m["createdAtEpoch"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                lastUpdateTimestamp = (m["lastUpdateTimestampEpoch"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                isVisible = true,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun restorePreferencesAndContexts(uid: String): Boolean {
        val userDoc = firestore
            .collection(FirestorePaths.COLLECTION_USERS)
            .document(uid)
            .get()
            .await()

        val hasCloudState = userDoc.contains("extractionPreferences") || userDoc.contains("userContexts")

        if (userDoc.contains("extractionPreferences")) {
            val preferences = (userDoc.get("extractionPreferences") as? List<Map<String, Any?>>).orEmpty()
            db.extractionPreferenceDao().deleteAll()
            preferences.forEach { m ->
                val id = m["id"] as? String ?: return@forEach
                db.extractionPreferenceDao().upsertPreference(
                    ExtractionPreference(
                        id = id,
                        statement = m["statement"] as? String ?: "",
                        preferenceType = m["preferenceType"] as? String ?: "",
                        createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    )
                )
            }
        }
        if (userDoc.contains("userContexts")) {
            val contexts = (userDoc.get("userContexts") as? List<Map<String, Any?>>).orEmpty()
            db.userContextDao().deleteAll()
            contexts.forEach { m ->
                val id = m["id"] as? String ?: return@forEach
                db.userContextDao().upsertContext(
                    UserContext(
                        id = id,
                        statement = m["statement"] as? String ?: "",
                        category = m["category"] as? String ?: "",
                        createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    )
                )
            }
        }
        return hasCloudState
    }
}
