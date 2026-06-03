package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.notifications.NotiUnit

/**
 * Local access layer for the current notification drawer rows.
 *
 * This DAO owns the mutable, latest-known state of each notification key. Historical notification
 * records belong in NotiRecordDao; keep cross-record context assembly outside this DAO when possible.
 */
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

    /** Paginated variant used by the export pipeline to avoid loading all rows at once. */
    @Query("SELECT * FROM noti_drawer LIMIT :limit OFFSET :offset")
    fun getAllPaged(limit: Int, offset: Int): List<NotiUnit>

    /** Paginated variant for active-only export. */
    @Query("SELECT * FROM noti_drawer WHERE isDismissed = 0 LIMIT :limit OFFSET :offset")
    fun getAllActivePaged(limit: Int, offset: Int): List<NotiUnit>

    @Query("SELECT * FROM noti_drawer WHERE isDismissed = 0")
    fun getAllActiveFlow(): Flow<List<NotiUnit>>

    @Query("SELECT notiKey FROM noti_drawer WHERE isDismissed = 0")
    fun getAllActiveKeys(): List<String>

    @Query("SELECT notiKey FROM noti_drawer WHERE isDismissed = 0 AND shouldExtractReminder = 1")
    fun getAllActiveShouldExtractKeys(): List<String>

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

    @Query("UPDATE noti_drawer SET isPinned = :pinned WHERE notiKey = :notiKey")
    suspend fun setPinned(notiKey: String, pinned: Boolean)

    @Query("UPDATE noti_drawer SET sortScore = :newSortScore WHERE notiKey = :notiKey")
    fun updateSortScore(notiKey: String, newSortScore: Float)

    @Query("UPDATE noti_drawer SET explanation = :newExplanation WHERE notiKey = :notiKey")
    fun updateExplanation(notiKey: String, newExplanation: String)

    // Dismiss unit by key (Also set isRead = 1)
    @Query("UPDATE noti_drawer SET isDismissed = 1, isRead = 1, sortPosition = -1 WHERE notiKey = :notiKey")
    suspend fun dismissUnitByKey(notiKey: String)

    // Dismiss units by keys
    @Query("UPDATE noti_drawer SET isDismissed = 1, isRead = 1, sortPosition = -1 WHERE notiKey IN (:notiKeys)")
    suspend fun dismissUnitsByKeys(notiKeys: List<String>)

    // Set unit read by key
    @Query("UPDATE noti_drawer SET isRead = 1 WHERE notiKey = :notiKey")
    suspend fun setUnitReadByKey(notiKey: String)

    // Set units read by keys
    @Query("UPDATE noti_drawer SET isRead = 1 WHERE notiKey IN (:notiKeys)")
    suspend fun setUnitsReadByKeys(notiKeys: List<String>)

    // Task-related attribute setters
    @Query("UPDATE noti_drawer SET shouldExtractReminder = :value WHERE notiKey = :notiKey")
    suspend fun setShouldExtractReminderByKey(notiKey: String, value: Boolean)

    @Query("UPDATE noti_drawer SET shouldExtractReminder = :value WHERE notiKey IN (:notiKeys)")
    fun setShouldExtractReminderByKeys(notiKeys: List<String>, value: Boolean)

    @Query("UPDATE noti_drawer SET hasTask = :value WHERE notiKey = :notiKey")
    fun setHasTaskByKey(notiKey: String, value: Boolean)

    @Query("UPDATE noti_drawer SET hasTask = :value WHERE notiKey IN (:notiKeys)")
    fun setHasTaskByKeys(notiKeys: List<String>, value: Boolean)

    @Query("UPDATE noti_drawer SET hasMemo = :value WHERE notiKey = :notiKey")
    fun setHasMemoByKey(notiKey: String, value: Boolean)

    @Query("UPDATE noti_drawer SET hasMemo = :value WHERE notiKey IN (:notiKeys)")
    fun setHasMemoByKeys(notiKeys: List<String>, value: Boolean)

    @Query("UPDATE noti_drawer SET hasEvent = :value WHERE notiKey = :notiKey")
    fun setHasEventByKey(notiKey: String, value: Boolean)

    @Query("UPDATE noti_drawer SET hasEvent = :value WHERE notiKey IN (:notiKeys)")
    fun setHasEventByKeys(notiKeys: List<String>, value: Boolean)

    // Manual ordering
    @Query("UPDATE noti_drawer SET sortPosition = :newPosition WHERE notiKey = :notiKey")
    suspend fun updateSortPosition(notiKey: String, newPosition: Int)

    @Query("UPDATE noti_drawer SET sortPosition = -1 WHERE notiKey = :notiKey")
    suspend fun resetSortPosition(notiKey: String)

    @Query("UPDATE noti_drawer SET sortPosition = -1")
    suspend fun resetAllSortPositions()

    /** Current manual positions for active notifications. */
    @Query("""
        SELECT notiKey FROM noti_drawer
        WHERE isDismissed = 0 AND sortPosition != -1
        ORDER BY sortPosition ASC
    """)
    suspend fun getActiveManualKeysOrdered(): List<String>

    data class ManualKeyPos(
        val notiKey: String,
        val sortPosition: Int,
    )

    /** Current manual positions (key + position) for active notifications. */
    @Query("""
        SELECT notiKey, sortPosition FROM noti_drawer
        WHERE isDismissed = 0 AND sortPosition != -1
        ORDER BY sortPosition ASC
    """)
    suspend fun getActiveManualKeyPositionsOrdered(): List<ManualKeyPos>

    /** Reset manual positions for active notifications. */
    @Query("UPDATE noti_drawer SET sortPosition = -1 WHERE isDismissed = 0 AND sortPosition != -1")
    suspend fun resetActiveManualPositions()

    // Active drawer stream, auto-sorted (manual positions will be applied in domain layer)
    @Query("""
        SELECT * FROM noti_drawer
        WHERE isDismissed = 0
        ORDER BY sortScore DESC, isRead ASC, lastUpdateTime DESC
    """)
    fun getAutoSortedActiveNotificationsNoRelation(): Flow<List<NotiUnit>>


    @Transaction
    suspend fun updateSortPositionsBulk(updates: List<Pair<String, Int>>) {
        updates.forEach { (key, pos) ->
            updateSortPosition(key, pos)
        }
    }
}
