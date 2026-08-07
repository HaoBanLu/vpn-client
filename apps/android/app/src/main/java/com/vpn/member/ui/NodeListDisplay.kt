package com.vpn.member.ui

import com.vpn.member.data.api.NodeItem
import com.vpn.member.ui.displayNodeName
import com.vpn.member.ui.isOnline
import com.vpn.member.vpn.AppProtocolSupport

/** 节点列表展示：筛选、排序、标签与地区行简化（P0 选线体验）。 */
object NodeListDisplay {
    fun filterConnectable(
        nodes: List<NodeItem>,
        filterRegion: String?,
    ): List<NodeItem> =
        nodes
            .filter { node ->
                filterRegion == null ||
                    node.region?.equals(filterRegion, ignoreCase = true) == true
            }
            .filter { AppProtocolSupport.isAppConnectable(it) }
            .filter { it.isOnline() }

    /** 已测延迟升序；未测或失败排在后面，同组按名称。 */
    fun sortByLatency(
        nodes: List<NodeItem>,
        latencyMap: Map<Long, Int>,
    ): List<NodeItem> =
        nodes.sortedWith(
            compareBy<NodeItem> { node ->
                val ms = latencyMap[node.id]
                ms == null || ms <= 0
            }.thenBy { node ->
                val ms = latencyMap[node.id]
                if (ms != null && ms > 0) ms else Int.MAX_VALUE
            }.thenBy { node ->
                displayNodeName(node.name)
            },
        )

    fun fastestNodeId(
        nodes: List<NodeItem>,
        latencyMap: Map<Long, Int>,
    ): Long? =
        nodes
            .mapNotNull { node ->
                latencyMap[node.id]?.takeIf { it > 0 }?.let { node.id to it }
            }
            .minByOrNull { it.second }
            ?.first

    /** 已选地区 Tab 时不再重复展示地区行。 */
    fun shouldShowRegionLine(
        filterRegion: String?,
        nodeRegion: String?,
    ): Boolean {
        if (filterRegion.isNullOrBlank()) return true
        return !filterRegion.equals(nodeRegion, ignoreCase = true)
    }

    /**
     * 筛选大陆等地区时，去掉与 Tab 语义重复的场景标签。
     * 例如 Tab=中国大陆 时隐藏「适合回国」。
     */
    fun displaySceneTags(
        tags: List<String>?,
        filterRegion: String?,
    ): List<String> {
        val raw = tags.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        if (raw.isEmpty()) return emptyList()
        val hideReturnHome =
            filterRegion.equals("cn", ignoreCase = true) ||
                filterRegion.equals("china", ignoreCase = true)
        return if (hideReturnHome) {
            raw.filterNot { it == "适合回国" }
        } else {
            raw
        }
    }
}
