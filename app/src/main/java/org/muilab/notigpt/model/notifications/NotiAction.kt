package org.muilab.notigpt.model.notifications

import androidx.room.Entity

/**
 * Room entity for an executable action exposed by an Android notification.
 *
 * Store only the action metadata needed to render and route an action. The actual PendingIntent remains
 * framework state managed by the listener/service layer rather than durable app data.
 */
@Entity(tableName = "notiAction", primaryKeys = ["notiActionId"])
data class NotiAction (
    val notiActionId: String,
    val notiKey: String,
    val actionType: String,
    val time: Long,
    val lastAppResumeTime: Long,
    val metadata: String = ""
) {
    constructor(notiKey: String, actionType: String, time: Long, lastAppResumeTime: Long, metadata: String = ""): this (
        notiActionId = "${notiKey}_$time",
        notiKey = notiKey,
        actionType = actionType,
        time = time,
        lastAppResumeTime = lastAppResumeTime,
        metadata = metadata
    )
}