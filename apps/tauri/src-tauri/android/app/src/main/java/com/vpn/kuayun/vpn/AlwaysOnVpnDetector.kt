package com.vpn.kuayun.vpn

import android.content.Context
import android.net.VpnService
import android.os.Build
import android.provider.Settings

/** Always-On / Lockdown 检测（各 ROM 差异大，best-effort）。 */
object AlwaysOnVpnDetector {
    data class Status(
        val alwaysOnConfigured: Boolean = false,
        val lockdownConfigured: Boolean = false,
    ) {
        val isHardened: Boolean get() = alwaysOnConfigured && lockdownConfigured
    }

    fun detect(context: Context, packageName: String = context.packageName): Status {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return Status()
        }
        val alwaysOn =
            runCatching {
                Settings.Secure.getString(context.contentResolver, "always_on_vpn_app")
            }.getOrNull()?.trim() == packageName
        val lockdown =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    Settings.Secure.getInt(context.contentResolver, "always_on_vpn_lockdown", 0)
                }.getOrDefault(0) == 1 && alwaysOn
            } else {
                false
            }
        return Status(
            alwaysOnConfigured = alwaysOn,
            lockdownConfigured = lockdown,
        )
    }

    /** 是否已授予 VPN 权限（非 Always-on）。 */
    fun isVpnPrepared(context: Context): Boolean = VpnService.prepare(context) == null
}
