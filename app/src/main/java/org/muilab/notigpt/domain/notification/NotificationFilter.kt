package org.muilab.notigpt.domain.notification

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * Pure(ish) rules for deciding whether a system notification should be processed by NotiGPT.
 *
 * This logic used to live inline in [org.muilab.notigpt.service.NotiListenerService].
 * Extracting it:
 * - makes the service easier to read
 * - makes the rules easier to test
 * - centralizes ignore reasons for logging and future tuning
 */
object NotificationFilter {

    enum class IgnoreReason {
        FROM_OUR_APP,
        ONGOING_OR_NOT_CLEARABLE,
        GROUP_SUMMARY,
        MEDIA_STYLE,
        CONNECTIVITY_NOTIFICATION,
        ANDROID_WIFI,
        ANDROID_NETWORKSTACK,
    }

    /**
     * @return the [IgnoreReason] if the notification should be ignored, or null if it should be processed.
     */
    fun ignoreReason(sbn: StatusBarNotification, appPackageName: String): IgnoreReason? {
        if (sbn.packageName == appPackageName) return IgnoreReason.FROM_OUR_APP

        if (sbn.isOngoing || !sbn.isClearable) return IgnoreReason.ONGOING_OR_NOT_CLEARABLE

        val flags = sbn.notification?.flags ?: 0
        if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) return IgnoreReason.GROUP_SUMMARY

        val template = sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TEMPLATE)
        if (template == Notification.MediaStyle::class.java.canonicalName) return IgnoreReason.MEDIA_STYLE

        return NotificationKeyRules.ignoreReasonForKey(sbn.key)
    }

    internal object NotificationKeyRules {
        fun ignoreReasonForKey(key: String): IgnoreReason? {
            if (key.contains("ConnectivityNotification")) return IgnoreReason.CONNECTIVITY_NOTIFICATION
            if (key.contains("com.android.wifi")) return IgnoreReason.ANDROID_WIFI
            if (key.contains("com.google.android.networkstack")) return IgnoreReason.ANDROID_NETWORKSTACK
            return null
        }
    }
}
