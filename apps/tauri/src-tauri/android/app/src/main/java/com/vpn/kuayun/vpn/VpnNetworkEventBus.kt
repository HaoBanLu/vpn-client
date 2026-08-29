package com.vpn.kuayun.vpn

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 物理网络变化时向 WebView 上报，触发完整重连调度。 */
object VpnNetworkEventBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun emit(reason: String) {
        _events.tryEmit(reason)
    }
}
