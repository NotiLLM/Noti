package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for stable user-context facts used by extraction and preference reasoning.
 *
 * User context should describe the person or environment, not one notification. If source evidence becomes
 * important, add explicit provenance fields rather than embedding long raw transcripts here.
 */
@Entity(tableName = "user_contexts")
data class UserContext(
    @PrimaryKey
    val id: String,
    val statement: String,
    val category: String,
    val createdAt: Long,
    val updatedAt: Long,
)

