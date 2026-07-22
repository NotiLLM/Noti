package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Prepared field-free replacement for the active legacy [ExtractionPreference].
 *
 * The temporary type name keeps the v54 application compilable until the coordinated v55 cutover.
 */
@Entity(tableName = "extraction_preferences")
data class ExtractionPreferenceV2(
    @PrimaryKey
    val id: String,
    val statement: String,
    val createdAt: Long,
    val updatedAt: Long,
)
