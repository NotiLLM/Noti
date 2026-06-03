package org.muilab.notigpt.domain.notification

import org.muilab.notigpt.model.notifications.NotiDisplayUnit

/** Sorts flat active notification units for the drawer. */
object DrawerItemSorter {

    private val autoComparator = compareByDescending<NotiDisplayUnit> { it.notiUnit.isSetToTop }
        .thenByDescending { it.notiUnit.setToTopTime }
        .thenByDescending { it.notiUnit.lastUpdateTime }

    fun sort(units: List<NotiDisplayUnit>): List<NotiDisplayUnit> {
        if (units.isEmpty()) return emptyList()

        val hasManualOrder = units.any { it.notiUnit.sortPosition >= 0 }
        return if (hasManualOrder) {
            applyManualPositions(units)
        } else {
            units.sortedWith(autoComparator)
        }
    }

    private fun applyManualPositions(units: List<NotiDisplayUnit>): List<NotiDisplayUnit> {
        val slots = arrayOfNulls<NotiDisplayUnit>(units.size)
        val (manual, auto) = units.partition { it.notiUnit.sortPosition >= 0 }
        val overflowAuto = mutableListOf<NotiDisplayUnit>()

        manual.sortedBy { it.notiUnit.sortPosition }.forEach { unit ->
            val clamped = unit.notiUnit.sortPosition.coerceIn(0, units.lastIndex)
            if (slots[clamped] == null) {
                slots[clamped] = unit
            } else {
                overflowAuto.add(unit)
            }
        }

        val filler = (auto + overflowAuto).sortedWith(autoComparator).iterator()
        for (index in slots.indices) {
            if (slots[index] == null && filler.hasNext()) {
                slots[index] = filler.next()
            }
        }

        return slots.filterNotNull()
    }
}
