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

    @Query("SELECT COUNT(*) FROM noti_drawer WHERE isDismissed = 0")
    fun getActiveNotiCount(): Int

    @Query("SELECT COUNT(*) FROM noti_drawer WHERE isDismissed = 0 AND isRead = 0")
    fun getActiveUnreadCount(): Int

    @Query("SELECT * FROM noti_drawer")
    fun getAll(): List<NotiUnit>

    @Query("SELECT * FROM noti_drawer WHERE isDismissed = 0")
    fun getAllActive(): List<NotiUnit>

    @Query("SELECT * FROM noti_drawer WHERE isDismissed = 0")
    fun getAllActiveFlow(): Flow<List<NotiUnit>>

    @Query("SELECT notiKey FROM noti_drawer WHERE isDismissed = 0")
    fun getAllActiveKeys(): List<String>

    @Query("SELECT * FROM noti_drawer WHERE notiKey = :notiKey")
    fun getByNotiKey(notiKey: String): NotiUnit?

    @Query("SELECT * FROM noti_drawer WHERE notiKey in (:notiKeys)")
    fun getByNotiKeys(notiKeys: List<String>): List<NotiUnit>

    @Query("SELECT notiKey FROM noti_drawer WHERE isDismissed = 0 AND isPinned = 0")
    fun getActiveNotPinnedKeys(): List<String>

    @Query("SELECT notiKey FROM noti_drawer WHERE isDismissed = 0 AND isRead = 0")
    fun getActiveUnreadKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(notiUnit: NotiUnit)

    @Insert
    fun insertAllUnits(notiUnits: List<NotiUnit>)

    @Update
    fun update(notiUnit: NotiUnit)

    // Update ToTop status
    @Query("UPDATE noti_drawer SET isSetToTop = :isSetToTop, setToTopTime = :timestamp WHERE notiKey = :notiKey")
    suspend fun updateToTopStatus(notiKey: String, isSetToTop: Boolean, timestamp: Long)

    // Flip pin
    @Query("UPDATE noti_drawer SET isPinned = NOT isPinned WHERE notiKey = :notiKey")
    suspend fun flipPin(notiKey: String)

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

    // Dismiss unit by key (Also set isRead = 1)
    @Query("UPDATE noti_drawer SET isDismissed = 1, isRead = 1 WHERE notiKey = :notiKey")
    suspend fun dismissUnitByKey(notiKey: String)

    // Dismiss units by keys
    @Query("UPDATE noti_drawer SET isDismissed = 1, isRead = 1 WHERE notiKey IN (:notiKeys)")
    suspend fun dismissUnitsByKeys(notiKeys: List<String>)

    // Set unit read by key
    @Query("UPDATE noti_drawer SET isRead = 1 WHERE notiKey = :notiKey")
    suspend fun setUnitReadByKey(notiKey: String)

    // Set units read by keys
    @Query("UPDATE noti_drawer SET isRead = 1 WHERE notiKey IN (:notiKeys)")
    suspend fun setUnitsReadByKeys(notiKeys: List<String>)

    // Task-related attribute setters
    @Query("UPDATE noti_drawer SET shouldExtractTask = :value WHERE notiKey = :notiKey")
    suspend fun setShouldExtractTaskByKey(notiKey: String, value: Boolean)

    @Query("UPDATE noti_drawer SET shouldExtractTask = :value WHERE notiKey IN (:notiKeys)")
    fun setShouldExtractTaskByKeys(notiKeys: List<String>, value: Boolean)

    @Query("UPDATE noti_drawer SET hasGenuineTask = :value WHERE notiKey = :notiKey")
    fun setHasGenuineTaskByKey(notiKey: String, value: Boolean)

    @Query("UPDATE noti_drawer SET hasGenuineTask = :value WHERE notiKey IN (:notiKeys)")
    fun setHasGenuineTaskByKeys(notiKeys: List<String>, value: Boolean)

    // Manual ordering
    @Query("UPDATE noti_drawer SET sortPosition = :newPosition WHERE notiKey = :notiKey")
    suspend fun updateSortPosition(notiKey: String, newPosition: Int)

    @Query("UPDATE noti_drawer SET sortPosition = -1 WHERE notiKey = :notiKey")
    suspend fun resetSortPosition(notiKey: String)

    @Query("UPDATE noti_drawer SET sortPosition = -1")
    suspend fun resetAllSortPositions()

    /** Any notification inside a group should not have a manual position. */
    @Query("UPDATE noti_drawer SET sortPosition = -1 WHERE groupId IS NOT NULL")
    suspend fun clearSortPositionsForGroupedItems()

    /** Current manual positions for active loose (non-grouped) notifications. */
    @Query("""
        SELECT notiKey FROM noti_drawer
        WHERE isDismissed = 0 AND groupId IS NULL AND sortPosition != -1
        ORDER BY sortPosition ASC
    """)
    suspend fun getActiveLooseManualKeysOrdered(): List<String>

    data class ManualKeyPos(
        val notiKey: String,
        val sortPosition: Int,
    )

    /** Current manual positions (key + position) for active loose (non-grouped) notifications. */
    @Query("""
        SELECT notiKey, sortPosition FROM noti_drawer
        WHERE isDismissed = 0 AND groupId IS NULL AND sortPosition != -1
        ORDER BY sortPosition ASC
    """)
    suspend fun getActiveLooseManualKeyPositionsOrdered(): List<ManualKeyPos>

    /** Reset manual positions for active loose (non-grouped) notifications. */
    @Query("UPDATE noti_drawer SET sortPosition = -1 WHERE isDismissed = 0 AND groupId IS NULL AND sortPosition != -1")
    suspend fun resetActiveLooseManualPositions()

    // Active drawer stream, auto-sorted (manual positions will be applied in domain layer)
    @Query("""
        SELECT * FROM noti_drawer
        WHERE isDismissed = 0
        ORDER BY sortScore DESC, isRead ASC, lastUpdateTime DESC
    """)
    fun getAutoSortedActiveNotificationsNoRelation(): Flow<List<NotiUnit>>

    @Query("UPDATE noti_drawer SET groupId = :groupId WHERE notiKey = :notiKey")
    suspend fun updateGroupId(notiKey: String, groupId: String?)

    @Query("UPDATE noti_drawer SET groupId = :newGroupId WHERE groupId = :oldGroupId")
    suspend fun moveGroupChildren(oldGroupId: String, newGroupId: String?)

    // Batch update ToTop for a group
    @Query("UPDATE noti_drawer SET isSetToTop = :isSetToTop, setToTopTime = :timestamp WHERE groupId = :groupId")
    suspend fun updateToTopStatusByGroupId(groupId: String, isSetToTop: Boolean, timestamp: Long)

    // Dismiss all items in a group (Dismiss)
    @Query("UPDATE noti_drawer SET isDismissed = 1, isRead = 1 WHERE groupId = :groupId AND isPinned = 0")
    suspend fun dismissGroup(groupId: String)

    // Check if the group still has active items
    @Query("SELECT COUNT(*) FROM noti_drawer WHERE groupId = :groupId AND isDismissed = 0")
    suspend fun getActiveCountForGroup(groupId: String): Int

    // Remove group association for a specific group (used for cleanup)
    @Query("UPDATE noti_drawer SET groupId = NULL WHERE groupId = :groupId")
    suspend fun ungroupItems(groupId: String)

    @Transaction
    suspend fun updateSortPositionsBulk(updates: List<Pair<String, Int>>) {
        updates.forEach { (key, pos) ->
            updateSortPosition(key, pos)
        }
    }
}
