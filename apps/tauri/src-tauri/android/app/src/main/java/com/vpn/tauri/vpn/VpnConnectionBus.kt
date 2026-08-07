package com.vpn.tauri.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VpnConnectionStatus(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val error: String? = null,
)

object VpnConnectionBus {
    private val _status = MutableStateFlow(VpnConnectionStatus())
    val status: StateFlow<VpnConnectionStatus> = _status.asStateFlow()

    fun update(state: ConnectionState, error: String? = null) {
        _status.value = VpnConnectionStatus(state = state, error = error)
    }
}
