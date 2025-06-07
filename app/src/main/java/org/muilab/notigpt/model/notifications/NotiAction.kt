package org.muilab.notigpt.model.notifications

import androidx.room.Entity

@Entity(tableName = "notiAction", primaryKeys = ["notiActionId"])
data class NotiAction (
    val notiActionId: String,
    val notiKey: String,
    val actionType: String,
    val time: Long,
) {
    constructor(notiKey: String, actionType: String, time: Long): this (
        notiActionId = "${notiKey}_$time",
        notiKey = notiKey,
        actionType = actionType,
        time = time
    )
}