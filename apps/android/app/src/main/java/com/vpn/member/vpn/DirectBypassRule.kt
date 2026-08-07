package com.vpn.member.vpn

/** 用户自定义 Mihomo 规则直连类型（Clash rules → DIRECT）。 */
enum class DirectBypassRuleType(val clashType: String, val label: String) {
    DOMAIN("DOMAIN", "完整域名"),
    DOMAIN_SUFFIX("DOMAIN-SUFFIX", "域名后缀"),
    DOMAIN_KEYWORD("DOMAIN-KEYWORD", "域名关键词"),
    IP_CIDR("IP-CIDR", "IP 段"),
    ;

    companion object {
        fun fromStored(value: String): DirectBypassRuleType? =
            entries.find { it.name == value.trim() }
    }
}

data class DirectBypassRule(
    val id: String,
    val type: DirectBypassRuleType,
    val value: String,
    val enabled: Boolean = true,
)
