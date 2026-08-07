package com.vpn.member.vpn

object AppRouteMode {
    const val FULL = "full"
    const val SPLIT = "split"

    fun fromDomesticDirectEnabled(enabled: Boolean): String = if (enabled) SPLIT else FULL

    fun isDomesticDirectEnabled(routeMode: String?): Boolean =
        routeMode?.equals(SPLIT, ignoreCase = true) == true

    fun normalizeStoredRouteMode(routeMode: String?): String =
        when {
            routeMode?.equals(FULL, ignoreCase = true) == true -> FULL
            routeMode?.equals(SPLIT, ignoreCase = true) == true -> SPLIT
            else -> FULL
        }

    /** App 默认全局（full），所有流量走代理。 */
    fun defaultForProfile(profile: String?): String = FULL
}
