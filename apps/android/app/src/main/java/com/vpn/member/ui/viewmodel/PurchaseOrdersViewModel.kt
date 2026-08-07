package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.OrderItem
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PurchaseOrdersUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val orders: List<OrderItem> = emptyList(),
    val error: String? = null,
)

class PurchaseOrdersViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PurchaseOrdersUiState())
    val state: StateFlow<PurchaseOrdersUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasData = _state.value.orders.isNotEmpty()
            _state.value = _state.value.copy(
                loading = !hasData,
                refreshing = hasData,
                error = null,
            )
            runCatching { repository.getOrders() }
                .onSuccess { orders ->
                    _state.value = PurchaseOrdersUiState(
                        loading = false,
                        refreshing = false,
                        orders = orders,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = ApiRequestSupport.mapError(e, "加载购买记录失败"),
                    )
                }
        }
    }
}
