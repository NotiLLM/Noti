package org.muilab.notigpt.model.notifications.components

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream
import androidx.core.graphics.createBitmap

/**
 * Embedded metadata extracted from Android's StatusBarNotification.
 *
 * This component owns framework-to-app normalization for package, title, text, people, icons, and timestamps.
 * Keep Android API parsing here so NotiUnit remains a composition of stable app-level fields.
 */
data class NotiMetadata(
    val pkgName: String,
    val hashKey: Int,
    val groupKey: String,
    val isAppGroup: Boolean,
    val isGroupChat: Boolean,
    var sortKey: String,
    var appName: String = "Unknown App",
    var lastUpdateTime: Long = 0L,
    var lastSyncTime: Long = 0L,
    var icon: String = "Unknown Icon",
    var largeIcon: String = "Unknown Icon",
    var isPeople: Boolean,
) {

    companion object {
        /**
         * Process-wide cache of decoded notification icons, keyed by the Base64 string.
         *
         * Icons are stored as Base64 PNG columns and would otherwise be re-decoded on every card
         * recomposition and every scroll re-bind. Decoding once per distinct icon keeps the card
         * list smooth at 100+ notifications. Sized by bitmap bytes; evicted entries are left for GC
         * (cards may still hold a reference, so we must not recycle them here).
         */
        private const val ICON_CACHE_BYTES = 8 * 1024 * 1024 // ~8 MB
        private val iconBitmapCache = object : LruCache<String, Bitmap>(ICON_CACHE_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }

        fun fetchIsPeople(sbn: StatusBarNotification): Boolean {
            val notification = sbn.notification
            val hasAndroid12CallPerson = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                notification.extras.get(Notification.EXTRA_CALL_PERSON) != null
            val hasAndroid12MissedCallCategory = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                notification.category == Notification.CATEGORY_MISSED_CALL
            return (notification.extras.get(Notification.EXTRA_MESSAGES) != null
                    || notification.extras.get(Notification.EXTRA_HISTORIC_MESSAGES) != null
                    || notification.extras.get(Notification.EXTRA_MESSAGING_PERSON) != null
                    || hasAndroid12CallPerson
                    || notification.extras.get(Notification.EXTRA_PEOPLE_LIST)
                .let { it != null && (it as ArrayList<*>).isNotEmpty() }
                    || notification.category == Notification.CATEGORY_MESSAGE
                    || notification.category == Notification.CATEGORY_CALL
                    || hasAndroid12MissedCallCategory)
        }
    }

    constructor(sbn: StatusBarNotification): this (
        pkgName = sbn.opPkg,
        hashKey = sbn.key.hashCode(),
        groupKey = sbn.notification?.group.toString(),
        isAppGroup = sbn.isGroup,
        isGroupChat = sbn.notification?.extras?.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) ?: false,
        sortKey = sbn.notification?.sortKey.toString(),
        isPeople = fetchIsPeople(sbn)
    )

    fun update(context: Context, sbn: StatusBarNotification) {
        // appName
        val pm = context.packageManager
        val applicationInfo: ApplicationInfo? =
            sbn.packageName?.let {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(it, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        pm.getApplicationInfo(it, 0)
                    }
                } catch(e: Exception) {
                    null
                }
            }
        appName = (if (applicationInfo != null) {
            pm.getApplicationLabel(applicationInfo).toString()
        } else {
            pkgName
        })

        // time
        lastUpdateTime = sbn.notification?.`when` ?: sbn.postTime

        // icon
        icon = iconToBase64(context, pm, sbn.notification.smallIcon)
        val bigiconObject = sbn.notification.getLargeIcon()
        largeIcon = iconToBase64(context, pm, bigiconObject)
        if (largeIcon == "null")
            largeIcon = icon

        sortKey = sbn.notification?.sortKey.toString()
        this.isPeople = this.isPeople || fetchIsPeople(sbn)
    }

    private fun iconToBase64(context: Context, pm: PackageManager, icon: Icon?): String {
        val bitmap = try {
            iconToBitmap(context, icon!!)
        } catch (e: Exception) {
            try {
                pm.getApplicationIcon(pkgName).toBitmap()
            } catch (e: Exception) {
                null
            }
        } ?: return "null"
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun iconToBitmap(context: Context, icon: Icon): Bitmap? {
        val drawable = icon.loadDrawable(context)
        if (drawable is BitmapDrawable)
            return drawable.bitmap

        val width = drawable!!.intrinsicWidth
        val height = drawable.intrinsicHeight
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun base64ToBitmap(iconStr: String): Bitmap? {
        return try {
            val cleaned = iconStr.trim()
            if (cleaned.isBlank() || cleaned == "null") return null

            iconBitmapCache.get(cleaned)?.let { return it }

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val byteArray = Base64.decode(cleaned, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
                ?.also { iconBitmapCache.put(cleaned, it) }
        } catch (_: Throwable) {
            null
        }
    }

    fun getLargeBitmap(): Bitmap? {
        return if (largeIcon != "null" && largeIcon.isNotBlank())
            base64ToBitmap(largeIcon) ?: getBitmap()
        else {
            getBitmap()
        }
    }

    fun getBitmap(): Bitmap? {
        return if (icon != "null" && icon.isNotBlank())
            base64ToBitmap(icon)
        else
            null
    }
}
