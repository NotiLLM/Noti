package org.muilab.notigpt.ui.home.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import org.muilab.notigpt.data.local.room.AppDatabase
import org.muilab.notigpt.data.local.room.dao.SmartFilterCounts
import org.muilab.notigpt.data.repository.notification.NotiClassificationRepository
import org.muilab.notigpt.model.features.NotiCategory
import org.muilab.notigpt.model.features.NotiLlmState
import org.muilab.notigpt.model.features.PendingOp
import org.muilab.notigpt.model.features.PendingOpType
import org.muilab.notigpt.model.features.SavedItem
import org.muilab.notigpt.model.features.SavedItemState
import org.muilab.notigpt.model.features.SavedItemType
import org.muilab.notigpt.model.notifications.NotiDisplayUnit
import org.muilab.notigpt.util.time.DayBoundaries

/** Aggregated review + smart-filter counts for one saved-item type/state slice. */
data class ReviewCounts(
    val newTasks: Int = 0,
    val updatedTasks: Int = 0,
    val newKeeps: Int = 0,
    val updatedKeeps: Int = 0,
) {
    val total: Int get() = newTasks + updatedTasks + newKeeps + updatedKeeps
}

/** One preview line on a home notification row: sender/app label, record count, and the app icon. */
data class CategoryPreviewLine(
    val label: String,
    val count: Int,
    val icon: Bitmap? = null,
)

/**
 * Preview for one notification category row on the home screen: the top few senders/apps by recency
 * with their new-notification counts, plus separate recent and older totals for the badges.
 */
data class CategoryPreview(
    val topLines: List<CategoryPreviewLine> = emptyList(),
    val recentRecords: Int = 0,
    val recentUnits: Int = 0,
    val olderRecords: Int = 0,
    val olderUnits: Int = 0,
)

