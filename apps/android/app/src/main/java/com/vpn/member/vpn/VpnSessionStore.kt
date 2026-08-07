package com.vpn.member.vpn

import android.content.Context
import com.vpn.member.data.local.AppPreferences

/**
 * VPN 会话意图与自动重连计数持久化（普通 SharedPreferences，不含敏感凭据）。
 */
class VpnSessionStore(
    context: Context,
    private val preferences: AppPreferences = AppPreferences(context.applicationContext),
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSnapshot(snapshot: VpnSessionSnapshot) {
        prefs.edit()
            .putBoolean(KEY_WAS_CONNECTED, snapshot.wasUserConnected)
            .putString(KEY_NODE, snapshot.nodeName)
            .putString(KEY_REGION, snapshot.region)
            .putString(KEY_PROFILE, snapshot.profile)
            .putString(KEY_ROUTE_MODE, snapshot.routeMode)
            .putString(KEY_SCENARIO, snapshot.connectionScenario)
            .putLong(KEY_SAVED_AT, snapshot.savedAtMs)
            .apply()
    }

    fun readSnapshot(): VpnSessionSnapshot? {
        if (!prefs.getBoolean(KEY_WAS_CONNECTED, false)) return null
        return VpnSessionSnapshot(
            wasUserConnected = true,
            nodeName = prefs.getString(KEY_NODE, null),
            region = prefs.getString(KEY_REGION, null),
            profile = prefs.getString(KEY_PROFILE, ConnectionScenario.PROFILE_OVERSEAS_WEAK).orEmpty(),
            routeMode = prefs.getString(KEY_ROUTE_MODE, AppRouteMode.FULL) ?: AppRouteMode.FULL,
            connectionScenario = prefs.getString(KEY_SCENARIO, ConnectionScenario.AUTO) ?: ConnectionScenario.AUTO,
            savedAtMs = prefs.getLong(KEY_SAVED_AT, 0L),
        )
    }

    fun clearSnapshot() {
        prefs.edit()
            .remove(KEY_WAS_CONNECTED)
            .remove(KEY_NODE)
            .remove(KEY_REGION)
            .remove(KEY_PROFILE)
            .remove(KEY_ROUTE_MODE)
            .remove(KEY_SCENARIO)
            .remove(KEY_SAVED_AT)
            .apply()
        resetReconnectAttempts()
    }

    fun incrementReconnectAttempts(): Int {
        val next = prefs.getInt(KEY_RECONNECT_ATTEMPTS, 0) + 1
        prefs.edit().putInt(KEY_RECONNECT_ATTEMPTS, next).apply()
        return next
    }

    fun resetReconnectAttempts() {
        prefs.edit().remove(KEY_RECONNECT_ATTEMPTS).apply()
    }

    fun getReconnectAttempts(): Int = prefs.getInt(KEY_RECONNECT_ATTEMPTS, 0)

    fun isKillSwitchEnabled(): Boolean = preferences.isKillSwitchEnabled()

    fun isIpv6LeakProtectionEnabled(): Boolean = preferences.isIpv6LeakProtectionEnabled()

    fun isBlockOnConnectFailureEnabled(): Boolean = preferences.isBlockOnConnectFailureEnabled()

    fun isReconnectKillSwitchHoldEnabled(): Boolean = preferences.isReconnectKillSwitchHoldEnabled()

    fun isBootAutoConnectEnabled(): Boolean = preferences.isBootAutoConnectEnabled()

    fun isAutoReconnectEnabled(): Boolean = preferences.isAutoReconnectEnabled()

    companion object {
        private const val PREFS_NAME = "vpn_session_store"
        private const val KEY_WAS_CONNECTED = "was_connected"
        private const val KEY_NODE = "node"
        private const val KEY_REGION = "region"
        private const val KEY_PROFILE = "profile"
        private const val KEY_ROUTE_MODE = "route_mode"
        private const val KEY_SCENARIO = "scenario"
        private const val KEY_SAVED_AT = "saved_at"
        private const val KEY_RECONNECT_ATTEMPTS = "reconnect_attempts"
    }
}
