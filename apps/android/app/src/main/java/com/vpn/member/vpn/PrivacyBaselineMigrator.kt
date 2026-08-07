package com.vpn.member.vpn

import com.vpn.member.data.local.AppPreferences

/** 隐私基线默认值迁移（老用户升级时一次性执行）。 */
object PrivacyBaselineMigrator {
    /** v2：连接失败默认不断网，避免弱网/探测误杀时全机无网。 */
    const val CURRENT_VERSION = 2

    /** 老用户从未改过 Kill Switch 时迁移为开启。 */
    fun shouldMigrateKillSwitchToEnabled(hasUserModifiedKillSwitch: Boolean): Boolean =
        !hasUserModifiedKillSwitch

    fun migrateIfNeeded(preferences: AppPreferences) {
        val version = preferences.getPrivacyBaselineVersion()
        if (version >= CURRENT_VERSION) return

        if (version < 1) {
            if (shouldMigrateKillSwitchToEnabled(preferences.hasUserModifiedKillSwitch())) {
                preferences.setKillSwitchEnabled(true)
            }
            preferences.setIpv6LeakProtectionEnabled(true)
            preferences.setReconnectKillSwitchHoldEnabled(true)
        }
        if (version < 2) {
            // 仅「已连接后意外断开」用 Kill Switch；连接失败阻断改为默认关闭。
            preferences.setBlockOnConnectFailureEnabled(false)
        }
        preferences.setPrivacyBaselineVersion(CURRENT_VERSION)
    }
}
