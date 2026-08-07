package com.vpn.member.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vpn.member.debug.AppDebugLogger
import com.vpn.member.vpn.PrivacyForceDisconnectEvents
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.vpn.ConnectionState
import com.vpn.member.vpn.VpnConnectionBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SessionHeartbeatManager(
    private val repository: AppRepository,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private var heartbeatJob: Job? = null
    private var isForeground = false
    private var vpnConnected = false
    private var latestStatus = VpnConnectionBus.status.value

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            VpnConnectionBus.status.collect { status ->
                latestStatus = status
                vpnConnected = status.state == ConnectionState.CONNECTED
            }
        }
        ensureHeartbeatLoop()
    }

    override fun onStart(owner: LifecycleOwner) {
        isForeground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
    }

    private fun ensureHeartbeatLoop() {
        if (heartbeatJob?.isActive == true) {
            return
        }
        heartbeatJob = scope.launch {
            while (isActive) {
                if (repository.isLoggedIn && (isForeground || vpnConnected)) {
                    runCatching {
                        val result =
                            repository.sendHeartbeat(
                                vpnConnected = vpnConnected,
                                probeStatus = latestStatus.probeStatus,
                                connectedNode = latestStatus.connectedNode,
                                probeLatencyMs = latestStatus.probeLatencyMs,
                                exitIp = latestStatus.exitIp,
                                exitCountry = latestStatus.exitCountry,
                                exitCity = latestStatus.exitCity,
                            )
                        val reason = result.force_disconnect_reason?.trim().orEmpty()
                        if (!result.subscription_active && reason.isNotBlank() && vpnConnected) {
                            PrivacyForceDisconnectEvents.publish(reason)
                        }
                    }.onFailure { e ->
                        AppDebugLogger.warn(
                            category = "session",
                            message = "心跳上报失败: ${e.message ?: "unknown"}",
                        )
                    }
                }
                delay(heartbeatIntervalMs())
            }
        }
    }

    private fun heartbeatIntervalMs(): Long {
        return when {
            isForeground -> FOREGROUND_INTERVAL_MS
            vpnConnected -> BACKGROUND_VPN_INTERVAL_MS
            else -> BACKGROUND_IDLE_INTERVAL_MS
        }
    }

    companion object {
        private const val FOREGROUND_INTERVAL_MS = 90_000L
        private const val BACKGROUND_VPN_INTERVAL_MS = 240_000L
        private const val BACKGROUND_IDLE_INTERVAL_MS = 300_000L
    }
}
