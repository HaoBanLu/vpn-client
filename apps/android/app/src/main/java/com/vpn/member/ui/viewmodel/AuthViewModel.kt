package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.RegistrationConfigData
import com.vpn.member.data.auth.AuthRequestSupport
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.debug.AppDebugLogger
import com.vpn.member.push.FcmPushBootstrap
import com.vpn.member.vpn.VpnController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
    val registrationConfig: RegistrationConfigData? = null,
    val sendCooldownSeconds: Int = 0,
    val codeSending: Boolean = false,
    val infoMessage: String? = null,
    val savedEmail: String = "",
    val savedPassword: String = "",
    val rememberLogin: Boolean = true,
)

class AuthViewModel(
    private val repository: AppRepository,
    private val vpnController: VpnController,
) : ViewModel() {
    private val savedCredentials = repository.getSavedLoginCredentials()
    private val _state =
        MutableStateFlow(
            AuthUiState(
                loggedIn = repository.isLoggedIn,
                savedEmail = savedCredentials.email,
                savedPassword = savedCredentials.password,
                rememberLogin = savedCredentials.remember,
            ),
        )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        loadRegistrationConfig()
    }

    fun loadRegistrationConfig() {
        viewModelScope.launch {
            runCatching { repository.getRegistrationConfig() }
                .onSuccess { config ->
                    _state.value = _state.value.copy(registrationConfig = config, error = null)
                }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, infoMessage = null)
    }

    fun sendRegisterCode(email: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(codeSending = true, error = null, infoMessage = null)
            runAuthRequest(
                operation = "send_register_code",
                onSuccess = {
                    val cooldown = _state.value.registrationConfig?.send_cooldown_seconds ?: 60
                    _state.value = _state.value.copy(
                        codeSending = false,
                        infoMessage = "验证码已发送，请检查邮箱",
                        sendCooldownSeconds = cooldown,
                    )
                    startCooldown(cooldown)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        codeSending = false,
                        error = AuthRequestSupport.mapError(e, "发送验证码失败"),
                    )
                },
            ) {
                repository.sendEmailCode(email, "register")
            }
        }
    }

    fun sendResetCode(email: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(codeSending = true, error = null, infoMessage = null)
            runAuthRequest(
                operation = "send_reset_code",
                onSuccess = {
                    val cooldown = _state.value.registrationConfig?.send_cooldown_seconds ?: 60
                    _state.value = _state.value.copy(
                        codeSending = false,
                        infoMessage = "如果邮箱已注册，验证码将发送到您的邮箱",
                        sendCooldownSeconds = cooldown,
                    )
                    startCooldown(cooldown)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        codeSending = false,
                        error = AuthRequestSupport.mapError(e, "发送验证码失败"),
                    )
                },
            ) {
                repository.forgotPassword(email)
            }
        }
    }

    fun login(email: String, password: String, rememberLogin: Boolean, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runAuthRequest(
                operation = "login",
                onSuccess = {
                    repository.ensurePrivacyAcceptedIfLoggedIn()
                    repository.saveLoginCredentials(rememberLogin, email, password)
                    _state.value =
                        AuthUiState(
                            loggedIn = true,
                            registrationConfig = _state.value.registrationConfig,
                            savedEmail = if (rememberLogin) email else "",
                            savedPassword = if (rememberLogin) password else "",
                            rememberLogin = rememberLogin,
                        )
                    onSuccess()
                    FcmPushBootstrap.refreshAfterLogin(repository)
                },
                onFailure = { e ->
                    AppDebugLogger.warn(
                        category = "auth",
                        message = "login failed: ${e.javaClass.simpleName}",
                        context = AuthRequestSupport.buildErrorContext(e),
                    )
                    _state.value = _state.value.copy(
                        loading = false,
                        error = AuthRequestSupport.mapError(e, "登录失败"),
                    )
                },
            ) {
                repository.login(email, password)
            }
        }
    }

    fun register(email: String, password: String, emailCode: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runAuthRequest(
                operation = "register",
                onSuccess = {
                    repository.acceptPrivacy()
                    _state.value = AuthUiState(loggedIn = true, registrationConfig = _state.value.registrationConfig)
                    onSuccess()
                    FcmPushBootstrap.refreshAfterLogin(repository)
                },
                onFailure = { e ->
                    AppDebugLogger.warn(
                        category = "auth",
                        message = "register failed: ${e.javaClass.simpleName}",
                        context = AuthRequestSupport.buildErrorContext(e),
                    )
                    _state.value = _state.value.copy(
                        loading = false,
                        error = AuthRequestSupport.mapError(e, "注册失败"),
                    )
                },
            ) {
                repository.ensureNetworkAvailable()
                repository.register(email, password, emailCode)
            }
        }
    }

    fun resetPassword(email: String, emailCode: String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runAuthRequest(
                operation = "reset_password",
                onSuccess = {
                    _state.value = _state.value.copy(loading = false, infoMessage = "密码已重置，请使用新密码登录")
                    onSuccess()
                },
                onFailure = { e ->
                    AppDebugLogger.warn(
                        category = "auth",
                        message = "reset_password failed: ${e.javaClass.simpleName}",
                        context = AuthRequestSupport.buildErrorContext(e),
                    )
                    _state.value = _state.value.copy(
                        loading = false,
                        error = AuthRequestSupport.mapError(e, "重置密码失败"),
                    )
                },
            ) {
                repository.resetPassword(email, emailCode, newPassword)
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            val saved = repository.getSavedLoginCredentials()
            _state.value =
                AuthUiState(
                    loggedIn = false,
                    registrationConfig = _state.value.registrationConfig,
                    savedEmail = saved.email,
                    savedPassword = saved.password,
                    rememberLogin = saved.remember,
                )
            onDone()
        }
    }

    private suspend fun runAuthRequest(
        operation: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
        block: suspend () -> Unit,
    ) {
        AuthRequestSupport.prepareControlPlaneRequest(vpnController)
        runCatching {
            AuthRequestSupport.withRetry(
                onRetry = { attempt, err ->
                    AppDebugLogger.info(
                        category = "auth",
                        message = "$operation retry scheduled",
                        context =
                            mapOf(
                                "operation" to operation,
                                "attempt" to attempt.toString(),
                                "reason" to err.javaClass.simpleName,
                            ) + AuthRequestSupport.buildErrorContext(err),
                    )
                },
                block = block,
            )
        }
            .onSuccess { onSuccess() }
            .onFailure { e -> onFailure(e) }
    }

    private fun startCooldown(seconds: Int) {
        if (seconds <= 0) return
        viewModelScope.launch {
            var remain = seconds
            while (remain > 0) {
                _state.value = _state.value.copy(sendCooldownSeconds = remain)
                kotlinx.coroutines.delay(1000)
                remain -= 1
            }
            _state.value = _state.value.copy(sendCooldownSeconds = 0)
        }
    }
}
