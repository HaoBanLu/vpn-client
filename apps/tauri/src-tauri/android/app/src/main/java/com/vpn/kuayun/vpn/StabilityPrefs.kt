package com.vpn.kuayun.vpn

import android.content.Context

/**
 * Android 稳定性偏好与开机恢复会话（SharedPreferences）。
 * 产品路径：系统 Always-on / lockdown 引导，不做自研防火墙 Kill Switch。
 */
object StabilityPrefs {
    private const val PREFS = "kuayun_vpn_prefs"
    private const val KEY_BOOT_AUTO = "boot_auto_connect"
    private const val KEY_WAS_CONNECTED = "session_was_connected"
    private const val KEY_LAST_CONFIG = "session_last_config"
    private const val KEY_LAST_NODE = "session_last_node"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBootAutoConnectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BOOT_AUTO, false)

    fun setBootAutoConnectEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BOOT_AUTO, enabled).apply()
    }

    fun wasUserConnected(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WAS_CONNECTED, false)

    fun lastConfig(context: Context): String =
        prefs(context).getString(KEY_LAST_CONFIG, "").orEmpty()

    fun lastNodeName(context: Context): String =
        prefs(context).getString(KEY_LAST_NODE, "智能选路").orEmpty().ifBlank { "智能选路" }

    fun markConnected(context: Context, config: String, nodeName: String) {
        if (config.isBlank()) return
        prefs(context)
            .edit()
            .putBoolean(KEY_WAS_CONNECTED, true)
            .putString(KEY_LAST_CONFIG, config)
            .putString(KEY_LAST_NODE, nodeName)
            .apply()
    }

    fun markUserDisconnected(context: Context) {
        prefs(context).edit().putBoolean(KEY_WAS_CONNECTED, false).apply()
    }
}
