package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/** User-owned choices made while a generated item is still awaiting review. */
@Entity(tableName = "pending_review_draft")
data class PendingReviewDraft(
    @PrimaryKey val reviewKey: String,
    /** Null means no override; 0 explicitly clears When; [SavedItem.WHEN_SOMEDAY] means Someday. */
    val whenAtMs: Long? = null,
    /** Nullable JSON state for a translation requested while this proposal is under review. */
    val translationStateJson: String? = null,
    val updatedAt: Long,
)
