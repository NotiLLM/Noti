package org.muilab.notigpt.data.repository.notification

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.muilab.notigpt.data.local.room.dao.NotiDrawerDao
import org.muilab.notigpt.data.local.room.dao.NotiRecordDao
import org.muilab.notigpt.domain.notification.DrawerItemSorter
import org.muilab.notigpt.model.notifications.NotiDisplayUnit

/** Builds flat active drawer notification units with their records. */
class NotiActiveUnitsRepository(
    private val notiDrawerDao: NotiDrawerDao,
    private val notiRecordDao: NotiRecordDao,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getActiveNotiUnits(): Flow<List<NotiDisplayUnit>> {
        return notiDrawerDao.getAutoSortedActiveNotificationsNoRelation().flatMapLatest { units ->
            val keys = units.map { it.notiKey }
            val displayUnitsFlow = if (keys.isEmpty()) {
                flowOf(emptyList())
            } else {
                notiRecordDao.getActiveRecordsFlowByKeys(keys).map { records ->
                    val recordsByKey = records.groupBy { it.notiKey }
                    units.map { unit ->
                        val unitRecords = recordsByKey[unit.notiKey]?.sortedBy { it.time } ?: emptyList()
                        NotiDisplayUnit(unit, unitRecords)
                    }
                }
            }

            displayUnitsFlow.map { displayUnits ->
                DrawerItemSorter.sort(displayUnits)
            }
        }
    }
}
