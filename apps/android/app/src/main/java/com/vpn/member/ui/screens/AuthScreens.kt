package com.vpn.member.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vpn.member.ui.components.KuayunBrandHeader
import com.vpn.member.ui.components.KuayunScreenBackground
import com.vpn.member.ui.viewmodel.AuthUiState

@Composable
fun LoginScreen(
    state: AuthUiState,
    bannerMessage: String? = null,
    onLogin: (String, String, Boolean) -> Unit,
    onNavigateRegister: () -> Unit,
    onNavigateForgotPassword: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf(state.savedEmail) }
    var password by rememberSaveable { mutableStateOf(state.savedPassword) }
    var rememberLogin by rememberSaveable { mutableStateOf(state.rememberLogin) }
    AuthForm(
        title = "欢迎回来",
        subtitle = "登录跨云，开启安全加速",
        email = email,
        password = password,
        loading = state.loading,
        error = state.error,
        infoMessage = state.infoMessage,
        bannerMessage = bannerMessage,
        onEmailChange = { email = it },
        onPasswordChange = { password = it },
        showPasswordToggle = true,
        rememberLogin = rememberLogin,
        onRememberLoginChange = { rememberLogin = it },
        primaryText = "登录",
        onPrimary = { onLogin(email, password, rememberLogin) },
        secondaryText = "没有账号？去注册",
        onSecondary = onNavigateRegister,
        extraFooter = {
            TextButton(onClick = onNavigateForgotPassword) {
                Text("忘记密码？")
            }
        },
    )
}

@Composable
fun RegisterScreen(
    state: AuthUiState,
    onRegister: (String, String, String?) -> Unit,
    onSendCode: (String) -> Unit,
    onNavigateLogin: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var emailCode by rememberSaveable { mutableStateOf("") }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    val requireEmailCode = state.registrationConfig?.email_verification_required == true
    AuthForm(
        title = "创建账户",
        subtitle = "注册跨云，畅享全球加速",
        email = email,
        password = password,
        loading = state.loading,
        error = state.error,
        infoMessage = state.infoMessage,
        onEmailChange = { email = it },
        onPasswordChange = { password = it },
        primaryText = "注册",
        primaryEnabled = acceptedTerms && state.registrationConfig?.registration_enabled != false,
        onPrimary = {
            onRegister(email, password, if (requireEmailCode) emailCode else null)
        },
        secondaryText = "已有账号？去登录",
        onSecondary = onNavigateLogin,
        extraContent = {
            if (requireEmailCode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = emailCode,
                        onValueChange = { emailCode = it },
                        label = { Text("邮箱验证码") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { onSendCode(email) },
                        enabled = !state.codeSending && state.sendCooldownSeconds == 0,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    ) {
                        Text(
                            if (state.sendCooldownSeconds > 0) "${state.sendCooldownSeconds}s" else "发送验证码",
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = acceptedTerms, onCheckedChange = { acceptedTerms = it })
                Text(text = "我已阅读并同意服务条款与隐私政策", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
fun ForgotPasswordScreen(
    state: AuthUiState,
    onSendCode: (String) -> Unit,
    onResetPassword: (String, String, String) -> Unit,
    onNavigateLogin: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var emailCode by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    AuthForm(
        title = "找回密码",
        subtitle = "通过邮箱验证码重置登录密码",
        email = email,
        password = newPassword,
        loading = state.loading,
        error = state.error,
        infoMessage = state.infoMessage,
        onEmailChange = { email = it },
        onPasswordChange = { newPassword = it },
        emailLabel = "邮箱",
        passwordLabel = "新密码",
        primaryText = "重置密码",
        primaryEnabled = state.registrationConfig?.password_reset_enabled != false &&
            newPassword.length >= 6 &&
            newPassword == confirmPassword &&
            emailCode.isNotBlank(),
        onPrimary = { onResetPassword(email, emailCode, newPassword) },
        secondaryText = "返回登录",
        onSecondary = onNavigateLogin,
        extraContent = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = emailCode,
                    onValueChange = { emailCode = it },
                    label = { Text("邮箱验证码") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { onSendCode(email) },
                    enabled = !state.codeSending && state.sendCooldownSeconds == 0,
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    Text(
                        if (state.sendCooldownSeconds > 0) "${state.sendCooldownSeconds}s" else "发送验证码",
                    )
                }
            }
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("确认新密码") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        },
    )
}

@Composable
private fun AuthForm(
    title: String,
    subtitle: String,
    email: String,
    password: String,
    loading: Boolean,
    error: String?,
    infoMessage: String? = null,
    bannerMessage: String? = null,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    emailLabel: String = "邮箱",
    passwordLabel: String = "密码",
    showPasswordToggle: Boolean = false,
    rememberLogin: Boolean? = null,
    onRememberLoginChange: ((Boolean) -> Unit)? = null,
    primaryText: String,
    primaryEnabled: Boolean = true,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
    extraFooter: (@Composable () -> Unit)? = null,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    KuayunScreenBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KuayunBrandHeader(title = title, subtitle = subtitle, showVersion = true)
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (bannerMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                        ) {
                            Text(
                                text = bannerMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        label = { Text(emailLabel) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text(passwordLabel) },
                        visualTransformation =
                            if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        trailingIcon =
                            if (showPasswordToggle) {
                                {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector =
                                                if (passwordVisible) {
                                                    Icons.Filled.VisibilityOff
                                                } else {
                                                    Icons.Filled.Visibility
                                                },
                                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    if (rememberLogin != null && onRememberLoginChange != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = rememberLogin,
                                onCheckedChange = onRememberLoginChange,
                            )
                            Text(
                                text = "记住账号密码",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    extraContent?.invoke()
                    if (infoMessage != null) {
                        Text(
                            text = infoMessage,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (error != null) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Button(
                        onClick = onPrimary,
                        enabled = !loading && primaryEnabled,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) {
                        if (loading) CircularProgressIndicator() else Text(primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
            TextButton(onClick = onSecondary, modifier = Modifier.padding(top = 8.dp)) {
                Text(secondaryText)
            }
            extraFooter?.invoke()
        }
    }
}