/**
 * Home-screen data: review counts, planned-date smart-filter counts, and per-category notification
 * previews. Counts come straight from Room (Flow-backed); notification previews are derived from the
 * active drawer's new records plus persisted LLM categories, passed in from the DrawerViewModel.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application.applicationContext)
    private val savedItemDao = db.reminderListDao()

    /**
     * Item-level review counts: users count the eventual items they'll review, not atomic pipeline
     * instructions. Staged creates are "new"; distinct existing items with staged changes are
     * "updated"; legacy new/updated rows (single-item regeneration) count unless a staged group
     * already covers them.
     */
    val reviewCounts: StateFlow<ReviewCounts> = combine(
        db.pendingOpDao().observeAll(),
        savedItemDao.observeNewItems(),
    ) { ops, legacyItems -> aggregateReviewCounts(ops, legacyItems) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewCounts())

    /** Re-evaluates the "today" boundary at each local midnight so the buckets stay correct while idle. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val smartFilterCounts: StateFlow<SmartFilterCounts> = midnightTicker()
        .flatMapLatest { savedItemDao.observeSmartFilterCounts(DayBoundaries.startOfTomorrowMs()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartFilterCounts())

    /** Active (non-completed) task total, for the drawer's Tasks entry badge. */
    val activeTaskCount: StateFlow<Int> = savedItemDao.observeActiveTaskCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Active (non-archived) keep total, for the drawer's Keep entry badge. */
    val activeKeepCount: StateFlow<Int> = savedItemDao.observeActiveKeepCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private fun midnightTicker() = flow {
        while (true) {
            emit(Unit)
            val next = DayBoundaries.startOfTomorrowMs()
            val wait = (next - System.currentTimeMillis() + 1_000L).coerceAtLeast(60_000L)
            delay(wait)
        }
    }

    private fun aggregateReviewCounts(ops: List<PendingOp>, legacyItems: List<SavedItem>): ReviewCounts {
        var nt = 0; var ut = 0; var nk = 0; var uk = 0
        ops.filter { it.opType == PendingOpType.Create }.forEach { op ->
            if (op.itemType == SavedItemType.Task) nt++ else nk++
        }
        val targeted = ops.filter { it.targetItemId.isNotBlank() }
        targeted.distinctBy { it.targetItemId }.forEach { op ->
            if (op.itemType == SavedItemType.Task) ut++ else uk++
        }
        val targetedIds = targeted.mapTo(mutableSetOf()) { it.targetItemId }
        legacyItems.filter { it.savedItemId !in targetedIds }.forEach { item ->
            val isTask = item.itemType == SavedItemType.Task
            val isNew = item.state == SavedItemState.New
            when {
                isTask && isNew -> nt++
                isTask && !isNew -> ut++
                !isTask && isNew -> nk++
                else -> uk++
            }
        }
        return ReviewCounts(nt, ut, nk, uk)
    }

    companion object {
        private const val MAX_PREVIEW_LABELS = 3

        /**
         * Recency window shared by the home previews and the category screens' time chip, derived
         * from the user-editable "Past X hours" setting (positive hours only).
         */
        fun newNotiWindowMs(): Long =
            org.muilab.notigpt.util.SharedPreferencesManager.homeNotiWindowHours * 60L * 60 * 1000

        /**
         * Splits the active drawer's new-notification units into Communication and Content previews.
         *
         * Communication groups by the thread's overall title (one line per notiKey) and counts
         * individual messages (records). Content groups by app name and counts threads (notiKeys).
         * Each thread is assigned to one bucket using its latest received record. Labels are ordered
         * by the most recent record in each group and capped to the top few, each carrying its app icon.
         */
        fun buildPreviews(
            newUnits: List<NotiDisplayUnit>,
            llmByKey: Map<String, NotiLlmState>,
            cutoffMs: Long,
        ): Map<String, CategoryPreview> {
            data class Group(var count: Int = 0, var latest: Long = 0L, var icon: Bitmap? = null)

            // Communication: keyed by notiKey so all senders in a thread collapse into one line.
            val comm = LinkedHashMap<String, Group>()
            // Content: keyed by app name; each group counts the number of distinct threads.
            val content = LinkedHashMap<String, Group>()
            var recentCommUnits = 0; var recentContentUnits = 0
            var recentCommRecords = 0; var recentContentRecords = 0
            var olderCommUnits = 0; var olderContentUnits = 0
            var olderCommRecords = 0; var olderContentRecords = 0

            newUnits.forEach { du ->
                val records = du.notiRecords
                val latestRecordTime = records.maxOfOrNull { it.time } ?: return@forEach
                val isRecent = latestRecordTime >= cutoffMs
                val category = NotiClassificationRepository.categoryOf(du.notiUnit, llmByKey[du.notiKey])
                val isComm = category == NotiCategory.Communication
                if (isComm) {
                    if (isRecent) {
                        recentCommUnits++
                        recentCommRecords += records.size
                        // Overall thread title (conversation/group title), not the individual sender.
                        val label = records.maxByOrNull { it.time }?.title?.ifBlank { du.notiUnit.appName }
                            ?: du.notiUnit.appName
                        val g = comm.getOrPut(label) { Group() }
                        g.count += records.size
                        if (latestRecordTime >= g.latest) {
                            g.latest = latestRecordTime
                            g.icon = du.notiUnit.bitmap
                        }
                    } else {
                        olderCommUnits++
                        olderCommRecords += records.size
                    }
                } else {
                    if (isRecent) {
                        recentContentUnits++
                        recentContentRecords += records.size
                        val g = content.getOrPut(du.notiUnit.appName) { Group() }
                        // Content counts threads, so one per unit regardless of record count.
                        g.count += 1
                        if (latestRecordTime >= g.latest) {
                            g.latest = latestRecordTime
                            g.icon = du.notiUnit.bitmap
                        }
                    } else {
                        olderContentUnits++
                        olderContentRecords += records.size
                    }
                }
            }

            fun top(map: Map<String, Group>): List<CategoryPreviewLine> =
                map.entries.sortedByDescending { it.value.latest }
                    .take(MAX_PREVIEW_LABELS)
                    .map { CategoryPreviewLine(it.key, it.value.count, it.value.icon) }

            return mapOf(
                NotiCategory.Communication to CategoryPreview(
                    topLines = top(comm),
                    recentRecords = recentCommRecords,
                    recentUnits = recentCommUnits,
                    olderRecords = olderCommRecords,
                    olderUnits = olderCommUnits,
                ),
                NotiCategory.Content to CategoryPreview(
                    topLines = top(content),
                    recentRecords = recentContentRecords,
                    recentUnits = recentContentUnits,
                    olderRecords = olderContentRecords,
                    olderUnits = olderContentUnits,
                ),
            )
        }
    }
}
