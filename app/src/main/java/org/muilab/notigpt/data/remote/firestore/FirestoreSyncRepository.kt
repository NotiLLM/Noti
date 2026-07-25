package org.muilab.notigpt.data.remote.firestore

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.ProposedOpRecord
import org.muilab.notigpt.util.SharedPreferencesManager
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/**
 * Firestore analytics sync.
 *
 * Storage (as requested):
 * - users/{userId}/savedItems/{savedItemId}
 * - users/{userId}
 */
class FirestoreSyncRepository(
    private val appContext: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val db: AppDatabase = AppDatabase.getInstance(appContext),
) {

    private val tag = "FirestoreSync"

    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    // Firestore data is partitioned per Firebase account; without a signed-in user every sync is a no-op.
    // Never fall back to a device identifier or a stale cached value.
    private fun userId(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    private fun usersDoc() = firestore
        .collection(FirestorePaths.COLLECTION_USERS)
        .document(userId())

    private fun savedItemDoc(savedItemId: String) = usersDoc()
        .collection(FirestorePaths.SUBCOLLECTION_SAVED_ITEMS)
        .document(savedItemId)

    private fun proposedOpRecordDoc(proposalId: String) = firestore
        .collection(FirestorePaths.COLLECTION_USERS)
        .document(userId())
        .collection(FirestorePaths.SUBCOLLECTION_PROPOSED_OP_RECORDS)
        .document(proposalId)

    /** Mirrors generated output and its decision state; it contains no source notification text. */
    suspend fun syncProposedOpRecord(proposal: ProposedOpRecord): Boolean = withContext(Dispatchers.IO) {
        if (userId().isBlank() || proposal.uid != userId()) return@withContext false
        try {
            proposedOpRecordDoc(proposal.proposalId).set(
                mapOf(
                    "proposalId" to proposal.proposalId,
                    "opId" to proposal.opId,
                    "batchId" to proposal.batchId,
                    "opType" to proposal.opType,
                    "payload" to proposal.payload,
                    "targetItemId" to proposal.targetItemId,
                    "itemType" to proposal.itemType,
                    "decision" to proposal.decision,
                    "createdAtMsEpoch" to proposal.createdAt,
                    "decisionAtMsEpoch" to proposal.decisionAt,
                    "schemaVersion" to 1,
                ),
                SetOptions.merge(),
            ).await()
            true
        } catch (t: Throwable) {
            Log.w(tag, "syncProposedOpRecord failed proposalId=${proposal.proposalId}", t)
            false
        }
    }

    suspend fun incrementNotiRecordCount() = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext
        try {
            // Merge an atomic increment; creates the doc/field if missing.
            usersDoc().set(
                mapOf(
                    "notiRecordCount" to FieldValue.increment(1),
                    "updatedAt" to TimeFormatters.toLocalIso(System.currentTimeMillis(), zoneId),
                ),
                SetOptions.merge()
            ).await()
            Log.d(tag, "incrementNotiRecordCount succeeded uid=${userId()}")
        } catch (t: Throwable) {
            Log.w(tag, "incrementNotiRecordCount failed", t)
        }
    }

    suspend fun ensureUserDoc() = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext
        try {
            val now = System.currentTimeMillis()
            val installMs = SharedPreferencesManager.installTimestampMs
            val payload: Map<String, Any?> = mapOf(
                "userId" to userId(),
                "timezoneId" to TimeZone.getDefault().id,
                "timezoneName" to TimeZone.getDefault().displayName,
                "locale" to Locale.getDefault().toLanguageTag(),
                // Ensure counter exists (won't overwrite if already present)
                "notiRecordCount" to FieldValue.increment(0),
                // Installation baseline
                "installedAt" to TimeFormatters.toLocalIso(installMs, zoneId),
                // Last seen
                "updatedAt" to TimeFormatters.toLocalIso(now, zoneId),
                "schemaVersion" to 2,
            )
            usersDoc().set(payload, SetOptions.merge()).await()
            Log.d(tag, "ensureUserDoc succeeded uid=${userId()}")
        } catch (t: Throwable) {
            Log.w(tag, "ensureUserDoc failed", t)
        }
    }

    /**
     * Marks the Firestore mirror of a hard-deleted item as deleted. Local deletion is a real row
     * delete, but the research record keeps the doc with a deletion marker; restore skips it.
     */
    suspend fun markSavedItemDeleted(savedItemId: String, ts: Long): Boolean = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext false
        try {
            savedItemDoc(savedItemId).set(
                mapOf(
                    "deleted" to true,
                    "deletedAt" to TimeFormatters.toLocalIso(ts, zoneId),
                    "deletedAtMsEpoch" to ts,
                ),
                SetOptions.merge()
            ).await()
            Log.d(tag, "markSavedItemDeleted succeeded savedItemId=$savedItemId uid=${userId()}")
            true
        } catch (t: Throwable) {
            Log.w(tag, "markSavedItemDeleted failed savedItemId=$savedItemId", t)
            false
        }
    }

    suspend fun syncSavedItem(item: SavedItem): Boolean = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext false
        ensureUserDoc()

        // Resolve provenance from the noti<->saved-item link table (single source of truth).
        val links = db.notiSavedItemLinkDao().getBySavedItemId(item.savedItemId)
        val linkedRecordIds = links.map { it.notiRecordId }.filter { it.isNotBlank() }.distinct()

        val now = System.currentTimeMillis()
        val payload: Map<String, Any?> = mapOf(
            "savedItemId" to item.savedItemId,
            "title" to item.title,
            "content" to item.content,
            "origin" to item.origin,
            "humanEditCount" to item.humanEditCount,
            "userEdited" to item.userEdited,
            "isTask" to FieldValue.delete(),
            "isTodo" to FieldValue.delete(),
            "isEvent" to FieldValue.delete(),
            "isCompleted" to item.isCompleted,
            "deadlineAtMs" to (if (item.deadlineAtMs > 0L) TimeFormatters.toLocalIso(item.deadlineAtMs, zoneId) else ""),
            "startAtMs" to FieldValue.delete(),
            "endAtMs" to FieldValue.delete(),
            "sourceNotiRecordIds" to linkedRecordIds,
            "sourceNotiRecordIdsCount" to linkedRecordIds.size,
            "isStarred" to item.isStarred,
            "whenAtMs" to FieldValue.delete(),
            "state" to item.state,
            "lastViewedChangeAt" to (if (item.lastViewedChangeAt > 0L) TimeFormatters.toLocalIso(item.lastViewedChangeAt, zoneId) else ""),
            "lastUpdateTimestamp" to TimeFormatters.toLocalIso(item.lastUpdateTimestamp, zoneId),
            "buttons" to item.buttons,
            "isViewed" to item.isViewed,
            "syncedAt" to TimeFormatters.toLocalIso(now, zoneId),
            // Raw epoch values: the ISO fields above are for human/analysis reads; restore uses these.
            "deadlineAtMsEpoch" to item.deadlineAtMs,
            "startAtMsEpoch" to FieldValue.delete(),
            "endAtMsEpoch" to FieldValue.delete(),
            "whenAtMsEpoch" to FieldValue.delete(),
            "doAtMs" to FieldValue.delete(),
            "doAtMsEpoch" to FieldValue.delete(),
            "lastUpdateTimestampEpoch" to item.lastUpdateTimestamp,
            "syncModifiedAt" to TimeFormatters.toLocalIso(item.syncModifiedAt, zoneId),
            "syncModifiedAtEpoch" to item.syncModifiedAt,
            "lastViewedChangeAtEpoch" to item.lastViewedChangeAt,
            "pendingTransformType" to item.pendingTransformType,
            "pendingTransformStatus" to item.pendingTransformStatus,
            "itemType" to item.itemType,
            "steps" to stepsPayload(item.savedItemId),
            // A normal sync is also the resurrection path for a locally edited item that was
            // previously marked deleted in Firestore.
            "deleted" to false,
            "deletedAt" to FieldValue.delete(),
            "deletedAtMsEpoch" to FieldValue.delete(),
            "subTasks" to FieldValue.delete(),
            "schemaVersion" to 8,
        )

        try {
            savedItemDoc(item.savedItemId).set(payload, SetOptions.merge()).await()
            Log.d(tag, "syncSavedItem succeeded savedItemId=${item.savedItemId} uid=${userId()}")
        } catch (t: Throwable) {
            Log.w(tag, "syncSavedItem failed savedItemId=${item.savedItemId}", t)
            return@withContext false
        }

        // Source record IDs remain as association metadata on the generated item. Raw notification
        // titles, bodies, people, messages, and snapshots are intentionally never uploaded.
        true
    }

    private suspend fun stepsPayload(savedItemId: String): List<Map<String, Any?>> = try {
        db.todoStepDao().getBySavedItemId(savedItemId).map { st ->
            mapOf(
                "todoStepId" to st.todoStepId,
                "text" to st.text,
                "isCompleted" to st.isCompleted,
                "position" to st.position,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Replays the complete local mirror to Firestore.
     *
     * This is used for first-time bootstrap and as a periodic retry after a device has been offline.
     * Individual writes remain best-effort so a transient failure does not block the local app; the
     * next run replays the complete current Room snapshot.
     */
    suspend fun syncAllLocalData() = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext

        syncAllLocalSavedItems()
        syncPreferencesAndContexts()
    }

    /** Uploads the local SavedItem mirror without replacing cloud-owned preference/context state. */
    suspend fun syncAllLocalSavedItems() = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext

        ensureUserDoc()
        val items = db.savedItemDao().getAll()
        items.forEach { syncSavedItem(it) }
        Log.i(tag, "syncAllLocalSavedItems complete uid=${userId()} savedItems=${items.size}")
    }

    /**
     * Mirrors the full extraction-preference and user-context sets onto users/{uid}.
     *
     * Both sets are small (tens of rows), so whole-set replacement keeps the cloud copy trivially
     * consistent with Room after any mutation.
     */
    suspend fun syncPreferencesAndContexts(): Boolean = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext false
        try {
            val general = db.generalPreferenceDao().getAll().map { preference ->
                mapOf(
                    "id" to preference.id,
                    "statement" to preference.statement,
                    "createdAt" to preference.createdAt,
                    "updatedAt" to preference.updatedAt,
                )
            }
            val prefs = db.extractionPreferenceDao().getAll().map { p ->
                mapOf(
                    "id" to p.id,
                    "statement" to p.statement,
                    "createdAt" to p.createdAt,
                    "updatedAt" to p.updatedAt,
                )
            }
            val knowledge = db.userKnowledgeDao().getAll().map { item ->
                mapOf(
                    "id" to item.id,
                    "statement" to item.statement,
                    "createdAt" to item.createdAt,
                    "updatedAt" to item.updatedAt,
                )
            }
            usersDoc().set(
                mapOf(
                    "generalPreferences" to general,
                    "extractionPreferences" to prefs,
                    "userKnowledge" to knowledge,
                    "updatedAt" to TimeFormatters.toLocalIso(System.currentTimeMillis(), zoneId),
                ),
                SetOptions.merge(),
            ).await()
            Log.d(tag, "syncPreferencesAndContexts succeeded uid=${userId()}")
            true
        } catch (t: Throwable) {
            Log.w(tag, "syncPreferencesAndContexts failed", t)
            false
        }
    }
}
