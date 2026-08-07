package com.vpn.member.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpn.member.data.repository.AppRepository
import com.vpn.member.vpn.AlwaysOnVpnDetector
import com.vpn.member.vpn.BatteryOptimizationGuide
import com.vpn.member.vpn.PrivacyLeakProbe
import com.vpn.member.vpn.PrivacyProbeHistoryStore
import com.vpn.member.vpn.ConnectTimingArchive
import com.vpn.member.vpn.ProtectionLevelChangeStore
import com.vpn.member.vpn.PrivacyOnboardingStore
import com.vpn.member.vpn.ConnectionState
import com.vpn.member.vpn.ProtectionLevel
import com.vpn.member.vpn.ProtectionLevelResolver
import com.vpn.member.vpn.ProbeStatus
import com.vpn.member.vpn.TunStackMode
import com.vpn.member.vpn.VpnConnectionBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StabilitySettingsUiState(
    val autoReconnectEnabled: Boolean = true,
    val killSwitchEnabled: Boolean = true,
    val ipv6ProtectionEnabled: Boolean = true,
    val reconnectKillSwitchHoldEnabled: Boolean = true,
    val blockOnConnectFailureEnabled: Boolean = false,
    val bootAutoConnectEnabled: Boolean = false,
    val batteryOptimizationIgnored: Boolean = true,
    val tunStackMode: String = TunStackMode.SYSTEM,
    val protectionLabel: String = "隐私保护已就绪 · 连接后生效",
    val protectionLevelName: String = "BASELINE_READY",
    val vpnConnected: Boolean = false,
    val alwaysOnConfigured: Boolean = false,
    val lockdownConfigured: Boolean = false,
    val protectionIncomplete: Boolean = false,
    val directConnectCount: Int = 0,
    val directBypassRuleCount: Int = 0,
    val lastPrivacyProbeAt: Long = 0L,
    val privacyProbeRunning: Boolean = false,
    val privacyProbeMessage: String? = null,
    val privacyProbeHistory: List<PrivacyProbeHistoryStore.Entry> = emptyList(),
    val protectionChangeHistory: List<ProtectionLevelChangeStore.Entry> = emptyList(),
    val connectTimingSummary: ConnectTimingArchive.Summary? = null,
    val showAdvanced: Boolean = false,
    val showDisableKillSwitchConfirm: Boolean = false,
    val toastMessage: String? = null,
    /** 最近一次 TUN 栈自动切换说明（来自 VpnTunnelService）。 */
    val tunStackAutoSwitchNote: String? = null,
)

