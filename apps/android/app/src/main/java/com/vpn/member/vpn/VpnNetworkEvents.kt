package com.vpn.member.vpn

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 物理网络切换事件（WiFi↔蜂窝、DNS 变更等），由 [VpnReconnectSupervisor] 消费。 */
object VpnNetworkEvents {
    private val _transportChanged = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val transportChanged: SharedFlow<String> = _transportChanged.asSharedFlow()

    fun notifyTransportChanged(reason: String) {
        _transportChanged.tryEmit(reason)
    }
}
