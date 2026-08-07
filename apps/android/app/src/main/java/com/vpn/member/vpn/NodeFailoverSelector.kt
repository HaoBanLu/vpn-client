package com.vpn.member.vpn

import com.vpn.member.data.api.NodeItem

/**
 * 健康探测连续失败后，在同区在线节点中选取备用节点（P2-4）。
 */
object NodeFailoverSelector {
    private fun NodeItem.isOnline(): Boolean = status.equals("online", ignoreCase = true)

    fun pickBackup(
        currentNodeName: String,
        currentRegion: String?,
        nodes: List<NodeItem>,
    ): NodeItem? {
        val current = nodes.find { it.name == currentNodeName } ?: return null
        val region =
            currentRegion?.trim()?.takeIf { it.isNotEmpty() }
                ?: current.region?.trim()?.takeIf { it.isNotEmpty() }

        val connectable =
            nodes.filter { node ->
                node.name != currentNodeName &&
                    node.isOnline() &&
                    AppProtocolSupport.isAppConnectable(node)
            }

        val sameRegion =
            if (region.isNullOrEmpty()) {
                connectable
            } else {
                connectable.filter { it.region.equals(region, ignoreCase = true) }
            }

        val candidates =
            sameRegion.ifEmpty {
                // 同区无备用时，回退同国家
                val country = current.country?.trim()?.takeIf { it.isNotEmpty() }
                if (country.isNullOrEmpty()) {
                    connectable
                } else {
                    connectable.filter { it.country.equals(country, ignoreCase = true) }
                }
            }

        if (candidates.isEmpty()) return null

        return candidates.minWithOrNull(
            compareBy<NodeItem> { it.latency_ms ?: Int.MAX_VALUE }.thenBy { it.name },
        )
    }
}
