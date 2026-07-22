package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A confirmed statement describing what deserves the user's attention. */
@Entity(tableName = "general_preferences")
data class GeneralPreference(
    @PrimaryKey
    val id: String,
    val statement: String,
    val createdAt: Long,
    val updatedAt: Long,
)
