package com.vpn.member.vpn

import android.content.Context
import android.net.VpnService
import com.vpn.member.data.network.NetworkMonitor
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.debug.AppDebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.lang.ref.WeakReference

/**
 * Application 级重连监督：网络恢复 / 切网 / 意外 FAILED 不依赖 Connect 页存活。
 *
 * 有 [VpnReconnectHost]（连接页）时优先走宿主完整「先备配置再 KS」路径；
 * 无宿主时用会话快照 + 本地/API 配置回退建连。
 */
class VpnReconnectSupervisor(
    context: Context,
    private val repository: AppRepository,
    private val vpnController: VpnController,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val hostLock = Any()
    private var hostRef: WeakReference<VpnReconnectHost>? = null

    @Volatile
    var userInitiatedDisconnect: Boolean = false
        private set

    @Volatile
    private var reconnectExecuting = false

    @Volatile
    private var pendingReconnectReason: String? = null

    private var autoReconnectJob: Job? = null
    private val fallbackMutex = Mutex()

    fun start() {
        scope.launch {
            NetworkMonitor.networkRestored.collect {
                onNetworkRestored()
            }
        }
        scope.launch {
            VpnNetworkEvents.transportChanged.collect { reason ->
                onTransportChanged(reason)
            }
        }
        scope.launch {
            var previous = vpnController.status.value.state
            vpnController.status.collect { status ->
                val newState = status.state
                if (
                    VpnFailedRecoveryPolicy.shouldScheduleAfterUnexpectedFailed(
                        previousState = previous,
                        newState = newState,
                        userInitiatedDisconnect = userInitiatedDisconnect,
                        autoReconnectEnabled = repository.isAutoReconnectEnabled(),
                        snapshot = repository.getVpnSessionSnapshot(),
                        physicalOnline = NetworkMonitor.hasValidatedPhysicalInternet(appContext),
                    )
                ) {
                    AppDebugLogger.info(
                        category = "reconnect",
                        message = "意外 FAILED，监督器调度重连",
                        context =
                            mapOf(
                                "from" to previous.name,
                                "error" to (status.error ?: ""),
                            ),
                    )
                    scheduleReconnect("dataplane_failed")
                }
                if (newState == ConnectionState.CONNECTED) {
                    repository.resetVpnReconnectAttempts()
                }
                previous = newState
            }
        }
    }

    fun attachHost(host: VpnReconnectHost) {
        synchronized(hostLock) {
            hostRef = WeakReference(host)
        }
    }

    fun detachHost(host: VpnReconnectHost) {
        synchronized(hostLock) {
            if (hostRef?.get() === host) {
                hostRef = null
            }
        }
    }

    private fun hostOrNull(): VpnReconnectHost? = synchronized(hostLock) { hostRef?.get() }

    fun markUserDisconnect() {
        userInitiatedDisconnect = true
        pendingReconnectReason = null
        reconnectExecuting = false
        autoReconnectJob?.cancel()
        autoReconnectJob = null
    }

    fun clearUserDisconnect() {
        userInitiatedDisconnect = false
    }

    fun onAppForeground() {
        vpnController.syncTunnelStateFromSystem()
        val state = currentConnectionState()
        if (
            VpnForegroundRestorePolicy.shouldScheduleForegroundRestore(
                serviceRunning = VpnTunnelStateSync.isServiceRunning(appContext),
                snapshot = repository.getVpnSessionSnapshot(),
                autoReconnectEnabled = repository.isAutoReconnectEnabled(),
                userInitiatedDisconnect = userInitiatedDisconnect,
                connectionState = state,
            )
        ) {
            scheduleReconnect("app_foreground")
        }
    }

    /** 供宿主健康探测 / Service 看门狗显式请求完整重连。 */
    fun scheduleReconnect(reason: String) {
        if (userInitiatedDisconnect || !repository.isAutoReconnectEnabled()) return
        val host = hostOrNull()
        if (host != null) {
            host.scheduleAutoReconnect(reason)
            return
        }
        scheduleFallbackReconnect(reason)
    }

    private fun currentConnectionState(): ConnectionState =
        hostOrNull()?.connectionState() ?: vpnController.status.value.state

    private suspend fun onNetworkRestored() {
        hostOrNull()?.onNetworkRestoredForUi()
        when (
            NetworkRestorePolicy.decide(
                connectionState = currentConnectionState(),
                userInitiatedDisconnect = userInitiatedDisconnect,
                autoReconnectEnabled = repository.isAutoReconnectEnabled(),
            )
        ) {
            NetworkRestoreAction.HEAL -> {
                hostOrNull()?.startHeal("network_restored")
                    ?: AppDebugLogger.info(
                        category = "network",
                        message = "网络恢复需自愈但无宿主，跳过",
                    )
            }
            NetworkRestoreAction.SCHEDULE_RECONNECT -> {
                AppDebugLogger.info(
                    category = "network",
                    message = "网络恢复，监督器准备完整重连",
                    context = mapOf("state" to currentConnectionState().name),
                )
                scheduleReconnect("network_restored")
            }
            NetworkRestoreAction.NONE -> Unit
        }
    }

    private fun onTransportChanged(reason: String) {
        if (!NetworkRestorePolicy.shouldRecoverOnTransportChange(currentConnectionState())) return
        if (userInitiatedDisconnect) return
        if (repository.isAutoReconnectEnabled()) {
            AppDebugLogger.info(
                category = "network",
                message = "物理网络切换，监督器准备完整重连",
                context = mapOf("reason" to reason),
            )
            scheduleReconnect("transport_$reason")
        } else {
            hostOrNull()?.startHeal(reason)
        }
    }

    private fun scheduleFallbackReconnect(reason: String) {
        pendingReconnectReason = reason
        if (reconnectExecuting) {
            AppDebugLogger.info(
                category = "reconnect",
                message = "监督器回退重连进行中，合并事件",
                context = mapOf("reason" to reason),
            )
            return
        }
        autoReconnectJob?.cancel()
        autoReconnectJob =
            scope.launch {
                fallbackMutex.withLock {
                    try {
                        delay(DnsChurnPolicy.reconnectDebounceMs(reason))
                        if (userInitiatedDisconnect || !repository.isAutoReconnectEnabled()) return@withLock
                        val why = pendingReconnectReason ?: reason
                        reconnectExecuting = true
                        if (why == "network_restored") {
                            repository.resetVpnReconnectAttempts()
                        }
                        runFallbackReconnectLoop(why)
                    } finally {
                        reconnectExecuting = false
                    }
                }
            }
    }

    private suspend fun runFallbackReconnectLoop(why: String) {
        var lastError: Throwable? = null
        for (round in 1..VpnAutoReconnectPolicy.MAX_ATTEMPTS) {
            if (userInitiatedDisconnect || !repository.isAutoReconnectEnabled()) return
            if (!awaitPhysicalNetworkReady(why)) {
                hostOrNull()?.notifyActionHint("网络已断开，恢复后将自动重连")
                return
            }
            val attempts = repository.incrementVpnReconnectAttempts()
            if (attempts > VpnAutoReconnectPolicy.MAX_ATTEMPTS) break

            hostOrNull()?.notifyActionHint(
                "网络已变化，正在自动重连（$attempts/${VpnAutoReconnectPolicy.MAX_ATTEMPTS}）…",
            )
            AppDebugLogger.info(
                category = "reconnect",
                message = "监督器回退自动重连",
                context = mapOf("reason" to why, "attempt" to attempts.toString()),
            )

            delay(AutoReconnectPrepPolicy.NETWORK_SETTLE_MS)
            if (userInitiatedDisconnect) return

            val networkRecovery = why == "network_restored" || why.startsWith("transport_")
            if (!(networkRecovery && attempts == 1)) {
                delay(VpnAutoReconnectPolicy.backoffDelayMs(attempts - 1))
            }
            if (userInitiatedDisconnect) return

            val prepared =
                runCatching {
                    withTimeout(VpnAutoReconnectPolicy.CONNECT_TIMEOUT_MS) {
                        prepareFallbackMaterials()
                    }
                }.onFailure { lastError = it }.getOrNull()

            if (prepared == null) {
                if (round < VpnAutoReconnectPolicy.MAX_ATTEMPTS) {
                    delay(VpnAutoReconnectPolicy.backoffDelayMs(attempts))
                    continue
                }
                break
            }

            val holdEnabled =
                repository.isKillSwitchEnabled() &&
                    repository.isReconnectKillSwitchHoldEnabled()
            val escalateFromLiveTunnel =
                why.startsWith("transport_") ||
                    why.startsWith("health_") ||
                    why == "health_failover" ||
                    why == "dataplane_failed" ||
                    why == "tunnel_watchdog" ||
                    why == "network_restored" ||
                    why == "app_foreground"
            val serviceRunning = VpnTunnelStateSync.isServiceRunning(appContext)
            val tunnelLive =
                serviceRunning &&
                    (
                        vpnController.status.value.state == ConnectionState.CONNECTED ||
                            VpnTunnelStateSync.isVpnTransportActive(appContext)
                    )

            if (
                AutoReconnectPrepPolicy.shouldHoldKillSwitchAfterPrep(
                    holdEnabled = holdEnabled,
                    escalateFromLiveTunnel = escalateFromLiveTunnel,
                    tunnelLive = tunnelLive,
                )
            ) {
                vpnController.disconnectHoldingKillSwitchForReconnect()
                delay(400)
            }

            val stillTunnelRunning =
                VpnTunnelStateSync.isServiceRunning(appContext) && VpnTunnelService.isTunnelRunning
            dispatchFallback(prepared, reconnect = stillTunnelRunning)
            repository.resetVpnReconnectAttempts()
            return
        }

        AppDebugLogger.warn(
            category = "reconnect",
            message = "监督器回退重连次数耗尽",
            context =
                mapOf(
                    "reason" to why,
                    "error" to (lastError?.message ?: lastError?.javaClass?.simpleName ?: ""),
                ),
        )
        hostOrNull()?.notifyActionHint(
            if (NetworkMonitor.hasValidatedPhysicalInternet(appContext)) {
                "自动重连失败，请手动点击连接"
            } else {
                "网络已断开，恢复后将自动重连"
            },
        )
    }

    private suspend fun awaitPhysicalNetworkReady(why: String): Boolean {
        val deadline = System.currentTimeMillis() + AutoReconnectPrepPolicy.PHYSICAL_READY_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (userInitiatedDisconnect) return false
            if (
                PostHealRecoveryPolicy.shouldProceedAutoReconnect(
                    NetworkMonitor.hasValidatedPhysicalInternet(appContext),
                )
            ) {
                return true
            }
            hostOrNull()?.notifyActionHint("网络已断开，恢复后将自动重连")
            delay(AutoReconnectPrepPolicy.PHYSICAL_POLL_MS)
        }
        return PostHealRecoveryPolicy.shouldProceedAutoReconnect(
            NetworkMonitor.hasValidatedPhysicalInternet(appContext),
        )
    }

    private data class FallbackPrepared(
        val configYaml: String,
        val routeTarget: String,
        val nodeLabel: String,
        val routeMode: String,
        val profile: String,
    )

    private suspend fun prepareFallbackMaterials(): FallbackPrepared {
        val snapshot = repository.getVpnSessionSnapshot()
        val node =
            repository.getSavedNode()?.trim()?.takeIf { it.isNotEmpty() }
                ?: snapshot?.nodeName?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("无选中节点")
        if (repository.getSavedNode().isNullOrBlank()) {
            repository.saveNode(node)
        }
        val profile =
            snapshot?.profile?.takeIf { it.isNotBlank() }
                ?: ConnectionScenario.PROFILE_OVERSEAS_WEAK
        val routeMode =
            snapshot?.routeMode?.takeIf { it.isNotBlank() }
                ?: AppRouteMode.FULL
        val scenario =
            snapshot?.connectionScenario?.takeIf { it.isNotBlank() }
                ?: ConnectionScenario.AUTO

        val yaml =
            runCatching {
                val resolved = ConnectionScenario.resolve(scenario, snapshot?.region, null)
                val config =
                    repository.getClientConfig(
                        region = null,
                        node = node,
                        profile = resolved.profile.ifBlank { profile },
                        routeMode = resolved.routeMode.ifBlank { routeMode },
                    )
                ClashConfigSanitizer.validateClashYaml(config.config)
                config.config
            }.getOrElse { apiError ->
                val cached = ClashConfigStore.readOrNull(appContext)?.trim().orEmpty()
                if (
                    AutoReconnectPrepPolicy.allowCachedConfigFallback(
                        apiFailed = true,
                        hasCachedConfig = cached.isNotEmpty(),
                    )
                ) {
                    AppDebugLogger.warn(
                        category = "reconnect",
                        message = "监督器拉配置失败，改用本地缓存",
                        context =
                            mapOf(
                                "node" to node,
                                "error" to (apiError.message ?: apiError::class.simpleName ?: ""),
                            ),
                    )
                    ClashConfigSanitizer.validateClashYaml(cached)
                    cached
                } else {
                    throw apiError
                }
            }

        val routeTarget =
            ClashRouteTarget.resolve(
                configNode = null,
                configYaml = yaml,
                selectedNode = node,
            )
        return FallbackPrepared(
            configYaml = yaml,
            routeTarget = routeTarget,
            nodeLabel = node,
            routeMode = routeMode,
            profile = profile,
        )
    }

    private fun dispatchFallback(
        prepared: FallbackPrepared,
        reconnect: Boolean,
    ) {
        if (VpnService.prepare(appContext) != null) {
            AppDebugLogger.warn(
                category = "reconnect",
                message = "监督器回退重连：缺少 VPN 授权",
            )
            return
        }
        if (reconnect) {
            vpnController.reconnect(
                prepared.configYaml,
                prepared.routeTarget,
                prepared.nodeLabel,
                prepared.routeMode,
                prepared.profile,
            )
        } else {
            vpnController.connect(
                prepared.configYaml,
                prepared.routeTarget,
                prepared.nodeLabel,
                prepared.routeMode,
                prepared.profile,
            )
        }
        AppDebugLogger.info(
            category = "reconnect",
            message = "监督器已下发回退重连",
            context =
                mapOf(
                    "node" to prepared.nodeLabel,
                    "reconnect" to reconnect.toString(),
                ),
        )
    }
}
