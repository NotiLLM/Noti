package org.muilab.notigpt.model.notifications

import androidx.room.DatabaseView

/**
 * Room view model for notification records that are currently eligible for active context display.
 *
 * Keep this as a query projection over NotiRecord rather than a separately written table. If visibility
 * rules change, update the backing view/migration and DAO queries together.
 */
@DatabaseView("""
    SELECT * FROM noti_record WHERE isDismissed = 0
""")
data class VisibleNotiRecord(
    // KEYS
    val notiRecordId: String,
    val notiKey: String,

    // TIME RELATED
    val whenTime: Long,
    val postTime: Long,

    // TITLE RELATED
    val person: String,
    val extraTitle: String,
    val extraBigTitle: String,
    val extraConversationTitle: String,

    // CONTENT RELATED
    val extraBigText: String,
    val extraText: String,
    val extraTextLines: String,
    val extraSummaryText: String,
    val extraInfoText: String,
    val extraSubText: String,

    // STATUS
    val isDismissed: Boolean
)