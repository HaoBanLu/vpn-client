package com.vpn.member.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VpnConnectionStatus(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val error: String? = null,
    val connectedNode: String? = null,
    val probeStatus: String? = null,
    val probeLatencyMs: Int? = null,
    val exitIp: String? = null,
    val exitCountry: String? = null,
    val exitCity: String? = null,
)

object VpnConnectionBus {
    private val _status = MutableStateFlow(VpnConnectionStatus())
    val status: StateFlow<VpnConnectionStatus> = _status.asStateFlow()

    fun update(
        state: ConnectionState,
        error: String? = null,
        connectedNode: String? = _status.value.connectedNode,
        probeStatus: String? = _status.value.probeStatus,
        probeLatencyMs: Int? = _status.value.probeLatencyMs,
    ) {
        _status.value =
            VpnConnectionStatus(
                state = state,
                error = error,
                connectedNode = connectedNode,
                probeStatus = probeStatus,
                probeLatencyMs = probeLatencyMs,
            )
    }

    fun updateConnectedNode(connectedNode: String?) {
        _status.value = _status.value.copy(connectedNode = connectedNode)
    }

    fun updateQuality(
        probeStatus: String?,
        probeLatencyMs: Int? = null,
        connectedNode: String? = null,
        exitIp: String? = null,
        exitCountry: String? = null,
        exitCity: String? = null,
    ) {
        _status.value =
            _status.value.copy(
                probeStatus = probeStatus,
                probeLatencyMs = probeLatencyMs ?: _status.value.probeLatencyMs,
                connectedNode = connectedNode ?: _status.value.connectedNode,
                exitIp = exitIp ?: _status.value.exitIp,
                exitCountry = exitCountry ?: _status.value.exitCountry,
                exitCity = exitCity ?: _status.value.exitCity,
            )
    }

    /** 登出 / 会话失效 / 重新登录前：清除残留 FAILED，避免连接页误显示「连接失败」。 */
    fun resetForSessionEnd() {
        _status.value = VpnConnectionStatus()
    }
}
