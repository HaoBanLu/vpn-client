package com.vpn.member.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunInfoCard
import com.vpn.member.ui.components.KuayunPageScaffold
import com.vpn.member.ui.viewmodel.ChangePasswordUiState

@Composable
fun ChangePasswordScreen(
    state: ChangePasswordUiState,
    onChangePassword: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var oldPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.success) {
        if (state.success) {
            oldPassword = ""
            newPassword = ""
        }
    }

    KuayunPageScaffold(modifier = modifier, scrollable = true) {
        KuayunBackHeader(
            title = "修改密码",
            subtitle = "定期更新密码可提升账户安全",
            onBack = onBack,
        )
        KuayunInfoCard {
            Text("请输入旧密码和新密码", fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                label = { Text("旧密码") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("新密码") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                "新密码至少 6 位",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = { onChangePassword(oldPassword, newPassword) },
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator()
                } else {
                    Text("保存新密码")
                }
            }
        }
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
