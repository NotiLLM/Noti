package org.muilab.notigpt.data.remote.firestore

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationUsageRepositoryTest {

    private val zone = ZoneId.of("UTC")
    private val repo = NotificationUsageRepository()

    @Test
    fun `buckets floor to the nearest 6-hour boundary`() {
        val at = ZonedDateTime.of(2026, 7, 23, 14, 45, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("uid1_2026-07-23_12", repo.bucketDocId("uid1", at, zone))
    }

    @Test
    fun `midnight falls in the 00 bucket`() {
        val at = ZonedDateTime.of(2026, 7, 23, 0, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("uid1_2026-07-23_00", repo.bucketDocId("uid1", at, zone))
    }

    @Test
    fun `just before a boundary stays in the earlier bucket`() {
        val at = ZonedDateTime.of(2026, 7, 23, 17, 59, 59, 0, zone).toInstant().toEpochMilli()
        assertEquals("uid1_2026-07-23_12", repo.bucketDocId("uid1", at, zone))
    }

    @Test
    fun `just after a boundary moves to the next bucket`() {
        val at = ZonedDateTime.of(2026, 7, 23, 18, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("uid1_2026-07-23_18", repo.bucketDocId("uid1", at, zone))
    }

    @Test
    fun `bucketStart is the exact boundary instant, not the sample time`() {
        val at = ZonedDateTime.of(2026, 7, 23, 14, 45, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 7, 23, 12, 0, 0, 0, zone).toInstant()
        assertEquals(expected, repo.bucketStart(at, zone).toInstant())
    }
}
