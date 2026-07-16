package org.muilab.notigpt.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.muilab.notigpt.data.repository.DataDeletionRepository

/** Coordinates confirmed settings-page deletions without placing storage/network work in Compose. */
@HiltViewModel
class DataDeletionViewModel @Inject constructor(
    private val repository: DataDeletionRepository,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Working : State
        data class Finished(val target: Target) : State
        data class Failed(val target: Target) : State
    }

    enum class Target { CloudGeneratedData, LocalNotificationHistory }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun delete(target: Target) {
        if (_state.value == State.Working) return
        viewModelScope.launch {
            _state.value = State.Working
            val result = runCatching {
                when (target) {
                    Target.CloudGeneratedData ->
                        repository.deleteCloudAndLocalGeneratedData().getOrThrow()
                    Target.LocalNotificationHistory ->
                        repository.clearLocalNotificationHistory()
                }
            }
            _state.value = result.fold(
                onSuccess = { State.Finished(target) },
                onFailure = { State.Failed(target) },
            )
        }
    }

    fun consumeResult() {
        if (_state.value != State.Working) _state.value = State.Idle
    }
}
