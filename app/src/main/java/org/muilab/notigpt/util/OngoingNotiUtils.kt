package org.muilab.notigpt.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.MainActivity
import org.muilab.notigpt.R
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.model.features.SavedItemType

/**
 * Notification group keys keep the app's distinct notification concerns from auto-bundling together on
 * Android <= 15. (Android 16+ force-groups all of an app's notifications regardless of these keys.)
 */
object NotiGroups {
    const val STATUS = "org.muilab.notigpt.group.STATUS"
    const val REMINDERS = "org.muilab.notigpt.group.REMINDERS"
    const val GENERATION = "org.muilab.notigpt.group.GENERATION"
}

/**
 * Helpers for the app's persistent foreground-style status notification.
 *
 * Keep notification-channel creation, icon rendering, and status posting here. Business counts and unread rules
 * should come from repositories before reaching this utility.
 */
fun createNotificationChannel(context: Context) {
    val channelId = "notigpt_all"
    val channelName = "NotiGPT All"
    val importance = NotificationManager.IMPORTANCE_HIGH
    val channel = NotificationChannel(channelId, channelName, importance)
    channel.description = "All Notifications"
    channel.setShowBadge(true)
    channel.setSound(null, null)
    channel.enableVibration(false)

    // Prevent user from "deleting" this channel's notifications (best-effort; OEM may still vary).
    // This does not replace a foreground service, but it helps.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        channel.setAllowBubbles(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        channel.setBypassDnd(false)
        channel.enableLights(false)
        channel.setShowBadge(true)
    }

    val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
}

fun createCountIcon(context: Context, number: Int, hasNotRead: Boolean): Bitmap {
    val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics).toInt()

    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)

    val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    shapePaint.color = Color.WHITE
    shapePaint.style = Paint.Style.STROKE
    shapePaint.strokeWidth = 2f
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (shapePaint.strokeWidth / 2), shapePaint)

    // Drawing the number in a contrasting color
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.color = if (hasNotRead) Color.WHITE else Color.BLACK
    textPaint.typeface = if (hasNotRead)
        Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    else
        Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    textPaint.textSize = size * when(number.toString().length) {
        1 -> 0.9f
        2 -> 0.75f
        else -> 0.6f
    }

    // Calculate vertical centering for text
    val metrics = textPaint.fontMetrics
    val x = size / 2f
    val y = size / 2f - (metrics.descent + metrics.ascent) / 2

    canvas.drawText(number.toString(), x, y, textPaint)

    return bitmap
}

/**
 * Posts or refreshes the persistent app status notification with active/unread counts.
 *
 * Keep count reads here lightweight; any richer business state should be computed before this utility is called.
 */
@RequiresApi(Build.VERSION_CODES.S)
fun postOngoingNotification(context: Context) {

    CoroutineScope(Dispatchers.IO).launch {
        val appDatabase = AppDatabase.getInstance(context)
        val drawerDao = appDatabase.drawerDao()
        val savedItemDao = appDatabase.reminderListDao()

        val allNotiCount = drawerDao.getActiveNotiCount()
        val newTaskCount = savedItemDao.countNewByType(SavedItemType.Task)
        val newKeepCount = savedItemDao.countNewByType(SavedItemType.Keep)
        val notiTitle = "$allNotiCount notifications"
        val smallIcon = createCountIcon(context, allNotiCount, false)

        // Summary body: "X new tasks, Y new keep, Z new notifications", omitting any zero clause.
        val clauses = buildList {
            if (newTaskCount > 0) add("$newTaskCount new tasks")
            if (newKeepCount > 0) add("$newKeepCount new keep")
            if (allNotiCount > 0) add("$allNotiCount new notifications")
        }
        val notiContent = if (clauses.isEmpty()) {
            context.getString(R.string.ongoing_status_idle)
        } else {
            clauses.joinToString(", ")
        }
        val bigTextStyle = NotificationCompat.BigTextStyle()
        bigTextStyle.bigText(notiContent)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val channelId = "notigpt_all"
        val notificationBuilder = NotificationCompat.Builder(context, channelId).apply {
            setSmallIcon(IconCompat.createWithBitmap(smallIcon))
            setContentTitle(notiTitle)
            setContentText(notiContent)
            setStyle(bigTextStyle)
            setColor(0xffcccccc.toInt())
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setSilent(true)
            setContentIntent(pendingIntent)
            setOngoing(true)  // This makes the notification ongoing
            setAutoCancel(false)
            setGroup(NotiGroups.STATUS)  // Keep status separate from reminders/generation groups.
        }
        val notificationId = 44

        val notificationManager = ContextCompat.getSystemService(
            context, NotificationManager::class.java
        ) as NotificationManager

        val built = notificationBuilder.build().apply {
            // Stronger than setOngoing on many devices: cannot be cleared via "Clear all".
            flags = flags or Notification.FLAG_NO_CLEAR
        }

        notificationManager.notify(notificationId, built)
    }
}
