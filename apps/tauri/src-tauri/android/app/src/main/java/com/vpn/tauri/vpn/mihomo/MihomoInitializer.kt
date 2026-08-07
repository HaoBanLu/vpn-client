package com.vpn.tauri.vpn.mihomo

import android.app.Application
import com.github.kr328.clash.core.bridge.Bridge

/** 延迟初始化 Mihomo（仅在连接 VPN 前调用）。 */
object MihomoInitializer {
    @Volatile
    private var ready = false

    fun ensureReady(app: Application) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            Bridge.ensureInitialized(app)
            ready = true
        }
    }
}
