package org.muilab.notigpt.domain.notification

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * Rules for deciding whether an Android system notification should enter the app pipeline.
 *
 * Keep device/framework filtering here so the listener service only coordinates capture and storage.
 * Add new ignore reasons here when they describe notification eligibility rather than UI behavior.
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
     * Returns why a notification should be skipped, or null when it should be captured.
     *
     * Prefer adding explicit reasons over boolean checks so listener logging and future tuning stay inspectable.
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
