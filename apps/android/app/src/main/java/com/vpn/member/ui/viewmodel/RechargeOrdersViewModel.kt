package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.RechargeOrderItem
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RechargeOrdersUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val orders: List<RechargeOrderItem> = emptyList(),
    val selectedOrder: RechargeOrderItem? = null,
    val error: String? = null,
)

class RechargeOrdersViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RechargeOrdersUiState())
    val state: StateFlow<RechargeOrdersUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasData = _state.value.orders.isNotEmpty()
            _state.value = _state.value.copy(
                refreshing = hasData,
                loading = !hasData,
                error = null,
            )
            runCatching { repository.getRechargeOrders() }
                .onSuccess { orders ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        orders = orders,
                        selectedOrder = _state.value.selectedOrder?.let { selected ->
                            orders.firstOrNull { it.id == selected.id }
                        },
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = ApiRequestSupport.mapError(e, "加载充值订单失败"),
                    )
                }
        }
    }

    fun selectOrder(order: RechargeOrderItem?) {
        _state.value = _state.value.copy(selectedOrder = order)
    }
}
