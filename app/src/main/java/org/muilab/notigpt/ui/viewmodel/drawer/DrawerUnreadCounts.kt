package org.muilab.notigpt.ui.viewmodel.drawer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller for unread-count summaries used by drawer filters and badges.
 *
 * Keep count aggregation here instead of scattering count queries through the screen. If counts become expensive,
 * this controller is the place to add throttling or cached flows.
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
