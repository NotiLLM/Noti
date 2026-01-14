package org.muilab.notigpt.ui.viewmodel.drawer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Legacy: category-based unread counts were used by the old bottom bar.
 * Categories are removed in the redesign, so this controller is now a no-op.
 */
internal class DrawerUnreadCounts(
    @Suppress("UNUSED_PARAMETER") private val scope: kotlinx.coroutines.CoroutineScope,
    @Suppress("UNUSED_PARAMETER") private val notiRepository: org.muilab.notigpt.repository.NotiRepository,
) {
    private val _unreadCountsByCategory = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCountsByCategory: StateFlow<Map<String, Int>> = _unreadCountsByCategory.asStateFlow()

    fun refresh() {
        _unreadCountsByCategory.value = emptyMap()
    }
}
