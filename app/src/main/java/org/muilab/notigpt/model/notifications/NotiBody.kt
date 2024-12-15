package org.muilab.notigpt.model.notifications

import android.app.Notification
import android.app.Notification.MessagingStyle.Message.getMessagesFromBundleArray
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import org.muilab.notigpt.util.SharedPreferencesManager
import java.util.TreeSet

data class NotiBody(
    // Modifiable Visible
    var latestTime: Long = 0L,
    var title: String = "",
    var wholeNotiRead: Boolean = false,
    // Modifiable Invisible

    //  Accumulatable
    val notiInfos: TreeSet<NotiInfo> = TreeSet(compareBy<NotiInfo> { it.time }),
    val prevNotiInfos: TreeSet<NotiInfo> = TreeSet(compareBy<NotiInfo> { it.time })
) {
    @RequiresApi(Build.VERSION_CODES.S)
    fun update(sbn: StatusBarNotification, isPeople: Boolean) {
        wholeNotiRead = false
        latestTime = if (sbn.notification?.`when` as Long > 0)
            sbn.notification?.`when` as Long
        else
            sbn.postTime
        title = fetchTitle(sbn, isPeople)
        val notiInfo = NotiInfo(sbn)
        if (isPeople) {
            if (!updatePreviousMessagesSuccess(sbn))
                notiInfos.add(notiInfo)
        } else {
            notiInfos.clear()
            notiInfos.add(notiInfo)
        }
        updateLatestTime()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun updatePreviousMessagesSuccess(sbn: StatusBarNotification): Boolean {
        var hasExtra = false

        val extraMessages = sbn.notification?.extras?.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (extraMessages != null) {
            getMessagesFromBundleArray(extraMessages)
                .forEach {
                    if (it.senderPerson != null) {
                        notiInfos.add(NotiInfo(it.timestamp, it.senderPerson?.name.toString(), it.text.toString()))
                        hasExtra = true
                    }
                }
        }
        val extraHistoricMessages = sbn.notification?.extras?.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES)
        if (extraMessages != null) {
            getMessagesFromBundleArray(extraHistoricMessages)
                .forEach {
                    if (it.senderPerson != null) {
                        notiInfos.add(NotiInfo(it.timestamp, it.senderPerson?.name.toString(), it.text.toString()))
                        hasExtra = true
                    }
                }
        }

        return hasExtra
    }

    private fun fetchTitle(sbn: StatusBarNotification, isPeople: Boolean): String {

        if (isPeople) {
            val conversationTitle = sbn.notification?.extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            if (conversationTitle != null)
                return conversationTitle.toString()
        }

        return sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE_BIG).let { bigTitle ->
            bigTitle?.toString() ?: sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE).let { title ->
                title?.toString() ?: ""
            }
        }
    }

    private fun updateLatestTime() {
        latestTime = notiInfos.last().time
    }

    private fun deleteHistory() {
        prevNotiInfos.clear()
    }

    fun removeNoti(isPeople: Boolean) {
        if (isPeople) {
            prevNotiInfos.addAll(notiInfos)
            keepHistoryByCount()
            dropHistoryByTime()
        } else {
            deleteHistory()
        }
        notiInfos.clear()
    }

    private fun keepHistoryByCount() {
        val countLimit = SharedPreferencesManager.historyNotiCountThreshold
        if (countLimit < 0)
            return

        val iterator = prevNotiInfos.iterator()
        var count = prevNotiInfos.size
        while (iterator.hasNext() && count > countLimit) {
            iterator.next()
            iterator.remove()
            count--
        }
    }

    private fun dropHistoryByTime() {
        val hoursLimit = SharedPreferencesManager.historyNotiHoursThreshold
        if (hoursLimit < 0)
            return
        val historyCutOffTime = System.currentTimeMillis() - hoursLimit * 60 * 60 * 1000
        val subSet = prevNotiInfos.headSet(NotiInfo(historyCutOffTime), true)
        prevNotiInfos.removeAll(subSet)
    }
}
