package org.muilab.notigpt.util

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
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.muilab.notigpt.MainActivity
import org.muilab.notigpt.database.room.DrawerDatabase
import org.muilab.notigpt.reciever.NotiActionReciever

fun createNotificationChannel(context: Context) {
    val channelId = "notigpt_all"
    val channelName = "NotiGPT All"
    val importance = NotificationManager.IMPORTANCE_HIGH
    val channel = NotificationChannel(channelId, channelName, importance)
    channel.description = "All Notifications"
    channel.setShowBadge(true)
    channel.setSound(null, null)
    channel.enableVibration(false)
    val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
}

fun createCountIcon(context: Context, number: Int, hasNotRead: Boolean): Bitmap {
    val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics).toInt()

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
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

@RequiresApi(Build.VERSION_CODES.S)
fun postOngoingNotification(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
        val drawerDatabase = DrawerDatabase.getInstance(context)
        val drawerDao = drawerDatabase.drawerDao()

        val allNotiCount = drawerDao.getAllNotiCount()
        val notiNotRead = drawerDao.getnotReadNotis()
        val notiNotReadCount = notiNotRead.size
        val hasNotRead = notiNotRead.isNotEmpty()
        val notiWithSenders = drawerDao.getNotiWithSenders()

        val notiTitle = if (hasNotRead) {
            "$notiNotReadCount unread ($allNotiCount in total)"
        } else
            "$allNotiCount notifications"

        val smallIcon = if (hasNotRead)
            createCountIcon(context, notiNotReadCount, true)
        else
            createCountIcon(context, allNotiCount, false)

        val sb = StringBuilder()

        if (hasNotRead){
            notiNotRead.forEach { notiUnit ->
                val notiCount = notiUnit.getNotiBody().size
                if (!notiUnit.isPeople)
                    sb.append("${notiUnit.metadata.appName}: ")
                sb.append(notiUnit.title)
                if (notiCount > 1)
                    sb.append(" ($notiCount messages)")
                sb.append("\n")
            }
        } else {
            var appName = ""
            notiWithSenders.forEach { notiUnit ->
                val notiSender = notiUnit.title
                val notiCount = notiUnit.getNotiBody().size
                sb.append(notiSender)
                if (notiCount > 1)
                    sb.append(" ($notiCount messages)")
                sb.append("\n")
            }
        }

        val notiContent = sb.toString()
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
            if (hasNotRead) {
                setColorized(true)
                setColor(0xffeed202.toInt())
            } else
                setColor(0xffcccccc.toInt())
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setSilent(true)
            setContentIntent(pendingIntent)
            setOngoing(true)  // This makes the notification ongoing
            if (hasNotRead) {
                val readIntentAction = Intent(context, NotiActionReciever::class.java).apply {
                    action = "read_all"
                }
                val readPendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    readIntentAction,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val pinIntentAction = Intent(context, NotiActionReciever::class.java).apply {
                    action = "pin_all"
                }
                val pinPendingIntent = PendingIntent.getBroadcast(
                    context,
                    1,
                    pinIntentAction,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                addAction(0, "Read All", readPendingIntent)
                addAction(1, "Pin All", pinPendingIntent)
            }
        }
        val notificationId = 44

        val notificationManager = ContextCompat.getSystemService(
            context, NotificationManager::class.java
        ) as NotificationManager
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
