package org.muilab.notigpt.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.domain.esm.EsmConfig
import org.muilab.notigpt.domain.esm.EsmSnapshotStatuses
import org.muilab.notigpt.domain.esm.EsmStatuses
import org.muilab.notigpt.domain.esm.EsmTriggerTypes
import org.muilab.notigpt.domain.esm.IRBShortSurveyV2
import org.muilab.notigpt.model.features.ReminderExtractionSnapshot
import org.muilab.notigpt.model.esm.EsmInstance
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.util.postEsmIndicatorNotification
import java.util.UUID
import org.muilab.notigpt.domain.esm.EsmTimePolicy

class EsmRepository(
    private val appContext: Context,
    private val db: AppDatabase = AppDatabase.getInstance(appContext),
) {
    private val esmDao = db.esmDao()
    private val snapshotDao = db.reminderSnapshotDao()

    suspend fun getNewestAvailable(): EsmInstance? = withContext(Dispatchers.IO) {
        esmDao.getNewestAvailable()
    }

    suspend fun getActiveCount(): Int = withContext(Dispatchers.IO) {
        esmDao.getActiveCount()
    }

    suspend fun getInstancesByStatuses(statuses: List<String>): List<EsmInstance> = withContext(Dispatchers.IO) {
        esmDao.getInstancesByStatuses(statuses)
    }

    suspend fun getSnapshot(snapshotId: String): ReminderExtractionSnapshot? = withContext(Dispatchers.IO) {
        snapshotDao.getSnapshot(snapshotId)
    }

    suspend fun setTriggerType(instanceId: String, triggerType: String) = withContext(Dispatchers.IO) {
        esmDao.setTriggerType(instanceId, triggerType)
    }

    /**
     * Create a test ESM for a given generated reminder.
     *
     * Snapshot policy (per your request):
     * - If we already have a stored extraction snapshot for this reminder, reuse it.
     * - Else build a snapshot from the reminder's associated notification keys:
     *   - If the notification is still active: include its latest active record (approx. display snapshot).
     *   - If dismissed: include its NotiUnit + last record (latest record regardless of dismissal).
     */
    suspend fun createTestEsmForReminder(reminder: ReminderUnit): EsmInstance = withContext(Dispatchers.IO) {
        require(reminder.associatedNotis.isNotEmpty()) { "Reminder has no associated notifications" }

        val now = System.currentTimeMillis()
        val snapshotId = "snap_${UUID.randomUUID()}"

        val notiKeys = reminder.associatedNotis.toList()

        val drawerDao = db.drawerDao()
        val recordDao = db.recordDao()

        val units = drawerDao.getByNotiKeys(notiKeys)
        val activeRecords = recordDao.getLatestActiveRecordsByKeys(notiKeys)
        val activeRecordMap = activeRecords.associateBy { it.notiKey }

        // Fallback for dismissed: get ALL records and take max whenTime.
        val allRecords = recordDao.getRecordsByKeys(notiKeys)
        val lastRecordMap = allRecords.groupBy { it.notiKey }
            .mapValues { (_, recs) -> recs.maxByOrNull { it.whenTime } }

        val payload = org.json.JSONObject().apply {
            put("reminder", org.json.JSONObject().apply {
                put("title", reminder.reminderTitle.ifBlank { "(Untitled)" })
                put("content", reminder.reminderContent)
                put("isTask", reminder.isTask)
                put("deadlineTimestamp", reminder.deadlineTimestamp)
                put("estimatedCompletionMinutes", reminder.estimatedCompletionTime)
            })

            val arr = org.json.JSONArray()
            for (key in notiKeys) {
                val unit = units.firstOrNull { it.notiKey == key }
                val activeRec = activeRecordMap[key]
                val lastRec = lastRecordMap[key]
                val rec = activeRec ?: lastRec

                val obj = org.json.JSONObject().apply {
                    // For internal linking only; UI should not display it.
                    put("notiKey", key)

                    put("notiUnit", unit?.let {
                        org.json.JSONObject().apply {
                            put("notiKey", it.notiKey)
                            put("groupId", it.groupId)
                            put("appName", it.appName)
                            put("pkgName", it.pkgName)
                            put("isPeople", it.isPeople)
                            put("isDismissed", it.isDismissed)
                            put("isPinned", it.isPinned)
                            put("isRead", it.isRead)
                            put("summary", it.summary)
                            put("sortScore", it.sortScore)
                            put("lastUpdateTime", it.lastUpdateTime)
                            put("lastSyncTime", it.lastSyncTime)
                            put("sortPosition", it.sortPosition)

                            // Include icon blobs for UI parity.
                            put("icon", it.metadata.icon)
                            put("largeIcon", it.metadata.largeIcon)
                        }
                    } ?: org.json.JSONObject())

                    put("notiRecords", org.json.JSONArray().apply {
                        // Keep it minimal: one record snapshot is enough for card rendering.
                        val r = rec
                        if (r != null) {
                            put(org.json.JSONObject().apply {
                                put("notiRecordId", r.notiRecordId)
                                put("notiKey", r.notiKey)
                                put("whenTime", r.whenTime)
                                put("postTime", r.postTime)
                                put("person", r.person)
                                put("extraTitle", r.extraTitle)
                                put("extraBigTitle", r.extraBigTitle)
                                put("extraConversationTitle", r.extraConversationTitle)
                                put("extraBigText", r.extraBigText)
                                put("extraText", r.extraText)
                                put("extraTextLines", r.extraTextLines)
                                put("extraSummaryText", r.extraSummaryText)
                                put("extraInfoText", r.extraInfoText)
                                put("extraSubText", r.extraSubText)
                                put("isDismissed", r.isDismissed)
                            })
                        }
                    })

                    put("snapshotSource", if (activeRec != null) "active_latest" else "dismissed_last")
                }

                arr.put(obj)
            }
            put("notis", arr)
        }.toString()

        snapshotDao.upsertSnapshot(
            ReminderExtractionSnapshot(
                snapshotId = snapshotId,
                status = EsmSnapshotStatuses.KEPT,
                reminderId = reminder.reminderId,
                payloadJson = payload,
                createdAt = now,
            )
        )

        val inst = EsmInstance(
            instanceId = "esm_${UUID.randomUUID()}",
            questionnaireId = IRBShortSurveyV2.questionnaireId,
            questionnaireVersion = IRBShortSurveyV2.questionnaireVersion,
            triggerType = EsmTriggerTypes.B_ENTERED_EDIT_PAGE,
            reminderId = reminder.reminderId,
            snapshotId = snapshotId,
            createdAt = now,
            availableAt = now,
            expiresAt = now + 60 * 60 * 1000L,
            status = EsmStatuses.AVAILABLE,
        )
        esmDao.insertInstance(inst)

        // Post indicator.
        postEsmIndicatorNotification(appContext)

        inst
    }

    /**
     * For debug/testing: creates a synthetic ESM + snapshot.
     */
    suspend fun createDebugEsmNow(): EsmInstance = withContext(Dispatchers.IO) {
        val snapshotId = "snap_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val payload = org.json.JSONObject().apply {
            put("reminder", org.json.JSONObject().apply {
                put("title", "(Debug reminder)")
                put("content", "")
                put("isTask", true)
                put("deadlineTimestamp", 0L)
                put("estimatedCompletionMinutes", 0L)
            })
            put("notis", org.json.JSONArray())
            put("notes", "DEBUG snapshot")
        }.toString()
        snapshotDao.upsertSnapshot(
            ReminderExtractionSnapshot(
                snapshotId = snapshotId,
                status = EsmSnapshotStatuses.KEPT,
                reminderId = "debug_reminder",
                payloadJson = payload,
                createdAt = now,
            )
        )

        val inst = EsmInstance(
            instanceId = "esm_${UUID.randomUUID()}",
            questionnaireId = IRBShortSurveyV2.questionnaireId,
            questionnaireVersion = IRBShortSurveyV2.questionnaireVersion,
            triggerType = EsmTriggerTypes.DEBUG,
            reminderId = "debug_reminder",
            snapshotId = snapshotId,
            createdAt = now,
            availableAt = now,
            expiresAt = now + 60 * 60 * 1000L,
            status = EsmStatuses.AVAILABLE,
        )
        esmDao.insertInstance(inst)
        inst
    }

    /**
     * Create an ESM instance bound to an existing reminder extraction snapshot.
     *
     * Contract:
     * - [reminderId] must exist (reminder was created).
     * - [snapshotId] must exist (snapshot was staged/kept during extraction).
     * - Status is PENDING by default so a worker can flip it to AVAILABLE after a delay.
     */
    suspend fun createEsmForSnapshot(
        reminderId: String,
        snapshotId: String,
        triggerType: String,
        availableDelayMs: Long,
        // ESM completion window: 1 hour after it becomes AVAILABLE.
        expiresAfterMs: Long = EsmConfig.QUESTIONNAIRE_EXPIRES_AFTER_MS,
    ): EsmInstance = withContext(Dispatchers.IO) {
        // Prevent multiple ESMs for the same reminder.
        if (esmDao.countInstancesByReminderId(reminderId) > 0) {
            throw IllegalStateException("ESM already exists for reminderId=$reminderId")
        }

        val now = System.currentTimeMillis()
        val availAt = now + availableDelayMs.coerceAtLeast(0L)

        val inst = EsmInstance(
            instanceId = "esm_${UUID.randomUUID()}",
            questionnaireId = IRBShortSurveyV2.questionnaireId,
            questionnaireVersion = IRBShortSurveyV2.questionnaireVersion,
            triggerType = triggerType,
            reminderId = reminderId,
            snapshotId = snapshotId,
            createdAt = now,
            availableAt = availAt,
            // Important: expires relative to availableAt, not createdAt.
            expiresAt = availAt + expiresAfterMs.coerceAtLeast(0L),
            status = EsmStatuses.PENDING,
        )
        esmDao.insertInstance(inst)
        inst
    }

    suspend fun hasAnyInstanceForReminder(reminderId: String): Boolean = withContext(Dispatchers.IO) {
        esmDao.countInstancesByReminderId(reminderId) > 0
    }

    suspend fun getReminder(reminderId: String): ReminderUnit? = withContext(Dispatchers.IO) {
        db.reminderListDao().getById(reminderId)
    }

    suspend fun getUnexpiredAvailable(nowMs: Long = System.currentTimeMillis()): List<EsmInstance> = withContext(Dispatchers.IO) {
        esmDao.getUnexpiredAvailable(nowMs)
    }

    /**
     * Attempts to schedule delivery for an existing instance, enforcing:
     * - max per anchored day
     * - cooldown (answered/unanswered)
     *
     * Returns the effective delay ms if scheduled, or null if blocked by caps.
     */
    suspend fun maybeEnqueueWithPolicy(
        instanceId: String,
        requestedDelayMs: Long,
    ): Long? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // Per-day cap (anchored day) - count deliveries (AVAILABLE timestamps).
        val window = EsmTimePolicy.anchoredDayWindowMs(now, EsmConfig.ANCHORED_DAY_START_HOUR, EsmConfig.ANCHORED_DAY_START_MINUTE)
        val deliveredToday = esmDao.countDeliveredBetween(window.startMs, window.endMs)
        if (deliveredToday >= EsmConfig.MAX_PER_ANCHORED_DAY) {
            return@withContext null
        }

        // Cooldown: if last answered is recent -> answered cooldown.
        // Else if any available was recent -> unanswered cooldown.
        val lastAnsweredAt = esmDao.getLastAnsweredAt() ?: 0L
        val lastAvailableAt = esmDao.getLastAvailableAt() ?: 0L

        val cooldownMs = when {
            lastAnsweredAt > 0L && (now - lastAnsweredAt) < EsmConfig.ANSWERED_COOLDOWN_MS ->
                EsmConfig.ANSWERED_COOLDOWN_MS - (now - lastAnsweredAt)
            lastAvailableAt > 0L && (now - lastAvailableAt) < EsmConfig.UNANSWERED_COOLDOWN_MS ->
                EsmConfig.UNANSWERED_COOLDOWN_MS - (now - lastAvailableAt)
            else -> 0L
        }

        val effectiveDelay = maxOf(0L, requestedDelayMs, cooldownMs)
        org.muilab.notigpt.database.server.esm.enqueueEsmDelivery(appContext.applicationContext, instanceId, effectiveDelay)
        effectiveDelay
    }

    suspend fun getInstanceByReminderId(reminderId: String): EsmInstance? = withContext(Dispatchers.IO) {
        esmDao.getInstanceByReminderId(reminderId)
    }

    /**
     * Trigger B should override a previously auto-generated Trigger C for the same reminder.
     *
     * If an instance exists and is still actionable (PENDING/AVAILABLE), we upgrade it to trigger B
     * and reschedule its availableAt according to policy.
     */
    suspend fun promoteToTriggerBAndReschedule(
        reminderId: String,
        requestedDelayMs: Long,
    ): Long? = withContext(Dispatchers.IO) {
        val inst = esmDao.getInstanceByReminderId(reminderId) ?: return@withContext null

        // If already answered/expired/discarded, don't touch.
        if (inst.status in listOf(EsmStatuses.ANSWERED, EsmStatuses.EXPIRED, EsmStatuses.DISCARDED_SUPERSEDED)) {
            return@withContext null
        }

        // If it's already trigger B, just enqueue with policy.
        if (inst.triggerType == EsmTriggerTypes.B_ENTERED_EDIT_PAGE) {
            return@withContext maybeEnqueueWithPolicy(inst.instanceId, requestedDelayMs)
        }

        // Only promote C -> B (leave A alone).
        if (inst.triggerType != EsmTriggerTypes.C_AUTO_GENERATED) {
            return@withContext maybeEnqueueWithPolicy(inst.instanceId, requestedDelayMs)
        }

        // Update trigger type immediately.
        esmDao.setTriggerType(inst.instanceId, EsmTriggerTypes.B_ENTERED_EDIT_PAGE)

        // Compute an effective delay under policy and reschedule availableAt/expiresAt.
        val now = System.currentTimeMillis()

        // reuse the same policy logic as maybeEnqueueWithPolicy
        val window = EsmTimePolicy.anchoredDayWindowMs(now, EsmConfig.ANCHORED_DAY_START_HOUR, EsmConfig.ANCHORED_DAY_START_MINUTE)
        val deliveredToday = esmDao.countDeliveredBetween(window.startMs, window.endMs)
        if (deliveredToday >= EsmConfig.MAX_PER_ANCHORED_DAY) {
            return@withContext null
        }

        val lastAnsweredAt = esmDao.getLastAnsweredAt() ?: 0L
        val lastAvailableAt = esmDao.getLastAvailableAt() ?: 0L
        val cooldownMs = when {
            lastAnsweredAt > 0L && (now - lastAnsweredAt) < EsmConfig.ANSWERED_COOLDOWN_MS ->
                EsmConfig.ANSWERED_COOLDOWN_MS - (now - lastAnsweredAt)
            lastAvailableAt > 0L && (now - lastAvailableAt) < EsmConfig.UNANSWERED_COOLDOWN_MS ->
                EsmConfig.UNANSWERED_COOLDOWN_MS - (now - lastAvailableAt)
            else -> 0L
        }

        val effectiveDelay = maxOf(0L, requestedDelayMs, cooldownMs)
        val newAvailAt = now + effectiveDelay
        val newExpiresAt = newAvailAt + EsmConfig.QUESTIONNAIRE_EXPIRES_AFTER_MS

        // If it was already AVAILABLE, move it back to PENDING so the delivery worker can re-open it.
        esmDao.rescheduleInstance(inst.instanceId, newAvailAt, newExpiresAt, EsmStatuses.PENDING)

        // Enqueue delivery.
        org.muilab.notigpt.database.server.esm.enqueueEsmDelivery(appContext.applicationContext, inst.instanceId, effectiveDelay)
        effectiveDelay
    }
}
