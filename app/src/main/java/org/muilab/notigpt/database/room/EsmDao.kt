package org.muilab.notigpt.database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.muilab.notigpt.model.esm.EsmAnswerEvent
import org.muilab.notigpt.model.esm.EsmInstance

@Dao
interface EsmDao {

    // --- Instances ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstance(instance: EsmInstance)

    @Query("SELECT * FROM esm_instance WHERE instanceId = :instanceId LIMIT 1")
    suspend fun getInstance(instanceId: String): EsmInstance?

    @Query("SELECT * FROM esm_instance WHERE status = :status ORDER BY availableAt ASC")
    suspend fun getInstancesByStatus(status: String): List<EsmInstance>

    @Query("SELECT * FROM esm_instance WHERE status IN (:statuses) ORDER BY availableAt ASC")
    suspend fun getInstancesByStatuses(statuses: List<String>): List<EsmInstance>

    /**
     * AVAILABLE instances whose questionnaire window has not expired.
     *
     * NOTE: This enforces the 'must answer within N minutes of being shown/available' rule.
     */
    @Query("SELECT * FROM esm_instance WHERE status = 'AVAILABLE' AND expiresAt > :nowMs ORDER BY availableAt ASC")
    suspend fun getUnexpiredAvailable(nowMs: Long): List<EsmInstance>

    @Query("SELECT * FROM esm_instance WHERE status = 'AVAILABLE' AND expiresAt > (strftime('%s','now') * 1000) ORDER BY availableAt ASC")
    fun getUnexpiredAvailableFlow(): Flow<List<EsmInstance>>

    @Query("UPDATE esm_instance SET status = :status WHERE instanceId = :instanceId")
    suspend fun setInstanceStatus(instanceId: String, status: String)

    @Query("UPDATE esm_instance SET triggerType = :triggerType WHERE instanceId = :instanceId")
    suspend fun setTriggerType(instanceId: String, triggerType: String)

    @Query(
        "UPDATE esm_instance SET status = :status, answeredAt = :answeredAt, isLate = :isLate WHERE instanceId = :instanceId"
    )
    suspend fun markAnswered(instanceId: String, status: String, answeredAt: Long, isLate: Boolean)

    @Query("SELECT * FROM esm_instance WHERE reminderId = :reminderId LIMIT 1")
    suspend fun getInstanceByReminderId(reminderId: String): EsmInstance?

    @Query("SELECT MAX(availableAt) FROM esm_instance WHERE status = 'AVAILABLE'")
    suspend fun getLastAvailableAt(): Long?

    @Query("SELECT MAX(answeredAt) FROM esm_instance WHERE status = 'ANSWERED'")
    suspend fun getLastAnsweredAt(): Long?

    @Query("SELECT COUNT(*) FROM esm_instance WHERE createdAt BETWEEN :startMs AND :endMs")
    suspend fun countCreatedBetween(startMs: Long, endMs: Long): Int

    @Query("SELECT COUNT(*) FROM esm_instance WHERE availableAt BETWEEN :startMs AND :endMs")
    suspend fun countDeliveredBetween(startMs: Long, endMs: Long): Int

    @Query("SELECT COUNT(*) FROM esm_instance WHERE status IN ('PENDING','AVAILABLE')")
    suspend fun getActiveCount(): Int

    @Query("SELECT * FROM esm_instance WHERE status = 'AVAILABLE' ORDER BY availableAt DESC LIMIT 1")
    suspend fun getNewestAvailable(): EsmInstance?

    @Query("SELECT COUNT(*) FROM esm_instance WHERE reminderId = :reminderId")
    suspend fun countInstancesByReminderId(reminderId: String): Int

    @Query("UPDATE esm_instance SET availableAt = :availableAt, expiresAt = :expiresAt, status = :status WHERE instanceId = :instanceId")
    suspend fun rescheduleInstance(instanceId: String, availableAt: Long, expiresAt: Long, status: String)

    // --- Answer events ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnswerEvent(event: EsmAnswerEvent)

    @Query("SELECT * FROM esm_answer_event WHERE instanceId = :instanceId ORDER BY answeredAt ASC")
    suspend fun getAnswerEvents(instanceId: String): List<EsmAnswerEvent>

    // --- Snapshots ---

    // (Moved to ReminderSnapshotDao)

    // --- Convenience / atomic ops ---

    @Transaction
    suspend fun saveAnswerAndMaybeMarkAnswered(
        instanceId: String,
        questionId: String,
        answerJson: String,
        answeredAt: Long,
    ) {
        upsertAnswerEvent(
            EsmAnswerEvent(
                instanceId = instanceId,
                questionId = questionId,
                answerJson = answerJson,
                answeredAt = answeredAt,
            )
        )
    }
}
