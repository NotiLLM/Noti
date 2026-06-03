package org.muilab.notigpt.data.repository.reminder

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
        if (reminder.sourceNotiRecordIds.isEmpty()) {
            return@withContext RelatedNotifications.Empty
        }

        val directRecordIds = reminder.sourceNotiRecordIds.filter { it.isNotBlank() }.distinct()
        val wantedKeys = reminder.associatedNotiKeys.toList()
        val snapshotRecordIds = resolveRecordIdsFromSnapshot(reminder, wantedKeys)
        val recordIds = (directRecordIds + snapshotRecordIds).distinct()

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

    private suspend fun resolveRecordIdsFromSnapshot(reminder: SavedItem, wantedKeys: List<String>): List<String> {
        val snapshotId = reminder.sourceExtractionSnapshotId ?: return emptyList()
        if (snapshotId.isBlank()) return emptyList()

        return try {
            val snap = db.reminderSnapshotDao().getSnapshot(snapshotId) ?: return emptyList()
            val obj = JSONObject(snap.payloadJson)
            if (obj.optInt("v", 1) != 2) return emptyList()

            val mappedIds = mutableListOf<String>()
            val mappingObj = obj.optJSONObject("notiKeyToRecordIds")
            if (mappingObj != null) {
                wantedKeys.forEach { key ->
                    val arr = mappingObj.optJSONArray(key) ?: return@forEach
                    for (i in 0 until arr.length()) {
                        val id = arr.optString(i).trim()
                        if (id.isNotBlank()) mappedIds += id
                    }
                }
            }
            if (mappedIds.isNotEmpty()) return mappedIds.distinct()

            val arr = obj.optJSONArray("recordIds") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i).trim()
                    if (id.isNotBlank()) add(id)
                }
            }.distinct()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve related notification snapshot id=$snapshotId", t)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "ReminderRelatedNotis"
    }
}
