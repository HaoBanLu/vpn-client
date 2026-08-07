package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.MemberSessionItem
import com.vpn.member.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevicesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val sessions: List<MemberSessionItem> = emptyList(),
    val deviceUsed: Int = 0,
    val deviceMax: Int = 1,
    val error: String? = null,
    val toast: String? = null,
    val revokingSessionId: String? = null,
)

class DevicesViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasData = !_state.value.loading
            _state.update { it.copy(refreshing = hasData, loading = !hasData, error = null) }
            runCatching {
                val data = repository.getMySessions()
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        sessions = data.sessions,
                        deviceUsed = data.device_quota.used,
                        deviceMax = data.device_quota.max,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message ?: "加载失败",
                    )
                }
            }
        }
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            _state.update { it.copy(revokingSessionId = sessionId, error = null) }
            runCatching {
                val data = repository.revokeMySession(sessionId)
                _state.update {
                    it.copy(
                        revokingSessionId = null,
                        sessions = data.sessions,
                        deviceUsed = data.device_quota.used,
                        deviceMax = data.device_quota.max,
                        toast = "设备已移除",
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        revokingSessionId = null,
                        toast = e.message ?: "操作失败",
                    )
                }
            }
        }
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }
}
