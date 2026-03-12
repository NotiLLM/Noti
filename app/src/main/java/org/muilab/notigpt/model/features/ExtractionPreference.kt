package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single active natural-language preference statement that governs how
 * the LLM extracts (or skips) tasks/memos from notifications.
 *
 * The local Room table is treated as the **single source of truth** for the
 * current rule set.  Historical / superseded rules are not kept locally;
 * the backend LLM handles merging.
 */
@Entity(tableName = "extraction_preferences")
data class ExtractionPreference(
    @PrimaryKey
    val id: String,

    /** Natural language rule, e.g. "Don't extract tasks from group chats unless I am mentioned." */
    val statement: String,

    /** One of [PreferenceType] wire values. */
    val preferenceType: String,

    val createdAt: Long,
    val updatedAt: Long,
)

/** Closed set of preference categories. */
object PreferenceType {
    const val WHETHER_TO_EXTRACT = "WHETHER_TO_EXTRACT"
    const val WHAT_TO_EXTRACT = "WHAT_TO_EXTRACT"
    const val REPRESENTATION = "REPRESENTATION"
}

