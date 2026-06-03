package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.muilab.notigpt.model.features.ReminderExtractionSnapshot

/**
 * Local access layer for extraction snapshots attached to generated reminders.
 *
 * Snapshots preserve which notification records were sent to the extraction pipeline. Keep JSON
 * parsing out of this DAO; parsing belongs in domain helpers or repositories that need the context.
 */
@Dao
interface ReminderSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: ReminderExtractionSnapshot)

    @Query("SELECT * FROM reminder_extraction_snapshot WHERE snapshotId = :snapshotId LIMIT 1")
    suspend fun getSnapshot(snapshotId: String): ReminderExtractionSnapshot?

    @Query("SELECT * FROM reminder_extraction_snapshot WHERE savedItemId = :savedItemId AND status = 'KEPT' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestKeptSnapshotForReminder(savedItemId: String): ReminderExtractionSnapshot?

    @Query("UPDATE reminder_extraction_snapshot SET status = :status, savedItemId = :savedItemId WHERE snapshotId = :snapshotId")
    suspend fun updateSnapshotStatusAndReminderId(snapshotId: String, status: String, savedItemId: String?)

    @Query("DELETE FROM reminder_extraction_snapshot WHERE snapshotId = :snapshotId")
    suspend fun deleteSnapshot(snapshotId: String)

    @Query("DELETE FROM reminder_extraction_snapshot WHERE status = 'DISCARDED' AND createdAt < :beforeMs")
    suspend fun deleteDiscardedSnapshotsBefore(beforeMs: Long)
}
