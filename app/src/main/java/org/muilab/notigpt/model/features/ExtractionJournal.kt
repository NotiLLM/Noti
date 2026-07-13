package org.muilab.notigpt.model.features

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object ExtractionJournalEventType {
    const val RecordsSent = "records_sent"
    const val ItemCreated = "item_created"
    const val ItemUpdated = "item_updated"
    const val UserCompleted = "user_completed"
    const val UserArchived = "user_archived"
    const val UserDeleted = "user_deleted"
    const val UserEdited = "user_edited"
    const val ItemRestored = "item_restored"

    /** User rejected a pending LLM update in review; the edit was rolled back. */
    const val UserRevertedUpdate = "user_reverted_update"

    /** Model considered the thread and declined to extract; detail carries its reason. */
    const val NoExtraction = "no_extraction"
}

/**
 * One event in a notiUnit's extraction history: records sent for extraction, items created/updated
 * from them, and what the user later did with those items (completed/archived/deleted/edited).
 *
 * The journal is the pipeline's memory. It is sent with every extraction request so the LLM knows
 * what was already generated from a thread and how the user handled it — preventing it from
 * re-creating a card the user already resolved when old records reappear as past context. Only the
 * most recent entries are kept verbatim; older ones are folded into [ExtractionJournalSummary].
 */
@Entity(
    tableName = "extraction_journal_entry",
    indices = [Index(value = ["notiKey", "createdAt"])],
)
data class ExtractionJournalEntry(
    @PrimaryKey(autoGenerate = true)
    val entryId: Long = 0L,
    val notiKey: String,
    val createdAt: Long,
    /** [ExtractionJournalEventType] */
    val eventType: String,
    /** The saved item this event concerns; empty for records_sent. */
    @ColumnInfo(defaultValue = "''")
    val savedItemId: String = "",
    /** Item title at event time, so the journal reads meaningfully after deletions. */
    @ColumnInfo(defaultValue = "''")
    val itemTitle: String = "",
    /** Short free text: change summary, record count, etc. */
    @ColumnInfo(defaultValue = "''")
    val detail: String = "",
)

/**
 * LLM-maintained rolling summary of a notiUnit's folded-away older journal entries.
 *
 * When the verbatim journal exceeds its cap, the oldest entries are sent to the extraction
 * workflow for condensing; the returned summary replaces [summaryText] and the folded rows are
 * deleted. Together `summary + recent entries` bound the journal's token cost per request.
 */
@Entity(tableName = "extraction_journal_summary")
data class ExtractionJournalSummary(
    @PrimaryKey
    val notiKey: String,
    @ColumnInfo(defaultValue = "''")
    val summaryText: String = "",
    @ColumnInfo(defaultValue = "0")
    val lastFoldedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val foldedEntryCount: Int = 0,
)
