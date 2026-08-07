package com.vpn.member.data.local

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 注册页勾选服务条款与隐私政策后写入；geodata 下载以此为门控。 */
    fun isPrivacyAccepted(): Boolean = prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)

    fun setPrivacyAccepted() {
        prefs.edit().putBoolean(KEY_PRIVACY_ACCEPTED, true).apply()
    }

    fun getDismissedUpdateVersionCode(): Int = prefs.getInt(KEY_DISMISSED_UPDATE_VERSION_CODE, 0)

    fun setDismissedUpdateVersionCode(versionCode: Int) {
        prefs.edit().putInt(KEY_DISMISSED_UPDATE_VERSION_CODE, versionCode).apply()
    }

    fun getLastUpdateCheckAt(): Long = prefs.getLong(KEY_LAST_UPDATE_CHECK_AT, 0L)

    fun setLastUpdateCheckAt(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_AT, timestamp).apply()
    }

    /** 用户勾选的不走 VPN 的应用包名（不含跨云自身）。 */
    fun getDirectConnectPackages(): Set<String> =
        prefs.getStringSet(KEY_DIRECT_CONNECT_PACKAGES, emptySet())?.toSet().orEmpty()

    fun setDirectConnectPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_DIRECT_CONNECT_PACKAGES, packages.toSet()).apply()
    }

    /** 用户自定义 Mihomo 规则直连（JSON 数组）。 */
    fun getDirectBypassRulesJson(): String = prefs.getString(KEY_DIRECT_BYPASS_RULES, "").orEmpty()

    fun setDirectBypassRulesJson(json: String) {
        prefs.edit().putString(KEY_DIRECT_BYPASS_RULES, json).apply()
    }

    fun isAutoReconnectEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_RECONNECT, true)

    fun setAutoReconnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
    }

    fun isKillSwitchEnabled(): Boolean = prefs.getBoolean(KEY_KILL_SWITCH, true)

    fun setKillSwitchEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_KILL_SWITCH, enabled)
            .putBoolean(KEY_KILL_SWITCH_USER_MODIFIED, true)
            .apply()
    }

    fun hasUserModifiedKillSwitch(): Boolean = prefs.getBoolean(KEY_KILL_SWITCH_USER_MODIFIED, false)

    fun isIpv6LeakProtectionEnabled(): Boolean = prefs.getBoolean(KEY_IPV6_LEAK_PROTECTION, true)

    fun setIpv6LeakProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IPV6_LEAK_PROTECTION, enabled).apply()
    }

    fun isReconnectKillSwitchHoldEnabled(): Boolean = prefs.getBoolean(KEY_RECONNECT_KS_HOLD, true)

    fun setReconnectKillSwitchHoldEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECONNECT_KS_HOLD, enabled).apply()
    }

    /** 默认 false：连接失败不断网；避免探测误杀时全机无网。可在设置中手动开启。 */
    fun isBlockOnConnectFailureEnabled(): Boolean = prefs.getBoolean(KEY_BLOCK_ON_CONNECT_FAILURE, false)

    fun setBlockOnConnectFailureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_ON_CONNECT_FAILURE, enabled).apply()
    }

    fun getPrivacyBaselineVersion(): Int = prefs.getInt(KEY_PRIVACY_BASELINE_VERSION, 0)

    fun setPrivacyBaselineVersion(version: Int) {
        prefs.edit().putInt(KEY_PRIVACY_BASELINE_VERSION, version).apply()
    }

    fun getLastPrivacyProbeAt(): Long = prefs.getLong(KEY_LAST_PRIVACY_PROBE_AT, 0L)

    fun setLastPrivacyProbeAt(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_PRIVACY_PROBE_AT, timestamp).apply()
    }

    fun isBootAutoConnectEnabled(): Boolean = prefs.getBoolean(KEY_BOOT_AUTO_CONNECT, false)

    fun setBootAutoConnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BOOT_AUTO_CONNECT, enabled).apply()
    }

    fun isBatteryOptimizationGuideDismissed(): Boolean =
        prefs.getBoolean(KEY_BATTERY_GUIDE_DISMISSED, false)

    fun setBatteryOptimizationGuideDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_GUIDE_DISMISSED, dismissed).apply()
    }

    /** TUN 用户态栈：gvisor / system / mixed（mixed 仅自动恢复路径写入）。 */
    fun getTunStackMode(): String? = prefs.getString(KEY_TUN_STACK_MODE, null)

    fun setTunStackMode(mode: String) {
        prefs.edit().putString(KEY_TUN_STACK_MODE, mode.trim().lowercase()).apply()
    }

    /** 最近一次 TUN 栈自动切换说明（连接与隐私页展示）。 */
    fun getTunStackAutoSwitchNote(): String? =
        prefs.getString(KEY_TUN_STACK_AUTO_SWITCH_NOTE, null)?.takeIf { it.isNotBlank() }

    fun setTunStackAutoSwitchNote(fromStack: String, toStack: String) {
        prefs.edit()
            .putString(
                KEY_TUN_STACK_AUTO_SWITCH_NOTE,
                "上次连接已自动从 $fromStack 切换为 $toStack（数据面不可用）",
            )
            .apply()
    }

    fun clearTunStackAutoSwitchNote() {
        prefs.edit().remove(KEY_TUN_STACK_AUTO_SWITCH_NOTE).apply()
    }

    companion object {
        private const val PREFS_NAME = "vpn_member_prefs"
        private const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
        private const val KEY_DISMISSED_UPDATE_VERSION_CODE = "dismissed_update_version_code"
        private const val KEY_LAST_UPDATE_CHECK_AT = "last_update_check_at"
        private const val KEY_DIRECT_CONNECT_PACKAGES = "direct_connect_packages"
        private const val KEY_DIRECT_BYPASS_RULES = "direct_bypass_rules"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect_enabled"
        private const val KEY_KILL_SWITCH = "kill_switch_enabled"
        private const val KEY_KILL_SWITCH_USER_MODIFIED = "kill_switch_user_modified"
        private const val KEY_IPV6_LEAK_PROTECTION = "ipv6_leak_protection_enabled"
        private const val KEY_RECONNECT_KS_HOLD = "reconnect_kill_switch_hold"
        private const val KEY_BLOCK_ON_CONNECT_FAILURE = "block_on_connect_failure"
        private const val KEY_PRIVACY_BASELINE_VERSION = "privacy_baseline_version"
        private const val KEY_LAST_PRIVACY_PROBE_AT = "last_privacy_probe_at"
        private const val KEY_BOOT_AUTO_CONNECT = "boot_auto_connect_enabled"
        private const val KEY_BATTERY_GUIDE_DISMISSED = "battery_guide_dismissed"
        private const val KEY_TUN_STACK_MODE = "tun_stack_mode"
        private const val KEY_TUN_STACK_AUTO_SWITCH_NOTE = "tun_stack_auto_switch_note"
    }
}
