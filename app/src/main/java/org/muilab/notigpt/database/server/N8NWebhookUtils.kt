package org.muilab.notigpt.database.server

data class UpdateNotificationRequest(
    val userId: String,
    val notification: Map<String, Any>?
)