package org.muilab.notigpt.model.notifications

import androidx.room.DatabaseView

@DatabaseView("""
    SELECT * FROM noti_record WHERE isVisible = 1
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
    val isVisible: Boolean
)