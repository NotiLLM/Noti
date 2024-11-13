package org.muilab.notigpt.model.notifications

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.room.Embedded
import androidx.room.Entity
import org.muilab.notigpt.util.getDisplayTimeStr


@Entity(tableName = "noti_drawer", primaryKeys = ["notiKey"])
data class NotiUnit(
    // Fixed (On Init)
    val notiKey: String, // primary key
    @Embedded val metadata: NotiMetadata,
    @Embedded val body: NotiBody = NotiBody(),
    @Embedded val feature: NotiFeature = NotiFeature(),
    @Embedded val actions: NotiActions = NotiActions(),
    @Embedded val outcome: NotiOutcome = NotiOutcome(),
) {

    @RequiresApi(Build.VERSION_CODES.S)
    constructor(
        context: Context,
        sbn: StatusBarNotification
    ): this(
        notiKey = sbn.key,
        metadata = NotiMetadata(sbn)
    ) {
        updateNoti(context, sbn)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun updateNoti(context: Context, sbn: StatusBarNotification) {
        metadata.update(context, sbn)
        val isPeople = getIsPeople()
        body.update(sbn, isPeople)
        resetSummary()
    }

    // METADATA RELATED CALLS

    fun getHashKey(): Int {
        return metadata.hashKey
    }

    fun getPkgName(): String {
        return metadata.pkgName
    }

    fun getAppName(): String {
        return metadata.appName
    }

    fun getIsPeople(): Boolean {
        return metadata.isPeople
    }

    fun getLargeBitmap(): Bitmap? {
        return metadata.getLargeBitmap()
    }

    fun getBitmap(): Bitmap? {
        return metadata.getBitmap()
    }

    // BODY RELATED CALLS

    fun getWholeNotiRead(): Boolean {
        return body.wholeNotiRead
    }

    fun markAsRead() {
        body.wholeNotiRead = true
        for (notiInfo in body.notiInfos) {
            notiInfo.notiSeen = true
        }
    }

    fun markInfosAsRead(seenInfos: Set<Long>) {
        var checkAllRead = true
        for (notiInfo in body.notiInfos) {
            for (infoTime in seenInfos)
                if (infoTime == notiInfo.time)
                    notiInfo.notiSeen = true
            if (!notiInfo.notiSeen)
                checkAllRead = false
        }
        if (checkAllRead)
            body.wholeNotiRead = true
    }

    fun getNotiBody(): List<NotiInfo> {
        return body.notiInfos.toList()
    }

    fun getPrevBody(): List<NotiInfo> {
        return body.prevNotiInfos.toList()
    }

    fun getTitle(): String {
        return body.title
    }

    fun getLatestTimeStr(): String {
        return getDisplayTimeStr(body.latestTime)
    }

    // ACTIONS RELATED CALLS

    fun flipNotiPin() {
        actions.flipPin()
    }

    fun getPinned(): Boolean {
        return actions.pinned
    }

    fun setPinned(isPinned: Boolean) {
        actions.pinned = isPinned
    }

    fun removeNoti() {
        metadata.isVisible = false
        body.removeNoti()
    }

    // OUTCOMES RELATED CALLS

    fun getScore(): Double {
        return outcome.score
    }

    fun resetSummary() {
        outcome.summary = ""
    }

    fun getSummary(): String {
        return outcome.summary
    }

    fun getExplanation(): String {
        return outcome.explanation
    }

    fun resetGPTValues() {
        outcome.resetOutcomes()
    }

    // FOR SERVER UPLOAD
    fun toServerNoti(userId: String): Map<String, Any> {
        return mapOf<String, Any>(
            "id" to "${userId}_$notiKey",
            "user_id" to userId,
            "title" to getTitle(),
            "app_name" to getAppName(),
            "post_time" to getLatestTimeStr(),
            "noti_key" to notiKey,
            "body" to getNotiBody().map {
                mapOf<String, Any>(
                    "title" to it.title,
                    "time" to getDisplayTimeStr(it.time),
                    "content" to it.content
                )
            }
        )
    }
}