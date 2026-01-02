package org.muilab.notigpt.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiDrawerItem
import org.muilab.notigpt.model.notifications.NotiGroup
import org.muilab.notigpt.model.notifications.NotiGroupItem
import org.muilab.notigpt.model.notifications.NotiItem
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.components.NotiDisplayState
import org.muilab.notigpt.model.notifications.components.NotiMetadata

/**
 * JVM-only tests for drawer grouping/sorting.
 *
 * These tests protect against accidental regressions when changing DAO queries or UI rendering.
 */
class DrawerGrouperTest {

    @Test
    fun `groupAndSort groups children when group has 2+ items`() {
        val groupId = "g1"
        val groups = listOf(NotiGroup(groupId = groupId, title = "Group"))

        val u1 = fakeUnit(notiKey = "k1", groupId = groupId, isTop = false, topTime = 0, lastUpdate = 10)
        val u2 = fakeUnit(notiKey = "k2", groupId = groupId, isTop = true, topTime = 99, lastUpdate = 20)
        val u3 = fakeUnit(notiKey = "k3", groupId = null, isTop = false, topTime = 0, lastUpdate = 30)

        val items = DrawerGrouper.groupAndSort(
            displayUnits = listOf(
                NotiDisplayUnit(u1, emptyList()),
                NotiDisplayUnit(u2, emptyList()),
                NotiDisplayUnit(u3, emptyList()),
            ),
            groups = groups
        )

        // Expect: grouped item + loose item
        assertEquals(2, items.size)
        assert(items[0] is NotiGroupItem)
        assert(items[1] is NotiItem)

        val groupItem = items[0] as NotiGroupItem
        // Within group: top item first
        assertEquals(listOf("k2", "k1"), groupItem.children.map { it.notiUnit.notiKey })
    }

    @Test
    fun `groupAndSort does not create group when only one child`() {
        val groupId = "g1"
        val groups = listOf(NotiGroup(groupId = groupId, title = "Group"))

        val u1 = fakeUnit(notiKey = "k1", groupId = groupId, isTop = false, topTime = 0, lastUpdate = 10)

        val items = DrawerGrouper.groupAndSort(
            displayUnits = listOf(NotiDisplayUnit(u1, emptyList())),
            groups = groups
        )

        assertEquals(1, items.size)
        assert(items[0] is NotiItem)
    }

    @Test
    fun `groupAndSort sorts final list by top then topTime then latestTime`() {
        val groups = emptyList<NotiGroup>()

        val topOld = fakeUnit(notiKey = "topOld", groupId = null, isTop = true, topTime = 1, lastUpdate = 50)
        val topNew = fakeUnit(notiKey = "topNew", groupId = null, isTop = true, topTime = 2, lastUpdate = 1)
        val nonTop = fakeUnit(notiKey = "nonTop", groupId = null, isTop = false, topTime = 0, lastUpdate = 999)

        val items: List<NotiDrawerItem> = DrawerGrouper.groupAndSort(
            displayUnits = listOf(
                NotiDisplayUnit(nonTop, emptyList()),
                NotiDisplayUnit(topOld, emptyList()),
                NotiDisplayUnit(topNew, emptyList()),
            ),
            groups = groups
        )

        // Top with higher topTime first, regardless of latestTime
        assertEquals(listOf("topNew", "topOld", "nonTop"), items.map { it.id })
    }

    private fun fakeUnit(
        notiKey: String,
        groupId: String?,
        isTop: Boolean,
        topTime: Long,
        lastUpdate: Long,
    ): NotiUnit {
        return NotiUnit(
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
                isPinned = false
                isRead = true
            },
            taskAttr = org.muilab.notigpt.model.notifications.components.NotiTaskAttr(
                shouldExtractTask = false,
                hasGenuineTask = false
            ),
            groupId = groupId
        )
    }
}
