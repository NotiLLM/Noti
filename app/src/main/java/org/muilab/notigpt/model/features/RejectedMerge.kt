package org.muilab.notigpt.model.features

import androidx.room.Entity

/**
 * Cool-down marker for a merge suggestion the user rejected.
 *
 * When a merge op is rejected in review, every pair of items it would have combined is recorded
 * here. While a pair is inside the cool-down window the merge stages (D1/D2) never see it as a
 * candidate again, so the user isn't nagged with the same suggestion. It is deliberately not a
 * permanent blocklist: circumstances change, so after the window expires the pair becomes
 * eligible again and expired rows are purged.
 *
 * [itemIdA]/[itemIdB] are stored in normalized (lexicographic) order so each pair has one row.
 */
@Entity(tableName = "rejected_merge", primaryKeys = ["itemIdA", "itemIdB"])
data class RejectedMerge(
    val itemIdA: String,
    val itemIdB: String,
    val rejectedAt: Long,
) {
    companion object {
        /** Default cool-down before a rejected pair may be suggested again. */
        const val DEFAULT_COOLDOWN_MS: Long = 7L * 24 * 60 * 60 * 1000

        /** Builds the normalized-order row for a rejected pair. */
        fun of(idX: String, idY: String, rejectedAt: Long): RejectedMerge {
            val (a, b) = if (idX <= idY) idX to idY else idY to idX
            return RejectedMerge(a, b, rejectedAt)
        }
    }
}
