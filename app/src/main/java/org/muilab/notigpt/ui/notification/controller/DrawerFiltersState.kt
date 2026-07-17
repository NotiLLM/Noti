package org.muilab.notigpt.ui.notification.controller

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mutable filter/search/sort state for the notification drawer ViewModel.
 *
 * Keep this as UI-query state only. Persisted notification ordering and read state should stay in
 * their database-backed controllers instead of being hidden in filters.
 */
@Immutable
internal class DrawerFiltersState {

    private val _isTargetLoading = MutableStateFlow(false)
    val isTargetLoading: StateFlow<Boolean> = _isTargetLoading.asStateFlow()

    private val _targetLoadingToken = MutableStateFlow(0L)

    private val _isSortingMode = MutableStateFlow(false)
    val isSortingMode: StateFlow<Boolean> = _isSortingMode.asStateFlow()

    fun toggleSortingMode() {
        _isSortingMode.value = !_isSortingMode.value
    }

    fun startTargetLoading() {
        _isTargetLoading.value = true
        _targetLoadingToken.value = System.currentTimeMillis()
    }

    fun clearTargetLoading() {
        _isTargetLoading.value = false
        _targetLoadingToken.value = 0L
    }

    fun shouldClearTargetLoading(): Boolean {
        return _targetLoadingToken.value != 0L || _isTargetLoading.value
    }

}
