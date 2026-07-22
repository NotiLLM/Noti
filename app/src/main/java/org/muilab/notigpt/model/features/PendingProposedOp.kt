package org.muilab.notigpt.model.features

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object PendingProposedOpType {
    /** Proposes a brand-new saved item; the item id is generated on apply. */
    const val Create = "create"

    /** Proposes field-level changes to an existing saved item. */
    const val Update = "update"

    /** Proposes folding one or more items into a surviving item. */
    const val Merge = "merge"
}

/**
 * One staged pipeline instruction awaiting user review.
 *
 * The extraction pipeline never writes to [SavedItem] directly: every create/update/merge it
 * proposes lands here first. Accepting an op (or its item-level group) translates the payload into
 * real DB writes; rejecting deletes the row — nothing to roll back. This is what lets review show
 * a faithful preview ("what would change") and discard rejected work without trace.
 */
@Entity(
    tableName = "pending_proposed_op",
    indices = [
        Index(value = ["targetItemId"]),
        Index(value = ["batchId"]),
    ],
)
data class PendingProposedOp(
    @PrimaryKey(autoGenerate = true)
    val opId: Long = 0L,

    /** Source thread; empty for reflection (D2/E2) ops that aren't tied to one thread. */
    @ColumnInfo(defaultValue = "''")
    val notiKey: String = "",

    /** [PendingProposedOpType] */
    val opType: String,

    /**
     * Full instruction as returned by the pipeline (JSON object). For creates this holds every
     * item attribute; for updates the field-level instructions; for merges the surviving content
     * plus [mergeSourceItemIds].
     */
    val payload: String,

    /**
     * The existing item this op modifies (update/merge survivor). Empty for creates — the id is
     * generated when the op is applied.
     */
    @ColumnInfo(defaultValue = "''")
    val targetItemId: String = "",

    /**
     * For merge ops: JSON string array of item ids that fold into [targetItemId] and are removed
     * on apply. Empty JSON array otherwise.
     */
    @ColumnInfo(defaultValue = "[]")
    val mergeSourceItemIds: String = "[]",

    /** JSON string array of record ids cited as evidence. Ops citing unknown ids are dropped at staging. */
    @ColumnInfo(defaultValue = "[]")
    val evidenceRecordIds: String = "[]",

    /** The model's explanation for this op, shown in review. */
    @ColumnInfo(defaultValue = "''")
    val reason: String = "",

    /** "todo" or "keep" ([SavedItemType]) of the item this op produces/affects, for review counts. */
    @ColumnInfo(defaultValue = "'todo'")
    val itemType: String = SavedItemType.Todo,

    /** Groups the ops staged by one pipeline run. */
    @ColumnInfo(defaultValue = "''")
    val batchId: String = "",

    val createdAt: Long,
)
