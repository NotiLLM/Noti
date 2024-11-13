package org.muilab.notigpt.model.server

data class SortOutcome(
    val id: String,
    val score: Float,
    val explanation: String
)
