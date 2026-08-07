package com.vpn.member.vpn

import com.vpn.member.debug.AppDebugLogger
import java.io.File

/** 将 /client/config 返回的 Clash YAML 写入 Mihomo 配置目录（目录内需有 config.yaml）。 */
object ClashConfigSanitizer {
    fun prepareConfigDirectory(
        configYaml: String,
        configDir: File,
        geoReady: Boolean = true,
        rulesetsReady: Boolean = false,
        directBypassRules: List<DirectBypassRule> = emptyList(),
    ) {
        var yaml = configYaml.trim()
        if (!geoReady) {
            yaml = stripRemoteGeoRules(yaml)
            yaml = stripGeoipDnsFallback(yaml)
        }
        if (rulesetsReady) {
            yaml = localizeRuleProviders(yaml)
        } else {
            yaml = stripRemoteRuleProviders(yaml)
        }
        yaml = ClashDirectBypassPatcher.inject(yaml, directBypassRules)
        yaml =
            if (isDomesticReturnProfileYaml(yaml)) {
                hardenDomesticReturnDnsForAbroad(yaml)
            } else {
                preferOverseasFriendlyDns(yaml)
            }
        yaml = ensureSnifferForFullTunnel(yaml)
        yaml = ensureAndroidTunHints(yaml)
        validateClashYaml(yaml)

        configDir.mkdirs()
        repairCorruptedConfigPath(configDir)
        clearStaleOverrideFiles(configDir)

        val configFile = File(configDir, "config.yaml")
        val tempFile = File(configDir, "config.yaml.tmp")
        tempFile.writeText(yaml)
        if (!tempFile.renameTo(configFile)) {
            configFile.writeText(yaml)
            tempFile.delete()
        }

        AppDebugLogger.info(
            category = "mihomo",
            message = "config.yaml 已写入",
            context = mapOf("bytes" to yaml.length.toString(), "has_proxies" to yaml.contains("\nproxies:").toString()),
        )
    }

    fun validateClashYaml(yaml: String) {
        if (yaml.isBlank()) {
            error("android: config empty")
        }
        if (yaml.trimStart().startsWith("{")) {
            error("android: config is JSON, expected Clash YAML")
        }
        val hasProxiesSection =
            yaml.contains("\nproxies:") ||
                yaml.startsWith("proxies:") ||
                yaml.contains("\nproxy-providers:")
        if (!hasProxiesSection) {
            error("android: config missing proxies section")
        }
        val proxiesBody =
            yaml
                .substringAfter("proxies:")
                .substringBefore("proxy-groups:")
                .substringBefore("proxy-providers:")
        if (!Regex("""(?m)^\s*-\s*name:\s*\S+""").containsMatchIn(proxiesBody)) {
            error("android: config proxies section is empty")
        }
    }

