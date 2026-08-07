package com.vpn.member.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.RechargeOrderItem
import com.vpn.member.data.api.USDTConfigSummary
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.data.session.AppEvents
import com.vpn.member.data.session.RechargeStatusChange
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RechargeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val usdtEnabled: Boolean = false,
    val usdtConfig: USDTConfigSummary? = null,
    val quickAmounts: List<Double> = listOf(10.0, 50.0, 100.0, 200.0),
    val balance: Double = 0.0,
    val amountUsdt: Double = 50.0,
    val activeOrder: RechargeOrderItem? = null,
    val history: List<RechargeOrderItem> = emptyList(),
    val fromAddress: String = "",
    val txid: String = "",
    val proofImageUrl: String? = null,
    val proofFileName: String? = null,
    val uploadingProof: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val isAutoMode: Boolean
        get() = isAutoConfirmMode(usdtConfig)

    val scanIntervalSeconds: Int
        get() = usdtConfig?.scan_interval_seconds?.takeIf { it > 0 } ?: 60
}

fun isAutoConfirmMode(config: USDTConfigSummary?): Boolean {
    if (config == null) return true
    if (config.confirm_mode == "manual") return false
    if (config.confirm_mode == "auto") return true
    return config.auto_confirm_enabled != false
}

class RechargeViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RechargeUiState())
    val state: StateFlow<RechargeUiState> = _state.asStateFlow()
    private var pollingOrderId: Long? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val previous = _state.value
            val hasData = previous.usdtConfig != null || previous.history.isNotEmpty()
            _state.value = previous.copy(
                refreshing = hasData,
                loading = !hasData,
                error = null,
            )
            runCatching {
                val methods = repository.getPaymentMethods()
                val me = repository.getMe()
                val orders = repository.getRechargeOrders()
                val active = orders.firstOrNull { it.status == "pending_transfer" || it.status == "submitted" }
                Triple(methods, me.balance, orders to active)
            }.onSuccess { (methods, balance, ordersPair) ->
                val (orders, active) = ordersPair
                val quickAmounts = methods.usdt?.quick_amounts_usdt?.takeIf { it.isNotEmpty() }
                    ?: listOf(10.0, 50.0, 100.0, 200.0)
                _state.value = previous.copy(
                    loading = false,
                    refreshing = false,
                    usdtEnabled = methods.usdt_enabled,
                    usdtConfig = methods.usdt,
                    quickAmounts = quickAmounts,
                    balance = balance,
                    amountUsdt = previous.amountUsdt.takeIf { it > 0 }
                        ?: quickAmounts.firstOrNull { it == 50.0 }
                        ?: quickAmounts.firstOrNull()
                        ?: 50.0,
                    activeOrder = active,
                    history = orders,
                )
                if (shouldPoll(active, methods.usdt)) {
                    startPolling(active!!.id)
                } else {
                    pollingOrderId = null
                }
            }.onFailure { e ->
                _state.value = previous.copy(
                    loading = false,
                    refreshing = false,
                    error = ApiRequestSupport.mapError(e, "加载失败"),
                )
            }
        }
    }

    fun setAmount(amount: Double) {
        _state.value = _state.value.copy(amountUsdt = amount)
    }

    fun setFromAddress(value: String) {
        _state.value = _state.value.copy(fromAddress = value)
    }

    fun setTxid(value: String) {
        _state.value = _state.value.copy(txid = value)
    }

    fun uploadProof(uri: Uri, @Suppress("UNUSED_PARAMETER") displayName: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(uploadingProof = true, error = null, message = null)
            runCatching { repository.uploadRechargeProof(uri) }
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        uploadingProof = false,
                        proofImageUrl = result.url,
                        proofFileName = result.fileName,
                        message = "截图上传成功",
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        uploadingProof = false,
                        error = ApiRequestSupport.mapError(e, "截图上传失败"),
                    )
                }
        }
    }

    fun createOrder() {
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, error = null, message = null)
            val autoMode = _state.value.isAutoMode
            runCatching { repository.createRechargeOrder(_state.value.amountUsdt) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        submitting = false,
                        activeOrder = it.order,
                        fromAddress = "",
                        txid = "",
                        proofImageUrl = null,
                        proofFileName = null,
                        message =
                            if (autoMode) {
                                "充值单已创建，请转账后等待自动确认"
                            } else {
                                "充值单已创建，请转账后上传截图并填写付款地址"
                            },
                    )
                    if (autoMode) {
                        startPolling(it.order.id)
                    }
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        submitting = false,
                        error = ApiRequestSupport.mapError(e, "创建失败"),
                    )
                }
        }
    }

    fun saveTransferHint() {
        val order = _state.value.activeOrder ?: return
        val fromAddress = _state.value.fromAddress.trim()
        val proofImageUrl = _state.value.proofImageUrl
        val txid = _state.value.txid.trim()
        if (fromAddress.isEmpty() && proofImageUrl.isNullOrBlank() && txid.isEmpty()) {
            _state.value = _state.value.copy(error = "请至少填写一项转账信息")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, error = null)
            runCatching {
                repository.saveRechargeTransferHint(
                    order.id,
                    fromAddress.takeIf { it.isNotEmpty() },
                    proofImageUrl,
                    txid.takeIf { it.isNotEmpty() },
                )
            }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        submitting = false,
                        activeOrder = updated,
                        message = "已保存，系统将加速匹配到账",
                    )
                    startPolling(updated.id)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        submitting = false,
                        error = ApiRequestSupport.mapError(e, "保存失败"),
                    )
                }
        }
    }

    fun submitProof() {
        val order = _state.value.activeOrder ?: return
        val fromAddress = _state.value.fromAddress.trim()
        val proofImageUrl = _state.value.proofImageUrl
        if (fromAddress.isEmpty()) {
            _state.value = _state.value.copy(error = "请填写付款钱包地址")
            return
        }
        if (proofImageUrl.isNullOrBlank()) {
            _state.value = _state.value.copy(error = "请上传转账截图")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, error = null)
            runCatching {
                repository.submitRechargeOrder(
                    order.id,
                    fromAddress,
                    proofImageUrl,
                    _state.value.txid,
                )
            }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        submitting = false,
                        activeOrder = updated,
                        message = "已提交，等待人工审核",
                    )
                    startPolling(updated.id)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        submitting = false,
                        error = ApiRequestSupport.mapError(e, "提交失败"),
                    )
                }
        }
    }

    fun cancelOrder() {
        val order = _state.value.activeOrder ?: return
        viewModelScope.launch {
            runCatching { repository.cancelRechargeOrder(order.id) }
                .onSuccess {
                    pollingOrderId = null
                    _state.value = _state.value.copy(
                        activeOrder = null,
                        fromAddress = "",
                        txid = "",
                        proofImageUrl = null,
                        proofFileName = null,
                        message = "已取消",
                    )
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = ApiRequestSupport.mapError(e, "取消失败"))
                }
        }
    }

    fun startFreshRecharge() {
        pollingOrderId = null
        _state.value = _state.value.copy(
            activeOrder = null,
            fromAddress = "",
            txid = "",
            proofImageUrl = null,
            proofFileName = null,
            message = "请重新创建充值单",
            error = null,
        )
    }

    private fun shouldPoll(order: RechargeOrderItem?, config: USDTConfigSummary?): Boolean {
        if (order == null) return false
        if (order.status == "submitted") return true
        return order.status == "pending_transfer" && isAutoConfirmMode(config)
    }

    private fun startPolling(orderId: Long) {
        if (pollingOrderId == orderId) {
            return
        }
        pollingOrderId = orderId
        viewModelScope.launch {
            val intervalMs =
                (_state.value.scanIntervalSeconds.coerceAtLeast(10) * 1000L / 2).coerceAtLeast(5_000L)
            while (pollingOrderId == orderId) {
                delay(intervalMs)
                if (pollingOrderId != orderId) {
                    return@launch
                }
                runCatching { repository.getRechargeOrder(orderId) }
                    .onSuccess { order ->
                        _state.value = _state.value.copy(activeOrder = order)
                        when (order.status) {
                            "paid", "rejected", "expired", "cancelled" -> {
                                pollingOrderId = null
                                if (order.status == "paid" || order.status == "rejected") {
                                    AppEvents.notifyRechargeStatusChanged(
                                        RechargeStatusChange(order.id, order.order_no, order.status),
                                    )
                                }
                                refresh()
                                return@launch
                            }
                        }
                    }
            }
        }
    }
}
