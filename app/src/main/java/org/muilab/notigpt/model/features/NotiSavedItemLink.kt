package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object NotiSavedItemLinkRole {
    /** The record directly evidences the saved item's content (cited by the LLM). */
    const val Evidence = "evidence"

    /** The user attached this record manually. */
    const val Manual = "manual"
}

object NotiSavedItemLinkSource {
    const val LlmAutoExtraction = "llm_auto_extraction"
    const val LlmManualExtraction = "llm_manual_extraction"
    const val User = "user"
}

/**
 * Join table linking a notification record to a saved item (task/keep).
 *
 * This is the single source of truth for noti-to-saved-item provenance. Notification records and
 * saved items are many-to-many, so rows use a surrogate [linkId] with a unique index on
 * `(savedItemId, notiRecordId, role)`. Lookups run in all three directions: saved item → its
 * evidence records, record → the items it evidences, and notiUnit ([notiKey]) → its saved items.
 *
 * Only records the LLM cites as direct evidence (or the user attaches) get rows here — surrounding
 * thread context is loaded from `noti_record` on demand, never stored as links. There is
 * deliberately no foreign key on [notiRecordId]/[notiKey]: records and units can be pruned without
 * destroying saved-item provenance.
 *
 * [notiKey] is derivable from [notiRecordId] (`notiRecordId.substringBeforeLast("_")`) but is stored
 * so links can be queried by group key without parsing.
 */
@Entity(
    tableName = "noti_saved_item_link",
    foreignKeys = [
        ForeignKey(
            entity = SavedItem::class,
            parentColumns = ["savedItemId"],
            childColumns = ["savedItemId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["savedItemId", "notiRecordId", "role"], unique = true),
        Index("savedItemId"),
        Index("notiRecordId"),
        Index("notiKey"),
    ],
)
data class NotiSavedItemLink(
    @PrimaryKey(autoGenerate = true)
    val linkId: Long = 0L,
    /** Notification group key. */
    val notiKey: String,
    /** Notification record id, format "{notiKey}_{postTime}". */
    val notiRecordId: String,
    /** "todo" | "keep" — mirrors [SavedItem.itemType] at link time. */
    val type: String,
    /** The owning saved item id. */
    val savedItemId: String,
    /** [NotiSavedItemLinkRole] — why this record is linked. */
    val role: String = NotiSavedItemLinkRole.Evidence,
    /** [NotiSavedItemLinkSource] — which flow created the link. */
    val source: String = NotiSavedItemLinkSource.LlmAutoExtraction,
    /** Link creation timestamp (ms since epoch). */
    val createdAt: Long,
)
