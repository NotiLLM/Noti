package org.muilab.notigpt.data.repository.reminder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.muilab.notigpt.data.local.room.dao.ExtractionJournalDao
import org.muilab.notigpt.model.features.ExtractionJournalEntry
import org.muilab.notigpt.model.features.ExtractionJournalEventType
import org.muilab.notigpt.model.features.ExtractionJournalSummary

/**
 * Repository for the per-notiUnit extraction journal.
 *
 * The journal is what stops the pipeline from re-creating items the user already resolved: every
 * extraction request carries `summary + recent entries` per notiUnit so the LLM can see what was
 * generated from that thread before and what the user did with it. Entry text therefore needs to
 * stand alone (item titles, short detail), because the referenced items may since be deleted.
 */
class ExtractionJournalRepository(private val journalDao: ExtractionJournalDao) {

    /** One journal payload block for a notiUnit: rolling summary + recent verbatim entries. */
    data class JournalPayload(
        val summaryText: String,
        val recentEntries: List<ExtractionJournalEntry>,
    )

    /** Entries that should be folded into the rolling summary for one notiUnit. */
    data class FoldRequest(
        val notiKey: String,
        val currentSummary: String,
        val entriesToFold: List<ExtractionJournalEntry>,
    )

    suspend fun append(entry: ExtractionJournalEntry) = withContext(Dispatchers.IO) {
        journalDao.insertEntry(entry)
    }

    /**
     * Appends a no_extraction verdict, collapsing verdict streaks in place: a quiet-but-noisy
     * thread that keeps yielding nothing refreshes one entry instead of growing the journal.
     * A streak is broken only by a real event (item created/updated, user action) — the
     * records_sent bookkeeping written on every pass is skipped over.
     */
    suspend fun appendNoExtraction(notiKey: String, reason: String, at: Long) = withContext(Dispatchers.IO) {
        val lastRealEvent = journalDao.getRecentEntries(notiKey, RECENT_ENTRY_COUNT)
            .firstOrNull { it.eventType != ExtractionJournalEventType.RecordsSent }
        if (lastRealEvent != null && lastRealEvent.eventType == ExtractionJournalEventType.NoExtraction) {
            journalDao.updateEntryDetail(lastRealEvent.entryId, reason, at)
        } else {
            journalDao.insertEntry(
                ExtractionJournalEntry(
                    notiKey = notiKey,
                    createdAt = at,
                    eventType = ExtractionJournalEventType.NoExtraction,
                    detail = reason,
                )
            )
        }
    }

    suspend fun appendAll(entries: List<ExtractionJournalEntry>) = withContext(Dispatchers.IO) {
        entries.forEach { journalDao.insertEntry(it) }
    }

    /** Saved item ids that any of [notiKeys] produced or touched, per journal history. */
    suspend fun getSavedItemIdsForKeys(notiKeys: List<String>): List<String> = withContext(Dispatchers.IO) {
        if (notiKeys.isEmpty()) emptyList() else journalDao.getSavedItemIdsForKeys(notiKeys)
    }

    /** Journal blocks for the extraction request payload, keyed by notiKey. */
    suspend fun payloadFor(notiKeys: List<String>): Map<String, JournalPayload> = withContext(Dispatchers.IO) {
        if (notiKeys.isEmpty()) return@withContext emptyMap()
        val summaries = journalDao.getSummaries(notiKeys).associateBy { it.notiKey }
        notiKeys.associateWith { key ->
            JournalPayload(
                summaryText = summaries[key]?.summaryText ?: "",
                // Reverse to chronological order: the LLM reads the journal forward in time.
                recentEntries = journalDao.getRecentEntries(key, RECENT_ENTRY_COUNT).reversed(),
            )
        }
    }

    /**
     * Fold requests for notiUnits whose verbatim journal outgrew [FOLD_THRESHOLD].
     *
     * The oldest `count - RECENT_ENTRY_COUNT` entries are proposed for folding; they are only
     * deleted in [applyFoldedSummary] once the workflow returns the merged summary, so a failed
     * request loses nothing.
     */
    suspend fun buildFoldRequests(notiKeys: List<String>): List<FoldRequest> = withContext(Dispatchers.IO) {
        notiKeys.mapNotNull { key ->
            val count = journalDao.getEntryCount(key)
            if (count <= FOLD_THRESHOLD) return@mapNotNull null
            val toFold = journalDao.getOldestEntries(key, count - RECENT_ENTRY_COUNT)
            if (toFold.isEmpty()) return@mapNotNull null
            FoldRequest(
                notiKey = key,
                currentSummary = journalDao.getSummary(key)?.summaryText ?: "",
                entriesToFold = toFold,
            )
        }
    }

    /**
     * Fold requests for idle threads: keys with journal entries but no request activity, so their
     * history is condensed opportunistically by piggybacking on another request. [candidateKeys]
     * should already exclude keys in the current request; ALL entries fold (the thread is dormant —
     * if it revives, the summary alone carries the history).
     */
    suspend fun buildIdleFoldRequests(candidateKeys: List<String>): List<FoldRequest> = withContext(Dispatchers.IO) {
        candidateKeys.mapNotNull { key ->
            val toFold = journalDao.getOldestEntries(key, Int.MAX_VALUE)
            if (toFold.isEmpty()) return@mapNotNull null
            FoldRequest(
                notiKey = key,
                currentSummary = journalDao.getSummary(key)?.summaryText ?: "",
                entriesToFold = toFold,
            )
        }
    }

    /** All keys currently holding verbatim journal entries. */
    suspend fun getKeysWithEntries(): List<String> = withContext(Dispatchers.IO) {
        journalDao.getKeysWithEntries()
    }

    /**
     * Mechanical cleanup for idle threads that never produced an item: nothing worth an LLM
     * summary, so the oldest entries beyond the recent window are simply dropped.
     */
    suspend fun truncateOrphanJournal(notiKey: String) = withContext(Dispatchers.IO) {
        journalDao.deleteEntriesBeyond(notiKey, RECENT_ENTRY_COUNT)
    }

    /** Persists the workflow-returned rolling summary and deletes the folded entries. */
    suspend fun applyFoldedSummary(request: FoldRequest, newSummary: String) = withContext(Dispatchers.IO) {
        if (newSummary.isBlank()) return@withContext
        val existing = journalDao.getSummary(request.notiKey)
        journalDao.upsertSummary(
            ExtractionJournalSummary(
                notiKey = request.notiKey,
                summaryText = newSummary,
                lastFoldedAt = System.currentTimeMillis(),
                foldedEntryCount = (existing?.foldedEntryCount ?: 0) + request.entriesToFold.size,
            )
        )
        journalDao.deleteEntries(request.entriesToFold.map { it.entryId })
    }

    companion object {
        /** Verbatim entries kept (and sent) per notiUnit. */
        const val RECENT_ENTRY_COUNT = 15

        /** Entry count above which the oldest entries get folded into the rolling summary. */
        const val FOLD_THRESHOLD = 25

        /**
         * Payload origin tag derived from the event type: "user" events are ground truth about
         * what the user did, "llm" events are pipeline conclusions, "system" is bookkeeping.
         */
        fun originOf(eventType: String): String = when (eventType) {
            ExtractionJournalEventType.UserCompleted,
            ExtractionJournalEventType.UserArchived,
            ExtractionJournalEventType.UserDeleted,
            ExtractionJournalEventType.UserEdited,
            ExtractionJournalEventType.ItemRestored,
            ExtractionJournalEventType.UserRevertedUpdate,
            -> "user"
            ExtractionJournalEventType.RecordsSent -> "system"
            else -> "llm"
        }
    }
}
