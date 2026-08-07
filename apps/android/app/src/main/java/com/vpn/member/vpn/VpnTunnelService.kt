package com.vpn.member.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.kr328.clash.core.Clash
import com.vpn.member.MainActivityIntents
import com.vpn.member.R
import com.vpn.member.data.local.AppPreferences
import com.vpn.member.data.local.AuthDisconnectReasonStore
import com.vpn.member.debug.AppDebugLogger
import com.vpn.member.vpn.ConnectTimingTracker
import com.vpn.member.vpn.mihomo.GeoAssetPolicy
import com.vpn.member.vpn.mihomo.MihomoDnsFilter
import com.vpn.member.vpn.mihomo.MihomoEnvironment
import com.vpn.member.vpn.mihomo.MihomoGeoAssetManager
import com.vpn.member.vpn.mihomo.MihomoInitializer
import com.vpn.member.vpn.mihomo.MihomoTunnelRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class VpnTunnelService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** native 接管 TUN fd 后不再由 ParcelFileDescriptor 关闭，避免 fdsan double-close。 */
    private var tunActive = false
    private var running = false
    private var currentNodeName: String = "智能选路"
    private var lastConfig: String = ""
    private var userInitiatedDisconnect = false
    private var autoReconnectAttempts = 0
    private var notificationJob: Job? = null
    private var tunnelWatchdogJob: Job? = null
    private var tunnelWatchdogFailStreak = 0
    private var routeModeSplit = false
    private var clientProfile: String = ConnectionScenario.PROFILE_OVERSEAS_WEAK
    private var killSwitchActive = false
    private var killSwitchPfd: ParcelFileDescriptor? = null
    private var tunStackRetryUsed = false
    /** 数据面异常后禁止热重载，必须全量重建 TUN（避免「已连接但其它 App 无网」）。 */
    private var requireFullTunnelRestart = false
    private val sessionStore by lazy { VpnSessionStore(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            null -> {
                serviceScope.launch { tryRestoreAfterProcessDeath() }
            }
            ACTION_CONNECT -> {
                val config = resolveIntentConfig(intent)
                currentNodeName = intent.getStringExtra(EXTRA_NODE_NAME) ?: "智能选路"
                val routeTarget = resolveRouteTarget(intent)
                routeModeSplit = intent.getStringExtra(EXTRA_ROUTE_MODE)?.trim() == AppRouteMode.SPLIT
                clientProfile = resolveClientProfile(intent)
                serviceScope.launch { connect(config, routeTarget) }
            }
            ACTION_RECONNECT -> {
                val config = resolveIntentConfig(intent)
                currentNodeName = intent.getStringExtra(EXTRA_NODE_NAME) ?: "智能选路"
                val routeTarget = resolveRouteTarget(intent)
                routeModeSplit = intent.getStringExtra(EXTRA_ROUTE_MODE)?.trim() == AppRouteMode.SPLIT
                clientProfile = resolveClientProfile(intent)
                serviceScope.launch { reconnect(config, routeTarget) }
            }
            ACTION_DISCONNECT -> {
                userInitiatedDisconnect = true
                releaseKillSwitch()
                sessionStore.clearSnapshot()
                disconnect()
            }
            ACTION_DISCONNECT_AUTH -> {
                userInitiatedDisconnect = false
                releaseKillSwitch()
                sessionStore.clearSnapshot()
                VpnConnectionBus.resetForSessionEnd()
                disconnect()
            }
            /** 切网/探活失败后的自动重连：保留会话快照，按需维持 Kill Switch 防真实 IP 裸奔。 */
            ACTION_DISCONNECT_FOR_RECONNECT -> {
                userInitiatedDisconnect = false
                disconnectForReconnectHold()
            }
            ACTION_HEAL_TUNNEL -> {
                serviceScope.launch { healTunnelIfRunning("intent") }
            }
            ACTION_RESTORE -> {
                serviceScope.launch { tryRestoreAfterProcessDeath() }
            }
            ACTION_RELEASE_KILL_SWITCH -> {
                releaseKillSwitch()
                if (!running) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun tryRestoreAfterProcessDeath() {
        if (running || killSwitchActive) return
        if (!VpnAuthGate.isLoggedIn(this)) {
            Log.w(TAG, "restore skipped: not logged in")
            sessionStore.clearSnapshot()
            return
        }
        val snapshot = sessionStore.readSnapshot() ?: return
        if (!snapshot.wasUserConnected || !sessionStore.isAutoReconnectEnabled()) return
        if (prepare(this) != null) return

        autoReconnectAttempts = sessionStore.incrementReconnectAttempts()
        if (autoReconnectAttempts > VpnAutoReconnectPolicy.MAX_ATTEMPTS) {
            Log.w(TAG, "restore skipped: max reconnect attempts reached")
            return
        }

        val yaml = ClashConfigStore.readOrNull(this).orEmpty().trim()
        if (yaml.isBlank()) return

        currentNodeName = snapshot.nodeName ?: "智能选路"
        routeModeSplit = snapshot.routeMode == AppRouteMode.SPLIT
        clientProfile = snapshot.profile.ifBlank { ConnectionScenario.PROFILE_OVERSEAS_WEAK }
        Log.i(TAG, "restoring vpn session attempt=$autoReconnectAttempts node=$currentNodeName")
        connect("", snapshot.nodeName.orEmpty())
    }

    private suspend fun connect(config: String, routeTarget: String) {
        if (running) return
        tunStackRetryUsed = false

        VpnConnectionBus.update(ConnectionState.CONNECTING, error = null)
        startVpnForeground()

        val yaml = resolveConfigYaml(config)
        if (yaml.isBlank()) {
            VpnConnectionBus.update(ConnectionState.FAILED, "配置为空，请返回连接页刷新后重试")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        try {
            MihomoInitializer.ensureReady(application)
            startMihomo(yaml, routeTarget)
            running = true
            isTunnelRunning = true
            lastConfig = yaml
            autoReconnectAttempts = 0
            sessionStore.resetReconnectAttempts()
            userInitiatedDisconnect = false
            releaseKillSwitch()
            VpnSessionStatsTracker.reset()
            VpnTrafficBus.clear()
            ensurePostConnectVerifiedOrThrow()
            requireFullTunnelRestart = false
            VpnConnectionBus.update(ConnectionState.CONNECTED, error = null)
            startNotificationUpdater()
            startTunnelWatchdog()
            updateNotification()
            verifyTunnelInBackground()
        } catch (e: Throwable) {
            Log.e(TAG, "connect failed", e)
            cleanup(engageKillSwitchOnUnexpected = shouldBlockOnConnectFailure())
            VpnConnectionBus.update(ConnectionState.FAILED, mapTunnelError(e))
            if (!killSwitchActive) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun reconnect(config: String, routeTarget: String) {
        VpnConnectionBus.update(ConnectionState.CONNECTING, error = null)
        startVpnForeground()

        val yaml = resolveConfigYaml(config)
        if (yaml.isBlank()) {
            VpnConnectionBus.update(ConnectionState.FAILED, "配置为空，请返回连接页刷新后重试")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        try {
            if (running) {
                val forceFullRestart = requireFullTunnelRestart || tunStackRetryUsed
                if (forceFullRestart) {
                    VpnDiag.step("full_tunnel_restart", "dataplane_recovery")
                    stopMihomo()
                    MihomoInitializer.ensureReady(application)
                    startMihomo(yaml, routeTarget)
                } else {
                    try {
                        reloadMihomoKeepingTun(yaml, routeTarget)
                    } catch (hot: Exception) {
                        Log.w(TAG, "hot reload failed, fallback full restart", hot)
                        stopMihomo()
                        MihomoInitializer.ensureReady(application)
                        startMihomo(yaml, routeTarget)
                    }
                }
                lastConfig = yaml
                autoReconnectAttempts = 0
                sessionStore.resetReconnectAttempts()
                userInitiatedDisconnect = false
                releaseKillSwitch()
                VpnSessionStatsTracker.reset()
                VpnTrafficBus.clear()
                ensurePostConnectVerifiedOrThrow()
                requireFullTunnelRestart = false
                VpnConnectionBus.update(ConnectionState.CONNECTED, error = null)
                startNotificationUpdater()
                startTunnelWatchdog()
                updateNotification()
                verifyTunnelInBackground()
            } else {
                connect(config, routeTarget)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "reconnect failed", e)
            cleanup(engageKillSwitchOnUnexpected = shouldBlockOnConnectFailure())
            VpnConnectionBus.update(ConnectionState.FAILED, mapTunnelError(e))
            if (!killSwitchActive) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /** 连接完成前必须通过：代理可达 + TUN 数据面转发（避免仅 App 自身 mixed-port 通而浏览器无网）。 */
    private suspend fun ensurePostConnectVerifiedOrThrow() {
        try {
            runPostConnectVerification(tunStack(), initialConnectPhase = true)
        } catch (e: Exception) {
            if (isNodeProxyUnreachable(e)) {
                requireFullTunnelRestart = true
                throw e
            }
            if (shouldAttemptStackRecovery(e) && tryRecoverDataplane()) {
                return
            }
            requireFullTunnelRestart = true
            throw e
        }
    }

    private fun isNodeProxyUnreachable(e: Exception): Boolean {
        val msg = e.message?.lowercase().orEmpty()
        return msg.contains("no network") || msg.contains("proxy unreachable")
    }

    /** 已连接后周期性复检；TUN 数据面失效时断开并提示全量重连。 */
    private fun verifyTunnelInBackground() {
        serviceScope.launch {
            try {
                runPostConnectVerification(tunStack())
            } catch (e: Exception) {
                if (userInitiatedDisconnect || !running) return@launch
                if (shouldAttemptStackRecovery(e) && tryRecoverDataplane()) {
                    AppDebugLogger.info(
                        category = "mihomo",
                        message = "TUN 数据面自愈：已自动切换栈并重试通过",
                    )
                    return@launch
                }
                Log.w(TAG, "post_connect verify soft-fail (keeping tunnel)", e)
                markDataplaneDegraded(e.message ?: "verify failed")
            }
        }
    }

    private fun shouldAttemptStackRecovery(e: Exception): Boolean {
        if (routeModeSplit || tunStackRetryUsed || !running) return false
        val msg = e.message?.lowercase().orEmpty()
        // 回国模式「no network」= mixed-port 经代理访问国内站失败，换 TUN 栈无效。
        if (isDomesticReturnProfile() && msg.contains("no network")) return false
        return msg.contains("dataplane inactive") ||
            msg.contains("tunnel verify failed") ||
            msg.contains("proxy unreachable")
    }

    private suspend fun runPostConnectVerification(stack: String, initialConnectPhase: Boolean = false) {
        val domesticReturn = isDomesticReturnProfile()
        val overseas = OverseasLocaleHint.isOverseasTimezone()
        val policy =
            PostConnectVerifyPolicy.resolve(
                initialConnectPhase = initialConnectPhase,
                domesticReturn = domesticReturn,
                overseasTimezone = overseas,
            )
        if (initialConnectPhase) {
            VpnDiag.step(
                "post_connect_policy",
                extras =
                    mapOf(
                        "attempts" to policy.maxAttempts.toString(),
                        "retry_ms" to policy.retryDelayMs.toString(),
                        "settle_ms" to policy.settleMs.toString(),
                        "domestic_return" to domesticReturn.toString(),
                        "overseas_tz" to overseas.toString(),
                    ),
            )
        }

        TunConnectivityVerifier.verifyOrThrow(
            splitDomesticDirect = routeModeSplit,
            domesticReturn = domesticReturn,
            maxAttempts = policy.maxAttempts,
            retryDelayMs = policy.retryDelayMs,
            settleMs = policy.settleMs,
        )
        TunDataPlaneVerifier.verifyOrThrow(
            applicationContext,
            stack,
            routeModeSplit,
            domesticReturn = domesticReturn,
        )
    }

    /** system/gvisor/mixed 在部分机型 TUN 不转发时，自动切换下一栈再验。 */
    private suspend fun tryRecoverDataplane(): Boolean {
        if (routeModeSplit || tunStackRetryUsed || !running) return false
        val current = tunStack()
        val nextStack =
            when (current) {
                TunStackMode.SYSTEM -> TunStackMode.GVISOR
                TunStackMode.GVISOR -> TunStackMode.MIXED
                // 用户偏好或上次自动切到 mixed 后仍失败：回退 gvisor（真机 3.15 mixed 上 vpn_network_ok 失败）
                TunStackMode.MIXED -> TunStackMode.GVISOR
                else -> return false
            }
        tunStackRetryUsed = true
        VpnDiag.step(
            "tun_stack_fallback",
            extras = mapOf("from" to current, "to" to nextStack),
        )
        return runCatching {
            rebuildTun(nextStack)
            delay(400)
            runPostConnectVerification(nextStack)
            AppPreferences(this).setTunStackMode(nextStack)
            AppPreferences(this).setTunStackAutoSwitchNote(current, nextStack)
            AppDebugLogger.info(
                category = "mihomo",
                message = "TUN 栈已自动切换",
                context = mapOf("stack" to nextStack),
            )
            true
        }.getOrElse { err ->
            Log.w(TAG, "tun stack fallback to $nextStack failed", err)
            VpnDiag.warn("tun_stack_fallback", err.message ?: "failed", mapOf("to" to nextStack))
            false
        }
    }

    private fun hasTrustedSessionTraffic(): Boolean {
        val snap = VpnTrafficBus.snapshot.value.stats
        return ConnectProbePolicy.shouldTrustSessionTraffic(
            snap.downloadBytes,
            snap.uploadBytes,
        )
    }

    private fun markDataplaneDegraded(detail: String) {
        val tunInactive = detail.contains("dataplane inactive", ignoreCase = true)
        if (tunInactive) {
            // App 进程走 mixed-port 不计入 TUN；此处不能用 Mihomo 总流量误判为「有网」。
            requireFullTunnelRestart = true
            disconnectDataplaneInactive(detail)
            return
        }
        if (hasTrustedSessionTraffic()) {
            VpnDiag.step(
                "post_connect_verify",
                "skip_degraded_session_has_traffic",
                mapOf("detail" to detail),
            )
            VpnConnectionBus.updateQuality(probeStatus = ProbeStatus.OK.name.lowercase())
            return
        }
        VpnConnectionBus.updateQuality(probeStatus = ProbeStatus.DEGRADED.name.lowercase())
        VpnDiag.warn(
            "post_connect_verify",
            detail,
            mapOf("keep_tunnel" to "true", "force_disconnect" to "false"),
        )
        AppDebugLogger.info(
            category = "mihomo",
            message = "探针 soft-fail，保持隧道不断开",
            context = mapOf("detail" to detail.take(120)),
        )
    }

    private fun disconnectDataplaneInactive(detail: String) {
        if (!running || userInitiatedDisconnect) return
        serviceScope.launch {
            if (!running || userInitiatedDisconnect) return@launch
            Log.w(TAG, "dataplane inactive, disconnecting tunnel: $detail")
            VpnDiag.warn(
                "post_connect_verify",
                detail,
                mapOf("keep_tunnel" to "false", "force_disconnect" to "true"),
            )
            cleanup(engageKillSwitchOnUnexpected = shouldBlockOnConnectFailure())
            VpnConnectionBus.update(
                ConnectionState.FAILED,
                DataplaneFailureMessages.dataplaneInactive(tunStackRetryUsed),
            )
            if (!killSwitchActive) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun rebuildTun(stack: String) {
        runCatching { Clash.stopTun() }
        tunActive = false
        delay(150)
        openTun(stack)
        MihomoEnvironment.refreshPhysicalDns(this)
        VpnSessionStatsTracker.reset()
        VpnTrafficBus.clear()
    }

    private fun healTunnelIfRunning(reason: String) {
        if (!running || !tunActive) return
        // WiFi↔蜂窝 / 断网再恢复：先重绑底层物理网，再刷 DNS / 健康检查。
        rebindUnderlyingNetworks(reason)
        MihomoTunnelRecovery.heal(applicationContext, reason = reason)
    }

    /**
     * 更新 VpnService 声明的底层网。建隧时只设一次；切网后若不更新，系统 VPN 路径易僵死，
     * 而 mixed-port 仍可能通，造成「探测 OK、用户没网」。
     */
    private fun rebindUnderlyingNetworks(reason: String) {
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return@runCatching
            val physical = MihomoDnsFilter.findBestPhysicalNetwork(cm)
            if (physical != null) {
                setUnderlyingNetworks(arrayOf(physical))
                VpnDiag.step(
                    "rebind_underlying",
                    extras = mapOf("reason" to reason, "network" to physical.toString()),
                )
                AppDebugLogger.info(
                    category = "network",
                    message = "已重绑底层物理网",
                    context = mapOf("reason" to reason, "network" to physical.toString()),
                )
            } else {
                setUnderlyingNetworks(null)
                VpnDiag.warn("rebind_underlying", "no_physical", mapOf("reason" to reason))
                AppDebugLogger.info(
                    category = "network",
                    message = "暂无可用物理网，已清空 underlying",
                    context = mapOf("reason" to reason),
                )
            }
        }.onFailure { e ->
            VpnDiag.warn(
                "rebind_underlying",
                e.message ?: "failed",
                mapOf("reason" to reason),
            )
            AppDebugLogger.warn(
                category = "network",
                message = "重绑底层物理网失败",
                context = mapOf("reason" to reason, "error" to (e.message ?: "unknown")),
            )
        }
    }

    private fun tunStack(): String =
        TunStackMode.resolveForSession(
            AppPreferences(this).getTunStackMode(),
            domesticReturnFull = !routeModeSplit && isDomesticReturnProfile(),
        )

    private fun isDomesticReturnProfile(): Boolean = ConnectionScenario.isDomesticReturnProfile(clientProfile)

    private fun resolveClientProfile(intent: Intent?): String {
        val fromIntent = intent?.getStringExtra(EXTRA_CLIENT_PROFILE)?.trim().orEmpty()
        if (fromIntent.isNotBlank()) return fromIntent
        return sessionStore.readSnapshot()?.profile?.takeIf { it.isNotBlank() }
            ?: ConnectionScenario.PROFILE_OVERSEAS_WEAK
    }

    /** Intent 可能为空（配置已由 VpnController 写入 files/clash/config.yaml）。 */
    private fun resolveIntentConfig(intent: Intent?): String {
        if (intent?.getBooleanExtra(EXTRA_USE_STORED_CONFIG, false) == true) {
            return ""
        }
        return intent?.getStringExtra(EXTRA_CONFIG).orEmpty()
    }

    private fun resolveConfigYaml(intentConfig: String): String {
        val fromIntent = intentConfig.trim()
        if (fromIntent.isNotBlank()) return fromIntent
        return ClashConfigStore.readOrNull(this).orEmpty().trim()
    }

    private fun resolveRouteTarget(intent: Intent?): String {
        val explicit = intent?.getStringExtra(EXTRA_ROUTE_TARGET)?.trim().orEmpty()
        if (explicit.isNotBlank()) return explicit
        return intent?.getStringExtra(EXTRA_NODE_NAME)?.trim().orEmpty()
    }

    private suspend fun startMihomo(configYaml: String, routeTarget: String) {
        VpnDiag.step("start_mihomo", routeTarget)
        val geoPolicy = MihomoGeoAssetManager.policyForRouteMode(routeModeSplit)
        if (!MihomoGeoAssetManager.awaitReady(this, geoPolicy)) {
            error("android: geodata not ready, check network and retry")
        }
        if (geoPolicy == GeoAssetPolicy.FULL_TUNNEL) {
            VpnDiag.step("bundled_assets", extras = mapOf("geo_policy" to "full_tunnel_skip"))
        }
        val configDir = ClashConfigStore.directory(this)
        val rulesetsReady = MihomoGeoAssetManager.areRulesetsReady(this)
        VpnDiag.step(
            "bundled_assets",
            extras = mapOf("rulesets_ready" to rulesetsReady.toString()),
        )
        val directBypassRules = DirectBypassRuleStore.enabledRules(AppPreferences(this))
        ClashConfigSanitizer.prepareConfigDirectory(
            configYaml,
            configDir,
            geoReady = MihomoGeoAssetManager.isGeoReady(this),
            rulesetsReady = rulesetsReady,
            directBypassRules = directBypassRules,
        )

        runCatching { stopMihomo() }
        runCatching {
            Clash.reset()
            Clash.clearOverride(Clash.OverrideSlot.Persist)
            Clash.clearOverride(Clash.OverrideSlot.Session)
        }

        MihomoEnvironment.start(this)
        Clash.load(configDir).await()
        VpnDiag.logTunnelState("after_load")
        val patchTarget = resolvePatchTarget(routeTarget, configYaml)
        applySelectedNode(patchTarget)
        VpnDiag.logProxyGroup("Proxy")
        VpnDiag.logProxyGroup("手动选择")
        openTun()
        ConnectTimingTracker.markTunReady(applicationContext)
        VpnDiag.logTraffic("after_tun")
    }

    /** 切节点时热重载配置，保持 TUN 不拆。失败由调用方回退全量重启。 */
    private suspend fun reloadMihomoKeepingTun(configYaml: String, routeTarget: String) {
        VpnDiag.step("hot_reload", routeTarget)
        val configDir = ClashConfigStore.directory(this)
        val rulesetsReady = MihomoGeoAssetManager.areRulesetsReady(this)
        val directBypassRules = DirectBypassRuleStore.enabledRules(AppPreferences(this))
        ClashConfigSanitizer.prepareConfigDirectory(
            configYaml,
            configDir,
            geoReady = MihomoGeoAssetManager.isGeoReady(this),
            rulesetsReady = rulesetsReady,
            directBypassRules = directBypassRules,
        )
        Clash.load(configDir).await()
        VpnDiag.logTunnelState("after_hot_load")
        val patchTarget = resolvePatchTarget(routeTarget, configYaml)
        applySelectedNode(patchTarget)
        MihomoEnvironment.refreshPhysicalDns(this)
        VpnDiag.logTraffic("after_hot_reload")
    }

    private fun resolvePatchTarget(routeTarget: String, configYaml: String): String {
        val requested = routeTarget.trim()
        val leaf = ClashConfigParser.resolveLeafProxyName(configYaml)?.trim().orEmpty()
        val fromGroup = ClashConfigParser.resolveEffectiveNode(configYaml)?.trim().orEmpty()
        val resolved =
            when {
                leaf.isNotBlank() -> leaf
                fromGroup.isNotBlank() -> fromGroup
                else -> requested
            }
        VpnDiag.step(
            "patch_target",
            extras =
                mapOf(
                    "requested" to requested,
                    "leaf" to leaf,
                    "group" to fromGroup,
                    "resolved" to resolved,
                ),
        )
        return resolved.ifBlank { error("android: invalid route target") }
    }

    private suspend fun applySelectedNode(routeTarget: String) {
        val target = routeTarget.trim()
        if (target.isBlank() || !LineAcquireNode.isAcquirable(target)) {
            error("android: invalid route target $target")
        }

        val patched =
            ClashSelectorPatcher.apply(target) { group, selection ->
                val ok = Clash.patchSelector(group, selection)
                if (ok) {
                    Log.i(TAG, "patchSelector $group -> $selection")
                    VpnDiag.step("patch_selector", "$group -> $selection")
                } else if (group != "海外直连") {
                    VpnDiag.warn("patch_selector", "failed", mapOf("group" to group, "selection" to selection))
                }
                ok
            }
        if (!patched) {
            error("android: patchSelector failed for $target")
        }
    }

    private fun openTun(stack: String = tunStack()) {
        if (prepare(this) != null) error("android: missing vpn permission")

        val preferences = AppPreferences(this)
        val ipv6Protection = sessionStore.isIpv6LeakProtectionEnabled()

        val builder =
            Builder()
                .setSession("跨云")
                .setMtu(TUN_MTU)
                .addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
                .addDnsServer(TUN_PORTAL)
                .setBlocking(true)

        VpnTunRoutes.applyFullTunnelRoutes(builder, ipv6Protection)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // 声明底层物理网，便于系统对 VPN Network 做 VALIDATED（与硬门禁同源）。
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return@runCatching
            val physical = MihomoDnsFilter.findBestPhysicalNetwork(cm)
            if (physical != null) {
                builder.setUnderlyingNetworks(arrayOf(physical))
            }
        }

        val directPackages = AppDirectConnectStore.directPackages(this, preferences)
        applyDirectConnectPackages(builder, directPackages)
        VpnDiag.step(
            "direct_connect",
            extras =
                mapOf(
                    "count" to directPackages.size.toString(),
                    "packages" to directPackages.sorted().joinToString(",").take(500),
                ),
        )

        val pfd = builder.establish() ?: error("android: the application is not prepared or is revoked")
        // fd 交给 native 后须 detach，否则 stopTun + PFD.close 会 double-close 闪退。
        val fd = pfd.detachFd()
        pfd.close()

        Clash.startTun(
            fd = fd,
            stack = stack,
            gateway = "$TUN_GATEWAY/$TUN_SUBNET_PREFIX",
            portal = TUN_PORTAL,
            dns = TUN_DNS,
            markSocket = { sockFd -> VpnProtector.protect(this, sockFd) },
            querySocketUid = { protocol, source, target ->
                MihomoEnvironment.querySocketUid(this, protocol, source, target)
            },
        )
        tunActive = true
        VpnDiag.step("open_tun", extras = mapOf("stack" to stack, "fd" to fd.toString()))
        MihomoEnvironment.refreshPhysicalDns(this)
    }

    private fun stopMihomo() {
        if (!tunActive) {
            MihomoEnvironment.stop()
            return
        }
        tunActive = false
        MihomoEnvironment.stop()
        runCatching { Clash.stopTun() }
    }

    private fun disconnect() {
        if (!running && !tunActive && !killSwitchActive) {
            // 隧道已停但 Bus 仍可能残留 FAILED（如数据面超时后），需同步为未连接。
            VpnConnectionBus.update(ConnectionState.DISCONNECTED, error = null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val unexpected =
            PrivacyDisconnectPolicy.shouldEngageKillSwitch(
                userInitiatedDisconnect = userInitiatedDisconnect,
                killSwitchEnabled = sessionStore.isKillSwitchEnabled(),
            )
        cleanup(engageKillSwitchOnUnexpected = unexpected)
        if (!killSwitchActive) {
            VpnConnectionBus.update(ConnectionState.DISCONNECTED, error = null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * 自动重连前拆隧道：不清理会话快照；若开启「重连期保持阻断」则立刻 Kill Switch，
     * 避免热重载/空窗期流量走物理网暴露真实 IP。
     */
    private fun disconnectForReconnectHold() {
        val hold =
            PrivacyDisconnectPolicy.shouldHoldKillSwitchDuringReconnect(
                killSwitchEnabled = sessionStore.isKillSwitchEnabled(),
                reconnectHoldEnabled = sessionStore.isReconnectKillSwitchHoldEnabled(),
            )
        if (!running && !tunActive && !killSwitchActive) {
            if (hold) {
                engageKillSwitch()
            }
            return
        }
        cleanup(engageKillSwitchOnUnexpected = hold)
        if (killSwitchActive) {
            VpnConnectionBus.update(ConnectionState.FAILED, "网络异常，正在重连并保持断网保护…")
            startVpnForeground()
            updateNotification()
        } else {
            VpnConnectionBus.update(ConnectionState.DISCONNECTED, error = null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cleanup(engageKillSwitchOnUnexpected: Boolean = false) {
        if (!running && !tunActive && !killSwitchActive) return
        running = false
        isTunnelRunning = false
        stopNotificationUpdater()
        stopTunnelWatchdog()
        stopMihomo()
        runCatching { Clash.reset() }
        VpnTrafficBus.clear()
        if (engageKillSwitchOnUnexpected && sessionStore.isKillSwitchEnabled()) {
            engageKillSwitch()
        }
    }

    private fun engageKillSwitch() {
        if (killSwitchActive) return
        val ipv6Protection = sessionStore.isIpv6LeakProtectionEnabled()
        val builder = Builder().setSession("跨云")
        VpnTunRoutes.applyKillSwitchRoutes(builder, TUN_GATEWAY, TUN_SUBNET_PREFIX, ipv6Protection)
        killSwitchPfd = builder.establish()
        killSwitchActive = killSwitchPfd != null
        if (killSwitchActive) {
            startVpnForeground()
            VpnConnectionBus.update(ConnectionState.FAILED, "Kill Switch 已启用：网络已阻断")
            Log.w(TAG, "kill switch engaged")
        }
    }

    private fun shouldBlockOnConnectFailure(): Boolean =
        sessionStore.isKillSwitchEnabled() && sessionStore.isBlockOnConnectFailureEnabled()

    private fun releaseKillSwitch() {
        killSwitchPfd?.close()
        killSwitchPfd = null
        killSwitchActive = false
    }

    private fun startNotificationUpdater() {
        notificationJob?.cancel()
        notificationJob =
            serviceScope.launch {
                while (running) {
                    updateNotification()
                    delay(NOTIFICATION_REFRESH_MS)
                }
            }
    }

    private fun stopNotificationUpdater() {
        notificationJob?.cancel()
        notificationJob = null
    }

    /** Service 内隧道看门狗：不依赖 Connect 页；连续失败则请求 Application 级重连。 */
    private fun startTunnelWatchdog() {
        tunnelWatchdogJob?.cancel()
        tunnelWatchdogFailStreak = 0
        tunnelWatchdogJob =
            serviceScope.launch {
                while (running) {
                    delay(TunnelWatchdogPolicy.INTERVAL_MS)
                    if (!running || userInitiatedDisconnect) continue
                    val physicalOnline =
                        com.vpn.member.data.network.NetworkMonitor.hasValidatedPhysicalInternet(this@VpnTunnelService)
                    val domesticReturn = ConnectionScenario.isDomesticReturnProfile(clientProfile)
                    val vpnOk =
                        TunDataPlaneVerifier.probeVpnNetworkOk(
                            context = this@VpnTunnelService,
                            domesticReturn = domesticReturn,
                            attempts = 1,
                            timeoutMs = 3_000,
                            retryDelayMs = 0L,
                            settleMs = 0L,
                        )
                    tunnelWatchdogFailStreak =
                        TunnelWatchdogPolicy.nextFailStreak(
                            vpnNetworkOk = vpnOk,
                            previousStreak = tunnelWatchdogFailStreak,
                            physicalOnline = physicalOnline,
                        )
                    if (TunnelWatchdogPolicy.shouldRequestReconnect(tunnelWatchdogFailStreak)) {
                        tunnelWatchdogFailStreak = 0
                        AppDebugLogger.warn(
                            category = "reconnect",
                            message = "隧道看门狗连续失败，请求完整重连",
                        )
                        val app = application as? com.vpn.member.VpnMemberApp
                        app?.reconnectSupervisor?.scheduleReconnect("tunnel_watchdog")
                    }
                }
            }
    }

    private fun stopTunnelWatchdog() {
        tunnelWatchdogJob?.cancel()
        tunnelWatchdogJob = null
        tunnelWatchdogFailStreak = 0
    }

    private fun refreshTrafficSnapshot(): VpnTrafficSnapshot {
        val snapshot = VpnSessionStatsTracker.tick()
        VpnTrafficBus.publish(snapshot)
        return snapshot
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        ensureNotificationChannel()
        if (killSwitchActive) {
            val openIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    MainActivityIntents.openApp(this),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val killSwitchText =
                AuthDisconnectReasonStore.peek(this)
                    ?: "Kill Switch 已启用，防止 IP 泄露"
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("跨云 · 网络已阻断")
                .setContentText(killSwitchText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(killSwitchText))
                .setSmallIcon(R.drawable.ic_kuayun_cloud_small)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        }
        val traffic = refreshTrafficSnapshot()
        val stats = traffic.stats
        val rates = traffic.rates
        val contentText =
            buildString {
                append(currentNodeName)
                append(" · ↑ ")
                append(VpnSessionStatsTracker.formatSpeed(rates.uploadBps))
                append(" ↓ ")
                append(VpnSessionStatsTracker.formatSpeed(rates.downloadBps))
                append(" · ")
                append(VpnSessionStatsTracker.formatDuration(stats.durationMs))
            }

        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                MainActivityIntents.openApp(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val disconnectIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, VpnTunnelService::class.java).apply { action = ACTION_DISCONNECT },
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("跨云已连接")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(R.drawable.ic_kuayun_cloud_small)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "断开", disconnectIntent)
            .build()
    }

    private fun startVpnForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "跨云 VPN",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "显示连接状态、节点、实时速率与连接时长"
                },
            )
        }
    }

    override fun onRevoke() {
        userInitiatedDisconnect = false
        disconnect()
        super.onRevoke()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (running) {
            MihomoTunnelRecovery.onLowMemory()
        }
    }

    override fun onDestroy() {
        if (!killSwitchActive) {
            cleanup(
                engageKillSwitchOnUnexpected =
                    PrivacyDisconnectPolicy.shouldEngageKillSwitch(
                        userInitiatedDisconnect = userInitiatedDisconnect,
                        killSwitchEnabled = sessionStore.isKillSwitchEnabled(),
                    ),
            )
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun mapTunnelError(e: Throwable): String {
        val raw = e.message?.trim().orEmpty()
        val killSwitchMsg =
            if (shouldBlockOnConnectFailure()) {
                DataplaneFailureMessages.connectFailedWithKillSwitch(currentNodeName)
            } else {
                null
            }
        return when {
            raw.contains("vpn permission", ignoreCase = true) ||
                raw.contains("not prepared", ignoreCase = true) ||
                raw.contains("revoked", ignoreCase = true) ->
                "未获得 VPN 授权，请重新点击连接并允许系统弹窗"
            raw.contains("config", ignoreCase = true) && raw.contains("empty", ignoreCase = true) ->
                "节点配置为空，请返回连接页刷新后重试"
            raw.contains("proxies", ignoreCase = true) ->
                "节点配置无效（缺少代理节点），请返回连接页刷新后重试"
            raw.contains("tunnel verify", ignoreCase = true) ||
                raw.contains("proxy unreachable", ignoreCase = true) ->
                killSwitchMsg
                    ?: DataplaneFailureMessages.tunnelVerifyFailed(
                        domesticReturn = isDomesticReturnProfile(),
                        nodeName = currentNodeName,
                    )
            raw.contains("dataplane inactive", ignoreCase = true) ->
                killSwitchMsg
                    ?: DataplaneFailureMessages.dataplaneInactive(tunStackRetryUsed)
            raw.contains("geodata not ready", ignoreCase = true) ->
                "分流规则文件未就绪，请切换网络后重试；全流量连接无需等待该步骤"
            raw.contains("geoip", ignoreCase = true) && raw.contains("mmdb", ignoreCase = true) ->
                "DNS 规则库未就绪，请更新 App 后重试；若仍失败请切换节点"
            raw.contains("parse", ignoreCase = true) ||
                raw.contains("decode", ignoreCase = true) ||
                raw.contains("invalid", ignoreCase = true) ->
                "节点配置解析失败，请切换节点或联系客服检查订阅配置"
            raw.isNotBlank() -> "VPN 隧道建立失败：$raw"
            else -> "VPN 隧道建立失败，请检查节点、网络或重新安装最新 App"
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.vpn.member.CONNECT"
        const val ACTION_RECONNECT = "com.vpn.member.RECONNECT"
        const val ACTION_DISCONNECT = "com.vpn.member.DISCONNECT"
        /** 会话失效 / 登出：非用户主动断开，启用 Kill Switch。 */
        const val ACTION_DISCONNECT_AUTH = "com.vpn.member.DISCONNECT_AUTH"
        /** 切网/探活失败后自动重连：保留快照，可选保持 Kill Switch。 */
        const val ACTION_DISCONNECT_FOR_RECONNECT = "com.vpn.member.DISCONNECT_FOR_RECONNECT"
        /** 网络/亮屏恢复时轻量自愈，不断开 TUN。 */
        const val ACTION_HEAL_TUNNEL = "com.vpn.member.HEAL_TUNNEL"
        /** Service 被杀或开机后按持久化意图恢复隧道。 */
        const val ACTION_RESTORE = "com.vpn.member.RESTORE"
        /** 用户关闭 Kill Switch 或重新连接前释放阻断 TUN。 */
        const val ACTION_RELEASE_KILL_SWITCH = "com.vpn.member.RELEASE_KILL_SWITCH"
        const val EXTRA_CONFIG = "config"
        /** 为 true 时从 files/clash/config.yaml 读取配置（避免 Intent 传大段 YAML）。 */
        const val EXTRA_USE_STORED_CONFIG = "use_stored_config"
        const val EXTRA_NODE_NAME = "node_name"
        /** Mihomo selector / 节点名（非 UI 文案，如「自动选择」）。 */
        const val EXTRA_ROUTE_TARGET = "route_target"
        const val EXTRA_ROUTE_MODE = "route_mode"
        const val EXTRA_CLIENT_PROFILE = "client_profile"
        private const val CHANNEL_ID = "vpn_tunnel"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_REFRESH_MS = 1_000L
        private const val TAG = "VpnTunnelService"

        private const val TUN_MTU = 1500
        private const val TUN_SUBNET_PREFIX = 30
        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_PORTAL = "172.19.0.2"
        private const val TUN_DNS = "0.0.0.0"

        /** 供 VpnTunnelStateSync 判断隧道是否真正建立（非 Kill Switch 空壳）。 */
        @Volatile
        var isTunnelRunning: Boolean = false
            private set
    }
}
