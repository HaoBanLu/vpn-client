package com.vpn.member.vpn

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vpn.member.data.local.AppPreferences
import java.lang.reflect.Type
import java.net.Inet6Address
import java.net.InetAddress
import java.util.UUID

/** 规则直连：本地存储、校验与 Clash 行转换。 */
object DirectBypassRuleStore {
    private val gson = Gson()
    // R8 会抹掉匿名 TypeToken 的泛型签名，必须用 getParameterized
    private val ruleListType: Type =
        TypeToken.getParameterized(List::class.java, DirectBypassRule::class.java).type

    private val hostnameRegex =
        Regex(
            """^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)*[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$""",
        )

    fun loadRules(preferences: AppPreferences): List<DirectBypassRule> =
        parseJson(preferences.getDirectBypassRulesJson())

    fun saveRules(preferences: AppPreferences, rules: List<DirectBypassRule>) {
        preferences.setDirectBypassRulesJson(toJson(rules))
    }

    fun enabledRules(preferences: AppPreferences): List<DirectBypassRule> =
        loadRules(preferences).filter { it.enabled }

    fun enabledCount(preferences: AppPreferences): Int = enabledRules(preferences).size

    fun validateAndNormalize(type: DirectBypassRuleType, rawValue: String): Result<String> {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("规则内容不能为空"))
        }
        return when (type) {
            DirectBypassRuleType.DOMAIN,
            DirectBypassRuleType.DOMAIN_SUFFIX,
            -> validateHostname(trimmed, allowWildcardPrefix = type == DirectBypassRuleType.DOMAIN_SUFFIX)
            DirectBypassRuleType.DOMAIN_KEYWORD -> validateKeyword(trimmed)
            DirectBypassRuleType.IP_CIDR -> validateIpCidr(trimmed)
        }
    }

    fun dedupeForClash(rules: List<DirectBypassRule>): List<DirectBypassRule> {
        val seen = LinkedHashSet<String>()
        val result = ArrayList<DirectBypassRule>()
        rules.forEach { rule ->
            if (!rule.enabled) return@forEach
            val key = "${rule.type.name}:${rule.value.lowercase()}"
            if (seen.add(key)) {
                result.add(rule)
            }
        }
        return result
    }

    fun toClashLine(rule: DirectBypassRule): String {
        val prefix = "- ${rule.type.clashType},${rule.value},DIRECT"
        return if (rule.type == DirectBypassRuleType.IP_CIDR) {
            "$prefix,no-resolve"
        } else {
            prefix
        }
    }

    fun createRule(type: DirectBypassRuleType, rawValue: String): Result<DirectBypassRule> =
        validateAndNormalize(type, rawValue).map { normalized ->
            DirectBypassRule(
                id = UUID.randomUUID().toString(),
                type = type,
                value = normalized,
                enabled = true,
            )
        }

    internal fun parseJson(json: String): List<DirectBypassRule> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<DirectBypassRule>>(json, ruleListType)
                ?.filter { rule ->
                    rule.value.trim().isNotEmpty() &&
                        DirectBypassRuleType.fromStored(rule.type.name) != null
                }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    internal fun toJson(rules: List<DirectBypassRule>): String = gson.toJson(rules)

    private fun validateHostname(value: String, allowWildcardPrefix: Boolean): Result<String> {
        var normalized = value.lowercase()
        if (allowWildcardPrefix && normalized.startsWith("*.")) {
            normalized = normalized.removePrefix("*.")
        }
        if (normalized.length > 253 || !hostnameRegex.matches(normalized)) {
            return Result.failure(IllegalArgumentException("域名格式不正确"))
        }
        return Result.success(normalized)
    }

    private fun validateKeyword(value: String): Result<String> {
        val normalized = value.trim()
        if (normalized.length > 64) {
            return Result.failure(IllegalArgumentException("关键词长度不能超过 64 个字符"))
        }
        if (normalized.contains(',')) {
            return Result.failure(IllegalArgumentException("关键词不能包含逗号"))
        }
        return Result.success(normalized)
    }

    private fun validateIpCidr(value: String): Result<String> {
        val parts = value.split("/")
        if (parts.size != 2) {
            return Result.failure(IllegalArgumentException("IP 段格式应为 192.168.1.0/24"))
        }
        val ipPart = parts[0].trim()
        val prefix =
            parts[1].trim().toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("子网掩码长度无效"))
        val address =
            runCatching { InetAddress.getByName(ipPart) }
                .getOrElse { return Result.failure(IllegalArgumentException("IP 地址无效")) }
        val maxPrefix = if (address is Inet6Address) 128 else 32
        if (prefix !in 0..maxPrefix) {
            return Result.failure(IllegalArgumentException("子网掩码长度应在 0-$maxPrefix 之间"))
        }
        val host = address.hostAddress ?: ipPart
        return Result.success("$host/$prefix")
    }
}
