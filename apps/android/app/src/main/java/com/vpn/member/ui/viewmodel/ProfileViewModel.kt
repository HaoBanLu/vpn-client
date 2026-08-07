package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.OrderItem
import com.vpn.member.data.api.RechargeOrderItem
import com.vpn.member.data.api.SubscriptionActive
import com.vpn.member.data.api.SubscriptionUsage
import com.vpn.member.data.api.UserBrief
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.network.SessionInvalidatedException
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.notification.UserNotificationCoordinator
import com.vpn.member.data.session.AppEvents
import com.vpn.member.data.session.RechargeStatusChange
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AppNotification(
    val id: Long,
    val orderNo: String,
    val message: String,
    val type: String,
)

data class ProfileUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val user: UserBrief? = null,
    val subscription: SubscriptionActive? = null,
    val usage: SubscriptionUsage? = null,
    val orders: List<OrderItem> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val toastMessage: String? = null,
    val message: String? = null,
    val error: String? = null,
    val supportEnabled: Boolean = false,
    val directConnectCount: Int = 0,
    val directBypassRuleCount: Int = 0,
)

class ProfileViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var knownOrderStatuses: Map<Long, String> = emptyMap()
    private var pollingInitialized = false

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L
    }

    init {
        refresh()
        viewModelScope.launch {
            AppEvents.rechargeStatusChanged.collect { change ->
                handleExternalRechargeChange(change)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val hasData = _state.value.user != null
            _state.value = _state.value.copy(
                refreshing = hasData,
                loading = !hasData,
                error = null,
                message = null,
            )
            runCatching {
                repository.ensureNetworkAvailable()
                val user = repository.getMe()
                val orders = repository.getOrders()
                val rechargeOrders = repository.getRechargeOrders()
                val subscription = repository.getActiveSubscription()
                val usage =
                    if (subscription != null) {
                        runCatching { repository.getUsage() }.getOrNull()
                    } else {
                        null
                    }
                val supportConfig = repository.getSupportConfig()
                knownOrderStatuses = rechargeOrders.associate { it.id to it.status }
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    user = user,
                    subscription = subscription,
                    usage = usage,
                    orders = orders,
                    supportEnabled = supportConfig.enabled,
                    directConnectCount = repository.getDirectConnectCount(),
                    directBypassRuleCount = repository.getDirectBypassRuleCount(),
                )
            }.onFailure { e ->
                if (e is SessionInvalidatedException) return@onFailure
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = ApiRequestSupport.mapError(e, "加载个人信息失败"),
                )
            }
        }
    }

    fun startNotificationPolling() {
        if (pollingJob?.isActive == true) {
            return
        }
        pollingJob = viewModelScope.launch {
            while (isActive) {
                pollRechargeNotifications(!pollingInitialized)
                pollingInitialized = true
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopNotificationPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun clearUnreadNotifications() {
        _state.value = _state.value.copy(unreadNotificationCount = 0)
    }

    fun dismissToast() {
        _state.value = _state.value.copy(toastMessage = null)
    }

    private suspend fun pollRechargeNotifications(isFirstLoad: Boolean) {
        runCatching {
            val rechargeOrders = repository.getRechargeOrders()
            val changes = detectStatusChanges(rechargeOrders, isFirstLoad)
            knownOrderStatuses = rechargeOrders.associate { it.id to it.status }

            if (changes.isEmpty()) {
                if (!isFirstLoad) {
                    val user = repository.getMe()
                    _state.value = _state.value.copy(user = user)
                }
                return
            }

            val user = repository.getMe()
            val packageOrders = repository.getOrders()
            applyRechargeNotifications(changes, user, packageOrders)
        }
    }

    private fun detectStatusChanges(
        orders: List<RechargeOrderItem>,
        isFirstLoad: Boolean,
    ): List<RechargeStatusChange> {
        if (isFirstLoad) {
            return emptyList()
        }
        return orders.mapNotNull { order ->
            val previous = knownOrderStatuses[order.id]
            when {
                previous == "submitted" && order.status == "paid" ->
                    RechargeStatusChange(order.id, order.order_no, "paid")
                previous == "submitted" && order.status == "rejected" ->
                    RechargeStatusChange(order.id, order.order_no, "rejected")
                else -> null
            }
        }
    }

    private suspend fun handleExternalRechargeChange(change: RechargeStatusChange) {
        val user = runCatching { repository.getMe() }.getOrNull() ?: _state.value.user
        val packageOrders = runCatching { repository.getOrders() }.getOrElse { _state.value.orders }
        val rechargeOrders = runCatching { repository.getRechargeOrders() }.getOrNull()
        rechargeOrders?.let { knownOrderStatuses = it.associate { order -> order.id to order.status } }
        applyRechargeNotifications(listOf(change), user, packageOrders)
    }

    private fun applyRechargeNotifications(
        changes: List<RechargeStatusChange>,
        user: UserBrief?,
        packageOrders: List<OrderItem>,
    ) {
        if (changes.isEmpty()) {
            return
        }
        val existingKeys = _state.value.notifications.map { it.id to it.type }.toSet()
        val newNotifications = changes.map { change ->
            AppNotification(
                id = change.orderId,
                orderNo = change.orderNo,
                message = notificationMessage(change.status),
                type = change.status,
            )
        }.filter { (it.id to it.type) !in existingKeys }
        if (newNotifications.isEmpty()) {
            _state.value = _state.value.copy(user = user, orders = packageOrders)
            return
        }
        _state.value = _state.value.copy(
            user = user,
            orders = packageOrders,
            notifications = (_state.value.notifications + newNotifications).takeLast(10),
            unreadNotificationCount = _state.value.unreadNotificationCount + newNotifications.size,
            toastMessage = newNotifications.last().message,
        )
        changes.forEach { change ->
            UserNotificationCoordinator.notifyRechargeChange(change)
        }
    }

    private fun notificationMessage(status: String): String =
        when (status) {
            "paid" -> "USDT 充值已到账，余额已更新"
            "rejected" -> "USDT 充值被驳回，请查看原因"
            else -> "充值订单状态已更新"
        }
}
