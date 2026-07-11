package org.muilab.notigpt.model.features

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object SavedItemChangeType {
    const val Created = "created"
    const val LlmUpdate = "llm_update"
    const val UserEdit = "user_edit"
    const val Regenerated = "regenerated"
}

/**
 * One recorded change to a [SavedItem], written whenever the LLM creates/updates an item or the
 * user edits it.
 *
 * The LLM never rewrites existing item content (users have already read it); updates arrive as
 * structured ops — an appended fragment, added/removed sub-tasks, and old→new field changes — and
 * each op lands here verbatim. This table powers the plain-language change history in the item
 * detail view and the "what's new since last acknowledged" block; [SavedItem.content] remains the
 * full concatenated text, so nothing here is needed to render the item itself.
 */
@Entity(
    tableName = "saved_item_change_log",
    foreignKeys = [
        ForeignKey(
            entity = SavedItem::class,
            parentColumns = ["savedItemId"],
            childColumns = ["savedItemId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["savedItemId", "createdAt"])],
)
data class SavedItemChangeLog(
    @PrimaryKey(autoGenerate = true)
    val changeId: Long = 0L,
    val savedItemId: String,
    val createdAt: Long,
    /** [SavedItemChangeType] */
    val changeType: String,
    /** One-line human-readable summary of the change (LLM-provided for LLM changes). */
    @ColumnInfo(defaultValue = "''")
    val changeSummary: String = "",
    /** Content fragment appended to the item (without the localized timestamp header). */
    @ColumnInfo(defaultValue = "''")
    val appendedContent: String = "",
    /** JSON array of added sub-task objects [{savedSubItemId, title}, ...]. */
    @ColumnInfo(defaultValue = "[]")
    val addedSubTasksJson: String = "[]",
    /** JSON array of removed sub-tasks [{savedSubItemId, title, reason}, ...]. */
    @ColumnInfo(defaultValue = "[]")
    val removedSubTasksJson: String = "[]",
    /** JSON object of field changes {"deadlineAtMs": {"old": ..., "new": ...}, ...}. */
    @ColumnInfo(defaultValue = "{}")
    val changedFieldsJson: String = "{}",
    /** JSON array of notiRecordIds evidencing this change. */
    @ColumnInfo(defaultValue = "[]")
    val evidenceRecordIdsJson: String = "[]",
    /** "llm" | "user" — coarse actor label alongside [changeType]. */
    @ColumnInfo(defaultValue = "'llm'")
    val origin: String = "llm",
)
