package org.muilab.notigpt.model.notifications

import android.app.Notification
import android.app.Notification.MessagingStyle.Message.getMessagesFromBundleArray
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.service.notification.StatusBarNotification
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import org.muilab.notigpt.service.NotiListenerService
import java.io.ByteArrayOutputStream

data class NotiMetadata(
    val pkgName: String,
    val sbnCategory: String,
    val sbnKey: String,
    val hashKey: Int,
    val groupKey: String,
    val isAppGroup: Boolean,
    val isGroupChat: Boolean,
    var sortKey: String,
    var appName: String = "Unknown App",
    var icon: String = "Unknown Icon",
    var largeIcon: String = "Unknown Icon",
    var isVisible: Boolean = true,
    var people: MutableSet<String> = mutableSetOf(),
    var isPeople: Boolean,
) {

    companion object {
        @RequiresApi(Build.VERSION_CODES.S)
        fun fetchIsPeople(sbn: StatusBarNotification): Boolean {
            val notification = sbn.notification
            val messagingApps = setOf(
                "jp.naver.line.android"
            )
            return (notification.extras.get(Notification.EXTRA_MESSAGES) != null
                    || notification.extras.get(Notification.EXTRA_HISTORIC_MESSAGES) != null
                    || notification.extras.get(Notification.EXTRA_MESSAGING_PERSON) != null
                    || notification.extras.get(Notification.EXTRA_CALL_PERSON) != null
                    || notification.extras.get(Notification.EXTRA_PEOPLE_LIST)
                .let { it != null && (it as ArrayList<*>).isNotEmpty() }
                    || notification.category == Notification.CATEGORY_MESSAGE
                    || notification.category == Notification.CATEGORY_CALL
                    || notification.category == Notification.CATEGORY_MISSED_CALL)
        }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun fetchPeople(sbn: StatusBarNotification): List<String> {
            val fromExtras = sbn.notification?.extras?.get(Notification.EXTRA_PEOPLE_LIST).let { peopleList ->
                if (peopleList == null)
                    arrayListOf()
                else {
                    (peopleList as ArrayList<Person>)
                        .map { it.name.toString() }
                }
            }
            val fromMessages = sbn.notification?.extras?.getParcelableArray(Notification.EXTRA_MESSAGES).let { peopleParcelable ->
                if (peopleParcelable == null)
                    arrayListOf()
                else {
                    getMessagesFromBundleArray(peopleParcelable)
                        .mapNotNull { it.senderPerson?.name }
                        .map { it.toString() }
                }
            }
            val fromHistoricMessages = sbn.notification?.extras?.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES).let { peopleParcelable ->
                if (peopleParcelable == null)
                    arrayListOf()
                else {
                    getMessagesFromBundleArray(peopleParcelable)
                        .mapNotNull { it.senderPerson?.name }
                        .map { it.toString() }
                }
            }
            return fromExtras + fromMessages + fromHistoricMessages
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    constructor(sbn: StatusBarNotification): this (
        pkgName = sbn.opPkg,
        sbnCategory = sbn.notification?.category ?: "Unknown",
        sbnKey = sbn.key,
        hashKey = sbn.key.hashCode(),
        groupKey = sbn.notification?.group.toString(),
        isAppGroup = sbn.isGroup,
        isGroupChat = sbn.notification?.extras?.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) ?: false,
        sortKey = sbn.notification?.sortKey.toString(),
        isPeople = fetchIsPeople(sbn)
    )

    @RequiresApi(Build.VERSION_CODES.S)
    fun update(context: Context, sbn: StatusBarNotification) {
        // appName
        val pm = context.packageManager
        val applicationInfo: ApplicationInfo? =
            sbn.packageName?.let {
                try {
                    if (Build.VERSION.SDK_INT >= TIRAMISU) {
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

        // icon
        icon = iconToBase64(context, pm, sbn.notification.smallIcon)
        val bigiconObject = sbn.notification.getLargeIcon()
        largeIcon = iconToBase64(context, pm, bigiconObject)
        if (largeIcon == "null")
            largeIcon = icon

        // save pending intent
        val pendingIntent = sbn.notification?.contentIntent
        if (sbn.notification?.contentIntent != null)
            NotiListenerService.pendingIntents[sbnKey] = pendingIntent as PendingIntent

        sortKey = sbn.notification?.sortKey.toString()
        this.isPeople = this.isPeople || fetchIsPeople(sbn)
        people.addAll(fetchPeople(sbn))
        isVisible = true
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
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun base64ToBitmap(iconStr: String): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val byteArray = Base64.decode(iconStr, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
    }

    fun getLargeBitmap(): Bitmap? {
        return if (largeIcon != "null")
            base64ToBitmap(largeIcon)
        else {
            getBitmap()
        }
    }

    fun getBitmap(): Bitmap? {
        return if (icon != "null")
            base64ToBitmap(icon)
        else
            null
    }
}
