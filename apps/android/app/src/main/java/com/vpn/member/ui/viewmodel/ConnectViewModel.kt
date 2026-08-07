package com.vpn.member.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.api.ClientConfigData
import com.vpn.member.data.api.RegionItem
import com.vpn.member.data.network.ApiRequestSupport
import com.vpn.member.data.network.mapLoadError
import com.vpn.member.data.network.NetworkMonitor
import com.vpn.member.data.network.SessionInvalidatedException
import com.vpn.member.data.api.SubscriptionActive
import com.vpn.member.data.api.SubscriptionUsage
import com.vpn.member.data.api.UserPreferencesUpdate
import com.vpn.member.data.repository.AppException
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.data.session.AppEvents
import com.vpn.member.debug.AppDebugLogger
import com.vpn.member.vpn.AppProtocolSupport
import com.vpn.member.vpn.AppRouteMode
import com.vpn.member.vpn.ClashRouteTarget
import com.vpn.member.vpn.ConnectFailureReason
import com.vpn.member.vpn.ConnectionScenario
import com.vpn.member.vpn.NodeAccessHint
import com.vpn.member.vpn.BatteryOptimizationGuide
import com.vpn.member.vpn.mihomo.MihomoWarmup
import com.vpn.member.vpn.NodeFailoverMonitor
import com.vpn.member.vpn.NodeFailoverSelector
import com.vpn.member.vpn.AutoReconnectPrepPolicy
import com.vpn.member.vpn.VpnAutoReconnectPolicy
import com.vpn.member.vpn.DnsChurnPolicy
import com.vpn.member.vpn.VpnReconnectHost
import com.vpn.member.vpn.VpnReconnectSupervisor
import com.vpn.member.vpn.VpnSessionSnapshot
import com.vpn.member.vpn.VpnTunnelStateSync
import com.vpn.member.vpn.VpnTunnelService
import com.vpn.member.vpn.ConnectionState
import com.vpn.member.vpn.ConnectivityProbe
import com.vpn.member.vpn.ExitIpProbe
import com.vpn.member.vpn.LineAcquireNode
import com.vpn.member.vpn.PostHealRecoveryPolicy
import com.vpn.member.vpn.ProbeStatus
import com.vpn.member.vpn.TunDataPlaneVerifier
import com.vpn.member.vpn.toConnectFailureReason
import com.vpn.member.vpn.userMessage
import com.vpn.member.vpn.ResolvedConnectionConfig
import com.vpn.member.vpn.ClashConfigParser
import com.vpn.member.vpn.ClashConfigSanitizer
import com.vpn.member.vpn.ClashConfigStore
import com.vpn.member.vpn.ConnectProbePolicy
import com.vpn.member.vpn.ConnectTimingTracker
import com.vpn.member.vpn.VpnConnectionBus
import com.vpn.member.vpn.VpnController
import com.vpn.member.vpn.VpnSessionStatsTracker
import com.vpn.member.vpn.VpnTrafficBus
import com.vpn.member.vpn.PrivacyLeakProbe
import com.vpn.member.vpn.PrivacyProbeHistoryStore
import com.vpn.member.vpn.ProtectionLevelChangeStore
import com.vpn.member.vpn.PrivacyOnboardingStore
import com.vpn.member.vpn.ProtectionLevel
import com.vpn.member.vpn.ProtectionLevelResolver
import com.vpn.member.ui.displayNodeName
import com.vpn.member.ui.isOnline
import com.vpn.member.ui.regionDisplayName
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class ConnectUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val subscription: SubscriptionActive? = null,
    val usage: SubscriptionUsage? = null,
    val regions: List<RegionItem> = emptyList(),
    val selectedRegion: String? = null,
    val selectedNode: String? = null,
    val connectedNodeName: String? = null,
    val routeMode: String = AppRouteMode.FULL,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectPending: Boolean = false,
    val isSwitching: Boolean = false,
    /** 正在连接/切换的目标节点，用于节点列表仅高亮对应按钮 */
    val connectingNodeName: String? = null,
    val probeStatus: ProbeStatus = ProbeStatus.IDLE,
    val error: String? = null,
    /** 结构化失败原因，与 [error] 文案对应；占线与节点不可达分开。 */
    val failureReason: ConnectFailureReason? = null,
    val actionHint: String? = null,
    val renewalHint: String? = null,
    val appDebugEnabled: Boolean = false,
    /** 地区/节点切换后的短暂提示，由 MainShell Snackbar 消费 */
    val routeMessage: String? = null,
    val userId: Long? = null,
    val accountEmail: String? = null,
    val isVip: Boolean = false,
    val dashboardExpiresAt: String? = null,
    val connectionScenario: String = ConnectionScenario.AUTO,
    val connectionScenarioLabel: String? = null,
    val activeProfile: String? = null,
    val activeRouteMode: String? = null,
    val exitIp: String? = null,
    val exitCountry: String? = null,
    val exitCity: String? = null,
    val nodeProbeLatencyMs: Int? = null,
    val sceneTags: List<String> = emptyList(),
    /** 本次 VPN 会话经 TUN 转发的累计流量（上行） */
    val sessionUploadBytes: Long = 0L,
    /** 本次 VPN 会话经 TUN 转发的累计流量（下行） */
    val sessionDownloadBytes: Long = 0L,
    val sessionDurationMs: Long = 0L,
    val sessionUploadBps: Long = 0L,
    val sessionDownloadBps: Long = 0L,
    val protectionLevel: com.vpn.member.vpn.ProtectionLevel = com.vpn.member.vpn.ProtectionLevel.UNPROTECTED,
    val protectionLabel: String = "未保护",
    val showPrivacyOnboarding: Boolean = false,
    val privacyProbeRunning: Boolean = false,
    val requestNavigateToNodes: Boolean = false,
    val requestNavigateToPackages: Boolean = false,
)

private data class ConfigRouteQuery(
    val region: String?,
    val node: String?,
)

private enum class DisconnectReason {
    User,
}

private sealed class PendingConnectAction {
    data object Default : PendingConnectAction()

    data class Node(
        val nodeName: String,
        val region: String?,
    ) : PendingConnectAction()
}

