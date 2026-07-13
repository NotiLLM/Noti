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
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.util.SharedPreferencesManager
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/**
 * Firestore analytics sync.
 *
 * Storage (as requested):
 * - reminders/{userId}/reminders/{savedItemId}
 *   - notis/{notiKey}
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

    // Firestore data is partitioned per Google account; without a signed-in user every sync is a no-op.
    private fun userId(): String = FirebaseAuth.getInstance().currentUser?.uid
        ?: SharedPreferencesManager.userId.takeIf { it.isNotBlank() }.orEmpty()

    private fun usersDoc() = firestore
        .collection(FirestorePaths.COLLECTION_USERS)
        .document(userId())

    private fun reminderDoc(savedItemId: String) = firestore
        .collection(FirestorePaths.COLLECTION_REMINDERS_ROOT)
        .document(userId())
        .collection(FirestorePaths.SUBCOLLECTION_REMINDERS)
        .document(savedItemId)

    private fun reminderNotiDoc(savedItemId: String, notiKey: String) =
        reminderDoc(savedItemId).collection(FirestorePaths.SUBCOLLECTION_NOTIS).document(notiKey)

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
        } catch (t: Throwable) {
            Log.w(tag, "ensureUserDoc failed", t)
        }
    }

    suspend fun syncReminder(reminder: SavedItem) = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext
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
            "estimatedCompletionTime" to reminder.estimatedCompletionTime,
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
            "isVisible" to reminder.isVisible,
            "deletedAt" to (reminder.deletedAtMs?.let { TimeFormatters.toLocalIso(it, zoneId) } ?: ""),
            "lastUpdateTimestamp" to TimeFormatters.toLocalIso(reminder.lastUpdateTimestamp, zoneId),
            "buttons" to reminder.buttons,
            "isViewed" to reminder.isViewed,
            "sortScore" to reminder.sortScore,
            "reRankHistory" to reminder.reRankHistory,
            "syncedAt" to TimeFormatters.toLocalIso(now, zoneId),
            // Raw epoch values: the ISO fields above are for human/analysis reads; restore uses these.
            "deadlineAtMsEpoch" to reminder.deadlineAtMs,
            "startAtMsEpoch" to reminder.startAtMs,
            "endAtMsEpoch" to reminder.endAtMs,
            "doAtMsEpoch" to reminder.doAtMs,
            "lastUpdateTimestampEpoch" to reminder.lastUpdateTimestamp,
            "lastViewedChangeAtEpoch" to reminder.lastViewedChangeAt,
            "deletedAtMsEpoch" to (reminder.deletedAtMs ?: 0L),
            "itemType" to reminder.itemType,
            "subTasks" to subTasksPayload(reminder.savedItemId),
            "schemaVersion" to 5,
        )

        try {
            reminderDoc(reminder.savedItemId).set(payload, SetOptions.merge()).await()
        } catch (t: Throwable) {
            Log.w(tag, "syncReminder failed savedItemId=${reminder.savedItemId}", t)
            return@withContext
        }

        // === Notis subcollection: upload records referenced by the reminder's links ===
        if (linkedRecordIds.isEmpty()) return@withContext

        try {
            // notiKey -> record ids, derived from the link rows.
            val mapping: Map<String, List<String>> = links
                .groupBy { it.notiKey }
                .mapValues { (_, group) -> group.map { it.notiRecordId }.distinct() }
            if (mapping.isEmpty()) return@withContext

            val wantedKeys = mapping.keys.toList()
            val wantedRecordIds: Set<String> = linkedRecordIds.toSet()

            val records = db.recordDao().getRecordsByIds(wantedRecordIds.toList())
            val recordsByKey = records.groupBy { it.notiKey }

            val unitsByKey: Map<String, NotiUnit> = db.drawerDao().getByNotiKeys(wantedKeys).associateBy { it.notiKey }

            val nowIso = TimeFormatters.toLocalIso(now, zoneId)

            wantedKeys.forEach { key ->
                val unit = unitsByKey[key]
                val allowedIds = mapping[key].orEmpty().toHashSet()
                val keyRecords = recordsByKey[key].orEmpty().filter { it.notiRecordId in allowedIds }.sortedBy { it.time }

                val notiPayload: Map<String, Any?> = mapOf(
                    "notiKey" to key,
                    "pkgName" to (unit?.pkgName ?: ""),
                    "appName" to (unit?.appName ?: ""),
                    "records" to keyRecords.map { r ->
                        mapOf(
                            "notiRecordId" to r.notiRecordId,
                            "whenTime" to (if (r.whenTime > 0L) TimeFormatters.toLocalIso(r.whenTime, zoneId) else ""),
                            "postTime" to TimeFormatters.toLocalIso(r.postTime, zoneId),
                            "person" to r.person,
                            "extraTitle" to r.extraTitle,
                            "extraBigTitle" to r.extraBigTitle,
                            "extraConversationTitle" to r.extraConversationTitle,
                            "extraSubText" to r.extraSubText,
                            "extraText" to r.extraText,
                            "extraBigText" to r.extraBigText,
                            "extraTextLines" to r.extraTextLines,
                            "extraSummaryText" to r.extraSummaryText,
                            "extraInfoText" to r.extraInfoText,
                            "isDismissed" to r.isDismissed,
                        )
                    },
                    "syncedAt" to nowIso,
                    "schemaVersion" to 2,
                )

                reminderNotiDoc(reminder.savedItemId, key).set(notiPayload, SetOptions.merge()).await()
            }

        } catch (t: Throwable) {
            Log.w(tag, "syncReminder notis(snapshot-only) failed savedItemId=${reminder.savedItemId}", t)
        }
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
        } catch (t: Throwable) {
            Log.w(tag, "syncPreferencesAndContexts failed", t)
        }
    }
}
