package org.muilab.notigpt.model.features

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Well-known thread categories. The set is open: custom categories are plain strings. */
object NotiCategory {
    const val Communication = "communication"
    const val Content = "content"
}

/**
 * Per-notiKey LLM-derived state: everything the pipeline concludes about a notification thread.
 *
 * This table is the home for thread-level LLM conclusions (scan gate flags, categories, and any
 * future derived fields such as scores or thread summaries) so NotiUnit stays purely notification
 * content, display state, and the user's manual arrangement. Rows here are derived and disposable:
 * they can always be regenerated from notification records, unlike the tables they annotate.
 */
@Entity(tableName = "noti_llm_state")
data class NotiLlmState(
    @PrimaryKey
    val notiKey: String,
    /** Scan verdict: thread flagged for SavedItem extraction. Never demoted once true until consumed. */
    @ColumnInfo(defaultValue = "0")
    val shouldExtractSavedItem: Boolean = false,
    /** JSON string array of [NotiCategory] values, e.g. `["communication"]`. */
    @ColumnInfo(defaultValue = "'[]'")
    val categories: String = "[]",
    /** Model's one-line reason for the category decision. */
    @ColumnInfo(defaultValue = "''")
    val categoryReason: String = "",
    /** "llm" or "user" (a manual override wins over later model passes). */
    @ColumnInfo(defaultValue = "''")
    val categorySource: String = "",
    @ColumnInfo(defaultValue = "0")
    val lastClassifiedAt: Long = 0L,
    /** Total record count for the thread when it was last classified; drives rare re-checks. */
    @ColumnInfo(defaultValue = "0")
    val lastClassifiedRecordCount: Int = 0,
    /** End time of the last successful Stage B request for this thread. */
    @ColumnInfo(defaultValue = "0")
    val lastItemExtractionAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L,
)
