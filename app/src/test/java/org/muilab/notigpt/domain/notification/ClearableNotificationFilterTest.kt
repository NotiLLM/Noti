package org.muilab.notigpt.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.components.NotiDisplayState
import org.muilab.notigpt.model.notifications.components.NotiMetadata
import org.muilab.notigpt.model.notifications.components.NotiReminderAttr

class ClearableNotificationFilterTest {

    @Test
    fun `counts active unpinned display units as clearable`() {
        val units = listOf(
            fakeDisplayUnit(notiKey = "a", isPinned = false, isDismissed = false),
            fakeDisplayUnit(notiKey = "b", isPinned = true, isDismissed = false),
            fakeDisplayUnit(notiKey = "c", isPinned = false, isDismissed = true),
        )

        assertEquals(1, countClearableActiveNotifications(units))
    }

    private fun fakeDisplayUnit(
        notiKey: String,
        isPinned: Boolean,
        isDismissed: Boolean,
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
                    lastUpdateTime = 0L,
                    lastSyncTime = 0L,
                    icon = "",
                    largeIcon = "",
                    isPeople = false,
                ),
                displayState = NotiDisplayState().apply {
                    this.isPinned = isPinned
                    this.isDismissed = isDismissed
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
