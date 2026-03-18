package org.muilab.notigpt.repository.firestore

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.domain.esm.EsmUserSnapshot
import org.muilab.notigpt.model.esm.EsmInstance
import org.muilab.notigpt.model.features.ReminderUnit
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
 * - reminders/{userId}/reminders/{reminderId}
 *   - notis/{notiKey}
 * - esms/{userId}/esms/{instanceId}
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

    private fun userId(): String = SharedPreferencesManager.userId

    private fun usersDoc() = firestore
        .collection(FirestorePaths.COLLECTION_USERS)
        .document(userId())

    private fun reminderDoc(reminderId: String) = firestore
        .collection(FirestorePaths.COLLECTION_REMINDERS_ROOT)
        .document(userId())
        .collection(FirestorePaths.SUBCOLLECTION_REMINDERS)
        .document(reminderId)

    private fun esmDoc(instanceId: String) = firestore
        .collection(FirestorePaths.COLLECTION_ESMS_ROOT)
        .document(userId())
        .collection(FirestorePaths.SUBCOLLECTION_ESMS)
        .document(instanceId)

    private fun reminderNotiDoc(reminderId: String, notiKey: String) =
        reminderDoc(reminderId).collection(FirestorePaths.SUBCOLLECTION_NOTIS).document(notiKey)

    suspend fun incrementNotiRecordCount() = withContext(Dispatchers.IO) {
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

    suspend fun syncReminder(reminder: ReminderUnit) = withContext(Dispatchers.IO) {
        ensureUserDoc()

        val now = System.currentTimeMillis()
        val payload: Map<String, Any?> = mapOf(
            "reminderId" to reminder.reminderId,
            "reminderTitle" to reminder.reminderTitle,
            "reminderContent" to reminder.reminderContent,
            "origin" to reminder.origin,
            "humanEditCount" to reminder.humanEditCount,
            "userEdited" to reminder.userEdited,
            "isTask" to reminder.isTask,
            "isEvent" to reminder.isEvent,
            "isCompleted" to reminder.isCompleted,
            "deadlineTimestamp" to (if (reminder.deadlineTimestamp > 0L) TimeFormatters.toLocalIso(reminder.deadlineTimestamp, zoneId) else ""),
            "startTime" to (if (reminder.startTime > 0L) TimeFormatters.toLocalIso(reminder.startTime, zoneId) else ""),
            "endTime" to (if (reminder.endTime > 0L) TimeFormatters.toLocalIso(reminder.endTime, zoneId) else ""),
            "estimatedCompletionTime" to reminder.estimatedCompletionTime,
            "associatedNotiRecords" to reminder.associatedNotiRecords.toList(),
            "associatedNotiRecordsCount" to reminder.associatedNotiRecords.size,
            "extractionSnapshotId" to reminder.extractionSnapshotId,
            "isVisible" to reminder.isVisible,
            "deletedAt" to (reminder.deletedAtMs?.let { TimeFormatters.toLocalIso(it, zoneId) } ?: ""),
            "lastUpdateTimestamp" to TimeFormatters.toLocalIso(reminder.lastUpdateTimestamp, zoneId),
            "buttons" to reminder.buttons,
            "isViewed" to reminder.isViewed,
            "isPinned" to reminder.isPinned,
            "sortScore" to reminder.sortScore,
            "reRankHistory" to reminder.reRankHistory,
            "syncedAt" to TimeFormatters.toLocalIso(now, zoneId),
            "schemaVersion" to 3,
        )

        try {
            reminderDoc(reminder.reminderId).set(payload, SetOptions.merge()).await()
        } catch (t: Throwable) {
            Log.w(tag, "syncReminder failed reminderId=${reminder.reminderId}", t)
            return@withContext
        }

        // === Notis subcollection: ONLY upload records referenced by the reminder snapshot ===
        val snapshotId = reminder.extractionSnapshotId
        if (snapshotId.isNullOrBlank() || reminder.associatedNotiRecords.isEmpty()) return@withContext

        try {
            val snap = db.reminderSnapshotDao().getSnapshot(snapshotId) ?: return@withContext
            val grouping = EsmUserSnapshot.parseRecordIdGrouping(snap.payloadJson) ?: return@withContext

            val mapping = grouping.notiKeyToRecordIds
            if (mapping.isEmpty()) return@withContext

            val wantedKeys = reminder.associatedNotiKeys.toList().filter { it in mapping.keys }
            if (wantedKeys.isEmpty()) return@withContext

            val wantedRecordIds: Set<String> = wantedKeys.flatMap { mapping[it].orEmpty() }.toSet()
            if (wantedRecordIds.isEmpty()) return@withContext

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

                reminderNotiDoc(reminder.reminderId, key).set(notiPayload, SetOptions.merge()).await()
            }

        } catch (t: Throwable) {
            Log.w(tag, "syncReminder notis(snapshot-only) failed reminderId=${reminder.reminderId}", t)
        }
    }

    suspend fun syncEsmAnswerEvent(instanceId: String, questionId: String) = withContext(Dispatchers.IO) {
        ensureUserDoc()

        try {
            val inst: EsmInstance = db.esmDao().getInstance(instanceId) ?: return@withContext
            val events = db.esmDao().getAnswerEvents(instanceId)
            val answers: Map<String, Any?> = events.associate { it.questionId to it.answerJson }
            val answeredAtLatest = events.maxOfOrNull { it.answeredAt } ?: 0L
            val now = System.currentTimeMillis()

            val payload: Map<String, Any?> = mapOf(
                "instanceId" to inst.instanceId,
                "questionnaireId" to inst.questionnaireId,
                "questionnaireVersion" to inst.questionnaireVersion,
                "triggerType" to inst.triggerType,
                "reminderId" to inst.reminderId,
                "snapshotId" to inst.snapshotId,
                "createdAt" to TimeFormatters.toLocalIso(inst.createdAt, zoneId),
                "availableAt" to TimeFormatters.toLocalIso(inst.availableAt, zoneId),
                "expiresAt" to TimeFormatters.toLocalIso(inst.expiresAt, zoneId),
                "status" to inst.status,
                "answeredAt" to (if (inst.answeredAt > 0L) TimeFormatters.toLocalIso(inst.answeredAt, zoneId) else ""),
                "isLate" to inst.isLate,
                "answers" to answers,
                "answeredAtLatest" to (if (answeredAtLatest > 0L) TimeFormatters.toLocalIso(answeredAtLatest, zoneId) else ""),
                "lastAnswerQuestionId" to questionId,
                "syncedAt" to TimeFormatters.toLocalIso(now, zoneId),
                "schemaVersion" to 2,
            )

            esmDoc(instanceId).set(payload, SetOptions.merge()).await()
        } catch (t: Throwable) {
            Log.w(tag, "syncEsmAnswerEvent failed instanceId=$instanceId", t)
        }
    }
}
