package com.vpn.member.vpn

/**
 * 连接页宿主：可提供带 UI 的完整重连；监督器在无宿主时走缓存/会话回退路径。
 * 生命周期随 [com.vpn.member.ui.viewmodel.ConnectViewModel]，监督器本身挂在 Application。
 */
interface VpnReconnectHost {
    fun connectionState(): ConnectionState

    /** 调度完整自动重连（防抖 + 先备配置再 KS）；实现须可合并重复事件。 */
    fun scheduleAutoReconnect(reason: String)

    /** 仅关闭自动重连时的轻量自愈。 */
    fun startHeal(reason: String)

    fun notifyActionHint(hint: String?)

    /** 物理网恢复时刷新 dashboard（可选）。 */
    fun onNetworkRestoredForUi()
}
