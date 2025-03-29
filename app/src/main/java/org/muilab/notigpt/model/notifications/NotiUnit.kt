package org.muilab.notigpt.model.notifications

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.room.Embedded
import androidx.room.Entity
import org.muilab.notigpt.util.getAbsoluteTimeStr
import org.muilab.notigpt.util.getRelativeTimeStr


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
        body.update(sbn, isPeople)
        summary = ""
    }

    val hashKey: Int
        get() = metadata.hashKey

    val appName: String
        get() = metadata.appName

    val pkgName: String
        get() = metadata.pkgName

    val isPeople: Boolean
        get() = metadata.isPeople

    val largeBitmap: Bitmap?
        get() = metadata.getLargeBitmap()

    val bitmap: Bitmap?
        get() = metadata.getBitmap()


    // BODY RELATED CALLS

    val title: String
        get() = body.title

    val wholeNotiRead: Boolean
        get() = body.wholeNotiRead

    fun markAsRead() {
        body.wholeNotiRead = true
        body.notiInfos.forEach { it.notiSeen = true }
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

    fun getAbsLatestTimeStr(): String {
        return getAbsoluteTimeStr(body.latestTime)
    }

    fun getRelLatestTimeStr(): String {
        return getRelativeTimeStr(body.latestTime)
    }

    // ACTIONS RELATED CALLS

    var pinned: Boolean
        get() = actions.pinned
        set(value) {
            actions.pinned = value
        }

    fun flipNotiPin() {
        actions.flipPin()
    }

    fun removeNoti() {
        metadata.isVisible = false
        body.removeNoti(isPeople)
    }

    // OUTCOMES RELATED CALLS

    val embeddingString: String
        get() = outcome.embeddingString

    var summary: String
        get() = outcome.summary
        set(value) {
            outcome.summary = value
        }

    var sortScore: Double
        get() = outcome.sortScore
        set(value) {
            outcome.sortScore = value
        }

    val category: String
        get() = outcome.category

    var explanation: String
        get() = outcome.explanation
        set(value) {
            outcome.explanation = value
        }

    fun resetLLMValues() {
        outcome.resetOutcomes()
    }

    // FOR SERVER UPLOAD
    fun toServerNoti(userId: String): Map<String, Any> {
        return mapOf<String, Any>(
            "id" to "${userId}_$notiKey",
            "user_id" to userId,
            "title" to title,
            "app_name" to appName,
            "abs_post_time" to getAbsLatestTimeStr(),
            "rel_post_time" to getRelLatestTimeStr(),
            "noti_key" to notiKey,
            "body" to getNotiBody().map {
                mapOf<String, Any>(
                    "title" to it.getDisplayedTitle(pkgName, isPeople),
                    "abs_time" to getAbsoluteTimeStr(it.time),
                    "rel_time" to getRelativeTimeStr(it.time),
                    "content" to it.content
                )
            },
            "history_body" to getPrevBody().map {
                mapOf<String, Any>(
                    "title" to it.getDisplayedTitle(pkgName, isPeople),
                    "abs_time" to getAbsoluteTimeStr(it.time),
                    "rel_time" to getRelativeTimeStr(it.time),
                    "content" to it.content
                )
            },
        )
    }

    fun toDifyNoti(timeDiff: Long = System.currentTimeMillis()): Map<String, Any>? {

        val currentTime = System.currentTimeMillis()
        if (currentTime - body.latestTime > timeDiff)
            return null

        return mapOf<String, Any>(
            "noti-key" to notiKey,
            "title" to title,
            "app_name" to appName,
            "body" to getNotiBody()
                .filter { currentTime - it.time < timeDiff }
                .map {
                    mapOf<String, Any>(
                        "title" to it.getDisplayedTitle(pkgName, isPeople),
                        "abs_time" to getAbsoluteTimeStr(it.time),
                        "rel_time" to getRelativeTimeStr(it.time),
                        "content" to it.content
                    )
                },
            "history_body" to getPrevBody()
                .map {
                    mapOf<String, Any>(
                        "title" to it.getDisplayedTitle(pkgName, isPeople),
                        "abs_time" to getAbsoluteTimeStr(it.time),
                        "rel_time" to getRelativeTimeStr(it.time),
                        "content" to it.content
                    )
                },
        )
    }

    // FOR UI PRESENTATION
    fun withUpdatedSimilarity(similarity: Double): NotiUnit {
        return this.copy(outcome = outcome.copy(similarityScore = similarity))
    }
}