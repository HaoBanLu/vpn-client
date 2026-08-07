package com.vpn.member.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpn.member.BuildConfig
import com.vpn.member.ui.components.ConnectAccountBar
import com.vpn.member.ui.components.CurrentSubscriptionCard
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunInfoCard
import com.vpn.member.ui.components.KuayunPageScaffold
import com.vpn.member.ui.components.KuayunPullRefreshBox
import com.vpn.member.ui.components.KuayunScreenBackground
import com.vpn.member.ui.components.ProfileMenuEntry
import com.vpn.member.ui.components.ProfileMenuPanel
import androidx.compose.material3.Button
import com.vpn.member.ui.viewmodel.AppNotification
import com.vpn.member.ui.viewmodel.HelpUiState
import com.vpn.member.ui.viewmodel.ProfileUiState

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onRecharge: () -> Unit,
    onRechargeOrders: () -> Unit,
    onPurchaseOrders: () -> Unit,
    onChangePassword: () -> Unit,
    onTraffic: () -> Unit,
    onAbout: () -> Unit,
    onTickets: () -> Unit,
    onSupport: () -> Unit,
    onNavigateDebugLog: (() -> Unit)? = null,
    onNavigatePackages: () -> Unit,
    isVip: Boolean = false,
    expiresAt: String? = null,
    connectionScenarioLabel: String? = null,
    onNavigateDevices: (() -> Unit)? = null,
    onNavigateAppDirectConnect: (() -> Unit)? = null,
    onNavigateDirectBypassRule: (() -> Unit)? = null,
    onNavigateStabilitySettings: (() -> Unit)? = null,
    onOpenConnectionScenario: (() -> Unit)? = null,
    isLoggedIn: Boolean = false,
    accountFallbackEmail: String? = null,
    modifier: Modifier = Modifier,
) {
    val recentNotifications = state.notifications.takeLast(2).reversed()

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
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {

                if (state.user != null || isLoggedIn) {
                    ConnectAccountBar(
                        accountName = state.user?.email ?: accountFallbackEmail,
                        isVip = isVip || state.subscription != null,
                        expiresAt = expiresAt ?: state.subscription?.expires_at,
                        connectionScenarioLabel = connectionScenarioLabel,
                        onViewDevices = { onNavigateDevices?.invoke() },
                        onRecharge = onRecharge,
                        onChangePassword = onChangePassword,
                        onConnectionScenario = { onOpenConnectionScenario?.invoke() },
                        onLogout = onLogout,
                    )
                }

                recentNotifications.forEach { notification ->
                    NotificationCard(notification = notification, onClick = onRechargeOrders)
                }

                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    CurrentSubscriptionCard(
                        subscription = state.subscription,
                        usage = state.usage,
                        onNavigatePackages = onNavigatePackages,
                        onTraffic = onTraffic,
                    )

                    val balanceMeta = state.user?.let { "余额 ¥${"%.2f".format(it.balance)}" }
                    ProfileMenuPanel(
                        title = "账户",
                        headerMeta = balanceMeta,
                        entries =
                            listOf(
                                ProfileMenuEntry(
                                    title = "USDT 充值",
                                    subtitle = "余额充值与到账",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    onClick = onRecharge,
                                ),
                                ProfileMenuEntry(
                                    title = "充值订单",
                                    subtitle = "USDT 充值记录与状态",
                                    icon = Icons.Default.ReceiptLong,
                                    onClick = onRechargeOrders,
                                ),
                                ProfileMenuEntry(
                                    title = "购买记录",
                                    subtitle = "套餐订单与支付状态",
                                    icon = Icons.Default.ShoppingBag,
                                    onClick = onPurchaseOrders,
                                ),
                            ),
                    )

                    val directConnectSubtitle =
                        if (state.directConnectCount > 0) {
                            "已选 ${state.directConnectCount} 个应用直连"
                        } else {
                            "指定应用不走 VPN，其余默认加速"
                        }
                    val directBypassSubtitle =
                        if (state.directBypassRuleCount > 0) {
                            "已启用 ${state.directBypassRuleCount} 条规则直连"
                        } else {
                            "指定域名或 IP 不经代理节点"
                        }
                    ProfileMenuPanel(
                        title = "连接设置",
                        entries =
                            listOf(
                                ProfileMenuEntry(
                                    title = "应用直连",
                                    subtitle = directConnectSubtitle,
                                    icon = Icons.Default.Apps,
                                    onClick = { onNavigateAppDirectConnect?.invoke() },
                                ),
                                ProfileMenuEntry(
                                    title = "规则直连",
                                    subtitle = directBypassSubtitle,
                                    icon = Icons.Default.Public,
                                    onClick = { onNavigateDirectBypassRule?.invoke() },
                                ),
                                ProfileMenuEntry(
                                    title = "连接与隐私",
                                    subtitle = "防泄露保护、稳定性与隐私检测",
                                    icon = Icons.Default.Shield,
                                    onClick = { onNavigateStabilitySettings?.invoke() },
                                ),
                            ),
                    )

                    val supportEntries = buildList {
                        if (state.supportEnabled) {
                            add(
                                ProfileMenuEntry(
                                    title = "在线客服",
                                    subtitle = "Telegram、群组与人工协助",
                                    icon = Icons.Default.SupportAgent,
                                    onClick = onSupport,
                                ),
                            )
                        }
                        add(
                            ProfileMenuEntry(
                                title = "我的工单",
                                subtitle = "问题反馈与客服回复",
                                icon = Icons.Default.TaskAlt,
                                onClick = onTickets,
                            ),
                        )
                        if (state.user?.app_debug_enabled == true) {
                            add(
                                ProfileMenuEntry(
                                    title = "诊断日志",
                                    subtitle = "连接问题排查与上报",
                                    icon = Icons.Default.BugReport,
                                    onClick = { onNavigateDebugLog?.invoke() },
                                ),
                            )
                        }
                        add(
                            ProfileMenuEntry(
                                title = "关于跨云",
                                subtitle = "v${BuildConfig.VERSION_NAME} · code ${BuildConfig.VERSION_CODE}",
                                icon = Icons.Default.Info,
                                onClick = onAbout,
                            ),
                        )
                    }
                    ProfileMenuPanel(
                        title = "帮助与支持",
                        entries = supportEntries,
                    )
                }

                state.message?.let { Text(text = it, color = MaterialTheme.colorScheme.primary) }
                state.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when (notification.type) {
                "paid" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                "rejected" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(notification.message, fontWeight = FontWeight.SemiBold)
            Text(
                notification.orderNo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun HelpScreen(
    state: HelpUiState,
    onLoadSubscriptionUrl: () -> Unit,
    onCopied: () -> Unit,
    onBack: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    KuayunPageScaffold(scrollable = true, contentPadding = 20.dp) {
        KuayunBackHeader(
            title = "帮助中心",
            subtitle = "若 App 无法连接，可导出订阅链接在第三方客户端使用",
            onBack = onBack,
        )
        Button(
            onClick = onLoadSubscriptionUrl,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) CircularProgressIndicator() else Text("生成 Clash 订阅链接")
        }
        state.subscriptionUrl?.let { url ->
            KuayunInfoCard {
                Text(text = url, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(url))
                    onCopied()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("复制订阅链接")
            }
        }
        state.message?.let { Text(text = it) }
        state.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
    }
}
