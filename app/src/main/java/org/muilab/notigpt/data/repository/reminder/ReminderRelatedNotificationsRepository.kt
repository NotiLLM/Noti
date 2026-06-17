package org.muilab.notigpt.data.repository.reminder

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit

/**
 * Repository for resolving notification context attached to a reminder.
 *
 * This bridges reminder associations back to active notification units and records. Keep it read-focused so
 * reminder editing does not mutate notification history through this path.
 */
class ReminderRelatedNotificationsRepository(context: Context) {
    private val db = AppDatabase.getInstance(context.applicationContext)

    data class RelatedNotifications(
        val recordsByKey: Map<String, List<NotiRecord>>,
        val unitsByKey: Map<String, NotiUnit>,
    ) {
        companion object {
            val Empty = RelatedNotifications(emptyMap(), emptyMap())
        }
    }

    suspend fun getRelatedNotifications(reminder: SavedItem): RelatedNotifications = withContext(Dispatchers.IO) {
        val links = db.notiSavedItemLinkDao().getBySavedItemId(reminder.savedItemId)
        val recordIds = links.map { it.notiRecordId }.filter { it.isNotBlank() }.distinct()
        val wantedKeys = links.map { it.notiKey }.filter { it.isNotBlank() }.distinct()

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
        )
    }
}
