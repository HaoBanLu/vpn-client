package com.vpn.member.vpn

import android.content.pm.PackageManager.NameNotFoundException
import android.net.VpnService
import android.util.Log

private const val TAG = "AppDirectConnectTun"

/** 将用户勾选的应用直连包名写入 VpnService.Builder（不含跨云自身强制 bypass）。 */
fun applyDirectConnectPackages(
    builder: VpnService.Builder,
    packages: Set<String>,
) {
    packages.forEach { pkg ->
        try {
            builder.addDisallowedApplication(pkg)
        } catch (e: NameNotFoundException) {
            Log.w(TAG, "skip direct connect package: $pkg", e)
        }
    }
}
