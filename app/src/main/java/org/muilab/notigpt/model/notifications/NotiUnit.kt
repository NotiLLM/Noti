package org.muilab.notigpt.model.notifications

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.room.Embedded
import androidx.room.Entity
import org.muilab.notigpt.model.notifications.components.NotiMetadata
import org.muilab.notigpt.model.notifications.components.NotiDisplayState
import org.muilab.notigpt.util.getAppCategoryByAppName


@Entity(tableName = "noti_drawer", primaryKeys = ["notiKey"])
data class NotiUnit(
    val notiKey: String,
    @Embedded val metadata: NotiMetadata,
    @Embedded val displayState: NotiDisplayState = NotiDisplayState(),
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
        if (!isVisible)
            displayState.resetUserState()
        displayState.resetLLMState()
        // Set app category based on app name
        displayState.appCategory = getAppCategoryByAppName(context, metadata.appName)
    }

    val hashKey: Int
        get() = metadata.hashKey

    val appName: String
        get() = metadata.appName

    val pkgName: String
        get() = metadata.pkgName

    val lastUpdateTime: Long
        get() = metadata.lastUpdateTime

    val isPeople: Boolean
        get() = metadata.isPeople

    val largeBitmap: Bitmap?
        get() = metadata.getLargeBitmap()

    val bitmap: Bitmap?
        get() = metadata.getBitmap()

    // DISPLAY RELATED CALLS
    val isVisible: Boolean
        get() = displayState.isVisible

    var isPinned: Boolean
        get() = displayState.isPinned
        set(value) {
            displayState.isPinned = value
        }

    var isCompletelyRead: Boolean
        get() = displayState.isCompletelyRead
        set(value) {
            displayState.isCompletelyRead = value
        }

    // OUTCOMES RELATED CALLS

    var summary: String
        get() = displayState.summary
        set(value) {
            displayState.summary = value
        }

    var sortScore: Double
        get() = displayState.sortScore
        set(value) {
            displayState.sortScore = value
        }

    var category: String
        set(value) {
            displayState.category = value
        }
        get() = displayState.category

    var explanation: String
        get() = displayState.explanation
        set(value) {
            displayState.explanation = value
        }

    val appCategory: String
        get() = displayState.appCategory

    var sortPosition: Int
        get() = displayState.sortPosition
        set(value) {
            displayState.sortPosition = value
        }

    var appCategorySortPosition: Int
        get() = displayState.appCategorySortPosition
        set(value) {
            displayState.appCategorySortPosition = value
        }
}