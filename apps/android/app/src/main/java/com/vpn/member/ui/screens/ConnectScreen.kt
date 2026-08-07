package com.vpn.member.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vpn.member.ui.components.ConnectHero
import com.vpn.member.ui.components.ConnectNodeDetailCard
import com.vpn.member.ui.components.ConnectQuickStatus
import com.vpn.member.ui.components.KuayunInfoCard
import com.vpn.member.ui.components.KuayunMainTabBrandHeader
import com.vpn.member.ui.components.KuayunPullRefreshBox
import com.vpn.member.ui.components.KuayunScreenBackground
import com.vpn.member.ui.components.PrivacyOnboardingSheet
import com.vpn.member.ui.components.resolveConnectHeroCopy
import com.vpn.member.ui.viewmodel.ConnectUiState
import com.vpn.member.vpn.ConnectionState

@Composable
fun ConnectScreen(
    state: ConnectUiState,
    entryLatencyMs: Int? = null,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onBuy: () -> Unit,
    onNavigateNodes: (() -> Unit)? = null,
    onDismissPrivacyOnboarding: () -> Unit = {},
    onOpenPrivacyOnboardingVpnSettings: () -> Unit = {},
    onOpenPrivacyOnboardingBatterySettings: () -> Unit = {},
    onCompletePrivacyOnboarding: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PrivacyOnboardingSheet(
        visible = state.showPrivacyOnboarding,
        onOpenVpnSettings = onOpenPrivacyOnboardingVpnSettings,
        onOpenBatterySettings = onOpenPrivacyOnboardingBatterySettings,
        onFinish = onCompletePrivacyOnboarding,
        onDismiss = onDismissPrivacyOnboarding,
    )

    val connected = state.connectionState == ConnectionState.CONNECTED
    val columnGap = if (connected) 10.dp else 12.dp

    KuayunScreenBackground(modifier = modifier.fillMaxSize()) {
        KuayunPullRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(columnGap),
            ) {
                KuayunMainTabBrandHeader(
                    title = "连接",
                )

                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    val sub = state.subscription
                    if (sub == null) {
                        KuayunInfoCard {
                            Text(text = "暂无有效套餐")
                            Text(
                                text = "购买套餐后即可使用跨云加速服务",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        val heroCopy =
                            resolveConnectHeroCopy(
                                connectionState = ConnectionState.DISCONNECTED,
                                connectPending = false,
                                isSwitching = false,
                                connectedNodeName = null,
                                selectedNode = null,
                            ).copy(buttonLabel = "购买套餐")
                        ConnectHero(copy = heroCopy, onClick = onBuy)
                    } else {
                        state.renewalHint?.let { hint ->
                            Card(
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                    ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = hint,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }

                        val heroCopy =
                            resolveConnectHeroCopy(
                                connectionState = state.connectionState,
                                connectPending = state.connectPending,
                                isSwitching = state.isSwitching,
                                connectedNodeName = state.connectedNodeName,
                                selectedNode = state.selectedNode,
                                tunnelLatencyMs = state.nodeProbeLatencyMs,
                                entryLatencyMs = entryLatencyMs,
                            )
                        val heroOnClick =
                            when {
                                connected || heroCopy.connecting -> onDisconnect
                                else -> onConnect
                            }
                        ConnectHero(
                            copy = heroCopy,
                            onClick = heroOnClick,
                        )

                        if (connected) {
                            ConnectNodeDetailCard(
                                connected = true,
                                sessionUploadBytes = state.sessionUploadBytes,
                                sessionDownloadBytes = state.sessionDownloadBytes,
                                sessionDurationMs = state.sessionDurationMs,
                                sessionUploadBps = state.sessionUploadBps,
                                sessionDownloadBps = state.sessionDownloadBps,
                                remainingGb = state.usage?.remaining,
                                expiresAt = sub.expires_at,
                                nodeName = state.connectedNodeName ?: state.selectedNode,
                                onSwitchNode =
                                    onNavigateNodes?.let { navigate ->
                                        { navigate() }
                                    },
                            )
                        } else {
                            ConnectQuickStatus(
                                selectedNode = state.selectedNode,
                                connectionState = state.connectionState,
                                remainingGb = state.usage?.remaining,
                                expiresAt = sub.expires_at,
                                onPickNode = { onNavigateNodes?.invoke() },
                            )
                        }
                    }
                }

                state.error?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = err,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (state.connectionState == ConnectionState.FAILED && state.subscription != null) {
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                        Text("重试连接")
                    }
                }
            }
        }
    }
}
