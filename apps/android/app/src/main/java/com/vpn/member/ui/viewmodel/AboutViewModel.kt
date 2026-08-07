package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.ClientVersionData
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AboutUiState(
    val checking: Boolean = false,
    val checkMessage: String? = null,
    val update: ClientVersionData? = null,
    val checkError: String? = null,
)

class AboutViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AboutUiState())
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    fun checkUpdate() {
        viewModelScope.launch {
            _state.value = AboutUiState(checking = true)
            runCatching { repository.checkForUpdateAndRecord() }
                .onSuccess { update ->
                    _state.value = if (update.has_update || update.force_update) {
                        AboutUiState(update = update)
                    } else {
                        AboutUiState(checkMessage = "当前已是最新版本")
                    }
                }
                .onFailure { error ->
                    _state.value = AboutUiState(
                        checkError = ApiRequestSupport.mapError(error, "检查更新失败，请稍后重试"),
                    )
                }
        }
    }

    fun clearCheckResult() {
        _state.value = AboutUiState()
    }
}
