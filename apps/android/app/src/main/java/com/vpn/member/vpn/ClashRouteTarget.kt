package com.vpn.member.vpn

/**
 * 解析应传给 Mihomo 的节点名（App 仅支持用户选定节点，不做智能选路）。
 */
object ClashRouteTarget {
    fun resolve(
        configNode: String?,
        configYaml: String,
        selectedNode: String?,
    ): String {
        configNode?.trim()?.takeIf { LineAcquireNode.isAcquirable(it) }?.let { return it }
        selectedNode?.trim()?.takeIf { LineAcquireNode.isAcquirable(it) }?.let { return it }
        ClashConfigParser.resolveEffectiveNode(configYaml)?.let { return it }
        error("android: no selected node for route target")
    }
}
