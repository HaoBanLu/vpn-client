package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val submitting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val success: Boolean = false,
)

class ChangePasswordViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun changePassword(oldPassword: String, newPassword: String) {
        if (oldPassword.isBlank()) {
            _state.value = _state.value.copy(error = "请填写旧密码")
            return
        }
        if (newPassword.length < 6) {
            _state.value = _state.value.copy(error = "新密码至少 6 位")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, error = null, message = null, success = false)
            runCatching { repository.changePassword(oldPassword, newPassword) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        submitting = false,
                        success = true,
                        message = "密码已修改",
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        submitting = false,
                        error = ApiRequestSupport.mapError(e, "修改密码失败"),
                    )
                }
        }
    }

    fun clearFeedback() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
