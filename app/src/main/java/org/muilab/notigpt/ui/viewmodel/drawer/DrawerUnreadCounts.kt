package org.muilab.notigpt.ui.viewmodel.drawer

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.muilab.notigpt.repository.NotiRepository
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_SAVE

/**
 * Keeps unread-count logic out of DrawerViewModel.
 */
internal class DrawerUnreadCounts(
    private val scope: CoroutineScope,
    private val notiRepository: NotiRepository,
) {
    private val _unreadCountsByCategory = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCountsByCategory: StateFlow<Map<String, Int>> = _unreadCountsByCategory.asStateFlow()

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val counts = mutableMapOf<String, Int>()
            val cats = listOf(NOTI_CATEGORY_GENERAL, NOTI_CATEGORY_MAKETASK, NOTI_CATEGORY_SAVE, NOTI_CATEGORY_ARCHIVE)

            cats.forEach { cat ->
                counts[cat] = notiRepository.getVisibleNotReadNotificationCountByCategory(cat)
                counts["$cat-Total"] = notiRepository.getVisibleNotiCountByCategory(cat)
            }

            Log.d("DrawerViewModel", "Unread counts updated: $counts")
            _unreadCountsByCategory.value = counts
        }
    }
}

