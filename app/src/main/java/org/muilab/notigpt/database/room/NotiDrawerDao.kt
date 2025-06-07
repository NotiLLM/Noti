package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.server.SortOutcome

@Dao
interface NotiDrawerDao {

    @Query("SELECT COUNT(*) FROM noti_drawer WHERE isVisible = 1")
    fun getVisibleNotiCount(): Int

    @Query("SELECT * FROM noti_drawer")
    fun getAll(): List<NotiUnit>

    @Query("SELECT * FROM noti_drawer WHERE isVisible = 1")
    fun getAllVisible(): List<NotiUnit>

    @Query("SELECT * FROM noti_drawer WHERE isVisible = 1")
    fun getAllVisibleFlow(): Flow<List<NotiUnit>>

    @Query("SELECT notiKey FROM noti_drawer WHERE isVisible = 1")
    fun getAllVisibleKeys(): List<String>

    @Query("SELECT * FROM noti_drawer WHERE notiKey = :notiKey AND isVisible = 1")
    fun getByNotiKey(notiKey: String): NotiUnit?

    @Query("SELECT * FROM noti_drawer WHERE notiKey in (:notiKeys)")
    fun getByNotiKeys(notiKeys: List<String>): List<NotiUnit>

    @Query("SELECT notiKey FROM noti_drawer WHERE isVisible = 1 AND isPinned = 0 AND category = :category")
    fun getVisibleNotPinnedKeysByCategory(category: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(notiUnit: NotiUnit)

    @Update
    fun update(notiUnit: NotiUnit)

    @Query("UPDATE noti_drawer SET sortScore = :newSortScore WHERE notiKey = :notiKey")
    fun updateSortScore(notiKey: String, newSortScore: Float)

    @Query("UPDATE noti_drawer SET explanation = :newExplanation WHERE notiKey = :notiKey")
    fun updateExplanation(notiKey: String, newExplanation: String)

    @Transaction
    suspend fun updateSorting(sortOutcomes: List<SortOutcome>) {
        sortOutcomes.forEach { sortOutcome ->
            updateSortScore(sortOutcome.id, sortOutcome.score)
            updateExplanation(sortOutcome.id, sortOutcome.explanation)
        }
    }

    // Set unit invisible by key
    @Query("UPDATE noti_drawer SET isVisible = 0 WHERE notiKey = :notiKey")
    fun setUnitInvisibleByKey(notiKey: String)

    // Set units invisible by keys
    @Query("UPDATE noti_drawer SET isVisible = 0 WHERE notiKey IN (:notiKeys)")
    fun setUnitsInvisibleByKeys(notiKeys: List<String>)

    // Set units invisible by keys
    @Query("UPDATE noti_drawer SET isCompletelyRead = 1 WHERE notiKey IN (:notiKeys)")
    fun setUnitsReadByKeys(notiKeys: List<String>)
}