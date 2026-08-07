package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.PackageItem
import com.vpn.member.data.api.SubscriptionActive
import com.vpn.member.data.api.SubscriptionUsage
import com.vpn.member.data.api.UserBrief
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.network.SessionInvalidatedException
import com.vpn.member.data.repository.AppException
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.ui.purchaseSuccessMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PackagesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val user: UserBrief? = null,
    val subscription: SubscriptionActive? = null,
    val usage: SubscriptionUsage? = null,
    val packages: List<PackageItem> = emptyList(),
    val paying: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class PackagesViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PackagesUiState())
    val state: StateFlow<PackagesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val hasData = _state.value.packages.isNotEmpty()
            _state.value = _state.value.copy(
                refreshing = hasData,
                loading = !hasData,
                error = null,
            )
            runCatching { fetchPackagesData() }
                .onSuccess { data ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        user = data.user,
                        subscription = data.subscription,
                        usage = data.usage,
                        packages = data.packages,
                    )
                }
                .onFailure { e ->
                    if (e is SessionInvalidatedException) return@onFailure
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = ApiRequestSupport.mapError(e, "加载套餐失败"),
                    )
                }
        }
    }

    private suspend fun fetchPackagesData(): PackagesUiState {
        val user = repository.getMe()
        val packages = repository.getPackages()
        val subscription = repository.getActiveSubscription()
        val usage =
            if (subscription != null) {
                runCatching { repository.getUsage() }.getOrNull()
            } else {
                null
            }
        return _state.value.copy(
            user = user,
            subscription = subscription,
            usage = usage,
            packages = packages,
        )
    }

    fun purchase(packageId: Long, onSuccess: () -> Unit, onInsufficientBalance: () -> Unit = {}) {
        viewModelScope.launch {
            val pkg = _state.value.packages.find { it.id == packageId }
            val subBefore = _state.value.subscription
            _state.value = _state.value.copy(paying = true, error = null, message = null)
            runCatching {
                val orderId = repository.createOrder(packageId, "balance")
                repository.payOrder(orderId)
                repeat(10) {
                    val status = repository.pollOrderStatus(orderId)
                    if (status.status == "paid") return@repeat
                    delay(1000)
                }
            }.onSuccess {
                val successMsg =
                    if (pkg != null) {
                        purchaseSuccessMessage(subBefore, pkg)
                    } else {
                        "支付成功，请返回连接页"
                    }
                val refreshed = runCatching { fetchPackagesData() }.getOrElse { _state.value }
                _state.value = refreshed.copy(
                    paying = false,
                    message = successMsg,
                )
                onSuccess()
            }.onFailure { e ->
                if (e is SessionInvalidatedException) return@onFailure
                val appCode = (e as? AppException)?.appCode
                if (appCode == "INSUFFICIENT_BALANCE" ||
                    (e as? AppException)?.userMessage?.contains("余额不足") == true
                ) {
                    _state.value = _state.value.copy(paying = false, error = "余额不足，请先充值")
                    onInsufficientBalance()
                    return@launch
                }
                _state.value = _state.value.copy(
                    paying = false,
                    error = ApiRequestSupport.mapError(e, "支付失败"),
                )
            }
        }
    }
}
