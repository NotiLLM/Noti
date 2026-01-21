package org.muilab.notigpt.debug

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.muilab.notigpt.database.room.AppDatabase
import org.muilab.notigpt.model.features.ReminderUnit
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiItem
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.model.notifications.NotiUnit
import org.muilab.notigpt.model.notifications.components.NotiDisplayState
import org.muilab.notigpt.model.notifications.components.NotiMetadata
import org.muilab.notigpt.model.notifications.components.NotiReminderAttr
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Deterministic English dummy data for screenshots.
 *
 * Notes:
 * - Notifications are flat (no grouping).
 * - Reminders are *tasks only* (no memos).
 * - Icons are best-effort via package-name lookup; falls back to no icon.
 */
object DummyData {

    // Jan 21, 2026 12:00:00 local-ish anchor (ms). We keep it stable for screenshots.
    private val anchorTimeMs: Long = 1768996800000L

    object Notifications {
        /** Build 10 dummy drawer items compatible with [org.muilab.notigpt.ui.screens.NotificationsScreen]. */
        fun buildDrawerItems(context: Context): List<NotiItem> {
            val specs = listOf(
                NotiSpec(
                    key = "dummy_slack_1",
                    pkg = "com.Slack",
                    appName = "Slack",
                    isPeople = true,
                    minutesAgo = 2,
                    title = "Alex",
                    text = "Dinner tonight? I can do 7:30.",
                    isRead = false,
                ),
                NotiSpec(
                    key = "dummy_whatsapp_1",
                    pkg = "com.whatsapp",
                    appName = "WhatsApp",
                    isPeople = true,
                    minutesAgo = 8,
                    title = "Mom",
                    text = "Can you pick up milk on the way home?",
                    isRead = false,
                ),
                NotiSpec(
                    key = "dummy_messenger_1",
                    pkg = "com.facebook.orca",
                    appName = "Messenger",
                    isPeople = true,
                    minutesAgo = 14,
                    title = "Jordan",
                    text = "Got it. Thanks!",
                    isRead = true,
                ),
                NotiSpec(
                    key = "dummy_gmail_1",
                    pkg = "com.google.android.gm",
                    appName = "Gmail",
                    isPeople = false,
                    minutesAgo = 22,
                    title = "Your receipt",
                    text = "Payment confirmed. Thanks for your purchase.",
                    isRead = true,
                ),
                NotiSpec(
                    key = "dummy_calendar_1",
                    pkg = "com.google.android.calendar",
                    appName = "Calendar",
                    isPeople = false,
                    minutesAgo = 35,
                    title = "Reminder",
                    text = "Haircut appointment in 30 minutes.",
                    isRead = true,
                ),
                NotiSpec(
                    key = "dummy_delivery_1",
                    pkg = "com.ubercab",
                    appName = "Uber",
                    isPeople = false,
                    minutesAgo = 52,
                    title = "Driver arriving",
                    text = "Your ride is 2 minutes away.",
                    isRead = true,
                ),
                NotiSpec(
                    key = "dummy_music_1",
                    pkg = "com.spotify.music",
                    appName = "Spotify",
                    isPeople = false,
                    minutesAgo = 75,
                    title = "Daily Mix ready",
                    text = "New songs picked for you.",
                    isRead = true,
                ),
                NotiSpec(
                    key = "dummy_bank_1",
                    pkg = "com.mybank.mobile",
                    appName = "MyBank",
                    isPeople = false,
                    minutesAgo = 130,
                    title = "Card alert",
                    text = "$12.45 spent at Coffee Shop.",
                    isRead = true,
                ),
                NotiSpec(
                    key = "dummy_weather_1",
                    pkg = "com.google.android.apps.weather",
                    appName = "Weather",
                    isPeople = false,
                    minutesAgo = 210,
                    title = "Rain later",
                    text = "80% chance of rain after 5 PM.",
                    isRead = true,
                ),
                NotiSpec(
                    key = "dummy_photos_1",
                    pkg = "com.google.android.apps.photos",
                    appName = "Photos",
                    isPeople = false,
                    minutesAgo = 390,
                    title = "Backup complete",
                    text = "1,248 items backed up.",
                    isRead = true,
                ),
            )

            // Icon resolution is I/O-ish (package manager + DB). Do it once per package name.
            val iconByPkg = specs.map { it.pkg }.distinct().associateWith { pkg ->
                resolveIconBase64(context, pkg)
            }

            return specs.mapIndexed { idx, s ->
                val time = anchorTimeMs - TimeUnit.MINUTES.toMillis(s.minutesAgo.toLong())
                val metadata = NotiMetadata(
                    pkgName = s.pkg,
                    hashKey = (s.key + idx).hashCode(),
                    groupKey = s.pkg,
                    isAppGroup = false,
                    isGroupChat = false,
                    sortKey = "${time}",
                    appName = s.appName,
                    lastUpdateTime = time,
                    lastSyncTime = time,
                    icon = iconByPkg[s.pkg] ?: "null",
                    largeIcon = "null",
                    isPeople = s.isPeople,
                )

                val displayState = NotiDisplayState().apply {
                    isDismissed = false
                    isRead = s.isRead
                    summary = ""
                    explanation = ""
                    sortScore = 100.0
                }

                val unit = NotiUnit(
                    notiKey = s.key,
                    metadata = metadata,
                    displayState = displayState,
                    reminderAttr = NotiReminderAttr(
                        shouldExtractReminder = false,
                        hasTask = false,
                        hasMemo = false,
                    ),
                    groupId = null,
                )

                val record = NotiRecord(
                    notiRecordId = "${s.key}_${time}",
                    notiKey = s.key,
                    whenTime = time,
                    postTime = time,
                    person = if (s.isPeople) s.title else "",
                    extraTitle = if (!s.isPeople) s.title else "",
                    extraBigTitle = "",
                    extraConversationTitle = "",
                    extraBigText = "",
                    extraText = s.text,
                    extraTextLines = "",
                    extraSummaryText = "",
                    extraInfoText = "",
                    extraSubText = "",
                    isDismissed = false,
                    taskScanned = true,
                    taskExtracted = false,
                )

                NotiItem(NotiDisplayUnit(unit, listOf(record)))
            }
        }

