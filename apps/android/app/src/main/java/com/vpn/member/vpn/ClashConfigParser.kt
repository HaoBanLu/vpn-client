package com.vpn.member.vpn

/**
 * 从 Clash YAML 解析占线用的真实节点名（排除「自动选择」「手动选择」等组名）。
 */
object ClashConfigParser {
    private val GROUP_HEADER = Regex("""^\s*-\s*name:\s*(.+)\s*$""")
    private val PROXY_LINE = Regex("""^\s*-\s*(.+)\s*$""")

    /** proxies: 段第一个叶子节点名（与 Mihomo 实际出站一致）。 */
    fun resolveLeafProxyName(yaml: String): String? {
        var inProxiesSection = false
        for (line in yaml.lines()) {
            when {
                line.trim() == "proxies:" -> inProxiesSection = true
                inProxiesSection && line.trim() == "proxy-groups:" -> break
                inProxiesSection -> {
                    val match = Regex("""^\s*-\s*name:\s*(.+)\s*$""").find(line) ?: continue
                    val name = match.groupValues[1].trim()
                    if (LineAcquireNode.isAcquirable(name)) return name
                }
            }
        }
        return null
    }

    fun resolveEffectiveNode(yaml: String): String? {
        if (yaml.isBlank()) return null
        val modeGlobal = yaml.contains("\nmode: global") || yaml.startsWith("mode: global")
        val preferredGroup = if (modeGlobal) "GLOBAL" else "手动选择"
        return findFirstAcquirableProxyInGroup(yaml, preferredGroup)
            ?: findFirstAcquirableProxyInGroup(yaml, "手动选择")
            ?: findFirstAcquirableProxy(yaml)
    }

    private fun findFirstAcquirableProxyInGroup(yaml: String, groupName: String): String? {
        val lines = yaml.lines()
        var inTargetGroup = false
        var inProxies = false
        for (line in lines) {
            val header = GROUP_HEADER.find(line.trim())
            if (header != null) {
                inTargetGroup = header.groupValues[1].trim() == groupName
                inProxies = false
                continue
            }
            if (!inTargetGroup) continue
            if (line.trim() == "proxies:") {
                inProxies = true
                continue
            }
            if (inProxies) {
                if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("\t")) {
                    break
                }
                PROXY_LINE.matchEntire(line.trim())?.groupValues?.get(1)?.trim()?.let { proxy ->
                    if (LineAcquireNode.isAcquirable(proxy)) return proxy
                }
            }
        }
        return null
    }

    private fun findFirstAcquirableProxy(yaml: String): String? {
        var inProxiesSection = false
        for (line in yaml.lines()) {
            if (line.trim() == "proxies:") {
                inProxiesSection = true
                continue
            }
            if (!inProxiesSection) continue
            if (line.trim() == "proxy-groups:") break
            PROXY_LINE.matchEntire(line.trim())?.groupValues?.get(1)?.trim()?.let { name ->
                if (LineAcquireNode.isAcquirable(name)) return name
            }
        }
        return null
    }
}
