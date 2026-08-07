package com.vpn.member.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vpn.member.ui.components.KuayunBackHeader
import com.vpn.member.ui.components.KuayunInfoCard
import com.vpn.member.ui.components.KuayunPullRefreshBox
import com.vpn.member.ui.components.KuayunScreenBackground
import com.vpn.member.ui.components.KuayunSectionTitle
import com.vpn.member.ui.components.KuayunStatChip
import com.vpn.member.ui.components.UsdtAddressQrCode
import com.vpn.member.ui.viewmodel.RechargeUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val USDT_NETWORK_LABEL = "TRC20"
private const val USDT_NETWORK_FULL = "USDT-TRC20（Tron 网络）"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RechargeScreen(
    state: RechargeUiState,
    onRefresh: () -> Unit,
    onAmountChange: (Double) -> Unit,
    onFromAddressChange: (String) -> Unit,
    onTxidChange: (String) -> Unit,
    onPickProof: (Uri, String?) -> Unit,
    onCreateOrder: () -> Unit,
    onSubmitProof: () -> Unit,
    onSaveTransferHint: () -> Unit,
    onCancelOrder: () -> Unit,
    onRestartRecharge: () -> Unit,
    onViewOrders: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            onPickProof(uri, null)
        }
    }
    val pageSubtitle =
        if (state.isAutoMode) {
            "转账后系统自动确认，余额即时到账"
        } else {
            "转账后提交凭证，人工审核入账"
        }

    KuayunScreenBackground(modifier = modifier.fillMaxSize()) {
        KuayunPullRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                KuayunBackHeader(
                    title = "USDT 充值",
                    subtitle = pageSubtitle,
                    onBack = onBack,
                    trailingAction = {
                        TextButton(onClick = onViewOrders) {
                            Text("充值记录")
                        }
                    },
                )

                if (state.loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    RechargeBalanceSummary(state)

                    if (!state.usdtEnabled) {
                        RechargeDisabledCard()
                    } else if (state.activeOrder == null) {
                        RechargeAmountPicker(state, onAmountChange, onCreateOrder)
                    } else {
                        ActiveRechargeOrderSection(
                            state = state,
                            clipboard = clipboard,
                            proofPicker = { proofPicker.launch("image/*") },
                            onFromAddressChange = onFromAddressChange,
                            onTxidChange = onTxidChange,
                            onSubmitProof = onSubmitProof,
                            onSaveTransferHint = onSaveTransferHint,
                            onCancelOrder = onCancelOrder,
                            onRestartRecharge = onRestartRecharge,
                        )
                    }
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RechargeBalanceSummary(state: RechargeUiState) {
    KuayunInfoCard {
        Text(
            text = "当前余额",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "¥${"%.2f".format(state.balance)}",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (state.usdtEnabled && state.usdtConfig != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KuayunStatChip(
                    label = "汇率",
                    value = "1U = ¥${state.usdtConfig.exchange_rate}",
                    modifier = Modifier.weight(1f),
                )
                val estimated = state.amountUsdt * state.usdtConfig.exchange_rate
                KuayunStatChip(
                    label = "预计到账",
                    value = "¥${"%.2f".format(estimated)}",
                    modifier = Modifier.weight(1f),
                    highlight = true,
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NetworkAssistChip()
                AssistChip(
                    onClick = {},
                    label = { Text(if (state.isAutoMode) "自动确认" else "人工审核") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RechargeDisabledCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null)
            Text("USDT 充值暂未开放，请联系客服或稍后再试")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RechargeAmountPicker(
    state: RechargeUiState,
    onAmountChange: (Double) -> Unit,
    onCreateOrder: () -> Unit,
) {
    NetworkNoticeCard(compact = true)
    KuayunInfoCard {
        KuayunSectionTitle(title = "选择充值金额")
        Text(
            text = "创建订单后将获得 ${USDT_NETWORK_FULL} 收款地址与二维码",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.quickAmounts.forEach { amount ->
                val selected = kotlin.math.abs(state.amountUsdt - amount) < 0.01
                FilterChip(
                    selected = selected,
                    onClick = { onAmountChange(amount) },
                    label = { Text("${formatUsdtAmount(amount)} U") },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                )
            }
        }
        OutlinedTextField(
            value = formatUsdtInput(state.amountUsdt),
            onValueChange = { onAmountChange(it.toDoubleOrNull() ?: state.amountUsdt) },
            label = { Text("自定义金额 (USDT)") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            singleLine = true,
        )
    }
    Button(
        onClick = onCreateOrder,
        enabled = !state.submitting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.submitting) "创建中…" else "创建充值单")
    }
}

@Composable
private fun ActiveRechargeOrderSection(
    state: RechargeUiState,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    proofPicker: () -> Unit,
    onFromAddressChange: (String) -> Unit,
    onTxidChange: (String) -> Unit,
    onSubmitProof: () -> Unit,
    onSaveTransferHint: () -> Unit,
    onCancelOrder: () -> Unit,
    onRestartRecharge: () -> Unit,
) {
    val order = state.activeOrder ?: return
    var hintExpanded by remember(order.id) { mutableStateOf(false) }
    val estimatedCny = order.requested_usdt * order.exchange_rate

    OrderStatusStrip(
        status = rechargeStatusLabel(order.status, order.chain_auto_confirmed, state.isAutoMode),
        orderNo = order.order_no,
        expiredAt = order.expired_at,
        isAutoMode = state.isAutoMode,
    )

    if (order.status == "pending_transfer") {
        TransferHeroCard(
            amountUsdt = order.requested_usdt,
            estimatedCny = estimatedCny,
            isAutoMode = state.isAutoMode,
        )

        KuayunInfoCard {
            Text("扫码或复制地址转账", fontWeight = FontWeight.SemiBold)
            Text(
                text = "请使用钱包的 ${USDT_NETWORK_LABEL} 网络向以下地址转账",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                UsdtAddressQrCode(address = order.receive_address)
            }
            Text(
                text = order.receive_address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Button(
                onClick = { clipboard.setText(AnnotatedString(order.receive_address)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("复制收款地址")
            }
        }

        NetworkNoticeCard(compact = false, customTip = state.usdtConfig?.confirm_tips)

        if (state.isAutoMode) {
            AutoConfirmWaitingCard(scanIntervalSeconds = state.scanIntervalSeconds)
            AccelerateMatchCard(
                expanded = hintExpanded,
                onToggle = { hintExpanded = !hintExpanded },
                state = state,
                proofPicker = proofPicker,
                onFromAddressChange = onFromAddressChange,
                onTxidChange = onTxidChange,
                onSaveTransferHint = onSaveTransferHint,
            )
            TextButton(onClick = onCancelOrder, modifier = Modifier.fillMaxWidth()) {
                Text("取消充值单")
            }
        } else {
            ManualProofCard(
                state = state,
                proofPicker = proofPicker,
                onFromAddressChange = onFromAddressChange,
                onTxidChange = onTxidChange,
                onSubmitProof = onSubmitProof,
                onCancelOrder = onCancelOrder,
            )
        }
    } else {
        OrderResultCard(
            state = state,
            onRestartRecharge = onRestartRecharge,
        )
    }
}

@Composable
private fun OrderStatusStrip(
    status: String,
    orderNo: String,
    expiredAt: String?,
    isAutoMode: Boolean,
) {
    KuayunInfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("订单状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            NetworkAssistChip()
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
        Text("单号 $orderNo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        expiredAt?.let {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "请在 ${formatExpireTime(it)} 前完成转账",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isAutoMode) {
            Text(
                text = "转账完成后无需操作，系统将自动确认入账",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TransferHeroCard(
    amountUsdt: Double,
    estimatedCny: Double,
    isAutoMode: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "应付金额",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "${formatUsdtAmount(amountUsdt)} USDT",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "预计到账 ¥${"%.2f".format(estimatedCny)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = if (isAutoMode) "请按此金额转账，系统将自动匹配" else "请按此金额转账后提交凭证",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NetworkNoticeCard(compact: Boolean, customTip: String? = null) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Column {
                    Text("仅支持 $USDT_NETWORK_FULL", fontWeight = FontWeight.SemiBold)
                    if (!compact) {
                        Spacer(modifier = Modifier.height(6.dp))
                        NoticeBullet("必须使用 ${USDT_NETWORK_LABEL} 网络，勿用 ERC20 / BEP20 等其他链")
                        NoticeBullet("仅转入 USDT，其他币种或网络将无法找回")
                        NoticeBullet("转账金额须与订单金额一致（允许极小误差）")
                    } else {
                        Text(
                            text = "请确保钱包选择 ${USDT_NETWORK_LABEL} 网络",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    customTip?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeBullet(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun NetworkAssistChip() {
    AssistChip(
        onClick = {},
        label = { Text(USDT_NETWORK_LABEL) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    )
}

@Composable
private fun AutoConfirmWaitingCard(scanIntervalSeconds: Int) {
    KuayunInfoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("自动确认中", fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Text(
            text = "转账后系统约每 $scanIntervalSeconds 秒检测链上到账",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = "无需上传截图或提交审核，到账后人民币余额自动增加",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun AccelerateMatchCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    state: RechargeUiState,
    proofPicker: () -> Unit,
    onFromAddressChange: (String) -> Unit,
    onTxidChange: (String) -> Unit,
    onSaveTransferHint: () -> Unit,
) {
    KuayunInfoCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("加速匹配（选填）", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "长时间未到账时可填写 txid 或付款地址",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(if (expanded) "收起" else "展开", color = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                OutlinedTextField(
                    value = state.fromAddress,
                    onValueChange = onFromAddressChange,
                    label = { Text("付款钱包地址") },
                    placeholder = { Text("TRC20 转出地址") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.txid,
                    onValueChange = onTxidChange,
                    label = { Text("交易哈希 TxID") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedButton(
                    onClick = proofPicker,
                    enabled = !state.submitting && !state.uploadingProof,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(if (state.proofFileName != null) "重新选择截图" else "上传转账截图（选填）")
                }
                if (state.uploadingProof) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text("截图上传中…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(
                    onClick = onSaveTransferHint,
                    enabled = !state.submitting && !state.uploadingProof,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(if (state.submitting) "保存中…" else "保存加速信息")
                }
            }
        }
    }
}

@Composable
private fun ManualProofCard(
    state: RechargeUiState,
    proofPicker: () -> Unit,
    onFromAddressChange: (String) -> Unit,
    onTxidChange: (String) -> Unit,
    onSubmitProof: () -> Unit,
    onCancelOrder: () -> Unit,
) {
    KuayunInfoCard {
        Text("提交转账凭证", fontWeight = FontWeight.SemiBold)
        Text(
            text = "转账后请上传截图并填写付款地址，等待人工审核",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        OutlinedTextField(
            value = state.fromAddress,
            onValueChange = onFromAddressChange,
            label = { Text("付款钱包地址") },
            placeholder = { Text("TRC20 转出地址") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedButton(
            onClick = proofPicker,
            enabled = !state.submitting && !state.uploadingProof,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (state.proofFileName != null) "重新选择截图" else "上传转账截图")
        }
        OutlinedTextField(
            value = state.txid,
            onValueChange = onTxidChange,
            label = { Text("交易哈希（选填）") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    Button(
        onClick = onSubmitProof,
        enabled = !state.submitting && !state.uploadingProof && state.proofImageUrl != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.submitting) "提交中…" else "提交审核")
    }
    TextButton(onClick = onCancelOrder, modifier = Modifier.fillMaxWidth()) {
        Text("取消充值单")
    }
}

@Composable
private fun OrderResultCard(
    state: RechargeUiState,
    onRestartRecharge: () -> Unit,
) {
    val order = state.activeOrder ?: return
    KuayunInfoCard {
        when (order.status) {
            "submitted" ->
                Text(
                    if (state.isAutoMode) {
                        "正在确认链上到账，请稍候…\n可在「充值记录」查看进度"
                    } else {
                        "已提交凭证，等待人工审核\n可在「充值记录」查看进度"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            "paid" -> {
                val title = if (order.chain_auto_confirmed == true) "已自动确认到账" else "充值已到账"
                Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                order.credited_cny?.let {
                    Text("到账 ¥${"%.2f".format(it)}", modifier = Modifier.padding(top = 6.dp))
                }
                order.paid_at?.let {
                    Text("到账时间：${formatExpireTime(it)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
            "rejected" -> {
                Text(
                    order.reject_reason?.takeIf { it.isNotBlank() } ?: "充值被驳回",
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onRestartRecharge, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("重新发起充值")
                }
            }
            else -> Text(rechargeStatusLabel(order.status, order.chain_auto_confirmed, state.isAutoMode))
        }
    }
}

private fun formatUsdtAmount(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else "%.2f".format(amount)

private fun formatUsdtInput(amount: Double): String = formatUsdtAmount(amount)

private fun formatExpireTime(raw: String): String = runCatching {
    DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(raw))
}.getOrElse { raw.take(16).replace("T", " ") }

fun rechargeStatusLabel(status: String, autoConfirmed: Boolean? = null, isAutoMode: Boolean = false): String =
    when {
        status == "paid" && autoConfirmed == true -> "自动确认"
        status == "submitted" && isAutoMode -> "确认中"
        status == "pending_transfer" && isAutoMode -> "等待链上确认"
        status == "pending_transfer" -> "待转账"
        status == "submitted" -> "待审核"
        status == "paid" -> "已到账"
        status == "rejected" -> "已驳回"
        status == "expired" -> "已过期"
        status == "cancelled" -> "已取消"
        else -> status
    }
