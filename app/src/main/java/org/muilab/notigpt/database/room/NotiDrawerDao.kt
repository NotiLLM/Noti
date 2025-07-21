package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.NotiUnitWithRecords
import org.muilab.notigpt.model.server.SortOutcome

@Dao
interface NotiDrawerDao {

    @Query("SELECT COUNT(*) FROM noti_drawer WHERE isVisible = 1")
    fun getVisibleNotiCount(): Int

    @Query("SELECT COUNT(*) FROM noti_drawer WHERE isVisible = 1 AND category = :category")
    fun getVisibleNotiCountByCategory(category: String): Int

    @Query("SELECT COUNT(*) FROM noti_drawer WHERE isVisible = 1 AND isCompletelyRead = 0 AND category = :category")
    fun getVisibleNotReadCountByCategory(category: String): Int

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

    @Query("SELECT notiKey FROM noti_drawer WHERE isVisible = 1 AND category = :category")
    fun getVisibleKeysByCategory(category: String): List<String>

    @Query("SELECT notiKey FROM noti_drawer WHERE isVisible = 1 AND isPinned = 0 AND category = :category")
    fun getVisibleNotPinnedKeysByCategory(category: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(notiUnit: NotiUnit)

    @Insert
    fun insertAllUnits(notiUnits: List<NotiUnit>)

    @Update
    fun update(notiUnit: NotiUnit)

    // Update category of a notification unit
    @Query("UPDATE noti_drawer SET category = :newCategory, sortPosition = -1, appCategorySortPosition = -1 WHERE notiKey = :notiKey")
    fun updateCategory(notiKey: String, newCategory: String)

    // Flip pin
    @Query("UPDATE noti_drawer SET isPinned = NOT isPinned WHERE notiKey = :notiKey")
    fun flipPin(notiKey: String)

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

    @Query("UPDATE noti_drawer SET sortPosition = :newPosition WHERE notiKey = :notiKey")
    suspend fun updateSortPosition(notiKey: String, newPosition: Int)

    @Query("UPDATE noti_drawer SET appCategorySortPosition = :newPosition WHERE notiKey = :notiKey")
    suspend fun updateAppCategorySortPosition(notiKey: String, newPosition: Int)

    @Transaction
    suspend fun updateSortPositions(updates: List<Pair<String, Int>>, isAppCategoryView: Boolean) {
        updates.forEach { (key, pos) ->
            if (isAppCategoryView) {
                updateAppCategorySortPosition(key, pos)
            } else {
                updateSortPosition(key, pos)
            }
        }
    }

    @Query("UPDATE noti_drawer SET sortPosition = -1, appCategorySortPosition = -1")
    suspend fun resetAllSortPositions()

    // Set unit invisible by key
    @Query("UPDATE noti_drawer SET isVisible = 0, isCompletelyRead = 1 WHERE notiKey = :notiKey")
    fun setUnitInvisibleByKey(notiKey: String)

    // Set units invisible by keys
    @Query("UPDATE noti_drawer SET isVisible = 0, isCompletelyRead = 1 WHERE notiKey IN (:notiKeys)")
    fun setUnitsInvisibleByKeys(notiKeys: List<String>)

    // Set units read by keys
    @Query("UPDATE noti_drawer SET isCompletelyRead = 1 WHERE notiKey IN (:notiKeys)")
    fun setUnitsReadByKeys(notiKeys: List<String>)

    @Transaction
    @Query("""
        SELECT * FROM noti_drawer
        WHERE isVisible = 1
        AND (:category = 'General' AND (category = '' OR category = 'General') OR category = :category)
        AND (:appCategory = 'All' OR appCategory = :appCategory)
        AND (CASE WHEN :isAppCategoryView = 1 THEN appCategorySortPosition != -1 ELSE sortPosition != -1 END)
        ORDER BY CASE WHEN :isAppCategoryView = 1 THEN appCategorySortPosition ELSE sortPosition END ASC
    """)
    fun getManuallySortedNotifications(
        category: String,
        appCategory: String,
        isAppCategoryView: Boolean
    ): Flow<List<NotiUnitWithRecords>>

    @Transaction
    @Query("""
        SELECT * FROM noti_drawer
        WHERE isVisible = 1
        AND (:category = 'General' AND (category = '' OR category = 'General') OR category = :category)
        AND (:appCategory = 'All' OR appCategory = :appCategory)
        AND (CASE WHEN :isAppCategoryView = 1 THEN appCategorySortPosition = -1 ELSE sortPosition = -1 END)
        ORDER BY sortScore DESC, lastUpdateTime DESC
    """)
    fun getAutoSortedNotifications(
        category: String,
        appCategory: String,
        isAppCategoryView: Boolean
    ): Flow<List<NotiUnitWithRecords>>
}