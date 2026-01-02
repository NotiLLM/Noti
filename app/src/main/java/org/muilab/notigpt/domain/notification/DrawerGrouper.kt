package org.muilab.notigpt.domain.notification

import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiDrawerItem
import org.muilab.notigpt.model.notifications.NotiGroup
import org.muilab.notigpt.model.notifications.NotiGroupItem
import org.muilab.notigpt.model.notifications.NotiItem

/**
 * Pure drawer grouping/sorting logic.
 *
 * Contract:
 * - Input: display units in any order + the full list of groups.
 * - Output: drawer items with groups formed iff a group has >= 2 children.
 * - Sorting (matches previous repository behavior):
 *   - Within group: isTop desc, setToTopTime desc, lastUpdateTime desc
 *   - Final list: isTop desc, setToTopTime desc, latestTime desc
 *
 * This is deliberately Android-free to allow fast JVM unit tests.
 */
object DrawerGrouper {

    fun groupAndSort(
        displayUnits: List<NotiDisplayUnit>,
        groups: List<NotiGroup>,
    ): List<NotiDrawerItem> {
        val groupMap = groups.associateBy { it.groupId }

        val groupedItemsMap = displayUnits
            .filter { it.notiUnit.groupId != null }
            .groupBy { it.notiUnit.groupId!! }

        val looseItems = displayUnits.filter { it.notiUnit.groupId == null }.toMutableList()
        val result = mutableListOf<NotiDrawerItem>()

        groupedItemsMap.forEach { (groupId, children) ->
            val group = groupMap[groupId]
            if (group != null && children.size > 1) {
                val sortedChildren = children.sortedWith(
                    compareByDescending<NotiDisplayUnit> { it.notiUnit.isSetToTop }
                        .thenByDescending { it.notiUnit.setToTopTime }
                        .thenByDescending { it.lastUpdateTime }
                )
                result.add(NotiGroupItem(group, sortedChildren))
            } else {
                looseItems.addAll(children)
            }
        }

        result.addAll(looseItems.map { NotiItem(it) })

        return result.sortedWith(
            compareByDescending<NotiDrawerItem> { it.isSetToTop }
                .thenByDescending { it.setToTopTime }
                .thenByDescending { it.latestTime }
        )
    }
}

