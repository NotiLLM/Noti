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
import org.muilab.notigpt.model.features.GeneratedProposal
import org.muilab.notigpt.util.SharedPreferencesManager
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/**
 * Firestore analytics sync.
 *
 * Storage (as requested):
 * - reminders/{userId}/reminders/{savedItemId}
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

    private fun reminderDoc(savedItemId: String) = firestore
        .collection(FirestorePaths.COLLECTION_REMINDERS_ROOT)
        .document(userId())
        .collection(FirestorePaths.SUBCOLLECTION_REMINDERS)
        .document(savedItemId)

    private fun generatedProposalDoc(proposalId: String) = firestore
        .collection(FirestorePaths.COLLECTION_GENERATED_PROPOSALS_ROOT)
        .document(userId())
        .collection(FirestorePaths.SUBCOLLECTION_PROPOSALS)
        .document(proposalId)

    /** Mirrors generated output and its decision state; it contains no source notification text. */
    suspend fun syncGeneratedProposal(proposal: GeneratedProposal): Boolean = withContext(Dispatchers.IO) {
        if (userId().isBlank() || proposal.uid != userId()) return@withContext false
        try {
            generatedProposalDoc(proposal.proposalId).set(
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
            Log.w(tag, "syncGeneratedProposal failed proposalId=${proposal.proposalId}", t)
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
    suspend fun markReminderDeleted(savedItemId: String, ts: Long): Boolean = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext false
        try {
            reminderDoc(savedItemId).set(
                mapOf(
                    "deleted" to true,
                    "deletedAt" to TimeFormatters.toLocalIso(ts, zoneId),
                    "deletedAtMsEpoch" to ts,
                ),
                SetOptions.merge()
            ).await()
            Log.d(tag, "markReminderDeleted succeeded savedItemId=$savedItemId uid=${userId()}")
            true
        } catch (t: Throwable) {
            Log.w(tag, "markReminderDeleted failed savedItemId=$savedItemId", t)
            false
        }
    }

    suspend fun syncReminder(reminder: SavedItem): Boolean = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext false
        ensureUserDoc()

        // Resolve provenance from the noti<->saved-item link table (single source of truth).
        val links = db.notiSavedItemLinkDao().getBySavedItemId(reminder.savedItemId)
        val linkedRecordIds = links.map { it.notiRecordId }.filter { it.isNotBlank() }.distinct()

        val now = System.currentTimeMillis()
        val payload: Map<String, Any?> = mapOf(
            "savedItemId" to reminder.savedItemId,
            "title" to reminder.title,
            "content" to reminder.content,
            "origin" to reminder.origin,
            "humanEditCount" to reminder.humanEditCount,
            "userEdited" to reminder.userEdited,
            "isTask" to reminder.isTask,
            "isEvent" to reminder.isEvent,
            "isCompleted" to reminder.isCompleted,
            "deadlineAtMs" to (if (reminder.deadlineAtMs > 0L) TimeFormatters.toLocalIso(reminder.deadlineAtMs, zoneId) else ""),
            "startAtMs" to (if (reminder.startAtMs > 0L) TimeFormatters.toLocalIso(reminder.startAtMs, zoneId) else ""),
            "endAtMs" to (if (reminder.endAtMs > 0L) TimeFormatters.toLocalIso(reminder.endAtMs, zoneId) else ""),
            "sourceNotiRecordIds" to linkedRecordIds,
            "sourceNotiRecordIdsCount" to linkedRecordIds.size,
            "isStarred" to reminder.isStarred,
            // "someday" sentinel (Long.MAX_VALUE) is not a real instant — formatting it would throw;
            // the raw value still round-trips via doAtMsEpoch below.
            "doAtMs" to when {
                SavedItem.isSomeday(reminder.doAtMs) -> "someday"
                reminder.doAtMs > 0L -> TimeFormatters.toLocalIso(reminder.doAtMs, zoneId)
                else -> ""
            },
            "state" to reminder.state,
            "lastViewedChangeAt" to (if (reminder.lastViewedChangeAt > 0L) TimeFormatters.toLocalIso(reminder.lastViewedChangeAt, zoneId) else ""),
            "lastUpdateTimestamp" to TimeFormatters.toLocalIso(reminder.lastUpdateTimestamp, zoneId),
            "buttons" to reminder.buttons,
            "isViewed" to reminder.isViewed,
            "syncedAt" to TimeFormatters.toLocalIso(now, zoneId),
            // Raw epoch values: the ISO fields above are for human/analysis reads; restore uses these.
            "deadlineAtMsEpoch" to reminder.deadlineAtMs,
            "startAtMsEpoch" to reminder.startAtMs,
            "endAtMsEpoch" to reminder.endAtMs,
            "doAtMsEpoch" to reminder.doAtMs,
            "lastUpdateTimestampEpoch" to reminder.lastUpdateTimestamp,
            "lastViewedChangeAtEpoch" to reminder.lastViewedChangeAt,
            "itemType" to reminder.itemType,
            "subTasks" to subTasksPayload(reminder.savedItemId),
            // A normal sync is also the resurrection path for a locally edited item that was
            // previously marked deleted in Firestore.
            "deleted" to false,
            "deletedAt" to FieldValue.delete(),
            "deletedAtMsEpoch" to FieldValue.delete(),
            "schemaVersion" to 6,
        )

        try {
            reminderDoc(reminder.savedItemId).set(payload, SetOptions.merge()).await()
            Log.d(tag, "syncReminder succeeded savedItemId=${reminder.savedItemId} uid=${userId()}")
        } catch (t: Throwable) {
            Log.w(tag, "syncReminder failed savedItemId=${reminder.savedItemId}", t)
            return@withContext false
        }

        // Source record IDs remain as association metadata on the generated item. Raw notification
        // titles, bodies, people, messages, and snapshots are intentionally never uploaded.
        true
    }

    private suspend fun subTasksPayload(savedItemId: String): List<Map<String, Any?>> = try {
        db.subTaskDao().getByReminderId(savedItemId).map { st ->
            mapOf(
                "savedSubItemId" to st.savedSubItemId,
                "title" to st.title,
                "description" to st.description,
                "itemType" to st.itemType,
                "isCompleted" to st.isCompleted,
                "deadlineAtMsEpoch" to st.deadlineAtMs,
                "startAtMsEpoch" to st.startAtMs,
                "endAtMsEpoch" to st.endAtMs,
                "buttons" to st.buttons,
                "sortOrder" to st.sortOrder,
                "createdAtEpoch" to st.createdAt,
                "lastUpdateTimestampEpoch" to st.lastUpdateTimestamp,
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

        syncAllLocalReminders()
        syncPreferencesAndContexts()
    }

    /** Uploads the local reminder mirror without replacing cloud-owned preference/context state. */
    suspend fun syncAllLocalReminders() = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext

        ensureUserDoc()
        val items = db.reminderListDao().getAll()
        items.forEach { syncReminder(it) }
        Log.i(tag, "syncAllLocalReminders complete uid=${userId()} reminders=${items.size}")
    }

    /**
     * Mirrors the full extraction-preference and user-context sets onto users/{uid}.
     *
     * Both sets are small (tens of rows), so whole-set replacement keeps the cloud copy trivially
     * consistent with Room after any mutation.
     */
    suspend fun syncPreferencesAndContexts() = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext
        try {
            val prefs = db.extractionPreferenceDao().getAllPreferences().map { p ->
                mapOf(
                    "id" to p.id,
                    "statement" to p.statement,
                    "preferenceType" to p.preferenceType,
                    "createdAt" to p.createdAt,
                    "updatedAt" to p.updatedAt,
                )
            }
            val contexts = db.userContextDao().getAllContexts().map { c ->
                mapOf(
                    "id" to c.id,
                    "statement" to c.statement,
                    "category" to c.category,
                    "createdAt" to c.createdAt,
                    "updatedAt" to c.updatedAt,
                )
            }
            usersDoc().set(
                mapOf(
                    "extractionPreferences" to prefs,
                    "userContexts" to contexts,
                    "updatedAt" to TimeFormatters.toLocalIso(System.currentTimeMillis(), zoneId),
                ),
                SetOptions.merge(),
            ).await()
            Log.d(tag, "syncPreferencesAndContexts succeeded uid=${userId()}")
        } catch (t: Throwable) {
            Log.w(tag, "syncPreferencesAndContexts failed", t)
        }
    }
}
