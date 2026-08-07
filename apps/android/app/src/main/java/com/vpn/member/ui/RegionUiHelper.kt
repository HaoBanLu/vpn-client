package com.vpn.member.ui

import com.vpn.member.data.api.NodeItem
import com.vpn.member.data.api.RegionItem

fun regionDisplayName(code: String?, regions: List<RegionItem>): String {
    if (code.isNullOrBlank()) return "智能"
    val matched = regions.firstOrNull { it.code.equals(code, ignoreCase = true) }
    return matched?.name?.takeIf { it.isNotBlank() } ?: code.uppercase()
}

fun RegionItem.displayLabel(): String = name?.takeIf { it.isNotBlank() } ?: code.uppercase()

fun nodeRegionLabel(region: String?, regionName: String?): String {
    val label = regionName?.takeIf { it.isNotBlank() }
        ?: region?.takeIf { it.isNotBlank() }?.uppercase()
    return label ?: "未知"
}

/** 节点名展示：去掉 @apps/、apps/ 等路径式前缀。 */
fun displayNodeName(name: String?): String {
    if (name.isNullOrBlank()) return ""
    var text = name.trim().removePrefix("@apps/").removePrefix("@")
    if (text.startsWith("apps/", ignoreCase = true)) {
        text = text.substringAfter('/')
    }
    return text
}

fun NodeItem.isOnline(): Boolean = status.equals("online", ignoreCase = true)
