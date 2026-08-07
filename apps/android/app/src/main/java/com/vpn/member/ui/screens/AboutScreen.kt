package com.vpn.member.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpn.member.BuildConfig
import com.vpn.member.data.api.ClientVersionData
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunInfoCard
import com.vpn.member.ui.components.KuayunPageScaffold
import com.vpn.member.ui.viewmodel.AboutUiState

@Composable
fun AboutScreen(
    state: AboutUiState,
    onCheckUpdate: () -> Unit,
    onStartUpdate: (ClientVersionData) -> Unit,
    pendingInstallVersion: String? = null,
    onContinueInstall: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    KuayunPageScaffold(scrollable = true, contentPadding = 20.dp) {
        KuayunBackHeader(
            title = "关于跨云",
            subtitle = "专业、安全、稳定的网络代理工具",
            onBack = onBack,
        )

        // 当前 App 信息
        KuayunInfoCard {
            Text(
                text = "当前 App",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AboutInfoRow(label = "版本名", value = BuildConfig.VERSION_NAME)
                AboutInfoRow(label = "版本码", value = BuildConfig.VERSION_CODE.toString())
            }
        }

        // 产品介绍
        KuayunInfoCard {
            Text(
                text = "产品介绍",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "跨云专注提供简洁易用的网络代理服务，帮助你更稳定地访问所需网络资源。",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "支持节点选择、一键连接、套餐订阅与流量查看，适合日常办公、学习与跨区域网络访问。",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "我们会持续优化连接体验与服务质量，让代理使用更专业、更可靠。",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 最新版本信息
        KuayunInfoCard {
            Text(
                text = "最新版本",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Button(
                onClick = onCheckUpdate,
                enabled = !state.checking,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
            ) {
                Text(if (state.checking) "检查中..." else "检查更新")
            }

            if (!pendingInstallVersion.isNullOrBlank() && onContinueInstall != null) {
                Button(
                    onClick = onContinueInstall,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                ) {
                    Text("继续安装 $pendingInstallVersion")
                }
            }

            state.checkMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            state.checkError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            state.update?.let { update ->
                Text(
                    text = if (update.force_update) "需要更新到最新版本" else "发现新版本",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AboutInfoRow(
                        label = "版本名",
                        value = update.latest_version_name?.takeIf { it.isNotBlank() } ?: "—",
                    )
                    AboutInfoRow(
                        label = "版本码",
                        value = if (update.latest_version_code > 0) {
                            update.latest_version_code.toString()
                        } else {
                            "—"
                        },
                    )
                }
                if (!update.release_notes.isNullOrBlank()) {
                    Text(
                        text = update.release_notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Button(
                    onClick = { onStartUpdate(update) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                ) {
                    Text("立即更新")
                }
            }
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
