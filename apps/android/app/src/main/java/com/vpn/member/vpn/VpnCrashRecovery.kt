package com.vpn.member.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.vpn.member.debug.AppDebugLogger
import kotlin.system.exitProcess

/** 未捕获崩溃后标记待恢复会话（P3-4）。 */
object VpnCrashRecovery {
    private const val TAG = "VpnCrashRecovery"
    private const val PREFS = "vpn_crash_recovery"
    private const val KEY_PENDING_RESTORE = "pending_restore"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val store = VpnSessionStore(appContext)
                val snapshot = store.readSnapshot()
                if (snapshot?.wasUserConnected == true && store.isAutoReconnectEnabled()) {
                    appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_PENDING_RESTORE, true)
                        .apply()
                    AppDebugLogger.error(
                        category = "crash",
                        message = "未捕获异常，已标记 VPN 待恢复",
                        context = mapOf("thread" to thread.name, "error" to (throwable.message ?: "-")),
                    )
                    AppDebugLogger.flush()
                }
            }.onFailure { e ->
                Log.e(TAG, "crash handler failed", e)
            }
            previous?.uncaughtException(thread, throwable) ?: exitProcess(1)
        }
    }

    fun consumePendingRestore(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY_PENDING_RESTORE, false)
        if (pending) {
            prefs.edit().remove(KEY_PENDING_RESTORE).apply()
        }
        return pending
    }

    fun scheduleRestoreIfNeeded(context: Context) {
        if (!consumePendingRestore(context)) return
        if (!VpnAuthGate.isLoggedIn(context)) return
        val store = VpnSessionStore(context)
        if (!store.isAutoReconnectEnabled()) return
        val snapshot = store.readSnapshot() ?: return
        if (!snapshot.wasUserConnected) return
        if (VpnService.prepare(context) != null) return
        val intent =
            Intent(context, VpnTunnelService::class.java).apply {
                action = VpnTunnelService.ACTION_RESTORE
            }
        context.startForegroundService(intent)
    }
}
