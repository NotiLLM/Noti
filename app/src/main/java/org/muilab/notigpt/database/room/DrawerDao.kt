package org.muilab.notigpt.database.room

import androidx.lifecycle.LiveData
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
interface DrawerDao {

    @Query("SELECT COUNT(*) FROM noti_drawer WHERE isVisible = 1")
    fun getAllNotiCount(): Int

    @Query("SELECT COUNT(*) FROM noti_drawer WHERE isVisible = 1 AND wholeNotiRead = 0")
    fun getNotiNotSeenCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM noti_drawer WHERE isVisible = 1 AND wholeNotiRead = 1 AND pinned = 1")
    fun getNotiPinnedSeenCount(): Int

    @Query(
        """
        SELECT * FROM noti_drawer WHERE isVisible = 1 
        ORDER BY 
            CASE 
                /*
                WHEN pinned = 1 AND wholeNotiRead = 1 THEN 1
                WHEN pinned = 1 AND wholeNotiRead = 0 THEN 2
                WHEN pinned = 0 AND wholeNotiRead = 0 THEN 3
                WHEN pinned = 0 AND wholeNotiRead = 1 THEN 4
                */
                WHEN wholeNotiRead = 0 THEN 1
                WHEN wholeNotiRead = 1 THEN 2
            END,
            score DESC,
            latestTime DESC
    """
    )
    fun getAllVisible(): List<NotiUnit>

    @Query("""
        SELECT * FROM noti_drawer WHERE isVisible = 1 
        ORDER BY 
            CASE 
                /*
                WHEN pinned = 1 AND wholeNotiRead = 1 THEN 1
                WHEN pinned = 1 AND wholeNotiRead = 0 THEN 2
                WHEN pinned = 0 AND wholeNotiRead = 0 THEN 3
                WHEN pinned = 0 AND wholeNotiRead = 1 THEN 4
                */
                WHEN wholeNotiRead = 0 THEN 1
                WHEN wholeNotiRead = 1 THEN 2
            END,
            score DESC,
            latestTime DESC
    """)
    fun getAllVisibleFlow(): Flow<List<NotiUnit>>

    @Query(
        """
        SELECT * FROM noti_drawer WHERE isVisible = 1 AND wholeNotiRead = 0
        ORDER BY latestTime DESC
    """
    )
    fun getnotReadNotis(): List<NotiUnit>

    @Query(
        """
        SELECT * FROM noti_drawer WHERE isVisible = 1 AND isPeople = 1
        ORDER BY latestTime DESC
    """
    )
    fun getNotiWithSenders(): List<NotiUnit>

    @Query("SELECT * FROM noti_drawer WHERE sbnKey = :sbnKey")
    fun getBySbnKey(sbnKey: String): List<NotiUnit>

    @Query("SELECT * FROM noti_drawer WHERE sbnKey in (:sbnKeys)")
    fun getBySbnKeys(sbnKeys: List<String>): List<NotiUnit>

    @Query("SELECT * FROM noti_drawer WHERE hashKey = :hashKey")
    fun getByHashKey(hashKey: Int): List<NotiUnit>

    @Query("DELETE FROM noti_drawer WHERE sbnKey = :sbnKey")
    fun deleteBySbnKey(sbnKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(notiUnit: NotiUnit)

    @Update
    fun update(notiUnit: NotiUnit)

    @Update
    fun updateList(notiUnit: List<NotiUnit>)

    @Query("DELETE FROM noti_drawer")
    fun deleteAll()

    @Query("DELETE FROM noti_drawer WHERE pinned <> 1")
    fun deleteAllNotPinned()

    @Query("UPDATE noti_drawer SET score = :newScore WHERE sbnKey = :sbnKey")
    fun updateScore(sbnKey: String, newScore: Float)

    @Query("UPDATE noti_drawer SET explanation = :newExplanation WHERE sbnKey = :sbnKey")
    fun updateExplanation(sbnKey: String, newExplanation: String)

    @Transaction
    suspend fun updateSorting(sortOutcomes: List<SortOutcome>) {
        sortOutcomes.forEach { sortOutcome ->
            updateScore(sortOutcome.id, sortOutcome.score)
            updateExplanation(sortOutcome.id, sortOutcome.explanation)
        }
    }

    @Query("UPDATE noti_drawer SET embeddingString = :newEmbeddingString WHERE sbnKey = :sbnKey")
    fun updateEmbedding(sbnKey: String, newEmbeddingString: String)

    @Query("UPDATE noti_drawer SET similarityScore = :newSimilarity WHERE sbnKey = :sbnKey")
    fun updateSimilarity(sbnKey: String, newSimilarity: Double)

    @Query("UPDATE noti_drawer SET similarityScore = -1")
    fun resetSimilarity()
}