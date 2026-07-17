package org.muilab.notigpt.data.remote.firestore

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
import org.muilab.notigpt.domain.saveditem.SavedItemNormalization
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            val remoteDocs = fetchSavedItemDocs(uid)
            val localItems = db.savedItemDao().getAll()
            val sync = FirestoreSyncRepository(appContext)

            when {
                localItems.isEmpty() -> {
                    restoreSavedItems(remoteDocs)
                    val cloudHasUserState = restorePreferencesAndContexts(uid)
                    if (remoteDocs.isEmpty()) {
                        sync.syncAllLocalSavedItems()
                        if (!cloudHasUserState) sync.syncPreferencesAndContexts()
                    } else if (!cloudHasUserState) {
                        sync.syncPreferencesAndContexts()
                    }
                }

                remoteDocs.isEmpty() -> {
                    // This is the important first-connect/backfill path for existing local data.
                    val cloudHasUserState = restorePreferencesAndContexts(uid)
                    sync.syncAllLocalSavedItems()
                    if (!cloudHasUserState) sync.syncPreferencesAndContexts()
                }

                else -> {
                    reconcileSavedItems(remoteDocs, localItems, sync)
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

    private suspend fun fetchSavedItemDocs(uid: String): List<DocumentSnapshot> {
        val destination = firestore
            .collection(FirestorePaths.COLLECTION_USERS)
            .document(uid)
            .collection(FirestorePaths.SUBCOLLECTION_SAVED_ITEMS)
        val legacy = firestore
            .collection(FirestorePaths.LEGACY_COLLECTION_SAVED_ITEMS_ROOT)
            .document(uid)
            .collection(FirestorePaths.LEGACY_SUBCOLLECTION_SAVED_ITEMS)

        val currentById = destination.get().await().documents.associateBy { it.id }.toMutableMap()
        for (legacyDoc in legacy.get().await().documents) {
            try {
                val current = currentById[legacyDoc.id]
                val legacyTimestamp = maxOf(cloudUpdateTimestamp(legacyDoc), cloudDeletionTimestamp(legacyDoc))
                val currentTimestamp = current?.let {
                    maxOf(cloudUpdateTimestamp(it), cloudDeletionTimestamp(it))
                } ?: Long.MIN_VALUE
                val target = destination.document(legacyDoc.id)

                if (current == null || legacyTimestamp > currentTimestamp) {
                    target.set(legacyDoc.data.orEmpty(), SetOptions.merge()).await()
                }

                // Delete only after the destination can be read back. Failed copies remain in the
                // legacy collection and will be retried on the next reconciliation.
                val verified = target.get().await()
                if (verified.exists()) {
                    legacyDoc.reference.delete().await()
                    currentById[legacyDoc.id] = verified
                    Log.i(tag, "Migrated legacy SavedItem savedItemId=${legacyDoc.id} uid=$uid")
                }
            } catch (t: Throwable) {
                Log.w(tag, "Legacy SavedItem migration deferred savedItemId=${legacyDoc.id}", t)
            }
        }

        val docs = destination.get().await().documents
        Log.d(tag, "Fetched ${docs.size} cloud SavedItem docs uid=$uid")
        return docs
    }

    private suspend fun restoreSavedItems(docs: List<DocumentSnapshot>) {
        for (doc in docs) restoreOneSavedItem(doc)
        Log.d(tag, "Restored ${docs.size} SavedItem docs")
    }

    private suspend fun restoreOneSavedItem(doc: DocumentSnapshot) {
        val restoredItem = savedItemFrom(doc) ?: return
        val restoredSubItems = subItemsFrom(doc, restoredItem.savedItemId)
        val itemWithChildButtons = restoredItem.copy(
            buttons = SavedItemNormalization.mergeButtons(
                restoredItem.buttons,
                *legacyChildButtons(doc).toTypedArray(),
            ),
        )
        val normalized = SavedItemNormalization.normalize(itemWithChildButtons, restoredSubItems)
        // Remove stale local sub-items before applying the cloud snapshot.
        db.subTaskDao().hardDeleteByParentId(normalized.item.savedItemId)
        db.savedItemDao().upsert(normalized.item)
        normalized.subItems.forEach { db.subTaskDao().upsert(it) }
    }

    private suspend fun reconcileSavedItems(
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
                local == null && !isCloudDeleted(doc) -> restoreOneSavedItem(doc)
                local != null && isCloudDeleted(doc) -> {
                    val cloudDeletedAt = cloudDeletionTimestamp(doc)
                    if (cloudDeletedAt <= 0L || cloudDeletedAt >= local.lastUpdateTimestamp) {
                        db.subTaskDao().hardDeleteByParentId(id)
                        db.savedItemDao().hardDeleteById(id)
                        Log.i(tag, "Removed locally deleted cloud item savedItemId=$id")
                    } else {
                        // A newer local edit intentionally resurrects the cloud mirror.
                        sync.syncSavedItem(local)
                    }
                }
                local != null -> {
                    val cloudUpdatedAt = cloudUpdateTimestamp(doc)
                    when {
                        cloudUpdatedAt > local.lastUpdateTimestamp -> restoreOneSavedItem(doc)
                        local.lastUpdateTimestamp > cloudUpdatedAt -> sync.syncSavedItem(local)
                        else -> restoreOneSavedItem(doc)
                    }
                }
            }
        }

        // A document absent from the cloud may simply be a local item created while offline.
        localItems
            .filter { it.savedItemId !in remoteIds }
            .forEach { sync.syncSavedItem(it) }
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
            whenAtMs = doc.getLong("whenAtMsEpoch") ?: doc.getLong("doAtMsEpoch") ?: 0L,
            lastViewedChangeAt = doc.getLong("lastViewedChangeAtEpoch") ?: 0L,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun subItemsFrom(doc: DocumentSnapshot, parentId: String): List<SavedSubItem> {
        val raw = doc.get("subTasks") as? List<Map<String, Any?>> ?: return emptyList()
        return raw.mapIndexedNotNull { index, m ->
            val id = m["savedSubItemId"] as? String ?: return@mapIndexedNotNull null
            val text = legacySubItemText(m)
            if (text.isBlank()) return@mapIndexedNotNull null
            SavedSubItem(
                savedSubItemId = id,
                parentSavedItemId = parentId,
                text = text,
                isCompleted = m["isCompleted"] as? Boolean ?: false,
                position = ((m["position"] ?: m["sortOrder"]) as? Number)?.toInt() ?: index,
            )
        }.sortedWith(compareBy<SavedSubItem> { it.position }.thenBy { it.savedSubItemId })
            .mapIndexed { index, child -> child.copy(position = index) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun legacyChildButtons(doc: DocumentSnapshot): List<String> {
        val raw = doc.get("subTasks") as? List<Map<String, Any?>> ?: return emptyList()
        return raw.mapNotNull { it["buttons"] as? String }.filter { it.isNotBlank() && it != "[]" }
    }

    private fun legacySubItemText(map: Map<String, Any?>): String {
        val parts = mutableListOf<String>()
        val currentText = map["text"] as? String
        if (!currentText.isNullOrBlank()) {
            parts += currentText
        } else {
            (map["title"] as? String)?.takeIf { it.isNotBlank() }?.let(parts::add)
            (map["description"] as? String)?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        val zh = Locale.getDefault().language.startsWith("zh")
        listOf(
            "deadlineAtMsEpoch" to if (zh) "截止時間" else "Deadline",
            "startAtMsEpoch" to if (zh) "開始時間" else "Start",
            "endAtMsEpoch" to if (zh) "結束時間" else "End",
        ).forEach { (key, label) ->
            val value = (map[key] as? Number)?.toLong() ?: 0L
            if (value > 0L) {
                val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
                parts += "$label: $formatted"
            }
        }
        return SavedSubItem.normalizeText(parts.joinToString(" — "))
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
