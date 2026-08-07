package com.vpn.member.data.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class RechargeStatusChange(
    val orderId: Long,
    val orderNo: String,
    val status: String,
)

object AppEvents {
    private val _rechargeStatusChanged = MutableSharedFlow<RechargeStatusChange>(extraBufferCapacity = 4)
    val rechargeStatusChanged = _rechargeStatusChanged.asSharedFlow()

    private val _vpnConfigChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val vpnConfigChanged = _vpnConfigChanged.asSharedFlow()

    fun notifyRechargeStatusChanged(change: RechargeStatusChange) {
        _rechargeStatusChanged.tryEmit(change)
    }

    /** 应用直连 / 规则直连变更后通知连接页热重载（P2-3）。 */
    fun notifyVpnConfigChanged() {
        _vpnConfigChanged.tryEmit(Unit)
    }
}
