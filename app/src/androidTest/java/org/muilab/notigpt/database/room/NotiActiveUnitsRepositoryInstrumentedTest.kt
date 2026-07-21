package org.muilab.notigpt.database.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.repository.notification.NotiActiveUnitsRepository
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.components.NotiDisplayState
import org.muilab.notigpt.model.notifications.components.NotiMetadata

@RunWith(AndroidJUnit4::class)
class NotiActiveUnitsRepositoryInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: NotiActiveUnitsRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NotiActiveUnitsRepository(db.drawerDao(), db.recordDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun existingKeyEmitsAgainWhenAnotherRecordArrives() = runBlocking {
        db.drawerDao().insert(unit("existing"))
        db.recordDao().upsert(record("record-1", "existing", 1L))
        repository.getActiveNotiUnits().first { it.singleOrNull()?.notiRecords?.size == 1 }

        val update = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000) {
                repository.getActiveNotiUnits().first {
                    it.singleOrNull()?.notiRecords?.map(NotiRecord::notiRecordId) ==
                        listOf("record-1", "record-2")
                }
            }
        }

        db.recordDao().upsert(record("record-2", "existing", 2L))

        assertEquals(listOf("record-1", "record-2"), update.await().single().notiRecords.map { it.notiRecordId })
    }

    @Test
    fun newKeyEmitsWhenItsDrawerRowAndRecordArrive() = runBlocking {
        db.drawerDao().insert(unit("existing"))
        db.recordDao().upsert(record("record-1", "existing", 1L))
        repository.getActiveNotiUnits().first { it.size == 1 }

        val update = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000) {
                repository.getActiveNotiUnits().first { units ->
                    units.any { it.notiKey == "new" && it.notiRecords.singleOrNull()?.notiRecordId == "record-2" }
                }
            }
        }

        db.drawerDao().insert(unit("new"))
        db.recordDao().upsert(record("record-2", "new", 2L))

        assertEquals(setOf("existing", "new"), update.await().mapTo(mutableSetOf()) { it.notiKey })
    }

    @Test
    fun dismissedKeyDisappearsFromTheLiveStream() = runBlocking {
        db.drawerDao().insert(unit("existing"))
        db.recordDao().upsert(record("record-1", "existing", 1L))
        repository.getActiveNotiUnits().first { it.size == 1 }

        val update = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000) { repository.getActiveNotiUnits().first { it.isEmpty() } }
        }

        db.drawerDao().dismissUnitByKey("existing")
        db.recordDao().dismissRecordsByKey("existing")

        assertEquals(emptyList<NotiUnit>(), update.await().map { it.notiUnit })
    }

    private fun unit(key: String) = NotiUnit(
        notiKey = key,
        metadata = NotiMetadata(
            pkgName = "pkg",
            hashKey = key.hashCode(),
            groupKey = "",
            isAppGroup = false,
            isGroupChat = false,
            sortKey = "",
            appName = "App",
            lastUpdateTime = 1L,
            icon = "",
            largeIcon = "",
            isPeople = false,
        ),
        displayState = NotiDisplayState(),
    )

    private fun record(id: String, key: String, time: Long) = NotiRecord(
        notiRecordId = id,
        notiKey = key,
        whenTime = time,
        postTime = time,
    )
}
