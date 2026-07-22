package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A confirmed statement controlling whether and how Noti creates Todos or Keeps. */
@Entity(tableName = "extraction_preferences")
data class ExtractionPreference(
    @PrimaryKey
    val id: String,
    val statement: String,
    val createdAt: Long,
    val updatedAt: Long,
)
