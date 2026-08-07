package com.vpn.member.vpn

import android.content.Context
import android.net.VpnService
import android.os.Build
import android.provider.Settings

/**
 * Always-On / Lockdown 检测（各 ROM 差异大，best-effort）。
 * 无法可靠检测时返回 false，由引导页提示用户手动确认。
 */
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
        // 无 Settings 权限或 ROM 魔改时，用 prepare 作弱信号（非空表示尚未授权本 App 为 Always-On）
        val vpnPrepared = VpnService.prepare(context) != null
        return Status(
            alwaysOnConfigured = alwaysOn || (!vpnPrepared && alwaysOn),
            lockdownConfigured = lockdown,
        )
    }
}
