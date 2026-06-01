package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a detected conflict among extraction preferences.
 *
 * This model records the conflict explanation, source, and involved preference IDs. Keep resolution UI state
 * outside this entity unless conflict lifecycle becomes a durable domain concept.
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

