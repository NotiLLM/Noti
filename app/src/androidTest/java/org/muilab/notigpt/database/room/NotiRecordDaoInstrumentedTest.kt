package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.local.room.dao.NotiRecordDao
import org.muilab.notigpt.model.notifications.NotiRecord

@RunWith(AndroidJUnit4::class)
class NotiRecordDaoInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NotiRecordDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            // For tests we don’t need migrations, and allowing main thread simplifies setup.
            .allowMainThreadQueries()
            .build()
        dao = db.recordDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun hasRecordsInGap_countsBothVisibleAndHiddenRecords() = runBlocking {
        val key = "k"
        val recVisible = fakeRecord(id = "r1", key = key, whenTime = 100, visible = true)
        val recHidden = fakeRecord(id = "r2", key = key, whenTime = 200, visible = false)
        dao.insertAllRecords(listOf(recVisible, recHidden))

        // Gap includes both records
        val min = 50L
        val max = 250L

        // Current DAO counts all records in the table for the window.
        val count = dao.hasRecordsInGap(key, min, max)
        assertEquals(2, count)
    }

    @Test
    fun getGapRecordsNewer_returnsAscendingWithinWindow() = runBlocking {
        val key = "k"
        val records = listOf(
            fakeRecord(id = "r1", key = key, whenTime = 100, visible = true),
            fakeRecord(id = "r2", key = key, whenTime = 110, visible = true),
            fakeRecord(id = "r3", key = key, whenTime = 120, visible = true),
        )
        dao.insertAllRecords(records)

        val got = dao.getGapRecordsNewer(notiKey = key, minTime = 90, maxTime = 130, limit = 10)
        assertEquals(listOf("r1", "r2", "r3"), got.map { it.notiRecordId })
    }

    @Test
    fun getGapRecordsOlder_returnsDescendingWithinWindow() = runBlocking {
        val key = "k"
        val records = listOf(
            fakeRecord(id = "r1", key = key, whenTime = 100, visible = true),
            fakeRecord(id = "r2", key = key, whenTime = 110, visible = true),
            fakeRecord(id = "r3", key = key, whenTime = 120, visible = true),
        )
        dao.insertAllRecords(records)

        val got = dao.getGapRecordsOlder(notiKey = key, minTime = 90, maxTime = 130, limit = 10)
        assertEquals(listOf("r3", "r2", "r1"), got.map { it.notiRecordId })
    }

    @Test
    fun getContextOlder_andNewer_respectPivotAndOrdering() = runBlocking {
        val key = "k"
        dao.insertAllRecords(
            listOf(
                fakeRecord(id = "r1", key = key, whenTime = 100, visible = true),
                fakeRecord(id = "r2", key = key, whenTime = 200, visible = true),
                fakeRecord(id = "r3", key = key, whenTime = 300, visible = true),
            )
        )

        val older = dao.getContextOlder(notiKey = key, pivotTime = 250, limit = 10)
        // DAO contract: DESC ordering
        assertEquals(listOf("r2", "r1"), older.map { it.notiRecordId })

        val newer = dao.getContextNewer(notiKey = key, pivotTime = 250, limit = 10)
        // DAO contract: ASC ordering
        assertEquals(listOf("r3"), newer.map { it.notiRecordId })

        // sanity
        assertTrue(older.all { it.whenTime < 250 })
        assertTrue(newer.all { it.whenTime > 250 })
    }

    private fun fakeRecord(id: String, key: String, whenTime: Long, visible: Boolean): NotiRecord {
        // NotiRecord is a Room entity with many fields; set the ones used by queries.
        // Unused text fields can be empty.
        return NotiRecord(
            notiRecordId = id,
            notiKey = key,
            whenTime = whenTime,
            postTime = whenTime,
            person = "",
            extraTitle = "",
            extraBigTitle = "",
            extraConversationTitle = "",
            extraBigText = "",
            extraText = "",
            extraTextLines = "",
            extraSummaryText = "",
            extraInfoText = "",
            extraSubText = "",
            isDismissed = !visible,
        )
    }
}
