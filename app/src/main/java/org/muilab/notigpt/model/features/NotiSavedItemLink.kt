package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join table linking a notification record to a saved item (task/keep).
 *
 * This is the single source of truth for noti-to-saved-item provenance, replacing the older
 * [SavedItem.associatedNotis] set plus the reminder extraction snapshot blob. A single notification
 * record can contribute to multiple saved items, hence the composite primary key
 * `(notiRecordId, savedItemId)`.
 *
 * [notiKey] is derivable from [notiRecordId] (`notiRecordId.substringBeforeLast("_")`) but is stored
 * so links can be queried by group key without parsing.
 */
@Entity(
    tableName = "noti_saved_item_link",
    primaryKeys = ["notiRecordId", "savedItemId"],
    foreignKeys = [
        ForeignKey(
            entity = SavedItem::class,
            parentColumns = ["savedItemId"],
            childColumns = ["savedItemId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("savedItemId"), Index("notiKey")],
)
data class NotiSavedItemLink(
    /** Notification group key. */
    val notiKey: String,
    /** Notification record id, format "{notiKey}_{postTime}". */
    val notiRecordId: String,
    /** "task" | "keep" — mirrors [SavedItem.itemType] at link time. */
    val type: String,
    /** The owning saved item id. */
    val savedItemId: String,
    /** Link creation timestamp (ms since epoch). */
    val createdAt: Long,
)
