package com.vpn.member.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher
import com.vpn.member.vpn.VpnConnectionBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VpnController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val status: StateFlow<VpnConnectionStatus> = VpnConnectionBus.status

    fun prepare(launcher: ActivityResultLauncher<Intent>) {
        VpnService.prepare(appContext)?.let { launcher.launch(it) }
    }

    fun connect(
        configYaml: String,
        routeTarget: String? = null,
        displayName: String? = null,
        routeMode: String? = null,
        clientProfile: String? = null,
    ) {
        scope.launch {
            try {
                ClashConfigStore.persist(appContext, configYaml)
            } catch (e: IllegalStateException) {
                VpnConnectionBus.update(ConnectionState.FAILED, mapPersistError(e))
                return@launch
            }
            val target = routeTarget?.trim().orEmpty()
            val intent =
                Intent(appContext, VpnTunnelService::class.java).apply {
                    action = VpnTunnelService.ACTION_CONNECT
                    putExtra(VpnTunnelService.EXTRA_USE_STORED_CONFIG, true)
                    putExtra(VpnTunnelService.EXTRA_ROUTE_TARGET, target)
                    putExtra(VpnTunnelService.EXTRA_ROUTE_MODE, routeMode?.trim().orEmpty())
                    putExtra(
                        VpnTunnelService.EXTRA_NODE_NAME,
                        displayName?.trim().takeUnless { it.isNullOrBlank() }
                            ?: target.takeIf { it.isNotBlank() }
                            ?: "智能选路",
                    )
                    clientProfile?.trim()?.takeIf { it.isNotBlank() }?.let {
                        putExtra(VpnTunnelService.EXTRA_CLIENT_PROFILE, it)
                    }
                }
            appContext.startForegroundService(intent)
        }
    }

    fun reconnect(
        configYaml: String,
        routeTarget: String? = null,
        displayName: String? = null,
        routeMode: String? = null,
        clientProfile: String? = null,
    ) {
        scope.launch {
            try {
                ClashConfigStore.persist(appContext, configYaml)
            } catch (e: IllegalStateException) {
                VpnConnectionBus.update(ConnectionState.FAILED, mapPersistError(e))
                return@launch
            }
            val target = routeTarget?.trim().orEmpty()
            val intent =
                Intent(appContext, VpnTunnelService::class.java).apply {
                    action = VpnTunnelService.ACTION_RECONNECT
                    putExtra(VpnTunnelService.EXTRA_USE_STORED_CONFIG, true)
                    putExtra(VpnTunnelService.EXTRA_ROUTE_TARGET, target)
                    putExtra(VpnTunnelService.EXTRA_ROUTE_MODE, routeMode?.trim().orEmpty())
                    putExtra(
                        VpnTunnelService.EXTRA_NODE_NAME,
                        displayName?.trim().takeUnless { it.isNullOrBlank() }
                            ?: target.takeIf { it.isNotBlank() }
                            ?: "智能选路",
                    )
                    clientProfile?.trim()?.takeIf { it.isNotBlank() }?.let {
                        putExtra(VpnTunnelService.EXTRA_CLIENT_PROFILE, it)
                    }
                }
            appContext.startForegroundService(intent)
        }
    }

    private fun mapPersistError(e: IllegalStateException): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("proxies", ignoreCase = true) ->
                "节点配置无效（缺少代理节点），请返回连接页下拉刷新后重试"
            raw.contains("JSON", ignoreCase = true) ->
                "服务端配置格式异常，请确认已部署 Mihomo 版 API"
            else -> "节点配置写入失败，请清除 App 数据后重试"
        }
    }

    fun disconnect() {
        scope.launch {
            val intent =
                Intent(appContext, VpnTunnelService::class.java).apply {
                    action = VpnTunnelService.ACTION_DISCONNECT
                }
            appContext.startService(intent)
        }
    }

    /**
     * 切网/探活失败后的自动重连：拆掉失效隧道，按设置保持 Kill Switch，不清理会话快照。
     */
    fun disconnectHoldingKillSwitchForReconnect() {
        scope.launch {
            val intent =
                Intent(appContext, VpnTunnelService::class.java).apply {
                    action = VpnTunnelService.ACTION_DISCONNECT_FOR_RECONNECT
                }
            appContext.startService(intent)
        }
    }

    /** 鉴权失效 / 登出：断开隧道并启用 Kill Switch（若已开启）。 */
    fun disconnectForAuth() {
        VpnConnectionBus.resetForSessionEnd()
        scope.launch {
            val intent =
                Intent(appContext, VpnTunnelService::class.java).apply {
                    action = VpnTunnelService.ACTION_DISCONNECT_AUTH
                }
            appContext.startService(intent)
        }
    }

    /** VPN 已连接时触发自愈（同步 DNS、健康检查），不断开隧道。 */
    fun healTunnel() {
        val intent =
            Intent(appContext, VpnTunnelService::class.java).apply {
                action = VpnTunnelService.ACTION_HEAL_TUNNEL
            }
        appContext.startService(intent)
    }

    fun releaseKillSwitch() {
        val intent =
            Intent(appContext, VpnTunnelService::class.java).apply {
                action = VpnTunnelService.ACTION_RELEASE_KILL_SWITCH
            }
        appContext.startService(intent)
    }

    fun restoreSessionIfNeeded() {
        val intent =
            Intent(appContext, VpnTunnelService::class.java).apply {
                action = VpnTunnelService.ACTION_RESTORE
            }
        appContext.startForegroundService(intent)
    }

    fun syncTunnelStateFromSystem() {
        VpnTunnelStateSync.reconcileBusState(appContext)?.let { state ->
            VpnConnectionBus.update(state, error = null)
        }
    }
}
