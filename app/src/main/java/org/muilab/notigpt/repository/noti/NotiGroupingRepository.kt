package org.muilab.notigpt.repository.noti

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.muilab.notigpt.database.room.NotiDrawerDao
import org.muilab.notigpt.database.room.NotiGroupDao
import org.muilab.notigpt.database.room.NotiRecordDao
import org.muilab.notigpt.domain.notification.DrawerGrouper
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiDrawerItem

class NotiGroupingRepository(
    private val notiDrawerDao: NotiDrawerDao,
    private val notiRecordDao: NotiRecordDao,
    private val notiGroupDao: NotiGroupDao,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getGroupedNotifications(): Flow<List<NotiDrawerItem>> {
        return notiGroupDao.getAllGroupsFlow().flatMapLatest { groups ->
            val unitsFlow = notiDrawerDao.getAutoSortedActiveNotificationsNoRelation()

            unitsFlow.flatMapLatest { units ->
                val keys = units.map { it.notiKey }

                val displayUnitsFlow = if (keys.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    notiRecordDao.getActiveRecordsFlowByKeys(keys).map { recs ->
                        val groupedRecs = recs.groupBy { it.notiKey }
                        units.map { unit ->
                            val unitRecs = groupedRecs[unit.notiKey]?.sortedBy { it.time } ?: emptyList()
                            NotiDisplayUnit(unit, unitRecs)
                        }
                    }
                }

                displayUnitsFlow.map { displayUnits ->
                    DrawerGrouper.groupAndSort(displayUnits, groups)
                }
            }
        }
    }
}
