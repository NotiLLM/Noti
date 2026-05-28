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
 * - Grouping: unchanged; groups are formed iff a group has >= 2 children.
 * - Manual ordering: applies to loose (non-grouped) items only when any loose unit has sortPosition >= 0.
 * - Otherwise drawer ordering follows the visible product semantics: topped first, newer top time first, latest time next.
 * - Groups inherit topped/top-time/latest-time from their children.
 */
object DrawerGrouper {

    private val drawerDisplayUnitComparator = compareByDescending<NotiDisplayUnit> { it.notiUnit.isSetToTop }
        .thenByDescending { it.notiUnit.setToTopTime }
        .thenByDescending { it.lastUpdateTime }

    private val drawerItemComparator = compareByDescending<NotiDrawerItem> { it.isSetToTop }
        .thenByDescending { it.setToTopTime }
        .thenByDescending { it.latestTime }

    fun groupAndSort(
        displayUnits: List<NotiDisplayUnit>,
        groups: List<NotiGroup>,
    ): List<NotiDrawerItem> {
        val groupMap = groups.associateBy { it.groupId }

        val groupedItemsMap = displayUnits
            .filter { it.notiUnit.groupId != null }
            .groupBy { it.notiUnit.groupId!! }

        // Loose items are candidates for manual ordering.
        val looseUnits = displayUnits.filter { it.notiUnit.groupId == null }

        // Build grouped items first (children sorted purely by time desc for stability).
        val groupItems = mutableListOf<NotiGroupItem>()
        groupedItemsMap.forEach { (groupId, children) ->
            val group = groupMap[groupId]
            if (group != null && children.size > 1) {
                val sortedChildren = children.sortedWith(drawerDisplayUnitComparator)

                // Ensure children inherit "no manual sort" semantics.
                // This is defensive: DB layer also enforces sortPosition=-1 for grouped items.
                sortedChildren.forEach { child ->
                    if (child.notiUnit.sortPosition != -1) {
                        child.notiUnit.sortPosition = -1
                    }
                }

                groupItems.add(NotiGroupItem(group, sortedChildren))
            }
        }

        // If some children belong to a groupId that doesn't exist or has only 1 child, treat them as loose.
        val orphanedChildren = groupedItemsMap
            .filter { (gid, children) -> groupMap[gid] == null || children.size <= 1 }
            .values
            .flatten()

        val allLoose = (looseUnits + orphanedChildren)

        val looseHasManualOrder = allLoose.any { it.notiUnit.sortPosition >= 0 }
        val finalLooseItems = if (looseHasManualOrder) {
            applyManualPositions(allLoose).map { NotiItem(it) }
        } else {
            allLoose.sortedWith(drawerDisplayUnitComparator).map { NotiItem(it) }
        }

        return if (looseHasManualOrder) {
            finalLooseItems + groupItems.sortedWith(drawerItemComparator)
        } else {
            (finalLooseItems + groupItems).sortedWith(drawerItemComparator)
        }
    }

    private fun applyManualPositions(units: List<NotiDisplayUnit>): List<NotiDisplayUnit> {
        if (units.isEmpty()) return emptyList()

        val size = units.size

        // Step 1: place manual items into slots
        val slots: Array<NotiDisplayUnit?> = arrayOfNulls(size)

        // IMPORTANT: only sortPosition >= 0 is considered "manual".
        // Items with sortPosition == -1 are treated as auto-filled.
        val (manual, auto) = units.partition { it.notiUnit.sortPosition >= 0 }

        // Stable manual placement: if collisions happen, earlier ones win and the rest will be treated as auto.
        val overflowAuto = mutableListOf<NotiDisplayUnit>()
        manual
            .sortedBy { it.notiUnit.sortPosition }
            .forEach { du ->
                val clamped = du.notiUnit.sortPosition.coerceIn(0, size - 1)
                if (slots[clamped] == null) {
                    slots[clamped] = du
                } else {
                    // Collision: treat as auto-fill.
                    overflowAuto.add(du)
                }
            }

        // Step 2: fill gaps by latest time desc
        val filler = (auto + overflowAuto)
            .sortedByDescending { it.lastUpdateTime }
            .iterator()

        for (i in 0 until size) {
            if (slots[i] == null && filler.hasNext()) {
                slots[i] = filler.next()
            }
        }

        // Step 3: return the final ordered list.
        // DO NOT mutate sortPosition for auto items here.
        // - Mutating them to 0..N would make them look "manually sorted" in the UI (bold border)
        //   and would also cause them to be persisted as manual positions if any code writes back.
        // Manual items keep their existing sortPosition >= 0; auto items should remain -1.
        return slots.filterNotNull()
    }
}
