package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.debug.AppDebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DebugLogUiState(
    val entries: List<com.vpn.member.debug.AppDebugLogEntry> = emptyList(),
    val uploading: Boolean = false,
    val message: String? = null,
)

class DebugLogViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DebugLogUiState())
    val state: StateFlow<DebugLogUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            AppDebugLogger.entries.collect { entries ->
                _state.value = _state.value.copy(entries = entries)
            }
        }
    }

    fun uploadNow() {
        viewModelScope.launch {
            val snapshot = _state.value.entries
            if (snapshot.isEmpty()) return@launch
            _state.value = _state.value.copy(uploading = true, message = null)
            runCatching {
                AppDebugLogger.flush()
                repository.uploadAppDebugLogs(snapshot)
            }.onSuccess {
                _state.value = _state.value.copy(uploading = false, message = "已上报到服务器")
            }.onFailure {
                _state.value = _state.value.copy(uploading = false, message = "上报失败，请稍后重试")
            }
        }
    }
}
