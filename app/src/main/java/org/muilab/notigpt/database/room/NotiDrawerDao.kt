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

    @Query("SELECT * FROM noti_drawer WHERE notiKey = :notiKey")
    fun getByNotiKey(notiKey: String): NotiUnit?

    @Query("SELECT * FROM noti_drawer WHERE notiKey in (:notiKeys)")
    fun getByNotiKeys(notiKeys: List<String>): List<NotiUnit>

    @Query("SELECT notiKey FROM noti_drawer WHERE isVisible = 1 AND category = :category")
    fun getVisibleKeysByCategory(category: String): List<String>

    @Query("SELECT notiKey FROM noti_drawer WHERE isVisible = 1 AND isPinned = 0 AND category = :category")
    fun getVisibleNotPinnedKeysByCategory(category: String): List<String>

    @Query("SELECT notiKey FROM noti_drawer WHERE isVisible = 1 AND isCompletelyRead = 0 AND category = :category")
    fun getVisibleNotReadKeysByCategory(category: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(notiUnit: NotiUnit)

    @Insert
    fun insertAllUnits(notiUnits: List<NotiUnit>)

    @Update
    fun update(notiUnit: NotiUnit)

    // Update category of a notification unit (cleaned sort cols)
    @Query("UPDATE noti_drawer SET category = :newCategory, taskState = 0 WHERE notiKey = :notiKey")
    fun updateCategory(notiKey: String, newCategory: String)

    // Flip pin
    @Query("UPDATE noti_drawer SET isPinned = NOT isPinned WHERE notiKey = :notiKey")
    fun flipPin(notiKey: String)

    // Add taskState by one and take remainder with 3
    @Query("UPDATE noti_drawer SET taskState = (taskState + 1) % 3 WHERE notiKey = :notiKey")
    fun incrementTaskState(notiKey: String)

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
    @Query("UPDATE noti_drawer SET isVisible = 0, isCompletelyRead = 1 WHERE notiKey = :notiKey")
    fun setUnitInvisibleByKey(notiKey: String)

    // Set units invisible by keys
    @Query("UPDATE noti_drawer SET isVisible = 0, isCompletelyRead = 1 WHERE notiKey IN (:notiKeys)")
    fun setUnitsInvisibleByKeys(notiKeys: List<String>)

    // Set unit read by key
    @Query("UPDATE noti_drawer SET isCompletelyRead = 1 WHERE notiKey = :notiKey")
    fun setUnitReadByKey(notiKey: String)

    // Set units read by keys
    @Query("UPDATE noti_drawer SET isCompletelyRead = 1 WHERE notiKey IN (:notiKeys)")
    fun setUnitsReadByKeys(notiKeys: List<String>)

    // Task-related attribute setters
    @Query("UPDATE noti_drawer SET shouldExtractTask = :value WHERE notiKey = :notiKey")
    fun setShouldExtractTaskByKey(notiKey: String, value: Boolean)

    @Query("UPDATE noti_drawer SET shouldExtractTask = :value WHERE notiKey IN (:notiKeys)")
    fun setShouldExtractTaskByKeys(notiKeys: List<String>, value: Boolean)

    @Query("UPDATE noti_drawer SET hasGenuineTask = :value WHERE notiKey = :notiKey")
    fun setHasGenuineTaskByKey(notiKey: String, value: Boolean)

    @Query("UPDATE noti_drawer SET hasGenuineTask = :value WHERE notiKey IN (:notiKeys)")
    fun setHasGenuineTaskByKeys(notiKeys: List<String>, value: Boolean)

    // Simplified AutoSorted Query: Removed manual position logic
    @Transaction
    @Query("""
        SELECT * FROM noti_drawer
        WHERE isVisible = 1
        AND (:category = 'General' AND (category = '' OR category = 'General') OR category = :category)
        AND (:appCategory = 'All' OR appCategory = :appCategory)
        ORDER BY sortScore DESC, lastUpdateTime DESC
    """)
    fun getAutoSortedNotifications(
        category: String,
        appCategory: String
    ): Flow<List<NotiUnitWithRecords>>

    // Simplified NoRelation Query
    @Query("""
        SELECT * FROM noti_drawer
        WHERE isVisible = 1
        AND (:category = 'General' AND (category = '' OR category = 'General') OR category = :category)
        AND (:appCategory = 'All' OR appCategory = :appCategory)
        ORDER BY sortScore DESC, lastUpdateTime DESC
    """)
    fun getAutoSortedNotificationsNoRelation(
        category: String,
        appCategory: String
    ): Flow<List<NotiUnit>>

    // Simplified Limited Query
    @Query("""
        SELECT * FROM noti_drawer
        WHERE isVisible = 1
        AND (:category = 'General' AND (category = '' OR category = 'General') OR category = :category)
        AND (:appCategory = 'All' OR appCategory = :appCategory)
        ORDER BY sortScore DESC, lastUpdateTime DESC
        LIMIT :limit
    """)
    fun getAutoSortedNotificationsNoRelationLimited(
        category: String,
        appCategory: String,
        limit: Int
    ): Flow<List<NotiUnit>>

    @Query("UPDATE noti_drawer SET groupId = :groupId WHERE notiKey = :notiKey")
    suspend fun updateGroupId(notiKey: String, groupId: String?)

    @Query("UPDATE noti_drawer SET groupId = :newGroupId WHERE groupId = :oldGroupId")
    suspend fun moveGroupChildren(oldGroupId: String, newGroupId: String?)

    // Update category for all items in a group
    @Query("UPDATE noti_drawer SET category = :newCategory, taskState = 0 WHERE groupId = :groupId")
    suspend fun updateCategoryByGroupId(groupId: String, newCategory: String)

    // Set all items in a group to invisible (Dismiss)
    @Query("UPDATE noti_drawer SET isVisible = 0, isCompletelyRead = 1 WHERE groupId = :groupId AND isPinned = 0")
    suspend fun setGroupInvisible(groupId: String)

    // NEW: Check if the group still has visible items
    @Query("SELECT COUNT(*) FROM noti_drawer WHERE groupId = :groupId AND isVisible = 1")
    suspend fun getVisibleCountForGroup(groupId: String): Int

    // Remove group association for a specific group (used for cleanup)
    @Query("UPDATE noti_drawer SET groupId = NULL WHERE groupId = :groupId")
    suspend fun ungroupItems(groupId: String)
}