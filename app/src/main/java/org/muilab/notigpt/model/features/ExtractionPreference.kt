package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a user-authored or learned rule that guides reminder extraction.
 *
 * Preferences are reusable behavior inputs across notifications and this local table is the source of
 * truth for the active rule set. Keep UI chat state and conflict metadata in separate models so this
 * table remains a clean set of active preference statements.
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

