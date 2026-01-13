package org.muilab.notigpt.model.notifications

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import org.muilab.notigpt.model.notifications.components.NotiMetadata
import org.muilab.notigpt.model.notifications.components.NotiDisplayState
import org.muilab.notigpt.model.notifications.components.NotiTaskAttr

// Add index on groupId for faster lookups
@Entity(tableName = "noti_drawer", primaryKeys = ["notiKey"], indices = [Index(value = ["groupId"])])
data class NotiUnit(
    val notiKey: String,
    @Embedded val metadata: NotiMetadata,
    @Embedded val displayState: NotiDisplayState = NotiDisplayState(),
    @Embedded val taskAttr: NotiTaskAttr = NotiTaskAttr(),
    // Link to a parent group
    val groupId: String? = null
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
        // If the unit was previously dismissed, treat the incoming noti as a fresh active one.
        if (isDismissed) {
            displayState.resetUserState()
        }
        displayState.resetReadState()
        displayState.resetLLMState()
    }

    val hashKey: Int get() = metadata.hashKey
    val appName: String get() = metadata.appName
    val pkgName: String get() = metadata.pkgName
    val lastUpdateTime: Long get() = metadata.lastUpdateTime
    val lastSyncTime: Long get() = metadata.lastSyncTime
    val isPeople: Boolean get() = metadata.isPeople
    val largeBitmap: Bitmap? get() = metadata.getLargeBitmap()
    val bitmap: Bitmap? get() = metadata.getBitmap()

    /** Active drawer state: dismissed items are hidden from the active list. */
    val isDismissed: Boolean get() = displayState.isDismissed

    var isPinned: Boolean
        get() = displayState.isPinned
        set(value) { displayState.isPinned = value }

    var isRead: Boolean
        get() = displayState.isRead
        set(value) { displayState.isRead = value }

    var summary: String
        get() = displayState.summary
        set(value) { displayState.summary = value }

    var sortScore: Double
        get() = displayState.sortScore
        set(value) { displayState.sortScore = value }

    var explanation: String
        get() = displayState.explanation
        set(value) { displayState.explanation = value }

    /** Manual sort position in the active drawer. -1 means unset (auto-fill by time). */
    var sortPosition: Int
        get() = displayState.sortPosition
        set(value) { displayState.sortPosition = value }


    var shouldExtractTask: Boolean
        get() = taskAttr.shouldExtractTask
        set(value) { taskAttr.shouldExtractTask = value }

    var hasGenuineTask: Boolean
        get() = taskAttr.hasGenuineTask
        set(value) { taskAttr.hasGenuineTask = value }

    var isSetToTop: Boolean
        get() = displayState.isSetToTop
        set(value) { displayState.isSetToTop = value }

    var setToTopTime: Long
        get() = displayState.setToTopTime
        set(value) { displayState.setToTopTime = value }
}