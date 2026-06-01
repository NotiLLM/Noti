package org.muilab.notigpt.repository.noti

import org.json.JSONObject
import org.muilab.notigpt.database.room.NotiActionDao
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiRecordDao

/**
 * Repository slice for building notification export JSON streams.
 *
 * Keep export query selection and NotiExportFormatter coordination here. File writing and chunking belong to the
 * platform DataExportManager so export generation can remain lazy.
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
     * Builds a lazy JSON stream for notification export.
     *
     * The sequence fetches paginated DB batches and keeps export memory bounded: at peak, only one batch of
     * notification rows plus one unit's records/actions are materialized. File splitting stays in DataExportManager.
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
