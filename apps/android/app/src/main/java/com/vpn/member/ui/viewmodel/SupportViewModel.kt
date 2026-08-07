package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.SupportConfigData
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SupportUiState(
    val loading: Boolean = true,
    val config: SupportConfigData? = null,
    val error: String? = null,
)

class SupportViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SupportUiState())
    val state: StateFlow<SupportUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.getSupportConfig() }
                .onSuccess { config ->
                    _state.value = SupportUiState(
                        loading = false,
                        config = config,
                    )
                }
                .onFailure { e ->
                    _state.value = SupportUiState(
                        loading = false,
                        error = ApiRequestSupport.mapError(e, "加载客服配置失败"),
                    )
                }
        }
    }
}