class StabilitySettingsViewModel(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(StabilitySettingsUiState())
    val state: StateFlow<StabilitySettingsUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            VpnConnectionBus.status.collect { status ->
                val probe =
                    status.probeStatus?.let { raw ->
                        runCatching { ProbeStatus.valueOf(raw.uppercase()) }.getOrNull()
                    } ?: ProbeStatus.IDLE
                val level =
                    ProtectionLevelResolver.resolve(
                        status.state,
                        status.error,
                        probe,
                        privacyBaselineReady = repository.isPrivacyBaselineReady(),
                    )
                val prevName = _state.value.protectionLevelName
                if (prevName != level.name) {
                    val from =
                        runCatching { ProtectionLevel.valueOf(prevName) }.getOrNull()
                    ProtectionLevelChangeStore.appendLevelChange(
                        repository.applicationContext(),
                        from = from,
                        to = level,
                        reason = "vpn_status",
                    )
                }
                _state.value =
                    _state.value.copy(
                        protectionLabel = ProtectionLevelResolver.label(level, status.exitIp),
                        protectionLevelName = level.name,
                        vpnConnected = status.state == ConnectionState.CONNECTED,
                        protectionChangeHistory =
                            ProtectionLevelChangeStore.load(repository.applicationContext()),
                    )
            }
        }
    }

    fun refresh() {
        val ctx = repository.applicationContext()
        val alwaysOn = AlwaysOnVpnDetector.detect(ctx)
        val onboardingStore = PrivacyOnboardingStore(ctx)
        val protectionIncomplete =
            onboardingStore.hasSkippedSystemHardening() ||
                !alwaysOn.isHardened ||
                !BatteryOptimizationGuide.isIgnoringBatteryOptimizations(ctx)
        _state.value =
            StabilitySettingsUiState(
                autoReconnectEnabled = repository.isAutoReconnectEnabled(),
                killSwitchEnabled = repository.isKillSwitchEnabled(),
                ipv6ProtectionEnabled = repository.isIpv6LeakProtectionEnabled(),
                reconnectKillSwitchHoldEnabled = repository.isReconnectKillSwitchHoldEnabled(),
                blockOnConnectFailureEnabled = repository.isBlockOnConnectFailureEnabled(),
                bootAutoConnectEnabled = repository.isBootAutoConnectEnabled(),
                batteryOptimizationIgnored =
                    BatteryOptimizationGuide.isIgnoringBatteryOptimizations(ctx),
                tunStackMode = repository.getTunStackMode(),
                tunStackAutoSwitchNote = repository.getTunStackAutoSwitchNote(),
                protectionLabel = _state.value.protectionLabel,
                protectionLevelName = _state.value.protectionLevelName,
                alwaysOnConfigured = alwaysOn.alwaysOnConfigured,
                lockdownConfigured = alwaysOn.lockdownConfigured,
                protectionIncomplete = protectionIncomplete,
                directConnectCount = repository.getDirectConnectPackageCount(),
                directBypassRuleCount = repository.getDirectBypassRuleCount(),
                lastPrivacyProbeAt = repository.getLastPrivacyProbeAt(),
                privacyProbeHistory = PrivacyProbeHistoryStore.load(ctx),
                protectionChangeHistory = ProtectionLevelChangeStore.load(ctx),
                connectTimingSummary = ConnectTimingArchive.summarize(ctx),
            )
    }

    fun setTunStackMode(mode: String) {
        repository.setTunStackMode(mode)
        _state.value =
            _state.value.copy(
                tunStackMode = TunStackMode.resolve(mode),
                toastMessage = "TUN 栈已切换，下次连接 VPN 后生效",
            )
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        repository.setAutoReconnectEnabled(enabled)
        _state.value = _state.value.copy(autoReconnectEnabled = enabled)
    }

    fun requestDisableKillSwitch() {
        _state.value = _state.value.copy(showDisableKillSwitchConfirm = true)
    }

    fun confirmDisableKillSwitch() {
        repository.setKillSwitchEnabled(false)
        ProtectionLevelChangeStore.appendSettingChange(
            repository.applicationContext(),
            setting = "断网保护 Kill Switch",
            enabled = false,
        )
        _state.value =
            _state.value.copy(
                killSwitchEnabled = false,
                showDisableKillSwitchConfirm = false,
                toastMessage = "断网保护已关闭，断线时可能泄露真实 IP",
                protectionChangeHistory = ProtectionLevelChangeStore.load(repository.applicationContext()),
            )
    }

    fun dismissDisableKillSwitchConfirm() {
        _state.value = _state.value.copy(showDisableKillSwitchConfirm = false)
    }

    fun setBootAutoConnectEnabled(enabled: Boolean) {
        repository.setBootAutoConnectEnabled(enabled)
        _state.value = _state.value.copy(bootAutoConnectEnabled = enabled)
    }

    fun toggleAdvanced() {
        _state.value = _state.value.copy(showAdvanced = !_state.value.showAdvanced)
    }

    fun setBlockOnConnectFailureEnabled(enabled: Boolean) {
        repository.setBlockOnConnectFailureEnabled(enabled)
        _state.value = _state.value.copy(blockOnConnectFailureEnabled = enabled)
    }

    fun runPrivacyProbe() {
        if (_state.value.privacyProbeRunning) return
        viewModelScope.launch {
            _state.value = _state.value.copy(privacyProbeRunning = true, privacyProbeMessage = null)
            val result =
                PrivacyLeakProbe.run(
                    ipv6ProtectionEnabled = repository.isIpv6LeakProtectionEnabled(),
                )
            repository.setLastPrivacyProbeAt(System.currentTimeMillis())
            PrivacyProbeHistoryStore.append(repository.applicationContext(), result)
            val message =
                if (result.passed) {
                    "自检通过：出口 IP ${result.exitIp ?: "-"}"
                } else {
                    buildString {
                        append("自检未完全通过")
                        if (!result.exitIpLooksProtected) append(" · 出口 IP 异常")
                        if (result.ipv6LocalActive) append(" · IPv6 风险")
                        if (!result.dnsReachable) append(" · DNS 异常")
                    }
                }
            _state.value =
                _state.value.copy(
                    privacyProbeRunning = false,
                    lastPrivacyProbeAt = repository.getLastPrivacyProbeAt(),
                    privacyProbeHistory = PrivacyProbeHistoryStore.load(repository.applicationContext()),
                    privacyProbeMessage = message,
                )
        }
    }

    fun openBatterySettings() {
        BatteryOptimizationGuide.openBatteryOptimizationSettings(repository.applicationContext())
        refresh()
    }

    fun openVpnSettings() {
        BatteryOptimizationGuide.openVpnSettings(repository.applicationContext())
        _state.value = _state.value.copy(toastMessage = "请在系统 VPN 设置中开启「始终开启 VPN」并禁止绕过")
    }

    fun dismissToast() {
        _state.value = _state.value.copy(toastMessage = null, privacyProbeMessage = null)
    }
}
