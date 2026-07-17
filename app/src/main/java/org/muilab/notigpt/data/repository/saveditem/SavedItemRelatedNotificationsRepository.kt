package org.muilab.notigpt.data.repository.saveditem

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit

/**
 * Repository for resolving notification context attached to a SavedItem.
 *
 * This bridges SavedItem associations back to active notification units and records. Keep it read-focused so
 * SavedItem editing does not mutate notification history through this path.
 */
class SavedItemRelatedNotificationsRepository(context: Context) {
    private val db = AppDatabase.getInstance(context.applicationContext)

    data class RelatedNotifications(
        val recordsByKey: Map<String, List<NotiRecord>>,
        val unitsByKey: Map<String, NotiUnit>,
        /** Record ids that directly evidence the saved item (the link rows). */
        val evidenceRecordIds: Set<String> = emptySet(),
        /** Keys whose full thread context has been lazily loaded into [recordsByKey]. */
        val contextLoadedKeys: Set<String> = emptySet(),
    ) {
        companion object {
            val Empty = RelatedNotifications(emptyMap(), emptyMap())
        }
    }

    suspend fun getRelatedNotifications(item: SavedItem): RelatedNotifications = withContext(Dispatchers.IO) {
        val links = db.notiSavedItemLinkDao().getBySavedItemId(item.savedItemId)
        getByRecordIds(links.map { it.notiRecordId })
    }

    /**
     * Evidence context by explicit record ids — used for staged pending ops, which cite evidence
     * in their payload and have no link rows until the user accepts them.
     */
    suspend fun getByRecordIds(evidenceRecordIds: Collection<String>): RelatedNotifications = withContext(Dispatchers.IO) {
        val recordIds = evidenceRecordIds.filter { it.isNotBlank() }.distinct()
        val wantedKeys = recordIds.map { it.substringBeforeLast("_") }.filter { it.isNotBlank() }.distinct()

        if (recordIds.isEmpty() || wantedKeys.isEmpty()) {
            return@withContext RelatedNotifications.Empty
        }

        val recordsByIds = db.recordDao().getRecordsByIds(recordIds)
        val records = if (recordsByIds.isNotEmpty()) {
            recordsByIds
        } else {
            val idSet = recordIds.toHashSet()
            db.recordDao().getRecordsByKeys(wantedKeys).filter { it.notiRecordId in idSet }
        }

        val units = db.drawerDao().getByNotiKeys(wantedKeys).associateBy { it.notiKey }
        RelatedNotifications(
            recordsByKey = records.groupBy { it.notiKey },
            unitsByKey = units,
            evidenceRecordIds = recordIds.toSet(),
        )
    }

    /**
     * Lazily expands one notiUnit group with its surrounding (non-evidence) records.
     *
     * Only evidence records are stored as links; the rest of the thread is loaded from
     * noti_record on demand so the user can see the conversation around the evidence.
     */
    suspend fun withSurroundingContext(
        current: RelatedNotifications,
        notiKey: String,
        maxRecords: Int = 30,
    ): RelatedNotifications = withContext(Dispatchers.IO) {
        if (notiKey in current.contextLoadedKeys) return@withContext current
        val unitRecords = try {
            db.recordDao().getRecordsByKey(notiKey).sortedByDescending { it.whenTime }.take(maxRecords)
        } catch (_: Exception) {
            return@withContext current
        }
        val merged = (current.recordsByKey[notiKey].orEmpty() + unitRecords)
            .distinctBy { it.notiRecordId }
        current.copy(
            recordsByKey = current.recordsByKey + (notiKey to merged),
            contextLoadedKeys = current.contextLoadedKeys + notiKey,
        )
    }
}
