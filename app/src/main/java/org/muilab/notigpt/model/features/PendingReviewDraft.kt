package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/** User-owned choices made while a generated item is still awaiting review. */
@Entity(tableName = "pending_review_draft")
data class PendingReviewDraft(
    @PrimaryKey val reviewKey: String,
    /** Nullable JSON state for a translation requested while this proposal is under review. */
    val translationStateJson: String? = null,
    /** User-owned edits to an atomic Split batch; null for ordinary review entries. */
    val batchDraftJson: String? = null,
    val updatedAt: Long,
)
