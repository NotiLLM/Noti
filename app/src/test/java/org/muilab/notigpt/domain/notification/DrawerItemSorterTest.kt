package org.muilab.notigpt.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.components.NotiDisplayState
import org.muilab.notigpt.model.notifications.components.NotiMetadata
import org.muilab.notigpt.model.notifications.components.NotiReminderAttr

class DrawerItemSorterTest {

    @Test
    fun `sort orders flat notification units by top state then top time then latest update`() {
        val topOld = fakeDisplayUnit(notiKey = "topOld", isTop = true, topTime = 1, lastUpdate = 50)
        val topNew = fakeDisplayUnit(notiKey = "topNew", isTop = true, topTime = 2, lastUpdate = 1)
        val nonTop = fakeDisplayUnit(notiKey = "nonTop", isTop = false, topTime = 0, lastUpdate = 999)

        val sorted = DrawerItemSorter.sort(listOf(nonTop, topOld, topNew))

        assertEquals(listOf("topNew", "topOld", "nonTop"), sorted.map { it.notiKey })
    }

    @Test
    fun `sort applies manual positions when at least one active notification has a manual position`() {
        val first = fakeDisplayUnit(notiKey = "first", sortPosition = 0, lastUpdate = 1)
        val second = fakeDisplayUnit(notiKey = "second", sortPosition = 1, lastUpdate = 999)
        val auto = fakeDisplayUnit(notiKey = "auto", sortPosition = -1, lastUpdate = 500)

        val sorted = DrawerItemSorter.sort(listOf(second, auto, first))

        assertEquals(listOf("first", "second", "auto"), sorted.map { it.notiKey })
    }

    @Test
    fun `sort treats negative sort position as auto sorted`() {
        val older = fakeDisplayUnit(notiKey = "older", sortPosition = -1, lastUpdate = 1)
        val newer = fakeDisplayUnit(notiKey = "newer", sortPosition = -1, lastUpdate = 2)

        val sorted = DrawerItemSorter.sort(listOf(older, newer))

        assertEquals(listOf("newer", "older"), sorted.map { it.notiKey })
    }

    private fun fakeDisplayUnit(
        notiKey: String,
        isTop: Boolean = false,
        topTime: Long = 0L,
        lastUpdate: Long,
        sortPosition: Int = -1,
    ): NotiDisplayUnit {
        return NotiDisplayUnit(
            notiUnit = NotiUnit(
                notiKey = notiKey,
                metadata = NotiMetadata(
                    pkgName = "pkg",
                    hashKey = notiKey.hashCode(),
                    groupKey = "",
                    isAppGroup = false,
                    isGroupChat = false,
                    sortKey = "",
                    appName = "App",
                    lastUpdateTime = lastUpdate,
                    lastSyncTime = 0L,
                    icon = "",
                    largeIcon = "",
                    isPeople = false,
                ),
                displayState = NotiDisplayState().apply {
                    isSetToTop = isTop
                    setToTopTime = topTime
                    this.sortPosition = sortPosition
                    isPinned = false
                    isRead = true
                },
                reminderAttr = NotiReminderAttr(
                    shouldExtractReminder = false,
                    hasTask = false,
                ),
            ),
            notiRecords = emptyList(),
        )
    }
}
