package com.vpn.member.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpn.member.data.api.OrderItem
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunListCard
import com.vpn.member.ui.components.KuayunPullRefreshBox
import com.vpn.member.ui.components.KuayunScreenBackground
import com.vpn.member.ui.components.KuayunStateBlock
import com.vpn.member.ui.viewmodel.PurchaseOrdersUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PurchaseOrdersScreen(
    state: PurchaseOrdersUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KuayunScreenBackground(modifier = modifier.fillMaxSize()) {
        KuayunPullRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    KuayunBackHeader(
                        title = "购买记录",
                        subtitle = "套餐购买、续费与支付状态",
                        onBack = onBack,
                    )
                }
                item {
                    KuayunStateBlock(
                        loading = state.loading,
                        empty = !state.loading && state.orders.isEmpty(),
                        emptyMessage = "暂无购买记录",
                        error = state.error,
                    )
                }
                if (!state.loading && state.orders.isNotEmpty()) {
                    items(state.orders) { order ->
                        PurchaseOrderCard(order = order)
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseOrderCard(order: OrderItem) {
    KuayunListCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "订单 #${order.id}", fontWeight = FontWeight.Bold)
            Text(
                text = orderStatusLabel(order.status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "¥${"%.2f".format(order.amount)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "支付方式：${order.payment_method ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        order.created_at?.let {
            Text(
                text = "创建时间：${formatOrderTime(it)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun orderStatusLabel(status: String): String =
    when (status) {
        "paid" -> "已支付"
        "pending" -> "待支付"
        "cancelled" -> "已取消"
        "refunded" -> "已退款"
        else -> status
    }

private fun formatOrderTime(raw: String): String =
    runCatching {
        val instant = Instant.parse(raw)
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrElse { raw.take(16).replace("T", " ") }