    /** 无本地 geodata 时移除 GEOSITE/GEOIP，避免 Mihomo 首连从 GitHub 拉取失败。 */
    internal fun stripRemoteGeoRules(yaml: String): String {
        val lines = yaml.lines()
        val filtered =
            lines.filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("- GEOSITE,") || trimmed.startsWith("- GEOIP,")
            }
        if (filtered.size != lines.size) {
            AppDebugLogger.info(
                category = "mihomo",
                message = "已移除 GEOSITE/GEOIP 规则（无本地 geodata）",
            )
        }
        return filtered.joinToString("\n")
    }

    /** 无本地 geoip.metadb 时关闭 DNS fallback-filter.geoip，避免 Mihomo 从 GitHub 拉 MMDB。 */
    internal fun stripGeoipDnsFallback(yaml: String): String {
        var result = yaml
        val geoipTrue = Regex("""(?m)^(\s*)geoip:\s*true\s*$""")
        if (geoipTrue.containsMatchIn(result)) {
            result = geoipTrue.replace(result) { match -> "${match.groupValues[1]}geoip: false" }
            AppDebugLogger.info(
                category = "mihomo",
                message = "已关闭 DNS fallback-filter.geoip（无本地 geoip.metadb）",
            )
        }
        return result
            .lines()
            .filterNot { line -> line.trim().startsWith("geoip-code:") }
            .joinToString("\n")
            .let { if (it.isEmpty()) it else "$it\n" }
    }

    /** 无本地 ruleset 时移除 rule-providers 与 RULE-SET，避免 Mihomo 从 CDN 拉取失败（弱网/缅甸等）。 */
    internal fun stripRemoteRuleProviders(yaml: String): String {
        var result = yaml
        if (result.contains("rule-providers:")) {
            result =
                Regex("(?ms)^rule-providers:.*?(?=^\\S|\\z)")
                    .replace(result, "")
                    .trimEnd()
        }
        val lines =
            result.lines().filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("- RULE-SET,")
            }
        val stripped = lines.joinToString("\n").trimEnd()
        val normalized = if (stripped.isEmpty()) stripped else "$stripped\n"
        if (normalized != yaml) {
            AppDebugLogger.info(
                category = "mihomo",
                message = "已移除远程 rule-providers（无本地 ruleset）",
            )
        }
        return normalized
    }

    /** 内置 ruleset 已解压时改为 file 类型，避免首连从 CDN 拉规则失败。 */
    internal fun localizeRuleProviders(yaml: String): String {
        if (!yaml.contains("rule-providers:")) return yaml
        val localized =
            yaml
                .replace(
                    Regex(
                        """(?ms)(  (?:reject|cn):\n    )type: http\n    behavior: domain\n    url: [^\n]+\n    path: (\./ruleset/[^\n]+)\n    interval: \d+""",
                    ),
                    "$1type: file\n    behavior: domain\n    path: $2",
                ).replace("./ruleset/", "./providers/ruleset/")
        if (localized != yaml) {
            AppDebugLogger.info(category = "mihomo", message = "rule-providers 已切换为本地 file")
        }
        VpnDiag.step(
            "rule_providers",
            extras =
                mapOf(
                    "cn_path" to (Regex("path: (\\./providers/ruleset/cn\\.yaml)").find(localized)?.groupValues?.getOrNull(1) ?: "-"),
                    "reject_path" to (Regex("path: (\\./providers/ruleset/reject\\.yaml)").find(localized)?.groupValues?.getOrNull(1) ?: "-"),
                ),
        )
        return localized
    }

    /** 回国 profile 保留国内 DNS（doh.pub 等），供抖音/头条解析。 */
    internal fun isDomesticReturnProfileYaml(yaml: String): Boolean =
        yaml.contains("app-profile: domestic_return")

    /**
     * 回国加速在海外落地：default-nameserver / nameserver 中的国内 DNS 从缅甸等地直连易超时，
     * 导致 TUN 内 App 解析失败；mixed-port 探测仍可能通过（假连）。
     * 保留 fallback 远程 DoH（经代理），由 fake-ip + sniffer 承载国内站访问。
     */
    internal fun hardenDomesticReturnDnsForAbroad(yaml: String): String {
        val chinaMarkers =
            listOf(
                "doh.pub",
                "alidns",
                "223.5.5.5",
                "114.114.114.114",
                "119.29.29.29",
            )
        val sections = setOf("default-nameserver:", "nameserver:")
        var inTargetSection = false
        var changed = false
        val filtered =
            yaml.lines().filter { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("nameserver-policy:") -> {
                        inTargetSection = false
                        true
                    }
                    sections.any { trimmed.startsWith(it) } -> {
                        inTargetSection = true
                        true
                    }
                    trimmed.startsWith("fallback:") -> {
                        inTargetSection = false
                        true
                    }
                    inTargetSection && trimmed.startsWith("- ") -> {
                        val drop = chinaMarkers.any { marker -> trimmed.contains(marker, ignoreCase = true) }
                        if (drop) changed = true
                        !drop
                    }
                    inTargetSection && trimmed.isNotEmpty() && !trimmed.startsWith("-") && !line.startsWith("  ") -> {
                        inTargetSection = false
                        true
                    }
                    else -> true
                }
            }.toMutableList()
        if (!changed) return yaml
        injectDnsEntriesIfEmpty(filtered, "default-nameserver:", listOf("1.1.1.1", "8.8.8.8"))
        injectDnsEntriesIfEmpty(filtered, "nameserver:", listOf("1.1.1.1", "8.8.8.8"))
        AppDebugLogger.info(
            category = "mihomo",
            message = "回国模式已替换海外不可达的国内 DNS 引导项",
        )
        return filtered.joinToString("\n").let { if (it.isEmpty()) it else "$it\n" }
    }

    private fun injectDnsEntriesIfEmpty(
        lines: MutableList<String>,
        header: String,
        entries: List<String>,
    ) {
        val idx = lines.indexOfFirst { it.trim() == header }
        if (idx < 0) return
        var next = idx + 1
        while (next < lines.size && lines[next].trim().startsWith("- ")) {
            next++
        }
        if (next > idx + 1) return
        val injected = entries.map { "    - $it" }
        lines.addAll(idx + 1, injected)
    }

    /** 移除国内 DNS（缅甸/曼谷等弱网易超时），与后端 overseas_weak App 配置对齐。 */
    internal fun preferOverseasFriendlyDns(yaml: String): String {
        val chinaMarkers =
            listOf(
                "doh.pub",
                "alidns",
                "223.5.5.5",
                "114.114.114.114",
            )
        val lines = yaml.lines()
        var inNameserver = false
        var changed = false
        val filtered =
            lines.filter { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("nameserver:") -> {
                        inNameserver = true
                        true
                    }
                    inNameserver && trimmed.startsWith("fallback:") -> {
                        inNameserver = false
                        true
                    }
                    inNameserver && trimmed.startsWith("- ") -> {
                        val drop = chinaMarkers.any { marker -> trimmed.contains(marker, ignoreCase = true) }
                        if (drop) changed = true
                        !drop
                    }
                    inNameserver && trimmed.isNotEmpty() && !trimmed.startsWith("-") && !line.startsWith("  ") -> {
                        inNameserver = false
                        true
                    }
                    else -> true
                }
            }
        if (!changed) return yaml
        AppDebugLogger.info(
            category = "mihomo",
            message = "已移除国内 DNS（海外弱网优化）",
        )
        return filtered.joinToString("\n").let { if (it.isEmpty()) it else "$it\n" }
    }

    /** 全流量 MATCH,GLOBAL 时启用嗅探，辅助 fake-ip 与 TUN 路由。 */
    internal fun ensureSnifferForFullTunnel(yaml: String): String {
        if (!yaml.contains("MATCH,GLOBAL")) return yaml
        if (yaml.contains("\nsniffer:") || yaml.startsWith("sniffer:")) return yaml
        val block =
            """
            sniffer:
              enable: true
              override-destination: true
            """.trimIndent()
        AppDebugLogger.info(category = "mihomo", message = "已为全流量模式启用 sniffer")
        return yaml.trimEnd() + "\n\n$block\n"
    }

    /** TUN 模式：仅追加物理网 DNS；上游以配置内 nameserver 为准，避免 172.19.0.2 环路。 */
    internal fun ensureAndroidTunHints(yaml: String): String {
        if (yaml.contains("clash-for-android:")) return yaml
        return yaml.trimEnd() + "\n\nclash-for-android:\n  append-system-dns: false\n"
    }
}

private fun repairCorruptedConfigPath(configDir: File) {
    val configFile = File(configDir, "config.yaml")
    if (configFile.isDirectory) {
        configFile.deleteRecursively()
    }
}

private fun clearStaleOverrideFiles(configDir: File) {
    configDir.listFiles()?.forEach { file ->
        if (file.name == "config.yaml" || file.name == "config.yaml.tmp") return@forEach
        if (file.name.contains("override", ignoreCase = true) || file.name.endsWith(".json")) {
            file.delete()
        }
    }
}
