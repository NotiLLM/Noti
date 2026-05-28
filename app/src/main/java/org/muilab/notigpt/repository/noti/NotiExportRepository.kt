package org.muilab.notigpt.repository.noti

import org.json.JSONObject
import org.muilab.notigpt.database.room.NotiActionDao
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiRecordDao

/**
 * Export logic (JSON assembly) extracted from NotiRepository.
 *
 * Returns a lazy [Sequence] so records/actions for each notification are loaded on demand
 * and can be GC'd before the next unit is touched.
 *
 * NotiUnits themselves are fetched in pages of [BATCH_SIZE] rows so the entire noti_drawer
 * table is never materialised as a single List — the primary cause of heap fragmentation OOM
 * when a large number of dismissed notifications are present.
 */
class NotiExportRepository(
    private val notiDrawerDao: NotiDrawerDao,
    private val notiRecordDao: NotiRecordDao,
    private val notiActionDao: NotiActionDao,
) {
    companion object {
        /** Number of NotiUnit rows fetched per DB round-trip during export. */
        private const val BATCH_SIZE = 50
    }

    /**
     * Produces one [JSONObject] per notification, lazily and in paginated DB batches.
     * At peak, only [BATCH_SIZE] NotiUnit objects + one unit's records/actions exist in RAM.
     */
    fun exportLog(includeContext: Boolean, includeDismissed: Boolean): Sequence<JSONObject> =
        sequence {
            var offset = 0
            while (true) {
                val batch = if (includeDismissed)
                    notiDrawerDao.getAllPaged(BATCH_SIZE, offset)
                else
                    notiDrawerDao.getAllActivePaged(BATCH_SIZE, offset)

                if (batch.isEmpty()) break

                batch.forEach { notiUnit ->
                    val notiKey = notiUnit.notiKey
                    val notiRecords = if (includeContext) {
                        notiRecordDao.getRecordsByKey(notiKey)
                    } else {
                        notiRecordDao.getActiveRecordsByKey(notiKey)
                    }.sortedBy { it.time }

                    val notiActions = notiActionDao.getActionsByKey(notiKey).sortedBy { it.time }

                    yield(
                        NotiExportFormatter.formatUnit(
                            notiKey = notiKey,
                            appName = notiUnit.appName,
                            isPeople = notiUnit.isPeople,
                            records = notiRecords,
                            actions = notiActions,
                            includeContext = includeContext,
                        )
                    )
                }

                offset += batch.size
                if (batch.size < BATCH_SIZE) break   // last page
            }
        }
}
