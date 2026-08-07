package com.vpn.member.vpn

/** 数据面断开 / 隧道校验失败的用户可见文案（按场景细分，避免误导改 TUN 栈）。 */
object DataplaneFailureMessages {
    fun degradedDisconnect(
        nodeName: String,
        domesticReturn: Boolean,
        tunStackRetried: Boolean,
        overseasTimezone: Boolean = OverseasLocaleHint.isOverseasTimezone(),
    ): String {
        val node = nodeName.trim().ifBlank { "当前节点" }
        if (domesticReturn) {
            return if (overseasTimezone) {
                "回国节点「$node」从当前地区长时间不可达，已断开。请更换节点，或改用「海外模式」后重试"
            } else {
                "回国节点「$node」长时间无响应，已断开。请更换其他回国节点或稍后重试"
            }
        }
        if (tunStackRetried) {
            return "隧道数据面长时间不可用，已断开。请切换节点或检查网络"
        }
        return "隧道数据面长时间不可用，已断开。请切换节点或在「连接与隐私」中改用 gvisor 栈"
    }

    fun tunnelVerifyFailed(
        domesticReturn: Boolean,
        overseasTimezone: Boolean = OverseasLocaleHint.isOverseasTimezone(),
        nodeName: String? = null,
    ): String {
        val nodeSuffix = nodeName?.trim()?.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
        if (domesticReturn) {
            return if (overseasTimezone) {
                "回国线路从当前地区不可达$nodeSuffix，请更换节点或改用「海外模式」"
            } else {
                "回国节点不可达$nodeSuffix，请更换芜湖/上海/杭州等节点后重试"
            }
        }
        return "节点不可达：代理入口无响应$nodeSuffix，请更换节点后重试"
    }

    fun dataplaneInactive(tunStackRetried: Boolean): String =
        if (tunStackRetried) {
            "隧道数据面未生效，请切换节点或检查网络后重试"
        } else {
            "隧道数据面未生效，请断开重连；若仍失败可在「连接与隐私」中改用 gvisor 栈"
        }

    /** 连接阶段校验失败且已启用断网保护时的统一提示。 */
    fun connectFailedWithKillSwitch(nodeName: String? = null): String {
        val suffix = nodeName?.trim()?.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
        return "节点不可达$suffix，已启用断网保护"
    }
}
