package com.vpn.member.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunPageScaffold
import com.vpn.member.ui.components.ProtectionStatusBar
import com.vpn.member.ui.viewmodel.StabilitySettingsUiState
import com.vpn.member.vpn.ProtectionLevel
import com.vpn.member.vpn.PrivacyProbeHistoryStore
import com.vpn.member.vpn.TunStackMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StabilitySettingsScreen(
    state: StabilitySettingsUiState,
    onBack: () -> Unit,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onBootAutoConnectChanged: (Boolean) -> Unit,
    onTunStackModeChanged: (String) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenVpnSettings: () -> Unit,
    onRunPrivacyProbe: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onBlockOnConnectFailureChanged: (Boolean) -> Unit,
    onRequestDisableKillSwitch: () -> Unit,
    onConfirmDisableKillSwitch: () -> Unit,
    onDismissDisableKillSwitchConfirm: () -> Unit,
    onDismissToast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.toastMessage, state.privacyProbeMessage) {
        if (state.toastMessage != null || state.privacyProbeMessage != null) {
            kotlinx.coroutines.delay(3500)
            onDismissToast()
        }
    }

    val protectionLevel =
        runCatching { ProtectionLevel.valueOf(state.protectionLevelName) }
            .getOrDefault(ProtectionLevel.BASELINE_READY)

    val systemHardeningDone =
        listOf(
            state.alwaysOnConfigured,
            state.lockdownConfigured,
            state.batteryOptimizationIgnored,
        ).count { it }

    if (state.showDisableKillSwitchConfirm) {
        AlertDialog(
            onDismissRequest = onDismissDisableKillSwitchConfirm,
            title = { Text("关闭断网保护？") },
            text = {
                Text("关闭后，VPN 意外断开时可能暴露真实 IP。我了解可能泄露真实 IP。")
            },
            confirmButton = {
                TextButton(onClick = onConfirmDisableKillSwitch) { Text("仍要关闭") }
            },
            dismissButton = {
                TextButton(onClick = onDismissDisableKillSwitchConfirm) { Text("取消") }
            },
        )
    }

    KuayunPageScaffold(modifier = modifier, scrollable = true, contentPadding = 20.dp) {
        KuayunBackHeader(
            title = "连接与隐私",
            subtitle = "防泄露默认开启；可调整重连与系统加固",
            onBack = onBack,
        )

        // 区块 A：当前状态 + 隐私检测
        SettingsSectionCard {
            when {
                // 未连接：不强推「保护未完整」，避免与灰按钮「请先连接」抢主任务
                !state.vpnConnected -> {
                    ProtectionStatusBar(
                        level = ProtectionLevel.BASELINE_READY,
                        label = "未连接 · 防泄露将在连接后生效",
                    )
                    if (state.protectionIncomplete) {
                        Text(
                            text = "系统加固 $systemHardeningDone/3（可选加强）",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                state.protectionIncomplete -> {
                    ProtectionStatusBar(
                        level = ProtectionLevel.DEGRADED,
                        label = "保护未完整",
                    )
                    Text(
                        text = "请完成下方「系统级加固」待办项",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                else -> {
                    ProtectionStatusBar(level = protectionLevel, label = state.protectionLabel)
                }
            }
            if (state.directConnectCount > 0 || state.directBypassRuleCount > 0) {
                Text(
                    text = "已降低保护：应用直连 ${state.directConnectCount} 个 · 规则直连 ${state.directBypassRuleCount} 条",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            OutlinedButton(
                onClick = onRunPrivacyProbe,
                enabled = state.vpnConnected && !state.privacyProbeRunning,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            ) {
                Text(
                    when {
                        state.privacyProbeRunning -> "正在检测…"
                        state.vpnConnected -> "立即隐私检测"
                        else -> "请先连接 VPN 后再检测"
                    },
                )
            }
            state.privacyProbeMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.privacyProbeHistory.firstOrNull()?.let { entry: PrivacyProbeHistoryStore.Entry ->
                Text(
                    text = "最近检测",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "${formatProbeTime(entry.atMillis)} · ${memberFacingProbeSummary(entry)}",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (entry.passed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }
        }

        // 区块 B：连接设置
        SettingsSectionCard(title = "连接设置") {
            SettingRow(
                title = "断网自动重连",
                subtitle = "网络恢复后最多尝试 3 次自动连接",
                checked = state.autoReconnectEnabled,
                onCheckedChange = onAutoReconnectChanged,
            )
            SettingRow(
                title = "开机自动恢复连接",
                subtitle = "设备重启后尝试恢复上次连接（需已登录）",
                checked = state.bootAutoConnectEnabled,
                onCheckedChange = onBootAutoConnectChanged,
            )
        }

        // 区块 C：系统级加固
        SettingsSectionCard(
            title = "系统级加固",
            subtitle = "已完成 $systemHardeningDone / 3 项",
        ) {
            if (state.alwaysOnConfigured && state.lockdownConfigured && state.batteryOptimizationIgnored) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "系统加固已完成",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                PrivacyTodoRow(
                    title = "始终开启 VPN",
                    subtitle = "断网后由系统自动重新拉起 VPN",
                    done = state.alwaysOnConfigured,
                    onClick = onOpenVpnSettings,
                )
                PrivacyTodoRow(
                    title = "禁止绕过 VPN",
                    subtitle = "未走 VPN 时禁止上网，降低 IP 泄露",
                    done = state.lockdownConfigured,
                    onClick = onOpenVpnSettings,
                )
                PrivacyTodoRow(
                    title = "关闭电池优化",
                    subtitle = "避免后台被系统杀掉导致掉线",
                    done = state.batteryOptimizationIgnored,
                    onClick = onOpenBatterySettings,
                )
            }
            OutlinedButton(
                onClick = onOpenVpnSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("打开系统 VPN 设置")
            }
        }

        OutlinedButton(onClick = onToggleAdvanced, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.showAdvanced) "收起高级设置" else "展开高级设置")
        }
        if (state.showAdvanced) {
            SettingsSectionCard(title = "高级") {
                state.connectTimingSummary?.takeIf { it.count > 0 }?.let { timing ->
                    Text(
                        text = "本机首连耗时（最近 ${timing.count} 次）",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text =
                            buildString {
                                timing.p50Ms?.let { append("P50 ${it}ms · ") }
                                timing.p95Ms?.let { append("P95 ${it}ms · ") }
                                append("≤5s 达标 ${(timing.kpiMetRate * 100).toInt()}%")
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text = "TUN 网络栈", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "回国全流量未手动设置时默认 gvisor；部分机型 system 栈可能无法转发流量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.tunStackAutoSwitchNote?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.tunStackMode == TunStackMode.GVISOR,
                        onClick = { onTunStackModeChanged(TunStackMode.GVISOR) },
                        label = { Text("gvisor（推荐）") },
                    )
                    FilterChip(
                        selected = state.tunStackMode == TunStackMode.SYSTEM,
                        onClick = { onTunStackModeChanged(TunStackMode.SYSTEM) },
                        label = { Text("system") },
                    )
                }
                SettingRow(
                    title = "连接失败时阻断网络",
                    subtitle = "默认关闭。开启后节点探测失败会断网；弱网下可能误伤",
                    checked = state.blockOnConnectFailureEnabled,
                    onCheckedChange = onBlockOnConnectFailureChanged,
                )
                if (state.killSwitchEnabled) {
                    OutlinedButton(
                        onClick = onRequestDisableKillSwitch,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("关闭断网保护（不推荐）")
                    }
                }
            }
        }
        state.toastMessage?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                if (!title.isNullOrBlank()) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            },
        )
    }
}

@Composable
private fun PrivacyTodoRow(
    title: String,
    subtitle: String,
    done: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (!done) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint =
                    if (done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!done) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "去设置",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun formatProbeTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

/** 列表展示用白话摘要；兼容历史条目中的工程向 summary。 */
private fun memberFacingProbeSummary(entry: PrivacyProbeHistoryStore.Entry): String =
    if (entry.passed) {
        "已通过"
    } else {
        "未通过 · 可能泄露真实网络信息"
    }
