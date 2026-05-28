package org.muilab.notigpt.model.features

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A factual statement about the user (persona context), used alongside
 * extraction preferences to help the LLM understand implicit tasks.
 *
 * Examples:
 * - "I am a software engineer at Google"
 * - "I am currently traveling in Taiwan"
 * - "My partner's name is Alice"
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

