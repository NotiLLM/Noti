package org.muilab.notigpt.ui.viewmodel.drawer

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_ALL
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL

/**
 * Mutable filter/search/sort state for the notification drawer ViewModel.
 *
 * Keep this as UI-query state only. Persisted notification ordering, grouping, and read state should stay in
 * their database-backed controllers instead of being hidden in filters.
 */
@Immutable
internal class DrawerFiltersState {

    private val _category = MutableStateFlow(NOTI_CATEGORY_GENERAL)
    val category: StateFlow<String> = _category.asStateFlow()

    private val _appCategory = MutableStateFlow(APP_CATEGORY_ALL)
    val appCategory: StateFlow<String> = _appCategory.asStateFlow()

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

    fun setCategory(newCategory: String) {
        _category.value = newCategory
    }

    fun setAppCategory(newAppCategory: String) {
        _appCategory.value = newAppCategory
    }

    fun resetAppCategoryToAll() {
        _appCategory.value = APP_CATEGORY_ALL
    }
}