class ConnectViewModel(
    private val repository: AppRepository,
    private val vpnController: VpnController,
    private val reconnectSupervisor: VpnReconnectSupervisor,
) : ViewModel(),
    VpnReconnectHost {
    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()
    private var probeJob: Job? = null
    private var healthProbeJob: Job? = null
    private var autoReconnectJob: Job? = null
    private var transportRecoveryJob: Job? = null
    private var connectJob: Job? = null
    /** 切网事件风暴合并：以最后一次 reason 为准。 */
    @Volatile
    private var pendingReconnectReason: String? = null
    /** 防抖结束后已进入重连执行：后续 dns 风暴不得再 cancel，否则永远到不了 performConnect。 */
    @Volatile
    private var reconnectExecuting = false
    private var pendingConnectAction: PendingConnectAction? = null
    private var activeConfigJson: String? = null
    /** 用户主动断开；与 [VpnReconnectSupervisor] 共享。 */
    private var userInitiatedDisconnect: Boolean
        get() = reconnectSupervisor.userInitiatedDisconnect
        set(value) {
            if (value) {
                reconnectSupervisor.markUserDisconnect()
            } else {
                reconnectSupervisor.clearUserDisconnect()
            }
        }
    private var pendingDisconnectReason: DisconnectReason? = null
    private var privacyProbeJob: Job? = null
    /** 周期健康探测连续失败次数（切网/无网自动重连用）。 */
    private var healthFailStreak = 0
    private val privacyOnboardingStore by lazy {
        PrivacyOnboardingStore(repository.applicationContext())
    }

    private fun ConnectUiState.withProtection(statusError: String? = error): ConnectUiState {
        val baselineReady = repository.isPrivacyBaselineReady()
        // 连接页主区不展示探测降级；已连接时统一视为已保护，探测仅用于后台日志与 failover。
        val level =
            if (connectionState == ConnectionState.CONNECTED) {
                ProtectionLevel.PROTECTED
            } else {
                ProtectionLevelResolver.resolve(
                    connectionState,
                    statusError,
                    probeStatus,
                    privacyBaselineReady = baselineReady,
                )
            }
        if (level != protectionLevel) {
            ProtectionLevelChangeStore.appendLevelChange(
                repository.applicationContext(),
                from = protectionLevel,
                to = level,
                reason = "connect",
            )
        }
        return copy(
            protectionLevel = level,
            protectionLabel = ProtectionLevelResolver.label(level, exitIp),
        )
    }

    init {
        migrateRouteModeIfNeeded()
        vpnController.syncTunnelStateFromSystem()
        reconnectSupervisor.attachHost(this)
        refresh()
        viewModelScope.launch {
            AppEvents.vpnConfigChanged.collect {
                onVpnConfigChanged()
            }
        }
        viewModelScope.launch {
            VpnTrafficBus.snapshot.collect { traffic ->
                _state.update { current ->
                    if (current.connectionState != ConnectionState.CONNECTED) {
                        current
                    } else {
                        val sessionBytes =
                            traffic.stats.downloadBytes + traffic.stats.uploadBytes
                        val clearedDegraded =
                            ConnectProbePolicy.shouldTrustSessionTraffic(
                                traffic.stats.downloadBytes,
                                traffic.stats.uploadBytes,
                            ) &&
                                (
                                    current.probeStatus == ProbeStatus.DEGRADED ||
                                        ConnectProbePolicy.isBusDataplaneDegraded(
                                            vpnController.status.value.probeStatus,
                                        )
                                )
                        if (clearedDegraded) {
                            VpnConnectionBus.updateQuality(probeStatus = ProbeStatus.OK.name.lowercase())
                        }
                        current.copy(
                            sessionUploadBytes = traffic.stats.uploadBytes,
                            sessionDownloadBytes = traffic.stats.downloadBytes,
                            sessionDurationMs = traffic.stats.durationMs,
                            sessionUploadBps = traffic.rates.uploadBps,
                            sessionDownloadBps = traffic.rates.downloadBps,
                            probeStatus =
                                if (clearedDegraded) {
                                    ProbeStatus.OK
                                } else {
                                    ConnectProbePolicy.mergeProbeStatus(
                                        measured = current.probeStatus,
                                        busProbeStatus = vpnController.status.value.probeStatus,
                                        connectionState = ConnectionState.CONNECTED,
                                        sessionTrafficBytes = sessionBytes,
                                    )
                                },
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            vpnController.status.collect { status ->
                _state.update { current ->
                    val mergedProbe =
                        ConnectProbePolicy.mergeProbeStatus(
                            measured = current.probeStatus,
                            busProbeStatus = status.probeStatus,
                            connectionState = status.state,
                            sessionTrafficBytes =
                                current.sessionDownloadBytes + current.sessionUploadBytes,
                        )
                    val newState =
                        current.copy(
                            connectionState = status.state,
                            probeStatus =
                                if (status.state == ConnectionState.CONNECTED) {
                                    mergedProbe
                                } else {
                                    current.probeStatus
                                },
                            actionHint =
                                when {
                                    status.state != ConnectionState.CONNECTED -> current.actionHint
                                    else -> current.actionHint
                                },
                            connectPending =
                                when (status.state) {
                                    ConnectionState.CONNECTING,
                                    ConnectionState.CONNECTED,
                                    ConnectionState.FAILED,
                                    ConnectionState.DISCONNECTED,
                                    -> false
                                },
                            connectingNodeName =
                                when (status.state) {
                                    ConnectionState.CONNECTED,
                                    ConnectionState.FAILED,
                                    ConnectionState.DISCONNECTED,
                                    -> null
                                    else -> current.connectingNodeName
                                },
                            error =
                                when (status.state) {
                                    ConnectionState.FAILED -> status.error ?: current.error
                                    ConnectionState.CONNECTED -> null
                                    else -> current.error
                                },
                        )
                    when (status.state) {
                        ConnectionState.CONNECTED -> {
                            val cleared = newState.copy(isSwitching = false)
                            persistSessionSnapshot()
                            repository.resetVpnReconnectAttempts()
                            VpnConnectionBus.updateConnectedNode(cleared.connectedNodeName)
                            val busDegraded =
                                status.probeStatus?.equals("degraded", ignoreCase = true) == true
                            if (busDegraded) {
                                startPeriodicHealthProbe()
                                schedulePrivacyProbe()
                                cleared.withProtection()
                            } else if (current.probeStatus == ProbeStatus.IDLE || current.isSwitching) {
                                startProbe()
                                startPeriodicHealthProbe()
                                schedulePrivacyProbe()
                                cleared.copy(probeStatus = ProbeStatus.PROBING).withProtection()
                            } else {
                                startPeriodicHealthProbe()
                                schedulePrivacyProbe()
                                cleared.withProtection()
                            }
                        }
                        ConnectionState.DISCONNECTED, ConnectionState.FAILED -> {
                            probeJob?.cancel()
                            healthProbeJob?.cancel()
                            val reason = pendingDisconnectReason
                            pendingDisconnectReason = null
                            if (reason == DisconnectReason.User) {
                                userInitiatedDisconnect = true
                            }
                            newState.copy(
                                isSwitching = false,
                                probeStatus = ProbeStatus.IDLE,
                                actionHint = null,
                                connectedNodeName = null,
                                sessionUploadBytes = 0L,
                                sessionDownloadBytes = 0L,
                                sessionDurationMs = 0L,
                                sessionUploadBps = 0L,
                                sessionDownloadBps = 0L,
                            ).withProtection(status.error)
                        }
                        ConnectionState.CONNECTING ->
                            newState.copy(
                                probeStatus = ProbeStatus.IDLE,
                                isSwitching = current.isSwitching,
                                // 重连/切换中清空上次会话流量，避免刚连上仍显示几百 MB
                                sessionUploadBytes = 0L,
                                sessionDownloadBytes = 0L,
                                sessionDurationMs = 0L,
                                sessionUploadBps = 0L,
                                sessionDownloadBps = 0L,
                            ).withProtection(status.error)
                    }
                }
            }
        }
    }

    /** 用户点击连接后、权限弹窗前立即调用，保证按钮即时进入连接态。 */
    fun onConnectIntent(targetNode: String? = null) {
        if (_state.value.connectionState == ConnectionState.CONNECTED) return
        ConnectTimingTracker.markConnectClick()
        val node = targetNode?.trim().orEmpty().ifBlank { _state.value.selectedNode }
        _state.update {
            it.copy(
                connectPending = true,
                connectingNodeName = node,
                error = null,
                failureReason = null,
            )
        }
    }

    private fun startProbe() {
        probeJob?.cancel()
        probeJob =
            viewModelScope.launch {
                _state.update { it.copy(probeStatus = ProbeStatus.PROBING) }
                val splitDomesticDirect = AppRouteMode.isDomesticDirectEnabled(_state.value.routeMode)
                val domesticReturn =
                    ConnectionScenario.isDomesticReturnProfile(_state.value.activeProfile)
                val probeResult =
                    ConnectivityProbe.probeWithRetry(
                        splitDomesticDirect = splitDomesticDirect,
                        domesticReturn = domesticReturn,
                    )
                val probeStatus = probeResult.toStatus()
                val busDegraded =
                    vpnController.status.value.probeStatus?.equals("degraded", ignoreCase = true) == true
                if (busDegraded) {
                    AppDebugLogger.warn(
                        category = "probe",
                        message = "数据面探测未通过，保持连接（仅记录日志）",
                    )
                    _state.update { current ->
                        if (current.connectionState != ConnectionState.CONNECTED) return@update current
                        current
                            .copy(
                                probeStatus = ProbeStatus.DEGRADED,
                                nodeProbeLatencyMs = probeResult.latencyMs,
                            ).withProtection()
                    }
                    return@launch
                }
                val probeStatusKey = probeStatus.name.lowercase()
                val dashboard =
                    runCatching {
                        repository.getConnectDashboard(_state.value.selectedNode)
                    }.getOrNull()
                val runtimeNode = resolveRuntimeNodeName()
                val connectedNode =
                    runtimeNode?.takeIf { LineAcquireNode.isAcquirable(it) }
                        ?: _state.value.connectedNodeName
                VpnConnectionBus.updateQuality(
                    probeStatus = probeStatusKey,
                    connectedNode = connectedNode,
                    probeLatencyMs = probeResult.latencyMs,
                )
                val exitInfo =
                    if (probeStatus != ProbeStatus.FAILED) {
                        ExitIpProbe.probeViaVpn(repository.exitIpProbeContext())
                    } else {
                        null
                    }
                if (exitInfo != null) {
                    VpnConnectionBus.updateQuality(
                        probeStatus = probeStatusKey,
                        connectedNode = connectedNode,
                        probeLatencyMs = probeResult.latencyMs,
                        exitIp = exitInfo.ip,
                        exitCountry = exitInfo.country,
                        exitCity = exitInfo.city,
                    )
                }
                AppDebugLogger.info(
                    category = "probe",
                    message = "探测完成: status=$probeStatusKey latency=${probeResult.latencyMs}ms basic=${probeResult.basicOk} overseas=${probeResult.overseasOk}",
                    context =
                        buildMap {
                            put("reason", probeResult.failureCause?.name?.lowercase() ?: "ok")
                            put("attempts", ConnectivityProbe.DEFAULT_PROBE_ATTEMPTS.toString())
                        },
                )
                if (probeStatus == ProbeStatus.FAILED) {
                    val cause =
                        probeResult.failureCause?.toConnectFailureReason()
                            ?: ConnectFailureReason.NODE_UNREACHABLE
                    val nodeLabel = connectedNode ?: _state.value.selectedNode
                    AppDebugLogger.warn(
                        category = "probe",
                        message = "网络质量探测未通过，保持连接（仅记录日志）",
                        context =
                            mapOf(
                                "probe_cause" to (probeResult.failureCause?.name?.lowercase() ?: "-"),
                                "node" to (nodeLabel ?: ""),
                                "reason" to cause.logCode,
                            ),
                    )
                    _state.update { current ->
                        if (current.connectionState != ConnectionState.CONNECTED) {
                            return@update current
                        }
                        current.copy(
                            probeStatus = ProbeStatus.DEGRADED,
                            nodeProbeLatencyMs = probeResult.latencyMs,
                            error = null,
                            failureReason = null,
                        ).withProtection()
                    }
                    return@launch
                }
                if (probeStatus == ProbeStatus.SLOW || probeStatus == ProbeStatus.LIMITED_OVERSEAS) {
                    AppDebugLogger.warn(
                        category = "probe",
                        message = "网络质量: $probeStatusKey",
                        context =
                            mapOf(
                                "latency_ms" to (probeResult.latencyMs?.toString() ?: "-"),
                                "probe_cause" to (probeResult.failureCause?.name?.lowercase() ?: "-"),
                            ),
                    )
                }
                _state.update { current ->
                    if (current.connectionState != ConnectionState.CONNECTED) return@update current
                    val merged =
                        ConnectProbePolicy.mergeProbeStatus(
                            measured = probeStatus,
                            busProbeStatus = vpnController.status.value.probeStatus,
                            connectionState = ConnectionState.CONNECTED,
                            sessionTrafficBytes =
                                current.sessionDownloadBytes + current.sessionUploadBytes,
                        )
                    current.copy(
                        probeStatus = merged,
                        nodeProbeLatencyMs = probeResult.latencyMs ?: current.nodeProbeLatencyMs,
                        exitIp = exitInfo?.ip ?: dashboard?.exit_ip ?: current.exitIp,
                        exitCountry = exitInfo?.country ?: dashboard?.exit_country ?: current.exitCountry,
                        exitCity = exitInfo?.city ?: dashboard?.exit_city ?: current.exitCity,
                        connectedNodeName =
                            connectedNode?.takeIf { LineAcquireNode.isAcquirable(it) }
                                ?: current.connectedNodeName,
                        sceneTags = dashboard?.scene_tags?.takeIf { it.isNotEmpty() } ?: current.sceneTags,
                        error = null,
                        failureReason = null,
                    ).withProtection()
                }
            }
    }

    fun dismissPrivacyOnboarding() {
        pendingConnectAction = null
        _state.update { it.copy(showPrivacyOnboarding = false) }
    }

    /** @deprecated 隐私引导已改为静默基线，保留空实现避免旧调用方编译失败。 */
    fun onMainShellEnter() = Unit

    private fun interruptInFlightConnect() {
        connectJob?.cancel()
        val current = _state.value
        if (current.connectionState != ConnectionState.DISCONNECTED) {
            vpnController.disconnect()
        }
    }

    fun openPrivacyOnboardingVpnSettings() {
        BatteryOptimizationGuide.openVpnSettings(repository.applicationContext())
    }

    fun openPrivacyOnboardingBatterySettings() {
        BatteryOptimizationGuide.openBatteryOptimizationSettings(repository.applicationContext())
    }

    fun completePrivacyOnboarding(skippedSystemSettings: Boolean) {
        privacyOnboardingStore.markCompleted(skippedSystemSettings)
        val pending = pendingConnectAction
        pendingConnectAction = null
        _state.update { it.copy(showPrivacyOnboarding = false) }
        when (pending) {
            is PendingConnectAction.Node -> launchConnectToNode(pending.nodeName, pending.region)
            PendingConnectAction.Default -> startDefaultConnect()
            null -> Unit
        }
    }

    private fun schedulePrivacyProbe() {
        privacyProbeJob?.cancel()
        privacyProbeJob =
            viewModelScope.launch {
                delay(30_000L)
                if (_state.value.connectionState != ConnectionState.CONNECTED) return@launch
                _state.update { it.copy(privacyProbeRunning = true) }
                val result =
                    PrivacyLeakProbe.run(
                        ipv6ProtectionEnabled = repository.isIpv6LeakProtectionEnabled(),
                    )
                repository.setLastPrivacyProbeAt(System.currentTimeMillis())
                PrivacyProbeHistoryStore.append(repository.applicationContext(), result)
                _state.update { current ->
                    current.copy(
                        privacyProbeRunning = false,
                    ).withProtection()
                }
            }
    }

    /** 登录后 / 进主界面时预热 Mihomo 内核。 */
    fun warmupForFastConnect(app: Application) {
        MihomoWarmup.schedule(app)
    }

    private fun resolveRuntimeNodeName(): String? {
        val state = _state.value
        LineAcquireNode.resolve(
            selectedNode = state.selectedNode,
            configNode = null,
            effectiveNode = null,
        )?.let { return it }
        activeConfigJson?.let { json ->
            ClashConfigParser.resolveEffectiveNode(json)?.let { node ->
                if (LineAcquireNode.isAcquirable(node)) return node
            }
        }
        return null
    }

    fun refresh() {
        viewModelScope.launch {
            val hasData = !_state.value.loading
            _state.value =
                _state.value.copy(
                    refreshing = hasData,
                    loading = !hasData,
                    error = null,
                )
            runCatching {
                repository.ensureNetworkAvailable()
                val sub = repository.getActiveSubscription()
                val usage = if (sub != null) repository.getUsage() else null
                val regions = repository.getRegions()
                val renewalHint = sub?.let { buildRenewalHint(it.expires_at) }
                val appDebugEnabled = repository.isAppDebugEnabled()
                val me = runCatching { repository.getMe() }.getOrNull()
                val prefs = runCatching { repository.getUserPreferences() }.getOrNull()
                val dashboard =
                    runCatching {
                        repository.getConnectDashboard(repository.getSavedNode())
                    }.getOrNull()
                val selectedRegion = repository.getSavedRegion()
                val selectedNode = repository.getSavedNode()
                val connectionScenario = prefs?.connection_scenario ?: ConnectionScenario.AUTO
                val resolved = resolveConnectionConfig(connectionScenario)
                _state.value =
                    ConnectUiState(
                        loading = false,
                        refreshing = false,
                        subscription = sub,
                        usage = usage,
                        regions = regions,
                        selectedRegion = selectedRegion,
                        selectedNode = selectedNode,
                        routeMode = resolved.routeMode,
                        connectionState = vpnController.status.value.state,
                        appDebugEnabled = appDebugEnabled,
                        probeStatus =
                            if (vpnController.status.value.state == ConnectionState.CONNECTED) {
                                ProbeStatus.PROBING
                            } else {
                                ProbeStatus.IDLE
                            },
                        renewalHint = renewalHint,
                        userId = dashboard?.user_id ?: me?.id,
                        accountEmail = me?.email,
                        isVip = dashboard?.is_vip == true,
                        dashboardExpiresAt = dashboard?.expires_at ?: sub?.expires_at,
                        connectionScenario = connectionScenario,
                        connectionScenarioLabel = prefs?.connection_scenario_label ?: ConnectionScenario.label(connectionScenario),
                        activeProfile = resolved.profile,
                        activeRouteMode = resolved.routeMode,
                        exitIp = dashboard?.exit_ip,
                        exitCountry = dashboard?.exit_country,
                        exitCity = dashboard?.exit_city,
                        nodeProbeLatencyMs = dashboard?.probe_latency_ms,
                        sceneTags = dashboard?.scene_tags.orEmpty(),
                    )
                repository.saveRouteMode(resolved.routeMode)
                if (vpnController.status.value.state == ConnectionState.CONNECTED) {
                    startProbe()
                }
            }.onFailure { e ->
                if (e is SessionInvalidatedException) return@onFailure
                _state.value =
                    _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = mapLoadError(e),
                    )
            }
        }
    }

    fun selectRegion(region: String?) {
        repository.saveRegion(region)
        viewModelScope.launch {
            var selectedNode = _state.value.selectedNode
            if (!selectedNode.isNullOrBlank() && region != null) {
                val nodes = runCatching { repository.getNodes() }.getOrDefault(emptyList())
                val node = nodes.find { it.name == selectedNode }
                if (node != null && !node.region.equals(region, ignoreCase = true)) {
                    repository.saveNode(null)
                    selectedNode = null
                }
            }
            val resolved = resolveConnectionConfig(_state.value.connectionScenario)
            repository.saveRouteMode(resolved.routeMode)
            val message =
                if (!selectedNode.isNullOrBlank()) {
                    routeSelectionMessage(region, selectedNode)
                } else {
                    val regionLabel =
                        if (region.isNullOrBlank()) "全部地区" else regionDisplayName(region, _state.value.regions)
                    "已切换至「$regionLabel」，请前往节点页选择线路"
                }
            _state.value =
                _state.value.copy(
                    selectedNode = selectedNode,
                    selectedRegion = region,
                    routeMode = resolved.routeMode,
                    activeProfile = resolved.profile,
                    activeRouteMode = resolved.routeMode,
                    routeMessage = message,
                    error = null,
                )
            if (shouldAutoReconnect() && !selectedNode.isNullOrBlank()) {
                onConnectIntent()
                performConnect(reconnect = true, switchingHint = null)
            }
        }
    }

    fun dismissRouteMessage() {
        _state.value = _state.value.copy(routeMessage = null)
    }

    private fun routeSelectionMessage(region: String?, nodeName: String?): String {
        if (!nodeName.isNullOrBlank()) {
            return "已选定节点「${displayNodeName(nodeName)}」"
        }
        val regionLabel =
            if (region.isNullOrBlank()) {
                "全部地区"
            } else {
                regionDisplayName(region, _state.value.regions)
            }
        return "请前往节点页选择「$regionLabel」下的线路"
    }

    private fun resolveConnectionConfig(
        scenario: String,
        region: String? = _state.value.selectedRegion,
        accessMode: String? = null,
    ): ResolvedConnectionConfig = ConnectionScenario.resolve(scenario, region, accessMode)

    private fun resolveConfigRouteQuery(selectedNode: String): ConfigRouteQuery =
        ConfigRouteQuery(
            region = null,
            node = selectedNode,
        )

    private fun configParams(): Pair<String?, String?> {
        val node = _state.value.selectedNode?.trim().orEmpty()
        return null to node.takeIf { it.isNotBlank() }
    }

    private fun migrateRouteModeIfNeeded() {
        viewModelScope.launch {
            val resolved = resolveConnectionConfig(_state.value.connectionScenario)
            repository.saveRouteMode(resolved.routeMode)
        }
    }

    fun selectNode(nodeName: String?) {
        repository.saveNode(nodeName)
        _state.value = _state.value.copy(selectedNode = nodeName)
    }

    fun connectToNode(nodeName: String, region: String?) {
        launchConnectToNode(nodeName, region)
    }

    private fun launchConnectToNode(nodeName: String, region: String?) {
        interruptInFlightConnect()
        _state.update {
            it.copy(
                connectingNodeName = nodeName,
                connectPending = true,
                error = null,
            )
        }
        connectJob =
            viewModelScope.launch {
            val nodes = runCatching { repository.getNodes() }.getOrDefault(emptyList())
            val node = nodes.find { it.name == nodeName }
            if (node != null && !node.isOnline()) {
                _state.value = _state.value.copy(error = "该节点已离线，请选择其他节点", connectPending = false, connectingNodeName = null)
                return@launch
            }
            if (node != null && !AppProtocolSupport.isAppConnectable(node)) {
                _state.value =
                    _state.value.copy(
                        error = AppProtocolSupport.unsupportedReason(node),
                        connectPending = false,
                        connectingNodeName = null,
                    )
                return@launch
            }
            val sub = _state.value.subscription
            if (sub == null) {
                _state.value = _state.value.copy(error = "请先购买套餐", connectPending = false, connectingNodeName = null)
                return@launch
            }
            repository.saveNode(nodeName)
            repository.saveRegion(region)
            val resolved =
                resolveConnectionConfig(
                    _state.value.connectionScenario,
                    region = region,
                    accessMode = node?.access_mode,
                )
            repository.saveRouteMode(resolved.routeMode)
            val reconnect = _state.value.connectionState == ConnectionState.CONNECTED
            _state.value =
                _state.value.copy(
                    selectedNode = nodeName,
                    selectedRegion = region,
                    routeMode = resolved.routeMode,
                    activeProfile = resolved.profile,
                    activeRouteMode = resolved.routeMode,
                    routeMessage = null,
                    connectingNodeName = nodeName,
                    actionHint = NodeAccessHint.scenarioMismatchHint(_state.value.connectionScenario, node?.access_mode),
                    error = null,
                    isSwitching = reconnect,
                )
            userInitiatedDisconnect = false
            performConnect(reconnect = reconnect, switchingHint = if (reconnect) "正在切换节点…" else null)
        }
    }

    fun onNodeSelected(nodeName: String, region: String?) {
        connectToNode(nodeName, region)
    }

    fun onClearNodeSelected() {
        viewModelScope.launch {
            repository.saveNode(null)
            _state.value =
                _state.value.copy(
                    selectedNode = null,
                    routeMessage = null,
                    error = null,
                )
        }
    }

    fun connect() {
        val sub = _state.value.subscription
        if (sub == null) {
            _state.update {
                it.copy(
                    error = null,
                    connectPending = false,
                    requestNavigateToPackages = true,
                )
            }
            return
        }
        if (_state.value.selectedNode.isNullOrBlank()) {
            _state.update {
                it.copy(
                    error = null,
                    connectPending = false,
                    requestNavigateToNodes = true,
                )
            }
            return
        }
        startDefaultConnect()
    }

    private fun startDefaultConnect() {
        userInitiatedDisconnect = false
        onConnectIntent()
        connectJob?.cancel()
        connectJob =
            viewModelScope.launch {
                performConnect(reconnect = false, switchingHint = null)
            }
    }

    private fun requireSelectedNode(): String {
        val node = _state.value.selectedNode?.trim().orEmpty()
        if (node.isBlank() || !LineAcquireNode.isAcquirable(node)) {
            error("请先在节点页选择要连接的节点")
        }
        return node
    }

    private data class PreparedConnect(
        val selectedNode: String,
        val configYaml: String,
        val routeTarget: String,
        val nodeLabel: String,
        val routeMode: String,
        val profile: String,
        val fromCache: Boolean,
    )

    private suspend fun performConnect(reconnect: Boolean, switchingHint: String?) {
        val targetNode = _state.value.selectedNode?.trim().orEmpty().ifBlank { null }
        _state.update {
            it.copy(
                probeStatus = ProbeStatus.IDLE,
                error = null,
                failureReason = null,
                isSwitching = switchingHint != null,
                connectPending = true,
                connectingNodeName = targetNode ?: it.connectingNodeName,
            )
        }
        runCatching {
            withTimeout(VpnAutoReconnectPolicy.CONNECT_TIMEOUT_MS) {
                val prepared = prepareConnectMaterials(allowCacheFallback = false)
                dispatchPreparedConnect(prepared, reconnect = reconnect)
            }
        }.onFailure { e ->
            if (e is SessionInvalidatedException) return@onFailure
            if (e is TimeoutCancellationException) {
                val resolved =
                    ConnectFailureReason.NODE_UNREACHABLE to
                        "连接超时（${VpnAutoReconnectPolicy.CONNECT_TIMEOUT_MS / 1000} 秒），请检查网络或更换节点"
                AppDebugLogger.error(
                    category = "connect_fail",
                    message = resolved.first.logCode,
                    context = mapOf("reason" to "timeout"),
                )
                _state.value =
                    _state.value.copy(
                        connectionState = ConnectionState.FAILED,
                        isSwitching = false,
                        connectPending = false,
                        connectingNodeName = null,
                        probeStatus = ProbeStatus.IDLE,
                        error = resolved.second,
                        failureReason = resolved.first,
                    )
                return@onFailure
            }
            val resolved = resolveConnectError(e) ?: return@onFailure
            AppDebugLogger.error(
                category = "connect_fail",
                message = resolved.reason.logCode,
                context =
                    mapOf(
                        "reason" to resolved.reason.logCode,
                        "detail" to resolved.message,
                        "type" to (e::class.simpleName ?: "unknown"),
                    ),
            )
            _state.value =
                _state.value.copy(
                    connectionState = ConnectionState.FAILED,
                    isSwitching = false,
                    connectPending = false,
                    connectingNodeName = null,
                    probeStatus = ProbeStatus.IDLE,
                    error = resolved.message,
                    failureReason = resolved.reason,
                )
        }
    }

    /** 拉取并校验连接材料；自动重连在 Kill Switch 前调用，避免阻断后拉不到 API。 */
    private suspend fun prepareConnectMaterials(allowCacheFallback: Boolean): PreparedConnect {
        val selectedNode = requireSelectedNode()
        return try {
            val routeQuery = resolveConfigRouteQuery(selectedNode)
            val (nodes, config) =
                coroutineScope {
                    val nodesJob = async { repository.getNodes() }
                    val configJob =
                        async {
                            val nodeMeta = nodesJob.await().find { it.name == selectedNode }
                            val resolved =
                                resolveConnectionConfig(
                                    _state.value.connectionScenario,
                                    region = _state.value.selectedRegion,
                                    accessMode = nodeMeta?.access_mode,
                                )
                            repository.saveRouteMode(resolved.routeMode)
                            _state.update {
                                it.copy(
                                    routeMode = resolved.routeMode,
                                    activeProfile = resolved.profile,
                                    activeRouteMode = resolved.routeMode,
                                )
                            }
                            AppDebugLogger.info(
                                category = "connect",
                                message = "开始连接",
                                context =
                                    mapOf(
                                        "region" to (routeQuery.region ?: _state.value.selectedRegion ?: ""),
                                        "node" to (routeQuery.node ?: ""),
                                        "profile" to resolved.profile,
                                        "route_mode" to resolved.routeMode,
                                        "scenario" to _state.value.connectionScenario,
                                    ),
                            )
                            fetchClientConfig(
                                region = routeQuery.region,
                                node = routeQuery.node,
                                profile = resolved.profile,
                                routeMode = resolved.routeMode,
                            )
                        }
                    Pair(nodesJob.await(), configJob.await())
                }
            val activeProfile = _state.value.activeProfile ?: ConnectionScenario.PROFILE_OVERSEAS_WEAK
            val activeRouteMode = _state.value.routeMode
            val node = nodes.find { it.name == selectedNode }
            if (node != null && !AppProtocolSupport.isAppConnectable(node)) {
                repository.saveNode(null)
                _state.update { it.copy(selectedNode = null) }
                error("所选节点不支持 App 连接：${AppProtocolSupport.unsupportedReason(node)}")
            }
            ClashConfigSanitizer.validateClashYaml(config.config)
            val displayNode = selectedNode
            val nodeLabel = displayNodeName(displayNode)
            val routeTarget =
                ClashRouteTarget.resolve(
                    configNode = config.node,
                    configYaml = config.config,
                    selectedNode = displayNode,
                )
            PreparedConnect(
                selectedNode = displayNode,
                configYaml = config.config,
                routeTarget = routeTarget,
                nodeLabel = nodeLabel,
                routeMode = activeRouteMode,
                profile = activeProfile,
                fromCache = false,
            )
        } catch (e: Exception) {
            val cached = cachedPreparedConnectOrNull(selectedNode)
            if (
                AutoReconnectPrepPolicy.allowCachedConfigFallback(
                    apiFailed = true,
                    hasCachedConfig = cached != null,
                ) &&
                allowCacheFallback &&
                cached != null
            ) {
                AppDebugLogger.warn(
                    category = "reconnect",
                    message = "拉配置失败，改用本地缓存配置重连",
                    context =
                        mapOf(
                            "node" to selectedNode,
                            "error" to (e.message ?: e::class.simpleName ?: "unknown"),
                        ),
                )
                cached
            } else {
                throw e
            }
        }
    }

    private fun cachedPreparedConnectOrNull(selectedNode: String): PreparedConnect? {
        val yaml =
            activeConfigJson?.trim()?.takeIf { it.isNotEmpty() }
                ?: ClashConfigStore.readOrNull(repository.applicationContext())?.trim()?.takeIf { it.isNotEmpty() }
                ?: return null
        return runCatching {
            ClashConfigSanitizer.validateClashYaml(yaml)
            val routeTarget =
                ClashRouteTarget.resolve(
                    configNode = null,
                    configYaml = yaml,
                    selectedNode = selectedNode,
                )
            PreparedConnect(
                selectedNode = selectedNode,
                configYaml = yaml,
                routeTarget = routeTarget,
                nodeLabel = displayNodeName(selectedNode),
                routeMode = _state.value.routeMode,
                profile = _state.value.activeProfile ?: ConnectionScenario.PROFILE_OVERSEAS_WEAK,
                fromCache = true,
            )
        }.getOrNull()
    }

    private fun dispatchPreparedConnect(
        prepared: PreparedConnect,
        reconnect: Boolean,
    ) {
        activeConfigJson = prepared.configYaml
        _state.value =
            _state.value.copy(
                connectedNodeName = prepared.selectedNode,
                activeProfile = prepared.profile,
                activeRouteMode = prepared.routeMode,
                routeMode = prepared.routeMode,
            )
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
        ConnectTimingTracker.markConfigDispatched()
        AppDebugLogger.info(
            category = "connect",
            message = "配置已下发，节点=${prepared.nodeLabel}，selector=${prepared.routeTarget}",
            context = mapOf("from_cache" to prepared.fromCache.toString()),
        )
    }

    private suspend fun awaitPhysicalNetworkReady(why: String): Boolean {
        val deadline = System.currentTimeMillis() + AutoReconnectPrepPolicy.PHYSICAL_READY_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (userInitiatedDisconnect) return false
            if (
                PostHealRecoveryPolicy.shouldProceedAutoReconnect(
                    NetworkMonitor.hasValidatedPhysicalInternet(repository.applicationContext()),
                )
            ) {
                return true
            }
            AppDebugLogger.info(
                category = "reconnect",
                message = "等待物理网恢复",
                context = mapOf("reason" to why),
            )
            _state.update {
                it.copy(actionHint = "网络已断开，恢复后将自动重连")
            }
            delay(AutoReconnectPrepPolicy.PHYSICAL_POLL_MS)
        }
        return PostHealRecoveryPolicy.shouldProceedAutoReconnect(
            NetworkMonitor.hasValidatedPhysicalInternet(repository.applicationContext()),
        )
    }

    override fun scheduleAutoReconnect(reason: String) {
        if (userInitiatedDisconnect || !repository.isAutoReconnectEnabled()) return
        val nodeForReconnect =
            _state.value.selectedNode?.trim()?.takeIf { it.isNotEmpty() }
                ?: repository.getVpnSessionSnapshot()?.nodeName?.trim()?.takeIf { it.isNotEmpty() }
        if (nodeForReconnect.isNullOrBlank()) {
            AppDebugLogger.warn(
                category = "reconnect",
                message = "自动重连跳过：无选中节点",
                context = mapOf("reason" to reason),
            )
            AppDebugLogger.flush()
            return
        }
        if (_state.value.selectedNode.isNullOrBlank()) {
            repository.saveNode(nodeForReconnect)
            _state.update { it.copy(selectedNode = nodeForReconnect) }
        }
        pendingReconnectReason = reason
        // 已进入执行阶段：只更新 reason，禁止 cancel（21:27：准备重连后被 dns 风暴掐死）
        if (reconnectExecuting) {
            AppDebugLogger.info(
                category = "reconnect",
                message = "重连进行中，合并事件",
                context = mapOf("reason" to reason),
            )
            return
        }
        autoReconnectJob?.cancel()
        autoReconnectJob =
            viewModelScope.launch {
                try {
                    delay(DnsChurnPolicy.reconnectDebounceMs(pendingReconnectReason ?: reason))
                    if (userInitiatedDisconnect || !repository.isAutoReconnectEnabled()) return@launch
                    val why = pendingReconnectReason ?: reason
                    reconnectExecuting = true

                    if (why == "network_restored") {
                        repository.resetVpnReconnectAttempts()
                    }

                    var lastError: Throwable? = null
                    for (round in 1..VpnAutoReconnectPolicy.MAX_ATTEMPTS) {
                        if (userInitiatedDisconnect || !repository.isAutoReconnectEnabled()) return@launch
                        if (!awaitPhysicalNetworkReady(why)) {
                            AppDebugLogger.info(
                                category = "reconnect",
                                message = "物理网不可用，推迟自动重连",
                                context = mapOf("reason" to why),
                            )
                            AppDebugLogger.flush()
                            _state.update {
                                it.copy(actionHint = "网络已断开，恢复后将自动重连")
                            }
                            return@launch
                        }

                        val attempts = repository.incrementVpnReconnectAttempts()
                        if (attempts > VpnAutoReconnectPolicy.MAX_ATTEMPTS) {
                            break
                        }
                        _state.update {
                            it.copy(
                                actionHint = "网络已变化，正在自动重连（$attempts/${VpnAutoReconnectPolicy.MAX_ATTEMPTS}）…",
                                connectPending = true,
                                error = null,
                                failureReason = null,
                            )
                        }
                        AppDebugLogger.info(
                            category = "reconnect",
                            message = "自动重连",
                            context = mapOf("reason" to why, "attempt" to attempts.toString()),
                        )
                        AppDebugLogger.flush()
                        userInitiatedDisconnect = false

                        // 给 DNS/VALIDATED 收敛；首轮也 settle，避免 KS 前抢连
                        delay(AutoReconnectPrepPolicy.NETWORK_SETTLE_MS)
                        if (userInitiatedDisconnect) return@launch

                        val networkRecovery =
                            why == "network_restored" || why.startsWith("transport_")
                        if (!(networkRecovery && attempts == 1)) {
                            delay(VpnAutoReconnectPolicy.backoffDelayMs(attempts - 1))
                        }
                        if (userInitiatedDisconnect) return@launch

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
                        val serviceRunning =
                            VpnTunnelStateSync.isServiceRunning(repository.applicationContext())
                        val tunnelLive =
                            serviceRunning &&
                                (
                                    _state.value.connectionState == ConnectionState.CONNECTED ||
                                        VpnTunnelStateSync.isVpnTransportActive(repository.applicationContext())
                                )

                        // 关键：先备配置（隧道/物理网仍可能出网），再 KS 拆隧道，避免 30s API 超时
                        val prepared =
                            runCatching {
                                withTimeout(VpnAutoReconnectPolicy.CONNECT_TIMEOUT_MS) {
                                    prepareConnectMaterials(allowCacheFallback = true)
                                }
                            }.onFailure { lastError = it }
                                .getOrNull()
                        if (prepared == null) {
                            AppDebugLogger.warn(
                                category = "reconnect",
                                message = "自动重连备配置失败",
                                context =
                                    mapOf(
                                        "reason" to why,
                                        "attempt" to attempts.toString(),
                                        "error" to (lastError?.message ?: lastError?.javaClass?.simpleName ?: ""),
                                    ),
                            )
                            if (round < VpnAutoReconnectPolicy.MAX_ATTEMPTS) {
                                delay(VpnAutoReconnectPolicy.backoffDelayMs(attempts))
                                continue
                            }
                            break
                        }

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
                            VpnTunnelStateSync.isServiceRunning(repository.applicationContext()) &&
                                VpnTunnelService.isTunnelRunning
                        dispatchPreparedConnect(prepared, reconnect = stillTunnelRunning)
                        // 下发成功即本轮调度完成；隧道结果由 VpnConnectionBus 推进 UI
                        repository.resetVpnReconnectAttempts()
                        return@launch
                    }

                    val physicalOnline =
                        NetworkMonitor.hasValidatedPhysicalInternet(repository.applicationContext())
                    AppDebugLogger.warn(
                        category = "reconnect",
                        message = "自动重连次数耗尽",
                        context =
                            mapOf(
                                "reason" to why,
                                "error" to (lastError?.message ?: lastError?.javaClass?.simpleName ?: ""),
                            ),
                    )
                    AppDebugLogger.flush()
                    _state.update {
                        it.copy(
                            connectPending = false,
                            connectionState = ConnectionState.FAILED,
                            actionHint =
                                if (physicalOnline) {
                                    "自动重连失败，请手动点击连接"
                                } else {
                                    "网络已断开，恢复后将自动重连"
                                },
                            error =
                                if (lastError is TimeoutCancellationException) {
                                    "连接超时（${VpnAutoReconnectPolicy.CONNECT_TIMEOUT_MS / 1000} 秒），请检查网络或更换节点"
                                } else {
                                    it.error
                                },
                        )
                    }
                } finally {
                    reconnectExecuting = false
                }
            }
    }

    private suspend fun fetchClientConfig(
        region: String?,
        node: String?,
        profile: String,
        routeMode: String,
    ): ClientConfigData = repository.getClientConfig(region, node, profile, routeMode)

    private fun shouldAutoReconnect(): Boolean {
        if (userInitiatedDisconnect) return false
        val state = _state.value.connectionState
        return state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
    }

    fun disconnect() {
        probeJob?.cancel()
        connectJob?.cancel()
        connectJob = null
        pendingConnectAction = null
        pendingDisconnectReason = DisconnectReason.User
        userInitiatedDisconnect = true
        repository.clearVpnSessionSnapshot()
        repository.resetVpnReconnectAttempts()
        autoReconnectJob?.cancel()
        healthProbeJob?.cancel()
        vpnController.disconnect()
        AppDebugLogger.info(category = "connect", message = "用户断开连接")
        AppDebugLogger.flush()
        _state.value =
            _state.value.copy(
                connectionState = ConnectionState.DISCONNECTED,
                connectPending = false,
                isSwitching = false,
                connectingNodeName = null,
                probeStatus = ProbeStatus.IDLE,
                error = null,
                failureReason = null,
                actionHint = null,
                connectedNodeName = null,
                exitIp = null,
                exitCountry = null,
                exitCity = null,
                nodeProbeLatencyMs = null,
                sceneTags = emptyList(),
                sessionUploadBytes = 0L,
                sessionDownloadBytes = 0L,
                sessionDurationMs = 0L,
                sessionUploadBps = 0L,
                sessionDownloadBps = 0L,
            )
        reloadDashboard()
    }

    fun consumeNavigateToNodesRequest() {
        _state.update { it.copy(requestNavigateToNodes = false) }
    }

    fun consumeNavigateToPackagesRequest() {
        _state.update { it.copy(requestNavigateToPackages = false) }
    }

    fun updateConnectionScenario(scenario: String) {
        viewModelScope.launch {
            runCatching {
                val pref =
                    repository.updateUserPreferences(
                        UserPreferencesUpdate(connection_scenario = scenario),
                    )
                val normalized = pref.connection_scenario ?: ConnectionScenario.normalize(scenario)
                val resolved = resolveConnectionConfig(normalized)
                repository.saveRouteMode(resolved.routeMode)
                _state.update {
                    it.copy(
                        connectionScenario = normalized,
                        connectionScenarioLabel = pref.connection_scenario_label ?: ConnectionScenario.label(normalized),
                        routeMode = resolved.routeMode,
                        activeProfile = resolved.profile,
                        activeRouteMode = resolved.routeMode,
                        routeMessage = "使用场景已更新",
                    )
                }
                if (shouldAutoReconnect()) {
                    onConnectIntent()
                    performConnect(reconnect = true, switchingHint = null)
                }
            }.onFailure { e ->
                if (e is SessionInvalidatedException) return@onFailure
                val err = mapConnectError(e) ?: return@onFailure
                _state.update { it.copy(error = err) }
            }
        }
    }

    fun reloadDashboard() {
        viewModelScope.launch {
            runCatching {
                applyDashboard(repository.getConnectDashboard(_state.value.selectedNode))
            }
        }
    }

    override fun connectionState(): ConnectionState = _state.value.connectionState

    override fun notifyActionHint(hint: String?) {
        _state.update { it.copy(actionHint = hint) }
    }

    override fun onNetworkRestoredForUi() {
        viewModelScope.launch {
            runCatching {
                applyDashboard(repository.getConnectDashboard(_state.value.selectedNode))
            }
        }
    }

    override fun startHeal(reason: String) {
        startNetworkRecovery(reason)
    }

    private fun startNetworkRecovery(reason: String) {
        transportRecoveryJob?.cancel()
        transportRecoveryJob =
            viewModelScope.launch {
                recoverAfterNetworkChange(reason)
            }
    }

    /**
     * 仅当**关闭自动重连**时走此路径：轻量自愈（重绑 underlying + 探测）。
     * 自动重连开启时禁止调用——必须走 [scheduleAutoReconnect]。
     */
    private suspend fun recoverAfterNetworkChange(reason: String) {
        if (_state.value.connectionState != ConnectionState.CONNECTED) return
        if (userInitiatedDisconnect) return
        if (repository.isAutoReconnectEnabled()) {
            // 防回归：默认路径不得再掉进 HEAL+探测
            AppDebugLogger.warn(
                category = "network",
                message = "自动重连已开启，跳过自愈改走完整重连",
                context = mapOf("reason" to reason),
            )
            scheduleAutoReconnect("transport_$reason")
            return
        }
        vpnController.healTunnel()
        AppDebugLogger.info(
            category = "network",
            message = "物理网络切换，已触发自愈（自动重连已关）",
            context = mapOf("reason" to reason),
        )
        delay(PostHealRecoveryPolicy.SETTLE_AFTER_HEAL_MS)
        if (_state.value.connectionState != ConnectionState.CONNECTED) return
        if (userInitiatedDisconnect) return

        val physicalOnline =
            NetworkMonitor.hasValidatedPhysicalInternet(repository.applicationContext())
        if (!physicalOnline) {
            healthFailStreak = 0
            AppDebugLogger.info(
                category = "network",
                message = "物理网不可用，保持隧道等待恢复",
                context = mapOf("reason" to reason),
            )
            _state.update {
                it.copy(actionHint = "网络已断开，恢复后将自动重连")
            }
            return
        }

        val splitDomesticDirect = AppRouteMode.isDomesticDirectEnabled(_state.value.routeMode)
        val domesticReturn = ConnectionScenario.isDomesticReturnProfile(_state.value.activeProfile)
        // 权威：系统 VPN 路径（与硬门禁同源）；mixed 仅作延迟展示与双保险。
        val vpnNetworkOk =
            TunDataPlaneVerifier.probeVpnNetworkOk(
                context = repository.applicationContext(),
                domesticReturn = domesticReturn,
                attempts = PostHealRecoveryPolicy.POST_HEAL_PROBE_ATTEMPTS,
                timeoutMs = PostHealRecoveryPolicy.POST_HEAL_PROBE_TIMEOUT_MS,
                retryDelayMs = PostHealRecoveryPolicy.POST_HEAL_RETRY_DELAY_MS,
            )
        val probeResult =
            ConnectivityProbe.probeWithRetry(
                timeoutMs = PostHealRecoveryPolicy.POST_HEAL_PROBE_TIMEOUT_MS,
                splitDomesticDirect = splitDomesticDirect,
                domesticReturn = domesticReturn,
                maxAttempts = PostHealRecoveryPolicy.POST_HEAL_PROBE_ATTEMPTS,
                retryDelayMs = PostHealRecoveryPolicy.POST_HEAL_RETRY_DELAY_MS,
            )
        val probeStatus = probeResult.toStatus()
        AppDebugLogger.info(
            category = "network",
            message = "切网后探测",
            context =
                mapOf(
                    "reason" to reason,
                    "status" to probeStatus.name.lowercase(),
                    "basic" to probeResult.basicOk.toString(),
                    "overseas" to probeResult.overseasOk.toString(),
                    "latency_ms" to probeResult.latencyMs.toString(),
                    "vpn_network_ok" to vpnNetworkOk.toString(),
                    "probe_path" to "system_vpn+mixed",
                    "physical_online" to "true",
                ),
        )
        if (
            PostHealRecoveryPolicy.shouldReconnectAfterNetworkRecovery(
                physicalOnline = true,
                vpnNetworkOk = vpnNetworkOk,
                mixedProbeStatus = probeStatus,
            )
        ) {
            _state.update {
                it.copy(
                    probeStatus = if (vpnNetworkOk) probeStatus else ProbeStatus.FAILED,
                    actionHint = "网络已变化，正在自动重连以恢复保护…",
                )
            }
            scheduleAutoReconnect("transport_$reason")
        } else {
            healthFailStreak = 0
            repository.resetVpnReconnectAttempts()
            _state.update {
                it.copy(
                    probeStatus = probeStatus,
                    nodeProbeLatencyMs = probeResult.latencyMs,
                    actionHint = null,
                )
            }
        }
    }

    private suspend fun onVpnConfigChanged() {
        if (_state.value.connectionState != ConnectionState.CONNECTED) return
        if (_state.value.selectedNode.isNullOrBlank()) return
        _state.update { it.copy(actionHint = "正在应用新的直连设置…", isSwitching = true) }
        performConnect(reconnect = true, switchingHint = "正在应用直连设置…")
    }

    private suspend fun trySwitchToBackupNode(): Boolean {
        val current = _state.value.selectedNode?.trim().orEmpty()
        if (current.isBlank()) return false
        val nodes = runCatching { repository.getNodes() }.getOrDefault(emptyList())
        val backup =
            NodeFailoverSelector.pickBackup(
                currentNodeName = current,
                currentRegion = _state.value.selectedRegion,
                nodes = nodes,
            ) ?: return false
        repository.saveNode(backup.name)
        backup.region?.let { repository.saveRegion(it) }
        _state.update {
            it.copy(
                selectedNode = backup.name,
                selectedRegion = backup.region ?: it.selectedRegion,
            )
        }
        AppDebugLogger.info(
            category = "failover",
            message = "health_failover $current -> ${backup.name}",
            context = mapOf("region" to (backup.region ?: "")),
        )
        return true
    }

    private fun persistSessionSnapshot() {
        if (userInitiatedDisconnect) return
        val state = _state.value
        repository.saveVpnSessionSnapshot(
            VpnSessionSnapshot(
                wasUserConnected = true,
                nodeName = state.selectedNode,
                region = state.selectedRegion,
                profile = state.activeProfile ?: ConnectionScenario.PROFILE_OVERSEAS_WEAK,
                routeMode = state.routeMode,
                connectionScenario = state.connectionScenario,
            ),
        )
    }

    private fun startPeriodicHealthProbe() {
        healthProbeJob?.cancel()
        healthProbeJob =
            viewModelScope.launch {
                while (isActive) {
                    val intervalMs =
                        if (_state.value.probeStatus == ProbeStatus.DEGRADED) {
                            VpnAutoReconnectPolicy.DEGRADED_HEALTH_PROBE_MS
                        } else {
                            VpnAutoReconnectPolicy.PERIODIC_HEALTH_PROBE_MS
                        }
                    delay(intervalMs)
                    if (_state.value.connectionState != ConnectionState.CONNECTED) continue
                    val splitDomesticDirect = AppRouteMode.isDomesticDirectEnabled(_state.value.routeMode)
                    val domesticReturn =
                        ConnectionScenario.isDomesticReturnProfile(_state.value.activeProfile)
                    val probeResult =
                        ConnectivityProbe.probeWithRetry(
                            splitDomesticDirect = splitDomesticDirect,
                            domesticReturn = domesticReturn,
                        )
                    val probeStatus =
                        ConnectProbePolicy.mergeProbeStatus(
                            measured = probeResult.toStatus(),
                            busProbeStatus = vpnController.status.value.probeStatus,
                            connectionState = ConnectionState.CONNECTED,
                            sessionTrafficBytes =
                                _state.value.sessionDownloadBytes + _state.value.sessionUploadBytes,
                        )
                    val probeFailed =
                        probeStatus == ProbeStatus.FAILED || probeStatus == ProbeStatus.DEGRADED
                    if (probeFailed) {
                        val physicalOnline =
                            NetworkMonitor.hasValidatedPhysicalInternet(repository.applicationContext())
                        healthFailStreak =
                            PostHealRecoveryPolicy.nextHealthFailStreak(
                                probeStatus,
                                healthFailStreak,
                                physicalOnline,
                            )
                        if (!physicalOnline) {
                            _state.update {
                                it.copy(
                                    probeStatus = probeStatus,
                                    actionHint = "网络已断开，恢复后将自动重连",
                                )
                            }
                            continue
                        }
                        // 自动同区 failover 默认关闭（见 NodeFailoverMonitor.AUTO_FAILOVER_ENABLED）。
                        NodeFailoverMonitor.recordFailure()
                        if (NodeFailoverMonitor.shouldFailover()) {
                            trySwitchToBackupNode()
                            NodeFailoverMonitor.reset()
                            healthFailStreak = 0
                            _state.update {
                                it.copy(probeStatus = ProbeStatus.DEGRADED)
                            }
                            scheduleAutoReconnect("health_failover")
                        } else if (
                            repository.isAutoReconnectEnabled() &&
                            PostHealRecoveryPolicy.shouldReconnectOnHealthStreak(
                                healthFailStreak,
                                physicalOnline = true,
                            )
                        ) {
                            healthFailStreak = 0
                            _state.update {
                                it.copy(
                                    probeStatus = probeStatus,
                                    actionHint = "连接异常，正在自动重连…",
                                )
                            }
                            scheduleAutoReconnect("health_probe_failed")
                        } else {
                            _state.update {
                                it.copy(probeStatus = ProbeStatus.DEGRADED)
                            }
                        }
                    } else {
                        healthFailStreak = 0
                        NodeFailoverMonitor.recordSuccess()
                        _state.update {
                            it.copy(
                                probeStatus = probeStatus,
                                nodeProbeLatencyMs = probeResult.latencyMs,
                            )
                        }
                    }
                }
            }
    }

    fun onAppForeground() {
        reconnectSupervisor.onAppForeground()
    }

    override fun onCleared() {
        reconnectSupervisor.detachHost(this)
        super.onCleared()
    }

    fun shouldShowBatteryOptimizationGuide(): Boolean =
        BatteryOptimizationGuide.shouldPrompt(
            repository.applicationContext(),
            com.vpn.member.data.local.AppPreferences(repository.applicationContext()),
        )

    fun openBatteryOptimizationSettings() {
        BatteryOptimizationGuide.openBatteryOptimizationSettings(repository.applicationContext())
    }

    fun dismissBatteryOptimizationGuide() {
        repository.setBatteryOptimizationGuideDismissed(true)
    }

    private fun applyDashboard(dashboard: com.vpn.member.data.api.ConnectDashboardData) {
        val runtimeNode = resolveRuntimeNodeName()
        _state.update { current ->
            val tunnelActive =
                current.connectionState == ConnectionState.CONNECTED ||
                    current.connectionState == ConnectionState.CONNECTING
            current.copy(
                userId = dashboard.user_id,
                isVip = dashboard.is_vip,
                dashboardExpiresAt = dashboard.expires_at,
                exitIp = if (tunnelActive) dashboard.exit_ip ?: current.exitIp else null,
                exitCountry = if (tunnelActive) dashboard.exit_country ?: current.exitCountry else null,
                exitCity = if (tunnelActive) dashboard.exit_city ?: current.exitCity else null,
                nodeProbeLatencyMs =
                    if (tunnelActive) {
                        dashboard.probe_latency_ms ?: current.nodeProbeLatencyMs
                    } else {
                        null
                    },
                sceneTags =
                    if (tunnelActive) {
                        dashboard.scene_tags.ifEmpty { current.sceneTags }
                    } else {
                        emptyList()
                    },
                connectedNodeName =
                    if (tunnelActive) {
                        when {
                            LineAcquireNode.isAcquirable(current.connectedNodeName) -> current.connectedNodeName
                            LineAcquireNode.isAcquirable(runtimeNode) -> runtimeNode
                            LineAcquireNode.isAcquirable(current.selectedNode) -> current.selectedNode
                            else -> current.connectedNodeName
                        }
                    } else {
                        null
                    },
            )
        }
    }

    private fun buildRenewalHint(expiresAt: String): String? {
        val days =
            runCatching {
                val expiry = parseInstant(expiresAt)
                ChronoUnit.DAYS.between(Instant.now(), expiry)
            }.getOrNull() ?: return null
        return when {
            days < 0 -> "套餐已过期，请立即续费"
            days == 0L -> "套餐今天到期，建议尽快续费"
            days <= 7 -> "套餐将在 $days 天后到期，建议提前续费"
            else -> null
        }
    }

    private fun parseInstant(raw: String): Instant =
        runCatching { Instant.parse(raw) }
            .getOrElse {
                runCatching {
                    LocalDate.parse(raw.take(10))
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                }.getOrElse {
                    Instant.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(raw.replace(" ", "T")))
                }
            }

    private data class ResolvedConnectError(
        val reason: ConnectFailureReason,
        val message: String,
    )

    private fun resolveConnectError(e: Throwable): ResolvedConnectError? {
        if (e is SessionInvalidatedException) return null
        val app =
            e as? AppException ?: return when {
                e.message?.contains("/127.", ignoreCase = true) == true ->
                    ResolvedConnectError(
                        ConnectFailureReason.CONFIG_INVALID,
                        "连接配置接口失败：检测到系统代理不可达，请关闭手机系统代理或重新安装最新 App 后重试",
                    )
                else -> {
                    val reason =
                        when (e) {
                            is java.net.UnknownHostException,
                            is java.net.SocketTimeoutException,
                            is java.io.IOException,
                            -> ConnectFailureReason.NETWORK_OFFLINE
                            else -> ConnectFailureReason.UNKNOWN
                        }
                    ResolvedConnectError(
                        reason,
                        ApiRequestSupport.mapError(e, "连接失败，请稍后重试"),
                    )
                }
            }
        return when (app.appCode) {
            "NO_ACTIVE_SUBSCRIPTION", "SUBSCRIPTION_EXPIRED" ->
                ResolvedConnectError(
                    ConnectFailureReason.SUBSCRIPTION,
                    ConnectFailureReason.SUBSCRIPTION.userMessage(),
                )
            "TRAFFIC_EXCEEDED" ->
                ResolvedConnectError(
                    ConnectFailureReason.SUBSCRIPTION,
                    "流量已用尽，请续费",
                )
            "NO_AVAILABLE_NODES" ->
                ResolvedConnectError(
                    ConnectFailureReason.NODE_UNREACHABLE,
                    "暂无可用节点，请更换地区或节点后重试",
                )
            "NODE_NOT_ACCESSIBLE" ->
                ResolvedConnectError(
                    ConnectFailureReason.LINE_HELD_CONFLICT,
                    ConnectFailureReason.LINE_HELD_CONFLICT.userMessage(),
                )
            "APP_NODE_REQUIRED" ->
                ResolvedConnectError(
                    ConnectFailureReason.CONFIG_INVALID,
                    "请先在节点页选择要连接的节点",
                )
            else -> {
                val msg = ApiRequestSupport.mapError(app, "连接失败，请稍后重试")
                val reason =
                    when {
                        msg.contains("线路配额") -> ConnectFailureReason.LINE_QUOTA_FULL
                        msg.contains("无权使用该节点") -> ConnectFailureReason.CONFIG_INVALID
                        msg.contains("节点不可用") -> ConnectFailureReason.NODE_UNREACHABLE
                        else -> ConnectFailureReason.UNKNOWN
                    }
                ResolvedConnectError(reason, msg)
            }
        }
    }

    /** @deprecated 使用 [resolveConnectError] */
    private fun mapConnectError(e: Throwable): String? = resolveConnectError(e)?.message
}
