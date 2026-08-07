package com.vpn.member.vpn

import android.content.Context
import com.vpn.member.data.local.AppPreferences

/**
 * 应用直连：系统层 bypass VPN TUN 的包名集合。
 *
 * 跨云自身**不再**强制写入 disallow：自身旁路会导致
 * [TunDataPlaneVerifier] 的系统 VPN Network 探测与浏览器不同路，
 * 出现「mixed 通 / vpn_network_ok 永远 false」的假阴性（换 TUN 栈无效）。
 * Mihomo→节点仍靠 [VpnProtector.protect]；本机 127.0.0.1 mixed-port 不进 TUN。
 */
object AppDirectConnectStore {
    fun userSelectedPackages(preferences: AppPreferences): Set<String> =
        preferences.getDirectConnectPackages()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun directConnectCount(preferences: AppPreferences): Int = userSelectedPackages(preferences).size

    @Suppress("UNUSED_PARAMETER")
    fun directPackages(context: Context, preferences: AppPreferences): Set<String> =
        normalizePackages(userSelectedPackages(preferences))

    /** 清洗用户勾选的直连包名；不自动加入跨云自身。 */
    fun normalizePackages(packages: Set<String>): Set<String> {
        val out = LinkedHashSet<String>()
        packages.forEach { pkg ->
            val trimmed = pkg.trim()
            if (trimmed.isNotEmpty()) {
                out.add(trimmed)
            }
        }
        return out
    }

    @Deprecated(
        message = "自身不再强制 bypass，请用 normalizePackages",
        replaceWith = ReplaceWith("normalizePackages(packages)"),
    )
    fun mergeWithSelf(packages: Set<String>, selfPackage: String): Set<String> {
        // 保留 selfPackage 参数签名以免旧测试/调用立刻崩；行为已改为不强制加入自身。
        @Suppress("UNUSED_VARIABLE")
        val ignored = selfPackage
        return normalizePackages(packages)
    }
}
