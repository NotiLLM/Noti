package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an unresolved conflict between extraction preferences that the
 * backend LLM detected but could not auto-resolve during a Quick-Sync or
 * Chat-Interact round-trip.
 *
 * Conflicts are surfaced to the user in the Preferences tab so they can
 * dismiss them or open a chat to resolve them.
 */
@Entity(tableName = "preference_conflicts")
data class PreferenceConflict(
    @PrimaryKey
    val conflictId: String,

    /** Human-readable description of the conflict, authored by the backend LLM. */
    val description: String,

    /** IDs of the preferences involved (comma-separated for Room simplicity). */
    val involvedPreferenceIds: String,

    /** Where this conflict was detected: "QUICK_SYNC" or "CHAT_INTERACT". */
    val source: String,

    val createdAt: Long,
)

