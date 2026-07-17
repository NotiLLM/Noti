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
 * Posts or refreshes the persistent app status notification with recent and review counts.
 *
 * Keep count reads here lightweight; any richer business state should be computed before this utility is called.
 */
fun postOngoingNotification(context: Context) {

    CoroutineScope(Dispatchers.IO).launch {
        val appDatabase = AppDatabase.getInstance(context)
        val drawerDao = appDatabase.drawerDao()
        val savedItemDao = appDatabase.savedItemDao()

        val windowHours = SharedPreferencesManager.homeNotiWindowHours.coerceAtLeast(1)
        val cutoffMs = System.currentTimeMillis() - windowHours * 60L * 60L * 1000L
        val recentNotiCount = drawerDao.getActiveNotiCountSince(cutoffMs)
        // Item-level review counts: staged proposals plus legacy new/updated rows, one per eventual
        // item (an existing item covered by a staged group isn't double-counted).
        val pendingProposedOps = appDatabase.pendingProposedOpDao().getAll()
        val stagedTargets = pendingProposedOps.filter { it.targetItemId.isNotBlank() }.mapTo(mutableSetOf()) { it.targetItemId }
        val legacyNew = savedItemDao.getNewItems().filter { it.savedItemId !in stagedTargets }
        fun countFor(type: String): Int =
            pendingProposedOps.count { it.itemType == type && it.targetItemId.isBlank() } +
                pendingProposedOps.filter { it.itemType == type && it.targetItemId.isNotBlank() }.distinctBy { it.targetItemId }.size +
                legacyNew.count { it.itemType == type }
        val newTaskCount = countFor(SavedItemType.Task)
        val newKeepCount = countFor(SavedItemType.Keep)
        val notiTitle = context.getString(R.string.ongoing_status_title)
        val smallIcon = createCountIcon(context, recentNotiCount, false)
        val notificationCount = context.resources.getQuantityString(
            R.plurals.ongoing_status_notification_count,
            recentNotiCount,
            recentNotiCount,
        )
        val taskCount = context.resources.getQuantityString(
            R.plurals.ongoing_status_task_count,
            newTaskCount,
            newTaskCount,
        )
        val keepCount = context.resources.getQuantityString(
            R.plurals.ongoing_status_keep_count,
            newKeepCount,
            newKeepCount,
        )
        val recentSummary = if (recentNotiCount > 0) {
            context.getString(
                R.string.ongoing_status_recent_window,
                windowHours,
                notificationCount,
            )
        } else {
            ""
        }
        val reviewPrefix = context.getString(R.string.ongoing_status_awaiting_review)
        val reviewCounts = context.getString(
            R.string.ongoing_status_join_string,
            taskCount,
            keepCount,
        )
        val reviewSummary = if (newTaskCount > 0 && newKeepCount > 0) {
            reviewPrefix + reviewCounts
        } else if (newTaskCount > 0) {
            reviewPrefix + taskCount
        } else if (newKeepCount > 0) {
            reviewPrefix + keepCount
        } else {
            ""
        }
        val notiContent = if (recentSummary.isNotBlank() && reviewSummary.isNotBlank()) {
            context.getString(
                R.string.ongoing_status_join_string,
                recentSummary,
                reviewSummary,
            )
        } else if (recentSummary.isNotBlank()) {
            recentSummary
        } else if (reviewSummary.isNotBlank()) {
            reviewSummary
        } else {
            context.getString(R.string.ongoing_status_nothing_new)
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
