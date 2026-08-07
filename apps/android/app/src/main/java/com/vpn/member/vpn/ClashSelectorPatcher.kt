package com.vpn.member.vpn

/**
 * 将用户选定节点写入 Mihomo selector。
 * Proxy 组只能选子组（手动选择/回国专线），不能直接选叶子节点名。
 */
object ClashSelectorPatcher {
    private const val GROUP_PROXY = "Proxy"
    private const val GROUP_MANUAL = "手动选择"
    private const val GROUP_RELAY = "回国专线"
    private const val GROUP_GLOBAL = "GLOBAL"
    private const val GROUP_DIRECT_POOL = "海外直连"

    fun apply(target: String, patch: (group: String, selection: String) -> Boolean): Boolean {
        val node = target.trim()
        if (node.isBlank()) return false

        var ok = false
        ok = patch(GROUP_MANUAL, node) || ok
        ok = patch(GROUP_RELAY, node) || ok
        ok = patch(GROUP_GLOBAL, node) || ok
        ok = patch(GROUP_PROXY, GROUP_MANUAL) || ok
        // 海外直连池仅含 direct 出口；回国节点不在此组，失败属预期，不计入 ok。
        patch(GROUP_DIRECT_POOL, node)
        return ok
    }
}
