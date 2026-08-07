package com.vpn.member.vpn

/** 占线 API 可识别的真实节点名（排除智能选路/selector 组标签）。 */
object LineAcquireNode {
    private val NON_NODE_TAGS =
        setOf(
            "自动选择",
            "手动选择",
            "智能选路",
            "auto",
            "manual",
        )

    fun isAcquirable(name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isBlank()) return false
        return trimmed !in NON_NODE_TAGS
    }

    fun resolve(
        selectedNode: String?,
        configNode: String?,
        effectiveNode: String?,
    ): String? =
        listOf(selectedNode, configNode, effectiveNode)
            .firstOrNull { isAcquirable(it) }
}
