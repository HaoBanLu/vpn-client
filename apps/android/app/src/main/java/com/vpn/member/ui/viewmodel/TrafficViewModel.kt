package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.DailyTrafficItem
import com.vpn.member.data.api.TrafficSummary
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrafficUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val summary: TrafficSummary? = null,
    val daily: List<DailyTrafficItem> = emptyList(),
    val error: String? = null,
)

class TrafficViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TrafficUiState())
    val state: StateFlow<TrafficUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasData = _state.value.summary != null
            _state.value = _state.value.copy(
                refreshing = hasData,
                loading = !hasData,
                error = null,
            )
            runCatching {
                val summary = repository.getTrafficSummary()
                val daily = repository.getTrafficDaily()
                _state.value = TrafficUiState(
                    loading = false,
                    refreshing = false,
                    summary = summary,
                    daily = daily,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = ApiRequestSupport.mapError(e, "加载流量统计失败"),
                )
            }
        }
    }
}
