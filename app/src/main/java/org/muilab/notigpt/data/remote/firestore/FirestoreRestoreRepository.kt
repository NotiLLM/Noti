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
 * Restore-on-login: pulls the signed-in account's saved items (with sub-tasks), extraction
 * preferences, and user contexts from Firestore into Room.
 *
 * Room remains the source of truth after restore — every local mutation pushes back up via
 * [FirestoreSyncRepository]. Restore only runs when the local saved_item table is empty (fresh
 * install, or right after an account-switch wipe), so it never merges into existing local data.
 * Notification links/journal are device-bound and are not restored.
 */
class FirestoreRestoreRepository(
    private val appContext: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val db: AppDatabase = AppDatabase.getInstance(appContext),
) {

    private val tag = "FirestoreRestore"

    suspend fun restoreIfLocalEmpty() = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext
        val hasLocal = try {
            db.reminderListDao().getAllVisible().isNotEmpty()
        } catch (_: Exception) {
            true // if in doubt, never clobber
        }
        if (hasLocal) {
            Log.d(tag, "Local data present; skipping restore")
            return@withContext
        }

        try {
            restoreReminders(uid)
            restorePreferencesAndContexts(uid)
            Log.d(tag, "Restore complete for uid=$uid")
        } catch (t: Throwable) {
            Log.w(tag, "Restore failed", t)
        }
    }

    private suspend fun restoreReminders(uid: String) {
        val docs = firestore
            .collection(FirestorePaths.COLLECTION_REMINDERS_ROOT)
            .document(uid)
            .collection(FirestorePaths.SUBCOLLECTION_REMINDERS)
            .get()
            .await()
            .documents

        for (doc in docs) {
            val item = savedItemFrom(doc) ?: continue
            db.reminderListDao().upsert(item)
            subItemsFrom(doc, item.savedItemId).forEach { db.subTaskDao().upsert(it) }
        }
        Log.d(tag, "Restored ${docs.size} reminder docs")
    }

    private fun savedItemFrom(doc: DocumentSnapshot): SavedItem? {
        val id = doc.getString("savedItemId") ?: doc.id
        if (id.isBlank()) return null
        // Deleted items stay in the cloud for analysis but are not resurrected locally.
        if (doc.getBoolean("isVisible") == false) return null
        val deletedAt = doc.getLong("deletedAtMsEpoch") ?: 0L
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
            estimatedCompletionTime = doc.getLong("estimatedCompletionTime") ?: 0L,
            origin = doc.getString("origin") ?: "manual",
            humanEditCount = (doc.getLong("humanEditCount") ?: 0L).toInt(),
            deletedAtMs = if (deletedAt > 0L) deletedAt else null,
            userEdited = doc.getBoolean("userEdited") ?: false,
            isVisible = true,
            buttons = doc.getString("buttons") ?: "[]",
            isViewed = doc.getBoolean("isViewed") ?: true,
            sortScore = (doc.getDouble("sortScore") ?: 50.0).toFloat(),
            reRankHistory = doc.getString("reRankHistory") ?: "[]",
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
    private suspend fun restorePreferencesAndContexts(uid: String) {
        val userDoc = firestore
            .collection(FirestorePaths.COLLECTION_USERS)
            .document(uid)
            .get()
            .await()

        (userDoc.get("extractionPreferences") as? List<Map<String, Any?>>)?.forEach { m ->
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
        (userDoc.get("userContexts") as? List<Map<String, Any?>>)?.forEach { m ->
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
}
