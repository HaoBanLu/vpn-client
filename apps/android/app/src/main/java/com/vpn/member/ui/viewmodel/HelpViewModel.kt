package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HelpUiState(
    val loading: Boolean = false,
    val subscriptionUrl: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class HelpViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HelpUiState())
    val state: StateFlow<HelpUiState> = _state.asStateFlow()

    fun loadSubscriptionUrl() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, message = null)
            runCatching { repository.buildClashSubscriptionUrl() }
                .onSuccess { url ->
                    _state.value = HelpUiState(subscriptionUrl = url)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = ApiRequestSupport.mapError(e, "获取订阅链接失败"),
                    )
                }
        }
    }

    fun markCopied() {
        _state.value = _state.value.copy(message = "订阅链接已复制")
    }
}
