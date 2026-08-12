package com.vpn.kuayun.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.kr328.clash.core.Clash
import com.vpn.kuayun.MainActivity
import com.vpn.kuayun.vpn.mihomo.MihomoInitializer
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
    private var tunInterface: ParcelFileDescriptor? = null
    private var running = false
    private var currentNodeName: String = "智能选路"
    private var lastConfig: String = ""
    private var userInitiatedDisconnect = false
    private var autoReconnectAttempts = 0
    private var notificationJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    /** native 已持有 fd 时为 true，cleanup 不可再 close PFD，防 double-close。 */
    private var tunFdDetached = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val config = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                currentNodeName = intent.getStringExtra(EXTRA_NODE_NAME) ?: "智能选路"
                serviceScope.launch { connect(config, currentNodeName) }
            }
            ACTION_RECONNECT -> {
                val config = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                currentNodeName = intent.getStringExtra(EXTRA_NODE_NAME) ?: "智能选路"
                serviceScope.launch { reconnect(config, currentNodeName) }
            }
            ACTION_DISCONNECT -> {
                userInitiatedDisconnect = true
                disconnect()
            }
            ACTION_RESTORE -> {
                serviceScope.launch { restoreFromPrefs() }
            }
        }
        return START_STICKY
    }

    private suspend fun restoreFromPrefs() {
        val config = StabilityPrefs.lastConfig(this)
        val node = StabilityPrefs.lastNodeName(this)
        if (config.isBlank()) {
            Log.w(TAG, "restore skipped: empty config")
            stopSelf()
            return
        }
        connect(config, node)
    }

    private suspend fun connect(config: String, nodeName: String) {
        if (config.isBlank()) {
            VpnConnectionBus.update(ConnectionState.FAILED, "配置为空，请重新拉取节点")
            stopSelf()
            return
        }
        if (running) return

        VpnConnectionBus.update(ConnectionState.CONNECTING, error = null)
        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            MihomoInitializer.ensureReady(application)
            startMihomo(config, nodeName)
            running = true
            lastConfig = config
            autoReconnectAttempts = 0
            userInitiatedDisconnect = false
            StabilityPrefs.markConnected(this, config, nodeName)
            VpnSessionStatsTracker.reset()
            VpnConnectionBus.update(ConnectionState.CONNECTED, error = null)
            startNotificationUpdater()
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            cleanup()
            VpnConnectionBus.update(ConnectionState.FAILED, mapTunnelError(e))
            stopSelf()
        }
    }

    private suspend fun reconnect(config: String, nodeName: String) {
        if (config.isBlank()) {
            VpnConnectionBus.update(ConnectionState.FAILED, "配置为空，请重新拉取节点")
            return
        }

        VpnConnectionBus.update(ConnectionState.CONNECTING, error = null)
        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            if (running) {
                stopMihomo()
                startMihomo(config, nodeName)
                lastConfig = config
                autoReconnectAttempts = 0
                userInitiatedDisconnect = false
                StabilityPrefs.markConnected(this, config, nodeName)
                VpnSessionStatsTracker.reset()
                VpnConnectionBus.update(ConnectionState.CONNECTED, error = null)
                startNotificationUpdater()
                updateNotification()
            } else {
                connect(config, nodeName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "reconnect failed", e)
            cleanup()
            VpnConnectionBus.update(ConnectionState.FAILED, mapTunnelError(e))
            stopSelf()
        }
    }

    private suspend fun startMihomo(configYaml: String, nodeName: String) {
        val configDir = File(filesDir, "clash")
        ClashConfigSanitizer.prepareConfigFile(configYaml, configDir)
        Clash.load(configDir).await()
        applySelectedNode(nodeName)
        openTun()
    }

    private fun applySelectedNode(nodeName: String) {
        val node = nodeName.trim()
        if (node.isBlank() || !LineAcquireNode.isAcquirable(node)) return
        val ok =
            ClashSelectorPatcher.apply(node) { group, selection ->
                val patched = Clash.patchSelector(group, selection)
                if (patched) {
                    Log.i(TAG, "patchSelector $group -> $selection")
                } else if (group != "海外直连") {
                    // 回国专线不含海外节点时失败属预期；其余失败记 warn
                    Log.w(TAG, "patchSelector failed group=$group selection=$selection")
                }
                patched
            }
        if (!ok) {
            error("android: patchSelector failed for $node")
        }
    }

    private fun openTun() {
        if (prepare(this) != null) error("android: missing vpn permission")

        val builder =
            Builder()
                .setSession("跨云")
                .setMtu(TUN_MTU)
                .addAddress(TUN_GATEWAY, TUN_SUBNET_PREFIX)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(TUN_PORTAL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // 对齐 Compose：声明底层物理网，避免「VPN 已连但系统不走隧道 / 没网」。
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return@runCatching
            val physical = findBestPhysicalNetwork(cm)
            if (physical != null) {
                builder.setUnderlyingNetworks(arrayOf(physical))
            }
        }

        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: NameNotFoundException) {
            Log.e(TAG, "addDisallowedApplication failed", e)
        }

        // 用户勾选的应用直连：旁路 TUN（暴露真实 IP）
        AppDirectConnectStore.userSelectedPackages(this).forEach { pkg ->
            if (pkg == packageName) return@forEach
            try {
                builder.addDisallowedApplication(pkg)
            } catch (e: NameNotFoundException) {
                Log.w(TAG, "skip direct connect package: $pkg", e)
            }
        }

        val pfd = builder.establish() ?: error("android: the application is not prepared or is revoked")
        // fd 交给 native 后须 detach，否则 stopTun + PFD.close 会 double-close。
        val fd = pfd.detachFd()
        pfd.close()
        tunInterface = null
        tunFdDetached = true

        Clash.startTun(
            fd = fd,
            stack = "system",
            gateway = "$TUN_GATEWAY/$TUN_SUBNET_PREFIX",
            portal = TUN_PORTAL,
            dns = TUN_DNS,
            markSocket = { socketFd -> protect(socketFd) },
            querySocketUid = { _, _, _ -> -1 },
        )

        registerNetworkCallback()
        rebindUnderlyingNetworks("after_start_tun")
    }

    private fun findBestPhysicalNetwork(cm: ConnectivityManager): Network? {
        val active = cm.activeNetwork
        val activeCaps = active?.let { cm.getNetworkCapabilities(it) }
        if (
            active != null &&
                activeCaps != null &&
                !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        ) {
            return active
        }
        return cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    /** 切网后若不更新 underlying，系统 VPN 路径易僵死（Compose 同款注释）。 */
    private fun rebindUnderlyingNetworks(reason: String) {
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return@runCatching
            val physical = findBestPhysicalNetwork(cm)
            if (physical != null) {
                setUnderlyingNetworks(arrayOf(physical))
                Log.i(TAG, "rebindUnderlyingNetworks ok reason=$reason network=$physical")
            } else {
                setUnderlyingNetworks(null)
                Log.w(TAG, "rebindUnderlyingNetworks no physical reason=$reason")
            }
        }.onFailure { e ->
            Log.e(TAG, "rebindUnderlyingNetworks failed reason=$reason", e)
        }
    }

    private fun registerNetworkCallback() {
        unregisterNetworkCallback()
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (running) rebindUnderlyingNetworks("onAvailable")
                }

                override fun onLost(network: Network) {
                    if (running) rebindUnderlyingNetworks("onLost")
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (running) rebindUnderlyingNetworks("onCapabilitiesChanged")
                }
            }
        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
            .onSuccess { networkCallback = callback }
            .onFailure { e -> Log.e(TAG, "registerNetworkCallback failed", e) }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
        }
    }

    private fun stopMihomo() {
        runCatching { Clash.stopTun() }
        if (!tunFdDetached) {
            runCatching { tunInterface?.close() }
        }
        tunInterface = null
        tunFdDetached = false
    }

    private fun disconnect() {
        if (userInitiatedDisconnect) {
            StabilityPrefs.markUserDisconnected(this)
        }
        if (!running && tunInterface == null && !tunFdDetached) {
            stopSelf()
            return
        }
        cleanup()
        VpnConnectionBus.update(ConnectionState.DISCONNECTED, error = null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        running = false
        unregisterNetworkCallback()
        stopNotificationUpdater()
        stopMihomo()
        runCatching { Clash.reset() }
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

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        ensureNotificationChannel()
        val stats = VpnSessionStatsTracker.snapshot()
        val rates = VpnSessionStatsTracker.sampleRates(stats)
        val contentText =
            buildString {
                append(currentNodeName)
                append(" · ↑ ")
                append(VpnSessionStatsTracker.formatRate(rates.uploadBps))
                append(" ↓ ")
                append(VpnSessionStatsTracker.formatRate(rates.downloadBps))
                append(" · 已用 ")
                append(VpnSessionStatsTracker.formatDuration(stats.durationMs))
            }
        val expandedText =
            buildString {
                append("累计 ↑ ")
                append(VpnSessionStatsTracker.formatBytes(stats.uploadBytes))
                append(" ↓ ")
                append(VpnSessionStatsTracker.formatBytes(stats.downloadBytes))
            }

        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "断开", disconnectIntent)
            .build()
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
                    description = "显示连接状态、节点与本次连接流量"
                },
            )
        }
    }

    override fun onRevoke() {
        disconnect()
        super.onRevoke()
    }

    override fun onDestroy() {
        cleanup()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun mapTunnelError(e: Exception): String {
        val raw = e.message?.trim().orEmpty()
        return when {
            raw.contains("vpn permission", ignoreCase = true) ||
                raw.contains("not prepared", ignoreCase = true) ||
                raw.contains("revoked", ignoreCase = true) ->
                "未获得 VPN 授权，请重新点击连接并允许系统弹窗"
            raw.contains("config", ignoreCase = true) && raw.contains("empty", ignoreCase = true) ->
                "节点配置为空，请返回连接页刷新后重试"
            raw.contains("parse", ignoreCase = true) ||
                raw.contains("decode", ignoreCase = true) ||
                raw.contains("invalid", ignoreCase = true) ->
                "节点配置解析失败，请切换节点或联系客服检查订阅配置"
            raw.isNotBlank() -> "VPN 隧道建立失败：$raw"
            else -> "VPN 隧道建立失败，请检查节点、网络或重新安装最新 App"
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.vpn.kuayun.CONNECT"
        const val ACTION_RECONNECT = "com.vpn.kuayun.RECONNECT"
        const val ACTION_DISCONNECT = "com.vpn.kuayun.DISCONNECT"
        const val ACTION_RESTORE = "com.vpn.kuayun.RESTORE"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_NODE_NAME = "node_name"
        private const val CHANNEL_ID = "vpn_tunnel"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_REFRESH_MS = 2000L
        private const val TAG = "VpnTunnelService"

        private const val TUN_MTU = 9000
        private const val TUN_SUBNET_PREFIX = 30
        private const val TUN_GATEWAY = "172.19.0.1"
        private const val TUN_PORTAL = "172.19.0.2"
        private const val TUN_DNS = "0.0.0.0"
    }
}
