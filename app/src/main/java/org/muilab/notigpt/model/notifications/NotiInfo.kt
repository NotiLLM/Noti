package org.muilab.notigpt.model.notifications

import android.app.Notification
import android.app.Notification.MessagingStyle.Message.getMessagesFromBundleArray
import android.app.Person
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi

data class NotiInfo (
    val time: Long,
    val person: String = "",
    var notiSeen: Boolean = false,
    val prevContent: String = "",
    val extraTitle: String = "",
    val extraBigTitle: String = "",
    val extraConversationTitle: String = "",
    val extraBigText: String = "",
    val extraText: String = "",
    val extraTextLines: String = "",
    val extraSummaryText: String = "",
    val extraInfoText: String = "",
    val extraSubText: String = ""
) {

    companion object {
        private fun fetchTime(sbn: StatusBarNotification): Long {
            val `when` = sbn.notification?.`when` as Long
            val postTime = sbn.postTime
            return if (`when` != 0L)
                `when`
            else
                postTime
        }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun fetchPerson(sbn: StatusBarNotification): String {
            val messages = sbn.notification?.extras?.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messages != null) {
                getMessagesFromBundleArray(messages).lastOrNull()?.senderPerson?.name.let {
                    if (it != null)
                        return it.toString()
                }
            }

            val callPerson = sbn.notification?.extras?.get(Notification.EXTRA_CALL_PERSON)
            if (callPerson != null && !(callPerson as Person).name.isNullOrBlank())
                return callPerson.name.toString()

            return ""
        }

        fun fetchExtra(sbn: StatusBarNotification, extraString: String): String {
            return sbn.notification?.extras?.getCharSequence(extraString).toString()
        }

        fun verifyExtra(extraTextString: String): Boolean {
            return extraTextString.isNotBlank() && extraTextString != "null"
        }

//        fun fetchBigText(sbn: StatusBarNotification): String {
//            return sbn.notification?.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
//        }
//
//        fun fetchText(sbn: StatusBarNotification): String {
//            return sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT).toString()
//        }
//
//        fun fetchTextLines(sbn: StatusBarNotification): String {
//            return sbn.notification?.extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).toString()
//        }
//
//        fun getSummaryText(sbn: StatusBarNotification): String {
//            return sbn.notification?.extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT).toString()
//        }
//
//        fun getInfoText(sbn: StatusBarNotification): String {
//            return sbn.notification?.extras?.getCharSequence(Notification.EXTRA_INFO_TEXT).toString()
//        }
//
//        fun getSubText(sbn: StatusBarNotification): String {
//            return sbn.notification?.extras?.getCharSequence(Notification.EXTRA_SUB_TEXT).toString()
//        }

        val senderInTitle = setOf<String>()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    constructor (sbn: StatusBarNotification): this(
        time = fetchTime(sbn),
        person = fetchPerson(sbn),
        extraTitle = fetchExtra(sbn, Notification.EXTRA_TITLE),
        extraBigTitle = fetchExtra(sbn, Notification.EXTRA_TITLE_BIG),
        extraConversationTitle = fetchExtra(sbn, Notification.EXTRA_CONVERSATION_TITLE),
        extraBigText = fetchExtra(sbn, Notification.EXTRA_BIG_TEXT),
        extraText = fetchExtra(sbn, Notification.EXTRA_TEXT),
        extraTextLines = fetchExtra(sbn, Notification.EXTRA_TEXT_LINES),
        extraSummaryText = fetchExtra(sbn, Notification.EXTRA_SUMMARY_TEXT),
        extraInfoText = fetchExtra(sbn, Notification.EXTRA_INFO_TEXT),
        extraSubText = fetchExtra(sbn, Notification.EXTRA_SUB_TEXT)
    )

    constructor(time: Long, person: String, msgContent: String): this(
        time = time,
        person = person,
        prevContent = msgContent
    )

    var title: String = ""
        get() {
            return if (verifyExtra(extraConversationTitle))
                extraConversationTitle
            else if (verifyExtra(extraBigTitle))
                extraBigTitle
            else
                extraTitle
        }

    fun getDisplayedTitle(packageName: String, isPerson: Boolean): String {
        return if (isPerson && packageName !in senderInTitle && verifyExtra(person))
            person
        else
            title
    }

    var content: String = ""
        get() {
            return if (prevContent.isNotEmpty())
                prevContent
            else if (verifyExtra(extraBigText))
                extraBigText
            else if (verifyExtra(extraText))
                extraText
            else if (verifyExtra(extraTextLines))
                extraTextLines
            else if (verifyExtra(extraSummaryText))
                extraSummaryText
            else if (verifyExtra(extraInfoText))
                extraInfoText
            else
                extraSubText
        }
}