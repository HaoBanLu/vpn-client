package com.vpn.member.vpn

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 心跳下发强制断开 VPN（套餐过期、流量用尽等）。 */
object PrivacyForceDisconnectEvents {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun publish(reason: String) {
        _events.tryEmit(reason)
    }
}
