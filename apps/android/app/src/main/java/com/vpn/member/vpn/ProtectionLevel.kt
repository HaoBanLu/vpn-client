package com.vpn.member.vpn

/** 连接页 / 隐私设置页展示的保护等级。 */
enum class ProtectionLevel {
    /** 未连接但隐私基线已就绪 */
    BASELINE_READY,
    UNPROTECTED,
    ESTABLISHING,
    PROTECTED,
    DEGRADED,
    BLOCKED,
}

object ProtectionLevelResolver {
    fun isPrivacyBaselineReady(
        killSwitchEnabled: Boolean,
        ipv6ProtectionEnabled: Boolean,
    ): Boolean = killSwitchEnabled && ipv6ProtectionEnabled

    fun resolve(
        connectionState: ConnectionState,
        error: String?,
        probeStatus: ProbeStatus,
        privacyBaselineReady: Boolean = false,
    ): ProtectionLevel {
        if (error?.contains("Kill Switch", ignoreCase = true) == true ||
            error?.contains("断网保护", ignoreCase = true) == true ||
            error?.contains("网络已阻断", ignoreCase = true) == true
        ) {
            return ProtectionLevel.BLOCKED
        }
        return when (connectionState) {
            ConnectionState.CONNECTING -> ProtectionLevel.ESTABLISHING
            ConnectionState.CONNECTED ->
                when (probeStatus) {
                    ProbeStatus.FAILED, ProbeStatus.DEGRADED -> ProtectionLevel.DEGRADED
                    else -> ProtectionLevel.PROTECTED
                }
            ConnectionState.FAILED ->
                if (error?.contains("阻断", ignoreCase = true) == true) {
                    ProtectionLevel.BLOCKED
                } else if (privacyBaselineReady) {
                    ProtectionLevel.BASELINE_READY
                } else {
                    ProtectionLevel.UNPROTECTED
                }
            ConnectionState.DISCONNECTED ->
                if (privacyBaselineReady) {
                    ProtectionLevel.BASELINE_READY
                } else {
                    ProtectionLevel.UNPROTECTED
                }
        }
    }

    fun label(level: ProtectionLevel, exitIp: String? = null): String =
        when (level) {
            ProtectionLevel.BASELINE_READY -> "隐私保护已就绪 · 连接后生效"
            ProtectionLevel.UNPROTECTED -> "未保护 · 建议开启断网保护"
            ProtectionLevel.ESTABLISHING -> "正在建立保护…"
            ProtectionLevel.PROTECTED ->
                if (!exitIp.isNullOrBlank()) "已保护 · 出口 $exitIp" else "已保护"
            ProtectionLevel.DEGRADED -> "保护降级 · 代理异常"
            ProtectionLevel.BLOCKED -> "网络已阻断（防泄露）"
        }
}
