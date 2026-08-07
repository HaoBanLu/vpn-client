package com.vpn.member.vpn

/**
 * DNS 抖动降噪：自动重连开启时仍走完整重连，但加长防抖以合并 dns 风暴。
 * 不改变「禁止 HEAL 当恢复」的产品规则。
 */
object DnsChurnPolicy {
    /** 相对 [NetworkRestorePolicy.RECONNECT_DEBOUNCE_MS] 的更长窗口。 */
    const val DNS_DEBOUNCE_MS = 3_000L

    fun isDnsOnlyReason(reason: String): Boolean {
        val r = reason.trim().lowercase()
        return r == "dns_changed" ||
            r == "transport_dns_changed" ||
            r.endsWith("_dns_changed")
    }

    fun reconnectDebounceMs(reason: String): Long =
        if (isDnsOnlyReason(reason)) DNS_DEBOUNCE_MS else NetworkRestorePolicy.RECONNECT_DEBOUNCE_MS
}
