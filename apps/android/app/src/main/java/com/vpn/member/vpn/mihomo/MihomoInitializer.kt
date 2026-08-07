package com.vpn.member.vpn.mihomo

import android.app.Application
import android.os.Build
import com.github.kr328.clash.core.bridge.Bridge
import com.vpn.member.BuildConfig
import com.vpn.member.debug.AppDebugLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 延迟初始化 Mihomo（仅在连接 VPN 前调用，避免启动阶段加载 native 导致闪退）。 */
object MihomoInitializer {
    @Volatile
    private var ready = false
    private val mutex = Mutex()

    suspend fun ensureReady(app: Application) {
        if (ready) return
        mutex.withLock {
            if (ready) return
            val abi = Build.SUPPORTED_ABIS?.firstOrNull().orEmpty()
            AppDebugLogger.info(
                category = "mihomo",
                message = "开始初始化连接栈",
                context = mapOf("slim" to BuildConfig.SLIM_NATIVE_LIBS.toString(), "abi" to abi),
            )
            try {
                if (BuildConfig.SLIM_NATIVE_LIBS) {
                    val dir = MihomoNativeLibManager.ensureReady(app)
                    Bridge.externalNativeLibDir = dir.absolutePath
                    AppDebugLogger.info(
                        category = "mihomo",
                        message = "使用外置 native 库",
                        context = mapOf("dir" to dir.absolutePath),
                    )
                }
                Bridge.ensureInitialized(app)
            } catch (t: Throwable) {
                AppDebugLogger.error(
                    category = "mihomo",
                    message = "连接栈初始化失败",
                    context =
                        mapOf(
                            "error" to (t.message ?: t::class.java.name),
                            "error_type" to t::class.java.name,
                            "slim" to BuildConfig.SLIM_NATIVE_LIBS.toString(),
                            "abis" to ((Build.SUPPORTED_ABIS ?: emptyArray()).joinToString(",")),
                        ),
                )
                throw t
            }
            ready = true
        }
    }
}
