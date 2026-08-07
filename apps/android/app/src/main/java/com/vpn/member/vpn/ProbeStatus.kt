package com.vpn.member.vpn

/** 隧道已建立且后台探测通过（仅用于延迟/质量展示，不决定主连接态）。 */
fun isConnectionVerified(
    connectionState: ConnectionState,
    probeStatus: ProbeStatus,
): Boolean =
    connectionState == ConnectionState.CONNECTED &&
        probeStatus in
            setOf(
                ProbeStatus.OK,
                ProbeStatus.SLOW,
                ProbeStatus.LIMITED_OVERSEAS,
                ProbeStatus.DEGRADED,
            )

/** 隧道已建立，连通性验证尚未完成。 */
fun isConnectionVerifying(
    connectionState: ConnectionState,
    probeStatus: ProbeStatus,
): Boolean =
    connectionState == ConnectionState.CONNECTED &&
        (probeStatus == ProbeStatus.PROBING || probeStatus == ProbeStatus.IDLE)

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
    /** 隧道已建立但质量较差，保持连接并提示用户（P0-2）。 */
    DEGRADED,
    /** 隧道已建立但基础联网失败，疑似配置/协议兼容问题 */
    FAILED,
}

/** 探测失败时的技术子因（写入诊断日志，映射为 [ConnectFailureReason]）。 */
enum class ProbeFailureCause {
    /** 系统 VPN 网络未出现 */
    VPN_NOT_UP,
    /** 经 Mihomo mixed-port 访问测速 URL 失败（多为节点入口不可达） */
    PROXY_UNREACHABLE,
    /** split 模式下本机物理网不可达 */
    PHYSICAL_OFFLINE,
}

fun ProbeFailureCause.toConnectFailureReason(): ConnectFailureReason =
    when (this) {
        ProbeFailureCause.VPN_NOT_UP -> ConnectFailureReason.PROBE_NO_VPN
        ProbeFailureCause.PROXY_UNREACHABLE -> ConnectFailureReason.NODE_UNREACHABLE
        ProbeFailureCause.PHYSICAL_OFFLINE -> ConnectFailureReason.PROBE_NETWORK_OFFLINE
    }

data class ProbeResult(
    val basicOk: Boolean,
    val overseasOk: Boolean,
    val slow: Boolean = false,
    /** 经 VPN 隧道到测速 URL 的单次 HEAD 延迟（毫秒）；仅 overseasOk 时有值 */
    val latencyMs: Int? = null,
    val failureCause: ProbeFailureCause? = null,
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
