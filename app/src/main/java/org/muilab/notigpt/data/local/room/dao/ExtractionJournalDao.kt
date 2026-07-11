package org.muilab.notigpt.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.muilab.notigpt.model.features.ExtractionJournalEntry
import org.muilab.notigpt.model.features.ExtractionJournalSummary

/**
 * Local access layer for the per-notiUnit extraction journal (entries + rolling summary).
 *
 * The journal is the extraction pipeline's memory; repositories read it when building request
 * payloads and fold old entries into the summary when the verbatim window overflows.
 */
@Dao
interface ExtractionJournalDao {

    @Insert
    suspend fun insertEntry(entry: ExtractionJournalEntry): Long

    @Query("SELECT * FROM extraction_journal_entry WHERE notiKey = :notiKey ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentEntries(notiKey: String, limit: Int): List<ExtractionJournalEntry>

    @Query("SELECT * FROM extraction_journal_entry WHERE notiKey = :notiKey ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getOldestEntries(notiKey: String, limit: Int): List<ExtractionJournalEntry>

    @Query("SELECT COUNT(*) FROM extraction_journal_entry WHERE notiKey = :notiKey")
    suspend fun getEntryCount(notiKey: String): Int

    @Query("SELECT DISTINCT notiKey FROM extraction_journal_entry")
    suspend fun getKeysWithEntries(): List<String>

    /** Refreshes a no_extraction entry in place so repeated verdicts don't pile up. */
    @Query("UPDATE extraction_journal_entry SET detail = :detail, createdAt = :createdAt WHERE entryId = :entryId")
    suspend fun updateEntryDetail(entryId: Long, detail: String, createdAt: Long)

    /** Mechanical truncation for orphan threads: drops the oldest entries beyond [keep]. */
    @Query(
        """
        DELETE FROM extraction_journal_entry WHERE entryId IN (
            SELECT entryId FROM extraction_journal_entry
            WHERE notiKey = :notiKey ORDER BY createdAt DESC, entryId DESC LIMIT -1 OFFSET :keep
        )
        """
    )
    suspend fun deleteEntriesBeyond(notiKey: String, keep: Int)

    @Query("SELECT DISTINCT savedItemId FROM extraction_journal_entry WHERE notiKey IN (:notiKeys) AND savedItemId != ''")
    suspend fun getSavedItemIdsForKeys(notiKeys: List<String>): List<String>

    @Query("DELETE FROM extraction_journal_entry WHERE entryId IN (:entryIds)")
    suspend fun deleteEntries(entryIds: List<Long>)

    @Query("SELECT * FROM extraction_journal_summary WHERE notiKey = :notiKey")
    suspend fun getSummary(notiKey: String): ExtractionJournalSummary?

    @Query("SELECT * FROM extraction_journal_summary WHERE notiKey IN (:notiKeys)")
    suspend fun getSummaries(notiKeys: List<String>): List<ExtractionJournalSummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: ExtractionJournalSummary)

    /** Account-switch wipe. */
    @Query("DELETE FROM extraction_journal_entry")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM extraction_journal_summary")
    suspend fun deleteAllSummaries()
}