        private data class NotiSpec(
            val key: String,
            val pkg: String,
            val appName: String,
            val isPeople: Boolean,
            val minutesAgo: Int,
            val title: String,
            val text: String,
            val isRead: Boolean,
        )

        private fun resolveIconBase64(context: Context, pkg: String): String {
            // 1) Prefer installed app icon by package name.
            packageIconToBase64(context, pkg).takeIf { it != "null" }?.let { return it }

            // 2) Fall back to any stored NotiUnit icon in local DB for the same package name.
            // Best-effort only: never crash and never block UI.
            return try {
                val db = AppDatabase.getInstance(context.applicationContext)
                val any = db.drawerDao().getAll().firstOrNull { it.pkgName == pkg }
                val icon = any?.metadata?.icon
                if (icon.isNullOrBlank()) "null" else icon
            } catch (_: Throwable) {
                "null"
            }
        }

        private fun packageIconToBase64(context: Context, pkg: String): String {
            return try {
                val drawable = context.packageManager.getApplicationIcon(pkg)
                val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
                bitmapToBase64(bitmap)
            } catch (_: PackageManager.NameNotFoundException) {
                "null"
            } catch (_: Throwable) {
                "null"
            }
        }

        private fun bitmapToBase64(bitmap: Bitmap): String {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(CompressFormat.PNG, 100, outputStream)
            val bytes = outputStream.toByteArray()
            return Base64.encodeToString(bytes, Base64.DEFAULT)
        }
    }

    object Tasks {
        private val initial: List<ReminderUnit> = buildInitialTasks()
        private val _tasks = MutableStateFlow(initial)
        val tasks: StateFlow<List<ReminderUnit>> = _tasks.asStateFlow()

        fun upsert(task: ReminderUnit) {
            val list = _tasks.value.toMutableList()
            val idx = list.indexOfFirst { it.reminderId == task.reminderId }
            if (idx >= 0) list[idx] = task else list.add(0, task)
            _tasks.value = list.sortedByDescending { it.lastUpdateTimestamp }
        }

        fun delete(id: String) {
            _tasks.value = _tasks.value.filterNot { it.reminderId == id }
        }

        fun setCompleted(id: String, completed: Boolean) {
            val now = System.currentTimeMillis()
            _tasks.value = _tasks.value.map {
                if (it.reminderId == id) it.copy(isCompleted = completed, lastUpdateTimestamp = now) else it
            }
        }

        private fun buildInitialTasks(): List<ReminderUnit> {
            val now = anchorTimeMs
            val day = TimeUnit.DAYS.toMillis(1)
            val hour = TimeUnit.HOURS.toMillis(1)

            val specs = listOf(
                TaskSpec("t_001", "Buy groceries", "Eggs, bananas, and bread.", now - hour, now + day, 20),
                TaskSpec("t_002", "Pay electricity bill", "Due this week. Pay in the app.", now - 2 * hour, now + 2 * day, 5),
                TaskSpec("t_003", "Book dentist appointment", "Call the clinic and pick a time.", now - 3 * hour, now + 5 * day, 10),
                TaskSpec("t_004", "Return package", "Drop off the return at the locker.", now - 5 * hour, now + 3 * day, 15),
                TaskSpec("t_005", "Laundry", "Wash and dry clothes.", now - 7 * hour, 0L, 60),
                TaskSpec("t_006", "Message landlord", "Ask about the repair schedule.", now - 10 * hour, now + 4 * day, 5),
                TaskSpec("t_007", "Refill prescription", "Request refill from the pharmacy.", now - 14 * hour, now + 6 * day, 5),
                TaskSpec("t_008", "Plan weekend trip", "Pick hotel and confirm transportation.", now - 18 * hour, 0L, 25),
                TaskSpec("t_009", "Clean kitchen", "Wipe counters and take out trash.", now - 22 * hour, 0L, 20),
                TaskSpec("t_010", "Recharge transit card", "Top up before commuting.", now - 26 * hour, now + 2 * day, 3),
            )

            return specs.mapIndexed { idx, s ->
                ReminderUnit(
                    reminderId = s.id,
                    reminderTitle = s.title,
                    reminderContent = s.content,
                    isTask = true,
                    isCompleted = (idx == 8),
                    lastUpdateTimestamp = s.lastUpdated,
                    deadlineTimestamp = s.deadline,
                    estimatedCompletionTime = s.ectMinutes.toLong(),
                    associatedNotis = emptySet(),
                    extractionSnapshotId = null,
                    origin = "dummy",
                    humanEditCount = 0,
                    deletedAtMs = null,
                    userEdited = false,
                )
            }.sortedByDescending { it.lastUpdateTimestamp }
        }

        private data class TaskSpec(
            val id: String,
            val title: String,
            val content: String,
            val lastUpdated: Long,
            val deadline: Long,
            val ectMinutes: Int,
        )
    }
}
