package org.muilab.notigpt.model.notifications

import android.app.Notification
import android.app.Person
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index

/**
 * Room entity for one captured notification content record.
 *
 * Records are the durable notification timeline used for context, scan, extraction, and reminder provenance.
 * Keep display-level mutable state on NotiUnit unless the flag describes processing of this specific record.
 */
@Entity(
    tableName = "noti_record",
    primaryKeys = ["notiRecordId"],
    indices = [Index(value = ["notiKey", "whenTime"], name = "idx_record_notiKey_whenTime")]
)
data class NotiRecord (

    // KEYS
    val notiRecordId: String,
    val notiKey: String,

    // TIME RELATED
    val whenTime: Long,
    val postTime: Long,

    // TITLE RELATED
    val person: String = "",
    val extraTitle: String = "",
    val extraBigTitle: String = "",
    val extraConversationTitle: String = "",

    // CONTENT RELATED
    val extraBigText: String = "",
    val extraText: String = "",
    val extraTextLines: String = "",
    val extraSummaryText: String = "",
    val extraInfoText: String = "",
    val extraSubText: String = "",

    // STATUS
    /** True when this record has been removed from the active notification drawer. */
    var isDismissed: Boolean = false,

    // TASK DETECTION/EXTRACTION FLAGS
    @ColumnInfo(defaultValue = "0")
    var taskScanned: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    var taskExtracted: Boolean = false,

    /** Claim flag for atomically reserving records during extraction and avoiding duplicate work. */
    @ColumnInfo(defaultValue = "0")
    var taskExtractionClaimed: Boolean = false,
    // Timestamp when record was claimed for extraction (millis since epoch). 0 means not claimed.
    @ColumnInfo(defaultValue = "0")
    var taskExtractionClaimedAt: Long = 0L,
) {

    companion object {
        @RequiresApi(Build.VERSION_CODES.S)
        private fun fetchPerson(sbn: StatusBarNotification): String {
            val messages = sbn.notification?.extras?.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messages != null) {
                Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages).lastOrNull()?.senderPerson?.name.let {
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

            fun sanitizeInput(str: CharSequence?): String {
                return str?.toString()
                    ?.replace("\\", "\\\\")
                    ?.replace("\"", "\\\"")
                    ?.replace("\n", "\\n")
                    ?: ""
            }

            return sanitizeInput(sbn.notification?.extras?.getCharSequence(extraString).toString())
        }

        fun verifyExtra(extraTextString: String): Boolean {
            return extraTextString.isNotBlank() && extraTextString != "null"
        }

        val senderInTitle = setOf<String>()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    constructor (sbn: StatusBarNotification): this(
        notiRecordId = "${sbn.key}_${sbn.postTime}",
        notiKey = sbn.key,
        whenTime = sbn.notification?.`when` as Long,
        postTime = sbn.postTime,
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

    val time: Long
        get() {
            return if (whenTime != 0L)
                whenTime
            else
                postTime
        }

    val title: String
        get() {
            return if (verifyExtra(extraConversationTitle))
                extraConversationTitle
            else if (verifyExtra(extraBigTitle))
                extraBigTitle
            else
                extraTitle
        }

    fun getDisplayedTitle(isPerson: Boolean): String {
        return if (isPerson && verifyExtra(person))
            person
        else
            title
    }

    val content: String
        get() {
            return if (verifyExtra(extraBigText))
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