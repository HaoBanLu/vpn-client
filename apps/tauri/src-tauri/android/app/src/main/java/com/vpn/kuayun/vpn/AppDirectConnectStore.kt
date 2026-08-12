package com.vpn.kuayun.vpn

import android.content.Context

/**
 * 应用直连：用户勾选的包名写入 SharedPreferences，TUN 建立时
 * [VpnService.Builder.addDisallowedApplication] 旁路。
 */
object AppDirectConnectStore {
    private const val PREFS = "kuayun_vpn_prefs"
    private const val KEY_PACKAGES = "direct_connect_packages"

    fun userSelectedPackages(context: Context): Set<String> =
        normalizePackages(
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_PACKAGES, emptySet())
                .orEmpty(),
        )

    fun directConnectCount(context: Context): Int = userSelectedPackages(context).size

    fun setUserSelectedPackages(context: Context, packages: Collection<String>) {
        val normalized = normalizePackages(packages)
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PACKAGES, normalized)
            .apply()
    }

    fun normalizePackages(packages: Collection<String>): Set<String> {
        val out = LinkedHashSet<String>()
        packages.forEach { pkg ->
            val trimmed = pkg.trim()
            if (trimmed.isNotEmpty()) out.add(trimmed)
        }
        return out
    }
}
