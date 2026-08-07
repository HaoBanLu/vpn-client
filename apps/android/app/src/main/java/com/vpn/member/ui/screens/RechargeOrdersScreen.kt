package com.vpn.member.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vpn.member.BuildConfig
import com.vpn.member.data.api.RechargeOrderItem
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunListCard
import com.vpn.member.ui.components.KuayunPullRefreshBox
import com.vpn.member.ui.components.KuayunScreenBackground
import com.vpn.member.ui.components.KuayunStateBlock
import com.vpn.member.ui.viewmodel.RechargeOrdersUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URL

@Composable
fun RechargeOrdersScreen(
    state: RechargeOrdersUiState,
    onRefresh: () -> Unit,
    onSelectOrder: (RechargeOrderItem?) -> Unit,
    onRechargeAgain: () -> Unit,
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    KuayunBackHeader(
                        title = "充值订单",
                        subtitle = "查看 USDT 充值进度与驳回原因",
                        onBack = onBack,
                    )
                }
                item {
                    KuayunStateBlock(
                        loading = state.loading,
                        empty = !state.loading && state.orders.isEmpty(),
                        emptyMessage = "暂无充值订单",
                        error = state.error,
                    )
                }
                if (!state.loading && state.orders.isEmpty()) {
                    item {
                        Button(
                            onClick = onRechargeAgain,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            Text("去充值")
                        }
                    }
                }
                if (!state.loading && state.orders.isNotEmpty()) {
                    items(state.orders, key = { it.id }) { order ->
                        RechargeOrderCard(
                            order = order,
                            selected = state.selectedOrder?.id == order.id,
                            onClick = {
                                onSelectOrder(if (state.selectedOrder?.id == order.id) null else order)
                            },
                        )
                    }
                }
            }
            state.selectedOrder?.let { order ->
                RechargeOrderDetailDialog(
                    order = order,
                    onRechargeAgain = onRechargeAgain,
                    onDismiss = { onSelectOrder(null) },
                )
            }
        }
    }
}

@Composable
private fun RechargeOrderCard(
    order: RechargeOrderItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    KuayunListCard(selected = selected, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(order.order_no, fontWeight = FontWeight.SemiBold)
            Text(rechargeStatusLabel(order.status, order.chain_auto_confirmed), color = rechargeStatusColor(order.status))
        }
        Text("${order.requested_usdt} USDT")
        order.credited_cny?.let {
            Text("到账约 ¥${"%.2f".format(it)}", style = MaterialTheme.typography.bodySmall)
        }
        order.created_at?.let {
            Text(
                formatTime(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (order.status == "rejected" && !order.reject_reason.isNullOrBlank()) {
            Text(
                "驳回：${order.reject_reason}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "点击查看详情",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RechargeOrderDetailDialog(
    order: RechargeOrderItem,
    onRechargeAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("充值订单详情", fontWeight = FontWeight.SemiBold)
                Text(
                    order.order_no,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailRow("状态", rechargeStatusLabel(order.status, order.chain_auto_confirmed))
                DetailRow("申请金额", "${order.requested_usdt} USDT")
                order.received_usdt?.let { DetailRow("实收金额", "$it USDT") }
                DetailRow("汇率", "1 USDT ≈ ¥${"%.2f".format(order.exchange_rate)}")
                order.credited_cny?.let { DetailRow("到账金额", "¥${"%.2f".format(it)}") }
                DetailRow("收款地址", order.receive_address)
                order.from_address?.let { DetailRow("付款地址", it) }
                order.txid?.let { DetailRow("交易哈希", it) }
                order.created_at?.let { DetailRow("创建时间", formatTime(it)) }
                order.proof_image_url?.let {
                    val proofUrl = resolveAssetUrl(it)
                    DetailRow("转账截图", proofUrl)
                    RemoteProofImage(proofUrl)
                }
                order.paid_at?.let { DetailRow("到账时间", formatTime(it)) }
                order.expired_at?.let { DetailRow("过期时间", formatTime(it)) }
                if (order.chain_auto_confirmed == true) {
                    DetailRow("确认方式", "自动扫链确认")
                }
                if (order.status == "rejected" && !order.reject_reason.isNullOrBlank()) {
                    Text(
                        "驳回原因：${order.reject_reason}",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            if (order.status == "rejected" || order.status == "expired" || order.status == "cancelled") {
                TextButton(
                    onClick = {
                        onDismiss()
                        onRechargeAgain()
                    },
                ) {
                    Text("重新发起充值")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun rechargeStatusLabel(status: String, autoConfirmed: Boolean? = null): String =
    com.vpn.member.ui.screens.rechargeStatusLabel(status, autoConfirmed, isAutoMode = false)

@Composable
private fun rechargeStatusColor(status: String) = when (status) {
    "paid" -> MaterialTheme.colorScheme.primary
    "rejected" -> MaterialTheme.colorScheme.error
    "submitted" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatTime(raw: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(raw))
}.getOrElse { raw.take(16).replace("T", " ") }

private fun resolveAssetUrl(path: String): String {
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val baseUrl = BuildConfig.APP_BASE_URL.trimEnd('/')
    val normalized = if (path.startsWith("/")) path else "/$path"
    if (normalized.startsWith("/api/uploads/")) return baseUrl + normalized
    if (normalized.startsWith("/uploads/")) return baseUrl + "/api" + normalized
    return baseUrl + normalized
}

@Composable
private fun RemoteProofImage(url: String) {
    var image by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        image = null
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                URL(url).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        }.onSuccess { bitmap ->
            if (bitmap == null) {
                error = "截图加载失败"
            } else {
                image = bitmap
            }
        }.onFailure {
            error = "截图加载失败，请稍后重试"
        }
    }

    when {
        image != null -> Image(
            bitmap = image!!.asImageBitmap(),
            contentDescription = "转账截图",
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentScale = ContentScale.Fit,
        )
        error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        else -> Text("截图加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
