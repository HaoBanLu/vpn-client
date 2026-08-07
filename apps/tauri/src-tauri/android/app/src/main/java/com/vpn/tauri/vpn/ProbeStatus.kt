package com.vpn.tauri.vpn

/** 连接后网络探测结果（隧道已建立前提下） */
enum class ProbeStatus {
    IDLE,
    PROBING,
    /** 基础联网与海外可达均正常 */
    OK,
    /** 基础联网正常，海外探测受限（常见于国内节点） */
    LIMITED_OVERSEAS,
    /** 隧道已建立，探测较慢但基础联网可用 */
    SLOW,
    /** 隧道已建立但基础联网失败，疑似配置/协议兼容问题 */
    FAILED,
}

data class ProbeResult(
    val basicOk: Boolean,
    val overseasOk: Boolean,
    val slow: Boolean = false,
    val latencyMs: Int? = null,
) {
    fun toStatus(): ProbeStatus =
        when {
            !basicOk -> ProbeStatus.FAILED
            basicOk && overseasOk && !slow -> ProbeStatus.OK
            basicOk && overseasOk && slow -> ProbeStatus.SLOW
            basicOk -> ProbeStatus.LIMITED_OVERSEAS
            else -> ProbeStatus.FAILED
        }
}
