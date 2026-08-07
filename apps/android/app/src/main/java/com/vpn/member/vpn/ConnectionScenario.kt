package com.vpn.member.vpn

data class ResolvedConnectionConfig(
    val profile: String,
    val routeMode: String,
)

object ConnectionScenario {
    const val AUTO = "auto"
    const val RETURN_HOME = "return_home"
    const val OVERSEAS = "overseas"

    const val PROFILE_DOMESTIC_RETURN = "domestic_return"
    const val PROFILE_OVERSEAS_WEAK = "overseas_weak"

    fun normalize(raw: String?): String =
        when (raw?.trim()?.lowercase()) {
            RETURN_HOME, "return-home", "returnhome", PROFILE_DOMESTIC_RETURN -> RETURN_HOME
            OVERSEAS, PROFILE_OVERSEAS_WEAK -> OVERSEAS
            else -> AUTO
        }

    fun label(scenario: String?): String =
        when (normalize(scenario)) {
            RETURN_HOME -> "回国加速"
            OVERSEAS -> "海外访问"
            else -> "自动"
        }

    fun isDomesticReturnProfile(profile: String?): Boolean =
        profile?.trim()?.equals(PROFILE_DOMESTIC_RETURN, ignoreCase = true) == true

    /** 选中国大陆/回国专线节点时，自动场景应走回国 profile（抖音、头条等）。 */
    fun inferDomesticReturnFromNode(region: String?, accessMode: String?): Boolean {
        val code = region?.trim()?.lowercase().orEmpty()
        if (code == "cn" || code == "china" || code.contains("中国")) return true
        return accessMode.equals("relay", ignoreCase = true)
    }

    /**
     * 解析 profile / route_mode。
     *
     * 当前默认全部为全局（full）：所有流量走所选节点，查 IP 显示节点出口。
     * 「自动」在选中 cn/回国专线节点时推断为 [PROFILE_DOMESTIC_RETURN]。
     */
    fun resolve(
        scenario: String?,
        nodeRegion: String? = null,
        accessMode: String? = null,
    ): ResolvedConnectionConfig {
        return when (normalize(scenario)) {
            RETURN_HOME ->
                ResolvedConnectionConfig(PROFILE_DOMESTIC_RETURN, AppRouteMode.FULL)
            OVERSEAS ->
                ResolvedConnectionConfig(PROFILE_OVERSEAS_WEAK, AppRouteMode.FULL)
            else ->
                if (inferDomesticReturnFromNode(nodeRegion, accessMode)) {
                    ResolvedConnectionConfig(PROFILE_DOMESTIC_RETURN, AppRouteMode.FULL)
                } else {
                    ResolvedConnectionConfig(PROFILE_OVERSEAS_WEAK, AppRouteMode.FULL)
                }
        }
    }
}
