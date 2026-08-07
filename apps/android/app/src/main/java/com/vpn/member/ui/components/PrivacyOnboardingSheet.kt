package com.vpn.member.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyOnboardingSheet(
    visible: Boolean,
    onOpenVpnSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onFinish: (skippedSystemSettings: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible) {
        if (visible) step = 0
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                0 -> {
                    Text(text = "隐私保护已默认开启", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "跨云默认启用断网保护、IPv6 防泄露与 DNS 保护。VPN 意外断开时将阻断网络，防止真实 IP 泄露。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { step = 1 },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("下一步")
                    }
                    TextButton(
                        onClick = { onFinish(true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("跳过引导，直接连接")
                    }
                }
                else -> {
                    Text(text = "系统级加固（推荐）", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "开启「始终开启 VPN」与「阻止未使用 VPN 的连接」，并关闭电池优化，可降低后台被杀与断线泄露风险。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onOpenVpnSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("打开系统 VPN 设置")
                    }
                    OutlinedButton(
                        onClick = onOpenBatterySettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("关闭电池优化")
                    }
                    Button(
                        onClick = { onFinish(false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("完成并连接")
                    }
                    TextButton(
                        onClick = { onFinish(true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("稍后设置，直接连接")
                    }
                }
            }
        }
    }
}
