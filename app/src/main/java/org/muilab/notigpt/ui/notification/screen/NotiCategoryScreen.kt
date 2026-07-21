package org.muilab.notigpt.ui.notification.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.data.repository.notification.NotiClassificationRepository
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.model.notifications.NotiRecord
import org.muilab.notigpt.ui.common.component.EmptyState
import org.muilab.notigpt.ui.home.viewmodel.HomeViewModel
import org.muilab.notigpt.ui.notification.component.card.noticard.NotiCard
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel
import org.muilab.notigpt.ui.reminder.screen.ReminderDateTimeDialog
import org.muilab.notigpt.ui.reminder.viewmodel.ScheduledReminderViewModel
import org.muilab.notigpt.util.SharedPreferencesManager

/**
 * Full-screen NotiCard list for one notification category (Communication / Content).
 *
 * Threads are classified via [NotiClassificationRepository] (persisted LLM category, falling back to
 * the messaging-style signal). Time chips split threads into recent X hours versus older
 * notifications using each thread's latest received record; the search bar filters records in memory. Cards can spawn a scheduled
 * reminder that snapshots the records visible at creation time.
 */
@Composable
fun NotiCategoryScreen(
    category: String,
    drawerViewModel: DrawerViewModel,
    scheduledReminderViewModel: ScheduledReminderViewModel,
    searchQuery: String,
    onOpenSavedItem: (SavedItem) -> Unit = {},
) {
    val context = LocalContext.current
    val newUnits by drawerViewModel.newNotificationUnits.collectAsState()
    val hasLoadedNotifications by drawerViewModel.hasLoadedInitialNotifications.collectAsState()
    val llmByKey by drawerViewModel.llmStatesByKey.collectAsState()

    // Lifted to the ViewModel so the top-bar Clear All action can scope to the same visible window.
    val recentOnly by drawerViewModel.notiRecentOnly.collectAsState()
    val normalizedQuery = searchQuery.trim().lowercase()

    // Pending "create reminder" from a card's records → opens the date-time dialog.
    var pendingReminder by remember { mutableStateOf<PendingReminder?>(null) }

    val cutoff = System.currentTimeMillis() - HomeViewModel.newNotiWindowMs()

    // The category's units with records filtered only by the search query (the time chip is applied
    // per-count below), reused for both the chip counts and the visible list.
    val categoryUnits: List<NotiDisplayUnit> = remember(newUnits, llmByKey, normalizedQuery, category) {
        newUnits.mapNotNull { du ->
            if (NotiClassificationRepository.categoryOf(du.notiUnit, llmByKey[du.notiKey]) != category) return@mapNotNull null
            val records = du.notiRecords.filter { record ->
                normalizedQuery.isBlank() || listOf(record.title, record.content, record.person)
                    .any { it.lowercase().contains(normalizedQuery) }
            }.sortedBy { it.time }
            if (records.isEmpty()) null else NotiDisplayUnit(du.notiUnit, records)
        }
    }

    // A thread belongs to exactly one bucket based on its latest received record.
    fun isRecent(du: NotiDisplayUnit): Boolean = du.notiRecords.maxOf { it.time } >= cutoff
    val recentCount = remember(categoryUnits, cutoff) { categoryUnits.count(::isRecent) }
    val olderCount = remember(categoryUnits, cutoff) { categoryUnits.count { !isRecent(it) } }

    val visibleUnits: List<NotiDisplayUnit> = remember(categoryUnits, recentOnly, cutoff) {
        categoryUnits
            .filter { isRecent(it) == recentOnly }
            .sortedByDescending { it.notiRecords.maxOf(NotiRecord::time) }
    }

    Column(Modifier.fillMaxSize()) {
        if (!hasLoadedNotifications) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.padding(8.dp))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = recentOnly,
                    onClick = { drawerViewModel.setNotiRecentOnly(true) },
                    label = { Text(stringResource(R.string.noti_filter_recent_count, SharedPreferencesManager.homeNotiWindowHours, recentCount)) },
                )
                FilterChip(
                    selected = !recentOnly,
                    onClick = { drawerViewModel.setNotiRecentOnly(false) },
                    label = { Text(stringResource(R.string.noti_filter_older_count, olderCount)) },
                )
            }

            if (visibleUnits.isEmpty()) {
                EmptyState(R.drawable.inbox, stringResource(R.string.noti_category_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    items(visibleUnits, key = { it.notiKey }, contentType = { "notiCard" }) { displayUnit ->
                        NotiCard(
                            context = context,
                            notiDisplayUnit = displayUnit,
                            isDragging = false,
                            drawerViewModel = drawerViewModel,
                            isCardVisible = true,
                            parentViewport = Rect.Zero,
                            onCreateReminder = { title, records ->
                                pendingReminder = PendingReminder(
                                    title = title,
                                    content = snapshotRecordsContent(title, records, displayUnit.notiUnit.isPeople),
                                    recordIds = records.map { it.notiRecordId },
                                )
                            },
                            onOpenSavedItem = onOpenSavedItem,
                        )
                    }
                }
            }
        }
    }

    pendingReminder?.let { pending ->
        ReminderDateTimeDialog(
            title = stringResource(R.string.ui_reminder_create_button),
            initialAtMs = System.currentTimeMillis(),
            onDismiss = { pendingReminder = null },
            onConfirm = { remindAtMs ->
                scheduledReminderViewModel.createForNotiRecords(pending.title, pending.content, pending.recordIds, remindAtMs)
                pendingReminder = null
            },
        )
    }
}

private data class PendingReminder(
    val title: String,
    val content: String,
    val recordIds: List<String>,
)

/**
 * One line per visible record, oldest first. Sender names prefix each line in message threads,
 * except when the sender is just the thread title (1:1 chats) where it would be redundant.
 */
private fun snapshotRecordsContent(title: String, records: List<NotiRecord>, isPeople: Boolean): String =
    records.sortedBy { it.time }.mapNotNull { record ->
        val body = record.content.trim()
        if (body.isEmpty()) return@mapNotNull null
        val sender = record.getDisplayedTitle(isPeople)
        if (isPeople && sender.isNotBlank() && sender != title) "$sender: $body" else body
    }.joinToString("\n")
