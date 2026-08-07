package com.vpn.member.vpn

import java.util.TimeZone

/** TUN 用户态栈模式；回国全流量默认 gvisor（system 在部分机型 TUN 不转发）。 */
object TunStackMode {
    const val SYSTEM = "system"
    const val GVISOR = "gvisor"
    const val MIXED = "mixed"

    fun resolve(raw: String?): String =
        when (raw?.trim()?.lowercase()) {
            SYSTEM, GVISOR, MIXED -> raw.trim().lowercase()
            else -> SYSTEM
        }

    /**
     * 未手动设置时：回国全流量优先 gvisor，其余默认 system。
     * 海外时区 + 回国全流量：强制 gvisor（忽略用户 system 偏好，避免 OPPO 等机型 TUN 不转发）。
     */
    fun resolveForSession(
        raw: String?,
        domesticReturnFull: Boolean,
        timezoneId: String = TimeZone.getDefault().id,
    ): String {
        if (domesticReturnFull && OverseasLocaleHint.isOverseasTimezone(timezoneId)) {
            return GVISOR
        }
        if (!raw.isNullOrBlank()) return resolve(raw)
        // 海外节点也默认 gvisor：部分国产机型 system 栈 TUN 不转发，会「已连接但无网」。
        return GVISOR
    }
}
