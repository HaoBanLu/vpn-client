package com.vpn.member.vpn.mihomo

import android.app.Application
import com.vpn.member.debug.AppDebugLogger
import com.vpn.member.vpn.mihomo.MihomoInitializer.ensureReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 登录后预热 Mihomo native 与 geo/ruleset，缩短首连耗时。 */
object MihomoWarmup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var scheduled = false

    fun schedule(app: Application) {
        if (scheduled) return
        scheduled = true
        scope.launch {
            runCatching {
                ensureReady(app)
                MihomoGeoAssetManager.scheduleInstall(app)
                AppDebugLogger.info(category = "mihomo", message = "连接栈预热完成")
            }.onFailure { e ->
                scheduled = false
                AppDebugLogger.warn(
                    category = "mihomo",
                    message = "连接栈预热失败",
                    context = mapOf("error" to (e.message ?: "unknown")),
                )
            }
        }
    }
}
