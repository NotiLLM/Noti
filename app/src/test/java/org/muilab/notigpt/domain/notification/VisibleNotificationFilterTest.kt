package org.muilab.notigpt.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.components.NotiDisplayState
import org.muilab.notigpt.model.notifications.components.NotiMetadata

class VisibleNotificationFilterTest {

    @Test
    fun `keeps existing and new keys whenever they have active records`() {
        val existing = displayUnit(
            "existing",
            listOf(record("existing-1", "existing"), record("existing-2", "existing")),
        )
        val newKey = displayUnit("new", listOf(record("new-1", "new")))

        assertEquals(
            listOf("existing", "new"),
            listOf(existing, newKey).withActiveRecords().map { it.notiKey },
        )
    }

    @Test
    fun `excludes drawer rows that have no active records`() {
        val withRecord = displayUnit("visible", listOf(record("visible-1", "visible")))
        val withoutRecords = displayUnit("empty", emptyList())

        assertEquals(
            listOf("visible"),
            listOf(withRecord, withoutRecords).withActiveRecords().map { it.notiKey },
        )
    }

    private fun displayUnit(key: String, records: List<NotiRecord>) = NotiDisplayUnit(
        notiUnit = NotiUnit(
            notiKey = key,
            metadata = NotiMetadata(
                pkgName = "pkg",
                hashKey = key.hashCode(),
                groupKey = "",
                isAppGroup = false,
                isGroupChat = false,
                sortKey = "",
                appName = "App",
                icon = "",
                largeIcon = "",
                isPeople = false,
            ),
            displayState = NotiDisplayState(),
        ),
        notiRecords = records,
    )

    private fun record(id: String, key: String) = NotiRecord(
        notiRecordId = id,
        notiKey = key,
        whenTime = 1L,
        postTime = 1L,
    )
}
