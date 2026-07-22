package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A confirmed fact about the user that may provide personalization context. */
@Entity(tableName = "user_knowledge")
data class UserKnowledge(
    @PrimaryKey
    val id: String,
    val statement: String,
    val createdAt: Long,
    val updatedAt: Long,
)
