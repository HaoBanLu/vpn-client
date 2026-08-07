package com.vpn.member.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpn.member.data.api.PackageItem
import com.vpn.member.data.api.SubscriptionActive
import com.vpn.member.ui.components.CurrentSubscriptionSummaryBar
import com.vpn.member.ui.components.KuayunMainTabBrandHeader
import com.vpn.member.ui.components.KuayunPullRefreshBox
import com.vpn.member.ui.isCurrentPackage
import com.vpn.member.ui.purchaseButtonState
import com.vpn.member.ui.components.KuayunScreenBackground
import com.vpn.member.ui.components.KuayunStatChip
import com.vpn.member.ui.components.KuayunStatusBadge
import com.vpn.member.ui.viewmodel.PackagesUiState

@Composable
fun PackagesScreen(
    state: PackagesUiState,
    onRefresh: () -> Unit,
    onPurchase: (Long) -> Unit,
    onInsufficientBalance: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    KuayunScreenBackground(modifier = modifier.fillMaxSize()) {
        KuayunPullRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
        ) {
            if (state.loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        KuayunMainTabBrandHeader(
                            title = "加速套餐",
                            subtitle = "选择适合你的流量方案，余额支付即时生效",
                        )
                    }
                    state.subscription?.let { sub ->
                        item {
                            CurrentSubscriptionSummaryBar(
                                subscription = sub,
                                usage = state.usage,
                            )
                        }
                    }
                    itemsIndexed(state.packages) { index, pkg ->
                        PackageCard(
                            pkg = pkg,
                            subscription = state.subscription,
                            recommended = index == 0 && state.subscription == null,
                            paying = state.paying,
                            userBalance = state.user?.balance ?: 0.0,
                            onPurchase = onPurchase,
                            onInsufficientBalance = onInsufficientBalance,
                        )
                    }
                    state.message?.let { message ->
                        item {
                            Text(
                                text = message,
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    state.error?.let { error ->
                        item {
                            Text(
                                text = error,
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageCard(
    pkg: PackageItem,
    subscription: SubscriptionActive?,
    recommended: Boolean,
    paying: Boolean,
    userBalance: Double,
    onPurchase: (Long) -> Unit,
    onInsufficientBalance: () -> Unit,
) {
    val isCurrent = isCurrentPackage(subscription, pkg)
    val buttonState = purchaseButtonState(subscription, pkg, userBalance, paying)
    val badgeText =
        when {
            isCurrent -> "当前套餐"
            recommended -> "推荐"
            else -> null
        }
    val badgeColor =
        if (isCurrent) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        recommended -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = pkg.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                badgeText?.let {
                    KuayunStatusBadge(text = it, color = badgeColor)
                }
            }
            Text(
                text = "¥${"%.2f".format(pkg.price)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            pkg.description?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KuayunStatChip(label = "时长", value = "${pkg.duration_days} 天")
                KuayunStatChip(label = "流量", value = "${pkg.traffic_gb.toInt()} GB", highlight = true)
            }
            Button(
                onClick = {
                    if (buttonState.insufficientBalance) {
                        onInsufficientBalance()
                    } else {
                        onPurchase(pkg.id)
                    }
                },
                enabled = buttonState.enabled,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    if (paying) {
                        "处理中…"
                    } else {
                        buttonState.label.text
                    },
                )
            }
        }
    }
}
